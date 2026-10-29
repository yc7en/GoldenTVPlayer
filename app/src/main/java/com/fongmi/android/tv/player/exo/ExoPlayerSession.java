package com.fongmi.android.tv.player.exo;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager;
import androidx.media3.exoplayer.source.preload.PreloadException;
import androidx.media3.exoplayer.source.preload.PreloadManagerListener;

import com.fongmi.android.tv.App;

/**
 * 播放会话：持有 ExoPlayer 与预加载器。
 *
 * 官方 media3 只提供预加载管理（{@link DefaultPreloadManager}），没有解码器切换、
 * libass 字幕渲染与字幕转录，这些能力已随 fork AAR 一并移除（ADR-9 /
 * R-261028-0002 §4）。
 */
final class ExoPlayerSession {

    private static final String TAG = ExoPlayerSession.class.getSimpleName();
    private static final int MAX_PRELOAD_BUFFER_BYTES = 64 * 1024 * 1024;
    private static final long PRELOAD_DURATION_MS = 10_000;

    private final DefaultPreloadManager preloadManager;
    private final ExoPlayer player;

    @Nullable
    private PreloadRequest preloadRequest;

    ExoPlayerSession(AudioProcessor audioProcessor) {
        DefaultPreloadManager.Builder builder = createPreloadManagerBuilder(audioProcessor);
        this.preloadManager = builder.build();
        this.preloadManager.addListener(new PreloadListener());
        this.player = ExoUtil.buildPlayer(builder);
    }

    private DefaultPreloadManager.Builder createPreloadManagerBuilder(AudioProcessor audioProcessor) {
        DefaultPreloadManager.Builder builder = new DefaultPreloadManager.Builder(App.get(), ignored -> DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(getPreloadStartPositionMs(), PRELOAD_DURATION_MS));
        builder.setMediaSourceFactory(ExoMediaSourceFactory.create());
        builder.setRenderersFactory(ExoUtil.buildRenderersFactory(audioProcessor));
        builder.setTrackSelectorFactory(ignored -> ExoUtil.buildTrackSelector());
        builder.setLoadControl(ExoUtil.buildPreloadLoadControl(MAX_PRELOAD_BUFFER_BYTES));
        return builder;
    }

    ExoPlayer player() {
        return player;
    }

    void preload(MediaItem mediaItem, long startPositionMs, Runnable onReady, Runnable onError) {
        PreloadRequest request = new PreloadRequest(mediaItem, Math.max(0, startPositionMs), onReady, onError);
        clearPreload();
        preloadRequest = request;
        preloadManager.add(request.mediaItem(), 0);
        preloadManager.invalidate();
    }

    @Nullable
    MediaSource usePreloadedMediaSource(MediaItem mediaItem) {
        PreloadRequest request = preloadRequest;
        if (request != null && mediaItem.equals(request.mediaItem())) return preloadManager.getMediaSource(request.mediaItem());
        clearPreload();
        return null;
    }

    MediaSource createMediaSource(MediaItem mediaItem) {
        MediaSource preloaded = usePreloadedMediaSource(mediaItem);
        return preloaded != null ? preloaded : ExoMediaSourceFactory.create().createMediaSource(mediaItem);
    }

    void clearPreload() {
        PreloadRequest request = preloadRequest;
        if (request == null) return;
        preloadRequest = null;
        preloadManager.remove(request.mediaItem());
    }

    void release() {
        preloadRequest = null;
        preloadManager.release();
        player.release();
    }

    long getPreloadStartPositionMs() {
        return preloadRequest == null ? 0 : preloadRequest.startPositionMs();
    }

    private final class PreloadListener implements PreloadManagerListener {

        @Override
        public void onCompleted(@NonNull MediaItem mediaItem) {
            onPreloadReady(mediaItem);
        }

        private void onPreloadReady(MediaItem mediaItem) {
            PreloadRequest request = preloadRequest;
            if (request == null || !mediaItem.equals(request.mediaItem())) return;
            Log.d(TAG, "Preload ready");
            request.onReady().run();
        }

        @Override
        public void onError(@NonNull PreloadException exception) {
            PreloadRequest request = preloadRequest;
            if (request == null || !exception.mediaItem.equals(request.mediaItem())) return;
            Log.w(TAG, "Preload failed", exception);
            clearPreload();
            request.onError().run();
        }
    }

    private record PreloadRequest(MediaItem mediaItem, long startPositionMs, Runnable onReady, Runnable onError) {
    }
}
