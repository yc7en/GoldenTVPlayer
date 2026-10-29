package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.track.LangUtil;
import com.fongmi.android.tv.setting.DecodeSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.SpeedSetting;
import com.github.catvod.utils.Auth;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 播放器构建工具。全部基于官方 androidx.media3 1.8.0 API（ADR-9）。
 */
public final class ExoUtil {

    public static ExoPlayer buildPlayer(DefaultPreloadManager.Builder preloadManagerBuilder) {
        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(App.get()).setSkipSilenceEnabled(SpeedSetting.isSkipSilence());
        ExoPlayer player = preloadManagerBuilder.buildExoPlayer(playerBuilder);
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(true);
        return player;
    }

    /**
     * 主播放器的缓冲策略：用户设置的倍数直接放大最小/最大缓冲时长。
     */
    static LoadControl buildLoadControl() {
        int buffer = PlayerSetting.getBuffer();
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * buffer, DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * buffer, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                .build();
    }

    /**
     * 预加载器独立分配缓冲，避免把主播放器也推到同样大的目标字节数。
     */
    static LoadControl buildPreloadLoadControl(int targetBufferBytes) {
        return new DefaultLoadControl.Builder()
                .setTargetBufferBytes(targetBufferBytes)
                .build();
    }

    public static Map<String, String> extractHeaders(MediaItem item) {
        Bundle extras = item.requestMetadata.extras;
        if (extras == null) return new HashMap<>();
        return extras.keySet().stream().filter(key -> extras.getString(key) != null).collect(Collectors.toMap(key -> key, extras::getString));
    }

    public static Auth.Context extractAuth(MediaItem item) {
        return item.localConfiguration != null && item.localConfiguration.tag instanceof Auth.Context auth ? auth : null;
    }

    static DefaultTrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        if (DecodeSetting.isPreferAAC()) builder.setPreferredAudioMimeType(MimeTypes.AUDIO_AAC);
        builder.setPreferredTextLanguages(LangUtil.getPreferredTextLanguages());
        builder.setTunnelingEnabled(DecodeSetting.isTunnelingEnabled());
        builder.setForceHighestSupportedBitrate(true);
        trackSelector.setParameters(builder.build());
        return trackSelector;
    }

    static RenderersFactory buildRenderersFactory() {
        return buildRenderersFactory(null);
    }

    static RenderersFactory buildRenderersFactory(@Nullable AudioProcessor audioProcessor) {
        return new ExoRenderersFactory(audioProcessor);
    }

    private static AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams, @Nullable AudioProcessor audioProcessor) {
        DefaultAudioSink.Builder builder = new DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams);
        if (audioProcessor != null) builder.setAudioProcessors(new AudioProcessor[]{audioProcessor});
        return builder.build();
    }

    private static final class ExoRenderersFactory extends DefaultRenderersFactory {

        @Nullable
        private final AudioProcessor audioProcessor;

        private ExoRenderersFactory(@Nullable AudioProcessor audioProcessor) {
            super(App.get());
            this.audioProcessor = audioProcessor;
            setEnableDecoderFallback(true);
            experimentalSetEnableMediaCodecVideoRendererPrewarming(true);
            setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
        }

        @Override
        protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
            return ExoUtil.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams, audioProcessor);
        }
    }
}
