package com.fongmi.android.tv.player.engine;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.effect.PlayerEffect;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.google.gson.JsonObject;

/**
 * 播放引擎契约。
 *
 * 只描述官方 media3 能表达的能力（ADR-9）：解码模式切换、多字幕轨、
 * 章节/剪辑版/碟片菜单都属于已移除的 fork 能力（R-261028-0002 §4）。
 */
public interface PlayerEngine {

    Type getType();

    Player getPlayer();

    @Nullable
    default JsonObject getPlaybackLog() {
        return null;
    }

    int getAudioChannelCount();

    void release();

    default void prepareForNewMedia() {
    }

    /**
     * 解码失败后的重试。官方 media3 由 {@code setEnableDecoderFallback(true)}
     * 在渲染器内部自动回退，引擎层不再做显式切换。
     */
    default boolean switchDecoderForRetry(PlaybackException exception) {
        return false;
    }

    default PlayerEffect getEffect() {
        return PlayerEffect.NONE;
    }

    boolean setMediaItem(PlaySpec spec, long startPositionMs);

    default void start(PlaySpec spec, long startPositionMs) {
        if (!setMediaItem(spec, startPositionMs)) return;
        getPlayer().prepare();
        getPlayer().play();
    }

    default void setEnding(long endingMs) {
    }

    default boolean isEndingManaged() {
        return false;
    }

    default long getOriginalDurationMs() {
        return C.TIME_UNSET;
    }

    default boolean preload(PlaySpec spec, long startPositionMs) {
        return preload(spec, startPositionMs, () -> {});
    }

    default boolean preload(PlaySpec spec, long startPositionMs, Runnable onError) {
        return false;
    }

    @Nullable
    default PlaySpec consumePreload(MediaItem mediaItem) {
        return null;
    }

    default boolean seekToNext() {
        return false;
    }

    default void clearPreload() {
    }

    default void bindPlayerView(PlayerView playerView) {
    }

    void stop();

    default void applySubtitleStyle() {
    }

    default boolean addSubtitle(Sub sub) {
        return false;
    }

    String getErrorMessage(PlaybackException e);

    ErrorAction handleError(PlaybackException e);

    enum ErrorAction {
        RECOVERED,
        DECODE,
        REFRESH,
        FATAL
    }

    enum Type {
        EXO
    }
}
