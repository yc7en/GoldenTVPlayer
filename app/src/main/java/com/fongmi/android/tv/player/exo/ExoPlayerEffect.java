package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Format;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.ExoPlayer;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.effect.PlayerEffect;
import com.fongmi.android.tv.player.effect.audio.AudioEffectBands;
import com.fongmi.android.tv.player.effect.audio.AudioEffectConfig;
import com.fongmi.android.tv.player.effect.audio.AudioEffectParameters;
import com.fongmi.android.tv.player.effect.audio.ExoAudioEffectController;
import com.fongmi.android.tv.player.effect.video.ExoVideoEffectController;
import com.fongmi.android.tv.player.effect.video.VideoEffectProfile;
import com.fongmi.android.tv.setting.VideoSetting;

public final class ExoPlayerEffect implements PlayerEffect {

    private final ExoAudioEffectController audioEffectController;
    private final ExoVideoEffectController videoEffectController;

    private boolean previewVideoEffect;
    private boolean previewAudioEffect;
    private boolean audioEffectFailed;
    private ExoPlayer player;

    public ExoPlayerEffect() {
        this.audioEffectController = new ExoAudioEffectController();
        this.videoEffectController = new ExoVideoEffectController();
    }

    public AudioProcessor getAudioProcessor() {
        return audioEffectController.getProcessor();
    }

    public void setPlayer(ExoPlayer player) {
        this.player = player;
        player.setVideoEffects(java.util.List.of());
    }

    public void release() {
        audioEffectController.release();
    }

    @Override
    public boolean supportsVideoEffect() {
        return true;
    }

    @Override
    public int getVideoEffectError() {
        return 0;
    }

    /**
     * 官方 media3 没有锐化效果，见 {@code ExoVideoEffectController} 的类注释。
     */
    @Override
    public boolean supportsVideoSharpness() {
        return false;
    }

    @Override
    public void applyVideoEffect() {
        videoEffectController.apply(player, getVideoProfile());
    }

    @Override
    public void previewVideoEffect(boolean original) {
        if (previewVideoEffect == original) return;
        previewVideoEffect = original;
        applyVideoEffect();
    }

    /**
     * 软件 DSP 不存在「不支持」的情况，只有应用失败；失败后不再尝试，避免反复重建。
     */
    @Override
    public boolean supportsAudioEffect() {
        return !audioEffectFailed;
    }

    @Override
    public AudioEffectBands getAudioEffectBands() {
        return AudioEffectBands.STANDARD;
    }

    @Override
    public int getAudioEffectError() {
        return audioEffectFailed ? R.string.error_audio_effect_apply : 0;
    }

    @Override
    public void applyAudioEffect() {
        int channelCount = getAudioChannelCount();
        audioEffectFailed = !audioEffectController.apply(getAudioConfig(channelCount == Format.NO_VALUE ? 2 : channelCount));
    }

    public void clearAudioEffect() {
        audioEffectController.release();
        audioEffectFailed = false;
    }

    @Override
    public void previewAudioEffect(boolean original) {
        if (previewAudioEffect == original) return;
        previewAudioEffect = original;
        applyAudioEffect();
    }

    @Override
    public boolean supportsSkipSilence() {
        return true;
    }

    @Override
    public void setSkipSilenceEnabled(boolean enabled) {
        player.setSkipSilenceEnabled(enabled);
    }

    private VideoEffectProfile getVideoProfile() {
        return previewVideoEffect ? VideoEffectProfile.off() : VideoSetting.getAppliedProfile();
    }

    private AudioEffectParameters getAudioConfig(int channelCount) {
        return previewAudioEffect ? AudioEffectParameters.DEFAULT : AudioEffectConfig.from(getAudioEffectBands(), channelCount);
    }

    private int getAudioChannelCount() {
        Format format = player.getAudioFormat();
        return format == null ? Format.NO_VALUE : format.channelCount;
    }
}
