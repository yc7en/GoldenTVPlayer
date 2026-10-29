package com.fongmi.android.tv.player.effect.audio;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 软件音频效果处理器：多段均衡、增益、声道处理、响度压缩与限幅。
 *
 * <p>设计约束（与播放管线有关，改动前请先读 {@code docs/ARCHITECTURE.md} 的音频链一节）：
 *
 * <ul>
 *   <li><b>不改变输出声道数</b>。{@code AudioSink} 只会在输入格式变化时重新配置处理器链，
 *       处理器在播放中改声道数不会即时生效。因此声道模式只重排/混合声道内容
 *       （单声道混音写回左右两路、翻转交换左右），声道数保持不变，切换可以立刻听到。
 *   <li><b>参数可在播放中热更新</b>。{@link #setParameters} 只重算系数，不清空滤波器历史，
 *       拖动均衡器滑块不会有爆音或重新起播。
 *   <li>只处理 16bit 与 float PCM。其他编码（如直通）由上层判定为不支持效果。
 * </ul>
 */
public final class AudioEffectProcessor extends BaseAudioProcessor {

    private static final float MAX_PEAK = 0.997f;
    private static final float BAND_Q = 1.41f;
    private static final int LIMITER_MIN_RELEASE_MS = 30;
    private static final int LIMITER_MAX_RELEASE_MS = 400;
    private static final int COMPRESSOR_ATTACK_MS = 8;
    private static final int COMPRESSOR_RELEASE_MS = 220;
    private static final float COMPRESSOR_THRESHOLD_DB = -18.0f;
    private static final float COMPRESSOR_RATIO = 2.0f;

    private AudioEffectParameters parameters = AudioEffectParameters.DEFAULT;

    private boolean active;
    private boolean floatEncoding;
    private int channelCount;
    private int sampleRate;

    private Biquad[][] filters = new Biquad[0][];
    private float[] channelGain = new float[0];
    private float[] compressorGain = new float[0];
    private float[] limiterGain = new float[0];
    private float[] frame = new float[0];
    private float masterGain = 1.0f;
    private float[] delayRing = new float[0];
    private int delayFrames;
    private int delayWrite;
    private int pendingAdvanceFrames;
    private boolean loudnessEnabled;
    private boolean limiterEnabled;
    private float limiterRelease;
    private float compressorAttack;
    private float compressorRelease;

    public synchronized void setParameters(AudioEffectParameters parameters) {
        this.parameters = parameters == null ? AudioEffectParameters.DEFAULT : parameters;
        if (active) {
            this.active = recompute();
        }
    }

    public synchronized AudioEffectParameters getParameters() {
        return parameters;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw new UnhandledAudioFormatException(inputAudioFormat);
        }
        this.floatEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT;
        this.channelCount = inputAudioFormat.channelCount;
        this.sampleRate = inputAudioFormat.sampleRate;
        this.active = recompute();
        return active ? inputAudioFormat : AudioFormat.NOT_SET;
    }

    @Override
    protected void onFlush() {
        clearState();
    }

    @Override
    protected void onReset() {
        clearState();
        active = false;
    }

    private void clearState() {
        for (Biquad[] chain : filters) if (chain != null) for (Biquad biquad : chain) biquad.reset();
        for (int i = 0; i < compressorGain.length; i++) compressorGain[i] = 1.0f;
        for (int i = 0; i < limiterGain.length; i++) limiterGain[i] = 1.0f;
        Arrays.fill(delayRing, 0.0f);
        delayWrite = 0;
        pendingAdvanceFrames = 0;
    }

    /**
     * 根据当前参数重算系数。返回 false 表示不需要处理（处理器整体旁路）。
     */
    private boolean recompute() {
        frame = new float[channelCount];
        channelGain = new float[channelCount];
        compressorGain = new float[channelCount];
        limiterGain = new float[channelCount];
        masterGain = 1.0f;
        limiterEnabled = false;
        loudnessEnabled = parameters.isLoudnessEnabled();
        applyOffset();
        if (!parameters.hasEffect()) {
            filters = new Biquad[0][];
            Arrays.fill(channelGain, 1.0f);
            return delayFrames > 0 || pendingAdvanceFrames > 0;
        }
        buildFilters();
        buildGains();
        buildDynamics();
        return true;
    }

    /**
     * 音轨偏移：正数用环形缓冲延迟输出，负数丢弃一次性帧数让声音提前。
     *
     * <p>只在目标延迟变化时重建缓冲，拖滑块不会反复清空历史。
     */
    private void applyOffset() {
        int frames = sampleRate <= 0 ? 0 : Math.round(parameters.getOffsetMs() / 1000.0f * sampleRate);
        if (frames > 0) {
            if (frames != delayFrames) {
                delayFrames = frames;
                delayRing = new float[frames * Math.max(1, channelCount)];
                delayWrite = 0;
            }
            pendingAdvanceFrames = 0;
        } else {
            delayFrames = 0;
            delayRing = new float[0];
            delayWrite = 0;
            pendingAdvanceFrames = -frames;
        }
    }

    private void buildFilters() {
        int bandCount = parameters.getBandCount();
        filters = new Biquad[channelCount][bandCount];
        if (bandCount == 0) return;
        for (int band = 0; band < bandCount; band++) {
            int frequency = parameters.getCenterFrequencyHz(band);
            float gainDb = parameters.getLevelMillibels(band) / 100.0f;
            for (int channel = 0; channel < channelCount; channel++) {
                Biquad biquad = new Biquad();
                if (gainDb != 0.0f) biquad.setPeaking(frequency, BAND_Q, gainDb, sampleRate);
                filters[channel][band] = biquad;
            }
        }
    }

    private void buildGains() {
        int mode = AudioChannelMode.resolve(parameters.getChannelMode(), channelCount);
        float balance = AudioChannelMode.canBalance(mode) ? parameters.getBalance() : 0.0f;
        for (int channel = 0; channel < channelCount; channel++) {
            float gain = 1.0f;
            if (channelCount == 2) {
                // 平衡量为正时衰减右声道，为负时衰减左声道，中间位置不动。
                if (channel == 0 && balance > 0) gain = 1.0f - balance;
                if (channel == 1 && balance < 0) gain = 1.0f + balance;
            }
            if (channel == 2 && AudioEffectParameters.isCenterGainAvailable(channelCount)) {
                gain *= millibelsToLinear(parameters.getCenterGainMillibels());
            }
            channelGain[channel] = gain;
        }
        masterGain = millibelsToLinear(parameters.getPreampMillibels() + parameters.getBoostMillibels());
    }

    private void buildDynamics() {
        float stability = parameters.getStability();
        // stability 为 0 时不做限幅，避免无谓的增益衰减。
        limiterEnabled = stability > 0.0f;
        int releaseMs = Math.round(LIMITER_MIN_RELEASE_MS + stability * (LIMITER_MAX_RELEASE_MS - LIMITER_MIN_RELEASE_MS));
        limiterRelease = releaseCoefficient(releaseMs);
        compressorAttack = releaseCoefficient(COMPRESSOR_ATTACK_MS);
        compressorRelease = releaseCoefficient(COMPRESSOR_RELEASE_MS);
    }

    private float releaseCoefficient(int milliseconds) {
        if (sampleRate <= 0 || milliseconds <= 0) return 0.0f;
        return (float) Math.exp(-1.0 / (milliseconds * 0.001 * sampleRate));
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int bytesPerFrame = channelCount * (floatEncoding ? 4 : 2);
        int frameCount = inputBuffer.remaining() / bytesPerFrame;
        if (frameCount == 0) return;
        // 负偏移需要丢帧，但必须保留至少一帧，否则输出缓冲长度为 0。
        int drop = Math.min(Math.max(pendingAdvanceFrames, 0), Math.max(frameCount - 1, 0));
        pendingAdvanceFrames -= drop;
        inputBuffer.position(inputBuffer.position() + drop * bytesPerFrame);
        ByteBuffer output = replaceOutputBuffer((frameCount - drop) * bytesPerFrame);
        for (int index = drop; index < frameCount; index++) {
            for (int channel = 0; channel < channelCount; channel++) frame[channel] = read(inputBuffer);
            filterFrame();
            mixChannels();
            applyGainAndDynamics();
            writeFrame(output);
        }
        output.flip();
    }

    /**
     * 延迟线：先取出 delayFrames 帧之前的样点写进输出，再把当前帧存进环形缓冲。
     */
    private void writeFrame(ByteBuffer output) {
        if (delayFrames <= 0) {
            for (int channel = 0; channel < channelCount; channel++) write(output, frame[channel]);
            return;
        }
        int base = delayWrite * channelCount;
        for (int channel = 0; channel < channelCount; channel++) {
            float delayed = delayRing[base + channel];
            delayRing[base + channel] = frame[channel];
            write(output, delayed);
        }
        delayWrite = delayWrite + 1 == delayFrames ? 0 : delayWrite + 1;
    }

    private void filterFrame() {
        if (filters.length == 0) return;
        for (int channel = 0; channel < channelCount; channel++) {
            Biquad[] chain = filters[channel];
            if (chain == null) continue;
            float sample = frame[channel];
            for (Biquad biquad : chain) sample = biquad.process(sample);
            frame[channel] = sample;
        }
    }

    /**
     * 声道模式：不改变输出声道数，只重排内容。输出声道数恒定，切换模式不需要重新配置管线。
     */
    private void mixChannels() {
        int mode = AudioChannelMode.resolve(parameters.getChannelMode(), channelCount);
        if (channelCount != 2) return;
        if (mode == AudioChannelMode.MONO) {
            float mono = (frame[0] + frame[1]) * 0.5f;
            frame[0] = mono;
            frame[1] = mono;
        } else if (mode == AudioChannelMode.REVERSE) {
            float left = frame[0];
            frame[0] = frame[1];
            frame[1] = left;
        }
    }

    private void applyGainAndDynamics() {
        boolean loudness = loudnessEnabled;
        for (int channel = 0; channel < channelCount; channel++) {
            float sample = frame[channel] * channelGain[channel] * masterGain;
            if (loudness) sample = compress(channel, sample);
            if (limiterEnabled) sample = limit(channel, sample);
            frame[channel] = sample > 1.0f ? 1.0f : sample < -1.0f ? -1.0f : sample;
        }
    }

    /**
     * 响度均衡：软拐点压缩，抬高小音量段、压住大音量段，让对白更容易听清。
     */
    private float compress(int channel, float sample) {
        float magnitude = Math.abs(sample);
        float previous = compressorGain[channel];
        float target = 1.0f;
        if (magnitude > 0.0f) {
            float levelDb = 20.0f * (float) Math.log10(magnitude);
            float over = levelDb - COMPRESSOR_THRESHOLD_DB;
            if (over > 0.0f) target = (float) Math.pow(10.0, -over * (1.0f - 1.0f / COMPRESSOR_RATIO) / 20.0f);
        }
        // 起控快、释放慢：target 小于当前增益时用攻击系数，否则用释放系数。
        float coefficient = target < previous ? compressorAttack : compressorRelease;
        float gain = target + (previous - target) * coefficient;
        compressorGain[channel] = gain;
        return sample * gain;
    }

    /**
     * 峰值限幅：只压不抬，避免均衡器提升后削顶。
     */
    private float limit(int channel, float sample) {
        float magnitude = Math.abs(sample);
        float previous = limiterGain[channel];
        float target = magnitude > MAX_PEAK ? MAX_PEAK / magnitude : 1.0f;
        float gain = target < previous ? target : target + (previous - target) * limiterRelease;
        limiterGain[channel] = gain;
        return sample * gain;
    }

    private float read(ByteBuffer buffer) {
        return floatEncoding ? buffer.getFloat() : buffer.getShort() / 32768.0f;
    }

    private void write(ByteBuffer buffer, float sample) {
        if (floatEncoding) buffer.putFloat(sample);
        else buffer.putShort((short) Math.round(sample * 32767.0f));
    }

    private int inputAudioFormatBytesPerFrame() {
        return channelCount * (floatEncoding ? 4 : 2);
    }

    private static float millibelsToLinear(int millibels) {
        return (float) Math.pow(10.0, millibels / 2000.0);
    }

    /**
     * RBJ 峰值滤波器（Peaking EQ）。每段独立状态，串成一条链即为多段均衡器。
     */
    private static final class Biquad {

        private float b0 = 1.0f;
        private float b1;
        private float b2;
        private float a1;
        private float a2;
        private float x1;
        private float x2;
        private float y1;
        private float y2;

        void setPeaking(int frequencyHz, float q, float gainDb, int sampleRate) {
            if (sampleRate <= 0) return;
            float frequency = Math.clamp(frequencyHz, 20, Math.max(21, Math.round(sampleRate * 0.45f)));
            double amplitude = Math.pow(10.0, gainDb / 40.0);
            double omega = 2.0 * Math.PI * frequency / sampleRate;
            double alpha = Math.sin(omega) / (2.0 * q);
            double a0 = 1.0 + alpha / amplitude;
            this.b0 = (float) ((1.0 + alpha * amplitude) / a0);
            this.b1 = (float) (-2.0 * Math.cos(omega) / a0);
            this.b2 = (float) ((1.0 - alpha * amplitude) / a0);
            this.a1 = (float) (-2.0 * Math.cos(omega) / a0);
            this.a2 = (float) ((1.0 - alpha / amplitude) / a0);
        }

        float process(float sample) {
            float output = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = sample;
            y2 = y1;
            y1 = output;
            return output;
        }

        void reset() {
            x1 = 0.0f;
            x2 = 0.0f;
            y1 = 0.0f;
            y2 = 0.0f;
        }
    }
}
