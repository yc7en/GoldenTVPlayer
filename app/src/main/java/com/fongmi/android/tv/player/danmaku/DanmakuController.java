package com.fongmi.android.tv.player.danmaku;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 弹幕控制器：绑定 PlayerView、按需拉取弹幕文件、跟随播放进度推进渲染。
 *
 * 取代 fork 的 {@code androidx.media3.ui.danmaku.DanmakuPlayerViewController}，
 * 只依赖官方 media3 的 PlayerView（ADR-9）。
 */
public final class DanmakuController implements AutoCloseable {

    private static final long TICK_MS = 200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::onTick;

    @Nullable
    private PlayerView playerView;
    @Nullable
    private DanmakuView view;
    @Nullable
    private OkHttpClient client;
    @Nullable
    private Call call;
    @Nullable
    private Player player;
    @Nullable
    private Uri source;
    private DanmakuConfig currentConfig = DanmakuConfig.DEFAULT;
    private long generation;
    private boolean enabled = true;
    private boolean closed;
    private long lastPosition = -1;

    private final Player.Listener playerListener = new Player.Listener() {

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (view != null) view.setPlaying(isPlaying && enabled);
            scheduleTick();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (view != null) view.setPlaying(state == Player.STATE_READY && player != null && player.isPlaying() && enabled);
            if (state == Player.STATE_READY) syncPosition(true);
        }

        @Override
        public void onPositionDiscontinuity(@Nullable Player.PositionInfo oldPosition, @Nullable Player.PositionInfo newPosition, int reason) {
            syncPosition(true);
        }
    };

    public void setOkHttpClient(@Nullable OkHttpClient client) {
        this.client = client;
    }

    @MainThread
    public void bind(@Nullable PlayerView playerView) {
        if (closed || this.playerView == playerView) return;
        detachPlayer();
        removeView();
        this.playerView = playerView;
        if (playerView == null) return;
        this.player = playerView.getPlayer();
        if (player != null) player.addListener(playerListener);
        attachView(playerView);
        if (view != null) view.setConfig(currentConfig);
        reload();
        scheduleTick();
    }

    private void attachView(PlayerView playerView) {
        if (!(playerView.getParent() instanceof ViewGroup parent)) return;
        DanmakuView created = new DanmakuView(playerView.getContext());
        created.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        created.setClickable(false);
        created.setFocusable(false);
        int index = parent.indexOfChild(playerView);
        parent.addView(created, Math.min(parent.getChildCount(), index + 1));
        view = created;
    }

    private void removeView() {
        DanmakuView removed = view;
        view = null;
        if (removed == null) return;
        if (removed.getParent() instanceof ViewGroup parent) parent.removeView(removed);
    }

    public void setConfig(@Nullable DanmakuConfig config) {
        currentConfig = config == null ? DanmakuConfig.DEFAULT : config;
        if (view != null) view.setConfig(currentConfig);
    }

    @MainThread
    public void setEnabled(boolean enabled) {
        if (closed || this.enabled == enabled) return;
        this.enabled = enabled;
        if (view != null) {
            view.setVisibility(enabled ? View.VISIBLE : View.GONE);
            view.setPlaying(enabled && player != null && player.isPlaying());
        }
        scheduleTick();
    }

    @MainThread
    public void setDataSource(@Nullable Uri uri) {
        if (closed || Objects.equals(source, uri)) return;
        source = uri;
        reload();
    }

    private void reload() {
        generation++;
        cancel();
        if (view != null) view.setItems(List.of());
        Uri uri = enabled ? source : null;
        if (uri == null || client == null || view == null) return;
        Request request = new Request.Builder().url(uri.toString()).build();
        call = client.newCall(request);
        long token = generation;
        call.enqueue(new Callback() {

            @Override
            public void onFailure(Call ignored, IOException e) {
                deliver(token, List.of());
            }

            @Override
            public void onResponse(Call ignored, Response response) {
                List<DanmakuItem> parsed;
                try (Response closeable = response) {
                    parsed = closeable.isSuccessful() && closeable.body() != null ? DanmakuParser.parse(closeable.body().string()) : List.of();
                } catch (IOException e) {
                    parsed = List.of();
                }
                deliver(token, parsed);
            }
        });
    }

    private void deliver(long token, List<DanmakuItem> parsed) {
        handler.post(() -> {
            if (closed || token != generation || view == null) return;
            view.setItems(parsed);
            syncPosition(true);
        });
    }

    /** 本机发送的弹幕立即上屏。 */
    @MainThread
    public void sendNow(@Nullable String text) {
        if (text == null || text.isBlank() || view == null || player == null) return;
        long position = Math.max(0, player.getCurrentPosition());
        view.append(new DanmakuItem(text, position, DanmakuItem.TYPE_SCROLL, 0xffffffff, 0, null));
    }

    public List<DanmakuItem> getItems() {
        return view == null ? List.of() : view.getItems();
    }

    private void scheduleTick() {
        handler.removeCallbacks(tick);
        if (!closed && enabled && player != null && player.isPlaying()) handler.postDelayed(tick, TICK_MS);
    }

    private void onTick() {
        if (closed) return;
        syncPosition(false);
        scheduleTick();
    }

    private void syncPosition(boolean force) {
        if (view == null || player == null) return;
        long position = player.getCurrentPosition();
        if (!force && position == lastPosition) return;
        lastPosition = position;
        view.setPlaying(enabled && player.isPlaying());
        if (force) view.seekTo(position);
        else view.updatePosition(position);
    }

    private void detachPlayer() {
        if (player == null) return;
        player.removeListener(playerListener);
        player = null;
    }

    private void cancel() {
        Call pending = call;
        call = null;
        if (pending != null) pending.cancel();
    }

    /** 直播 / 站点 Spider 推来的单条弹幕（无时间轴，落到当前播放位置）。 */
    @MainThread
    public void post(@Nullable DanmakuItem item) {
        if (item == null || view == null || closed) return;
        long position = player == null ? 0 : Math.max(0, player.getCurrentPosition());
        view.append(new DanmakuItem(item.text, position, item.type, item.color, item.size, item.colors));
    }

    @Override
    @MainThread
    public void close() {
        if (closed) return;
        closed = true;
        generation++;
        handler.removeCallbacksAndMessages(null);
        cancel();
        detachPlayer();
        removeView();
        playerView = null;
    }
}
