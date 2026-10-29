package com.fongmi.android.tv.player.exo;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.effect.PlayerEffect;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.server.LogSession;
import com.fongmi.android.tv.setting.DecodeSetting;
import com.fongmi.android.tv.setting.SubtitleSetting;
import com.github.catvod.utils.Auth;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * 单播放引擎实现（ADR-8）：ExoPlayer + 官方 media3 能力集（ADR-9）。
 */
public class ExoPlayerEngine implements PlayerEngine, AnalyticsListener {

    private final ExoErrorMessageProvider provider;
    private final ExoPlayerSession session;
    private final ExoPlayerEffect effect;
    private final ExoPlayer player;
    private final ExoPlaybackLog playbackLog = new ExoPlaybackLog();
    private final Runnable updateLog = this::updateEventLogger;
    private final Runnable logListener = () -> App.post(updateLog);
    private EventLogger eventLogger;
    private PlayerView boundView;
    private PlaySpec spec;
    private PlaySpec preloadSpec;
    private MediaItem preloadItem;
    private boolean preloadAttached;
    private long preloadStartPositionMs;

    public ExoPlayerEngine(Player.Listener listener) {
        this.effect = new ExoPlayerEffect();
        this.provider = new ExoErrorMessageProvider();
        this.session = new ExoPlayerSession(effect.getAudioProcessor());
        this.player = session.player();
        this.player.addListener(listener);
        this.player.addAnalyticsListener(this);
        this.player.addAnalyticsListener(playbackLog);
        this.effect.setPlayer(player);
        LogSession.get().addListener(logListener);
        updateEventLogger();
    }

    private void updateEventLogger() {
        boolean enabled = LogSession.get().isRunning();
        if (enabled && eventLogger == null) {
            eventLogger = new EventLogger("EventLogger-" + Integer.toHexString(System.identityHashCode(player)));
            player.addAnalyticsListener(eventLogger);
        } else if (!enabled && eventLogger != null) {
            player.removeAnalyticsListener(eventLogger);
            eventLogger = null;
        }
    }

    @Override
    public void onAudioTrackInitialized(@NonNull EventTime eventTime, @NonNull AudioSink.AudioTrackConfig audioTrackConfig) {
        effect.applyAudioEffect();
    }

    @Override
    public void onAudioTrackReleased(@NonNull EventTime eventTime, @NonNull AudioSink.AudioTrackConfig audioTrackConfig) {
        effect.applyAudioEffect();
    }

    @Override
    public void onAudioSinkError(@NonNull EventTime eventTime, @NonNull Exception error) {
        if (eventLogger == null) return;
        String message = String.valueOf(error.getMessage());
        int end = message.offsetByCodePoints(0, Math.min(1024, message.codePointCount(0, message.length())));
        AudioAttributes attributes = player.getAudioAttributes();
        Log.e("TV-Playback", "player=" + Integer.toHexString(System.identityHashCode(player)) + " event=audioSinkError"
                + " elapsedRealtimeMs=" + eventTime.realtimeMs + " positionMs=" + eventTime.currentPlaybackPositionMs
                + " playerAudioSessionId=" + player.getAudioSessionId() + " tunnelingSettingEnabled=" + DecodeSetting.isTunnelingEnabled()
                + " playerAudioUsage=" + attributes.usage + " playerAudioFlags=" + attributes.flags
                + " error=" + message.substring(0, end) + (end < message.length() ? " [truncated]" : ""));
    }

    @Override
    public void onTracksChanged(@NonNull EventTime eventTime, @NonNull Tracks tracks) {
        effect.applyVideoEffect();
    }

    @Override
    public Type getType() {
        return Type.EXO;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public JsonObject getPlaybackLog() {
        return playbackLog.snapshot();
    }

    @Override
    public int getAudioChannelCount() {
        Format format = player.getAudioFormat();
        return format == null ? Format.NO_VALUE : format.channelCount;
    }

    @Override
    public PlayerEffect getEffect() {
        return effect;
    }

    @Override
    public void release() {
        LogSession.get().removeListener(logListener);
        App.removeCallbacks(updateLog);
        if (eventLogger != null) {
            Log.i("TV-Playback", "player=" + Integer.toHexString(System.identityHashCode(player)) + " event=engineRelease");
            player.removeAnalyticsListener(eventLogger);
            eventLogger = null;
        }
        player.removeAnalyticsListener(this);
        player.removeAnalyticsListener(playbackLog);
        playbackLog.clear();
        effect.release();
        session.release();
    }

    @Override
    public boolean setMediaItem(PlaySpec spec, long startPositionMs) {
        this.spec = spec;
        clearPreloadQueue();
        MediaItem item = MediaItemFactory.from(this.spec);
        MediaSource source = session.createMediaSource(item);
        effect.clearAudioEffect();
        player.setMediaSource(source, startPositionMs);
        return true;
    }

    @Override
    public void setEnding(long endingMs) {
        if (spec != null) spec.setEnding(endingMs);
        if (preloadSpec != null) preloadSpec.setEnding(endingMs);
    }

    @Override
    public boolean preload(PlaySpec spec, long startPositionMs, Runnable onError) {
        if (player.getMediaItemCount() == 0 || player.getPlaybackState() == Player.STATE_IDLE || player.isCurrentMediaItemLive()) return false;
        clearPreload();
        preloadSpec = spec;
        preloadItem = MediaItemFactory.from(spec);
        preloadStartPositionMs = Math.max(0, startPositionMs);
        session.preload(preloadItem, preloadStartPositionMs, () -> onPreloadReady(preloadItem, preloadStartPositionMs), () -> onPreloadError(preloadItem, onError));
        return true;
    }

    private void onPreloadReady(MediaItem item, long startPositionMs) {
        if (preloadItem != item || preloadAttached || player.getPlaybackState() == Player.STATE_IDLE || player.getPlaybackState() == Player.STATE_ENDED) return;
        preloadAttached = true;
        player.addMediaSource(session.createMediaSource(item));
    }

    private void onPreloadError(MediaItem item, Runnable onError) {
        if (preloadItem != item) return;
        clearPreload();
        onError.run();
    }

    @Override
    public boolean seekToNext() {
        int index = player.getCurrentMediaItemIndex() + 1;
        if (!preloadAttached || preloadItem == null || index >= player.getMediaItemCount() || !preloadItem.equals(player.getMediaItemAt(index))) return false;
        player.seekTo(index, preloadStartPositionMs);
        return true;
    }

    @Override
    @Nullable
    public PlaySpec consumePreload(MediaItem item) {
        if (!preloadAttached || player.getCurrentMediaItemIndex() <= 0 || preloadItem == null || !preloadItem.equals(item)) return null;
        spec = preloadSpec;
        preloadSpec = null;
        preloadItem = null;
        preloadAttached = false;
        session.clearPreload();
        effect.applyAudioEffect();
        int index = player.getCurrentMediaItemIndex();
        if (index > 0) player.removeMediaItems(0, index);
        return spec;
    }

    @Override
    public void clearPreload() {
        clearPreloadQueue();
        session.clearPreload();
    }

    private void clearPreloadQueue() {
        preloadSpec = null;
        preloadItem = null;
        preloadAttached = false;
        int index = player.getCurrentMediaItemIndex() + 1;
        if (index < player.getMediaItemCount()) player.removeMediaItems(index, player.getMediaItemCount());
    }

    @Override
    public void bindPlayerView(PlayerView playerView) {
        this.boundView = playerView;
        playerView.setPlayer(player);
        applySubtitleStyle();
    }

    @Override
    public void applySubtitleStyle() {
        if (boundView == null) return;
        SubtitleSetting.applyStyle(boundView.getSubtitleView());
    }

    @Override
    public void stop() {
        clearPreloadQueue();
        player.stop();
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        return provider.get(e);
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        return switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> seekToDefaultPosition();
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> ErrorAction.DECODE;
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> ErrorAction.REFRESH;
            default -> ErrorAction.FATAL;
        };
    }

    private ErrorAction seekToDefaultPosition() {
        player.seekToDefaultPosition();
        player.prepare();
        return ErrorAction.RECOVERED;
    }
}
