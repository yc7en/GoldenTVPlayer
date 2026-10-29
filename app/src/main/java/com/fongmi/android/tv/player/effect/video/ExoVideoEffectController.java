package com.fongmi.android.tv.player.effect.video;

import androidx.media3.common.Effect;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.effect.Brightness;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.effect.RgbAdjustment;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 画质效果控制器：把 {@link VideoEffectProfile} 翻译成官方 media3 的 {@code GlEffect} 链。
 *
 * <p>映射关系（全部是官方公开效果，没有自造 GLSL）：
 *
 * <ul>
 *   <li>亮度 → {@link Brightness}（加法偏移）
 *   <li>对比度 → {@link Contrast}
 *   <li>饱和度 → {@link HslAdjustment#adjustSaturation}，倍数先换算成百分比增量
 *   <li>色相 → {@link HslAdjustment#adjustHue}
 *   <li>色温 → {@link RgbAdjustment} 的红/蓝增益
 *   <li>伽马 + 黑位抬升 → {@link VideoToneCurve}（3D LUT）
 * </ul>
 *
 * <p><b>锐化没有实现</b>：官方没有锐化效果，用 {@code SeparableConvolution} 自造卷积核
 * 需要显式管理采样点数，风险高于收益，因此由 {@code supportsVideoSharpness()} 上报不支持，
 * 设置面板会隐藏该滑块。预设里的 sharpness 值保留但不生效。
 */
public final class ExoVideoEffectController {

    private final List<Effect> effects = new ArrayList<>(5);
    private String signature = "";
    private boolean configured;

    public void apply(ExoPlayer player, VideoEffectProfile profile) {
        String next = profile.signature();
        boolean changed = !next.equals(signature);
        if (changed) {
            rebuild(profile);
            signature = next;
        }
        if (!configured || changed) push(player);
    }

    public void clear(ExoPlayer player) {
        signature = "";
        if (!configured) return;
        configured = false;
        effects.clear();
        player.setVideoEffects(List.of());
    }

    private void rebuild(VideoEffectProfile profile) {
        effects.clear();
        if (profile.getBrightness() != 0.0f) effects.add(new Brightness(profile.getBrightness()));
        if (profile.getContrast() != 1.0f) effects.add(new Contrast(profile.getContrast()));
        if (profile.getSaturation() != 1.0f || profile.getHue() != 0.0f) {
            effects.add(new HslAdjustment.Builder()
                    .adjustSaturation(percent(profile.getSaturation()))
                    .adjustHue(profile.getHue()).build());
        }
        if (profile.redGain() != 1.0f || profile.blueGain() != 1.0f) {
            effects.add(new RgbAdjustment.Builder()
                    .setRedScale(profile.redGain())
                    .setBlueScale(profile.blueGain()).build());
        }
        if (VideoToneCurve.isNeeded(profile.getGamma(), profile.getShadowLift())) {
            effects.add(VideoToneCurve.create(profile.getGamma(), profile.getShadowLift()));
        }
    }

    /**
     * 倍数换算成 HslAdjustment 需要的百分比增量：1.26 倍 → +26。
     */
    private static float percent(float multiplier) {
        return (multiplier - 1.0f) * 100.0f;
    }

    private void push(ExoPlayer player) {
        List<Effect> snapshot = List.copyOf(effects);
        // 暂停时先塞一个恒等链强制重绘，否则新效果要等到恢复播放才看得到。
        if (!player.getPlayWhenReady()) player.setVideoEffects(VideoFrameProcessor.REDRAW);
        player.setVideoEffects(snapshot);
        configured = !snapshot.isEmpty();
    }
}
