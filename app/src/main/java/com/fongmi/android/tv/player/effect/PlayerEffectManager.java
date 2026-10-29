package com.fongmi.android.tv.player.effect;

import com.fongmi.android.tv.player.effect.audio.AudioEffectBands;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.AudioSetting;
import com.fongmi.android.tv.setting.SpeedSetting;
import com.fongmi.android.tv.setting.VideoSetting;

import java.util.function.Supplier;

public final class PlayerEffectManager {

    private final Supplier<PlayerEngine> engineSupplier;

    public PlayerEffectManager(Supplier<PlayerEngine> engineSupplier) {
        this.engineSupplier = engineSupplier;
    }

    public boolean canSetAudioSetting() {
        return getEffect().supportsAudioEffect();
    }

    public AudioEffectBands getAudioSettingBands() {
        return getEffect().getAudioEffectBands();
    }

    public int getAudioSettingError() {
        return getEffect().getAudioEffectError();
    }

    public void setAudioSetting(int preset) {
        AudioSetting.putPreset(preset);
        refreshAudioSetting();
    }

    public void refreshAudioSetting() {
        getEffect().applyAudioEffect();
    }

    public void previewAudioSetting(boolean original) {
        getEffect().previewAudioEffect(original);
    }

    public int getAudioOffsetMs() {
        return AudioSetting.getOffsetMs();
    }

    /**
     * 音轨偏移走软件延迟线，改完必须重新下发参数才会生效。
     */
    public void setAudioOffsetMs(int offsetMs) {
        AudioSetting.putOffsetMs(offsetMs);
        refreshAudioSetting();
    }

    public boolean canSetVideoSetting() {
        return getEffect().supportsVideoEffect();
    }

    public int getVideoSettingError() {
        return getEffect().getVideoEffectError();
    }

    public boolean supportsVideoSharpness() {
        return getEffect().supportsVideoSharpness();
    }

    public void setVideoSetting(int preset) {
        VideoSetting.putPreset(preset);
        refreshVideoSetting();
    }

    public void refreshVideoSetting() {
        getEffect().applyVideoEffect();
    }

    public void previewVideoSetting(boolean original) {
        getEffect().previewVideoEffect(original);
    }

    public boolean supportsSkipSilence() {
        return getEffect().supportsSkipSilence();
    }

    public boolean isSkipSilence() {
        return SpeedSetting.isSkipSilence();
    }

    public void setSkipSilenceEnabled(boolean enabled) {
        SpeedSetting.putSkipSilence(enabled);
        getEffect().setSkipSilenceEnabled(enabled);
    }

    private PlayerEffect getEffect() {
        PlayerEngine engine = engineSupplier.get();
        return engine == null ? PlayerEffect.NONE : engine.getEffect();
    }
}
