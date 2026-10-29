package com.fongmi.android.tv.player.effect.audio;

import androidx.media3.common.audio.AudioProcessor;

/**
 * 音频效果控制器。真正的 DSP 全在 {@link AudioEffectProcessor} 里，这里只做参数下发与复位。
 *
 * <p>不使用平台 {@code android.media.audiofx.*}：那套依赖具体设备的 AudioFlinger 实现，
 * 且无法保证与 ExoPlayer 的音频链路一致；软件实现可复现、可移植。
 * 代价是效果开启时会关闭音频直通（offload）。
 */
public final class ExoAudioEffectController {

    private final AudioEffectProcessor processor = new AudioEffectProcessor();

    public AudioProcessor getProcessor() {
        return processor;
    }

    public boolean apply(AudioEffectParameters config) {
        processor.setParameters(config);
        return true;
    }

    public void release() {
        processor.setParameters(AudioEffectParameters.DEFAULT);
    }
}
