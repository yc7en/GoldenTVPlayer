package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

import com.fongmi.android.tv.R;

import java.util.function.LongSupplier;

/**
 * 播放控制条上的进度条：时间文本 + 可拖动的 {@link DefaultTimeBar}。
 *
 * <p>原来这个控件来自 media3 分支里的 {@code androidx.media3.ui.PlayerSeekView}，官方库没有，
 * 因此用官方公开控件重新拼一个：
 *
 * <ul>
 *   <li>布局 {@code res/layout/exo_player_seek_view.xml}，leanback 侧用 {@code LeanbackTimeBar}
 *       （带遥控器按住加速），移动端用官方 {@link DefaultTimeBar}。
 *   <li>进度靠 {@link Player.Listener} 加 500ms 轮询推进；拖动期间暂停刷新，避免和手指打架。
 *   <li>时长未知时（例如网页播放、直播）回落到 {@link #setDurationSupplier} 提供的值。
 * </ul>
 */
public class PlayerSeekView extends LinearLayout implements Player.Listener {

    private static final long TICK_MS = 500L;

    private final TextView positionView;
    private final TextView durationView;
    private final DefaultTimeBar timeBar;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            update();
            if (player != null && player.isPlaying()) postDelayed(this, TICK_MS);
        }
    };

    private LongSupplier durationSupplier = () -> C.TIME_UNSET;
    private Player player;
    private boolean scrubbing;

    public PlayerSeekView(Context context) {
        this(context, null);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.exo_player_seek_view, this, true);
        positionView = findViewById(androidx.media3.ui.R.id.exo_position);
        durationView = findViewById(androidx.media3.ui.R.id.exo_duration);
        timeBar = findViewById(androidx.media3.ui.R.id.exo_progress);
        timeBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(@NonNull TimeBar bar, long position) {
                scrubbing = true;
            }

            @Override
            public void onScrubMove(@NonNull TimeBar bar, long position) {
                updateText(position, duration());
            }

            @Override
            public void onScrubStop(@NonNull TimeBar bar, long position, boolean canceled) {
                scrubbing = false;
                syncTimeBar();
                update();
            }
        });
    }

    public TimeBar getTimeBar() {
        return timeBar;
    }

    /**
     * 播放器自身拿不到时长时（网页播放等）用来兜底。
     */
    public void setDurationSupplier(@Nullable LongSupplier supplier) {
        this.durationSupplier = supplier == null ? () -> C.TIME_UNSET : supplier;
        update();
    }

    public void setPlayer(@Nullable Player player) {
        removeCallbacks(ticker);
        if (this.player != null) this.player.removeListener(this);
        this.player = player;
        if (player != null) player.addListener(this);
        scrubbing = false;
        timeBar.setEnabled(player != null);
        update();
        if (player != null) postDelayed(ticker, TICK_MS);
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED)) {
            update();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    private void update() {
        if (scrubbing) return;
        long duration = duration();
        long position = position(duration);
        syncTimeBar(duration, position);
        updateText(position, duration);
    }

    private void syncTimeBar() {
        syncTimeBar(duration(), position(duration()));
    }

    private void syncTimeBar(long duration, long position) {
        if (duration == C.TIME_UNSET || duration <= 0) timeBar.setDuration(0);
        else timeBar.setDuration(duration);
        if (duration != C.TIME_UNSET && duration > 0) timeBar.setPosition(Math.min(position, duration));
        else timeBar.setPosition(0);
    }

    private void updateText(long position, long duration) {
        positionView.setText(format(position));
        durationView.setText(duration == C.TIME_UNSET || duration <= 0 ? "--:--" : format(duration));
    }

    private long position(long duration) {
        if (player == null) return 0;
        long position = player.getCurrentPosition();
        if (position < 0) return 0;
        return duration == C.TIME_UNSET ? position : Math.min(position, duration);
    }

    private long duration() {
        if (player != null) {
            long duration = player.getDuration();
            if (duration != C.TIME_UNSET && duration > 0) return duration;
        }
        return durationSupplier.getAsLong();
    }

    private static String format(long milliseconds) {
        if (milliseconds <= 0) return "0:00";
        return DateUtils.formatElapsedTime(milliseconds / 1000);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        removeCallbacks(ticker);
        if (visibility != View.VISIBLE) return;
        update();
        if (player != null) postDelayed(ticker, TICK_MS);
    }
}
