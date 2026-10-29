package com.fongmi.android.tv.player.effect.audio;

import java.util.Arrays;

/**
 * 音频效果的完整参数快照。由 {@link AudioEffectConfig} 从设置里生成，交给 {@link AudioEffectProcessor} 生效。
 *
 * <p>这份快照是不可变的：参数变化时重新生成一个新快照，避免处理线程读到半改状态。
 */
public final class AudioEffectParameters {

    public static final AudioEffectParameters DEFAULT = builder().build();

    private final int channelMode;
    private final int offsetMs;
    private final int centerGainMillibels;
    private final int boostMillibels;
    private final int preampMillibels;
    private final float balance;
    private final float stability;
    private final boolean loudnessEnabled;
    private final int[] centerFrequenciesHz;
    private final short[] levelsMillibels;

    private AudioEffectParameters(Builder builder) {
        this.channelMode = AudioChannelMode.clamp(builder.channelMode);
        this.offsetMs = Math.clamp(builder.offsetMs, -10_000, 10_000);
        this.centerGainMillibels = builder.centerGainMillibels;
        this.boostMillibels = builder.boostMillibels;
        this.preampMillibels = builder.preampMillibels;
        this.balance = Math.clamp(builder.balance, -1.0f, 1.0f);
        this.stability = Math.clamp(builder.stability, 0.0f, 1.0f);
        this.loudnessEnabled = builder.loudnessEnabled;
        this.centerFrequenciesHz = Arrays.copyOf(builder.centerFrequenciesHz, builder.centerFrequenciesHz.length);
        this.levelsMillibels = Arrays.copyOf(builder.levelsMillibels, builder.levelsMillibels.length);
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getChannelMode() {
        return channelMode;
    }

    /**
     * 音轨偏移，单位毫秒。正数让声音延后，负数让声音提前。
     */
    public int getOffsetMs() {
        return offsetMs;
    }

    public int getCenterGainMillibels() {
        return centerGainMillibels;
    }

    public int getBoostMillibels() {
        return boostMillibels;
    }

    public int getPreampMillibels() {
        return preampMillibels;
    }

    public float getBalance() {
        return balance;
    }

    public float getStability() {
        return stability;
    }

    public boolean isLoudnessEnabled() {
        return loudnessEnabled;
    }

    public int getBandCount() {
        return Math.min(centerFrequenciesHz.length, levelsMillibels.length);
    }

    public int getCenterFrequencyHz(int index) {
        return centerFrequenciesHz[index];
    }

    public short getLevelMillibels(int index) {
        return levelsMillibels[index];
    }

    /**
     * 是否有均衡器频段需要生效。
     */
    public boolean hasBands() {
        if (getBandCount() == 0) return false;
        for (int i = 0; i < getBandCount(); i++) if (levelsMillibels[i] != 0) return true;
        return false;
    }

    /**
     * 是否整体需要处理。
     */
    public boolean hasEffect() {
        return hasBands() || preampMillibels != 0 || boostMillibels != 0 || balance != 0
                || stability > 0 || loudnessEnabled || !AudioChannelMode.isAuto(channelMode)
                || centerGainMillibels != 0;
    }

    /**
     * 中置增益只对 5.1(6 声道) 与 7.1(8 声道) 有意义。
     */
    public static boolean isCenterGainAvailable(int channelCount) {
        return channelCount == 6 || channelCount == 8;
    }

    public static final class Builder {

        private int channelMode = AudioChannelMode.AUTO;
        private int offsetMs;
        private int centerGainMillibels;
        private int boostMillibels;
        private int preampMillibels;
        private float balance;
        private float stability;
        private boolean loudnessEnabled;
        private int[] centerFrequenciesHz = new int[0];
        private short[] levelsMillibels = new short[0];

        public Builder setChannelMode(int channelMode) {
            this.channelMode = channelMode;
            return this;
        }

        public Builder setOffsetMs(int offsetMs) {
            this.offsetMs = offsetMs;
            return this;
        }

        public Builder setCenterGainMillibels(int centerGainMillibels) {
            this.centerGainMillibels = centerGainMillibels;
            return this;
        }

        public Builder setBoostMillibels(int boostMillibels) {
            this.boostMillibels = boostMillibels;
            return this;
        }

        public Builder setPreampMillibels(int preampMillibels) {
            this.preampMillibels = preampMillibels;
            return this;
        }

        public Builder setBalance(float balance) {
            this.balance = balance;
            return this;
        }

        public Builder setStability(float stability) {
            this.stability = stability;
            return this;
        }

        public Builder setLoudnessEnabled(boolean loudnessEnabled) {
            this.loudnessEnabled = loudnessEnabled;
            return this;
        }

        /**
         * 均衡器频段。两个数组按索引一一对应，长度不一致时取较短者。
         */
        public Builder setEqualizerBands(int[] centerFrequenciesHz, short[] levelsMillibels) {
            this.centerFrequenciesHz = centerFrequenciesHz == null ? new int[0] : centerFrequenciesHz;
            this.levelsMillibels = levelsMillibels == null ? new short[0] : levelsMillibels;
            return this;
        }

        public AudioEffectParameters build() {
            return new AudioEffectParameters(this);
        }
    }
}
