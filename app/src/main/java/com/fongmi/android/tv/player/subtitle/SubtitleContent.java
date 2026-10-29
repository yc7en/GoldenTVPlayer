package com.fongmi.android.tv.player.subtitle;

import java.util.List;

/**
 * 字幕内容（逐句文本列表）。
 *
 * <p>原来的实现是 media3 分支的 {@code androidx.media3.common.text.SubtitleContent}：
 * 播放器在解析字幕时顺便把每一句 cue 缓存下来，供「字幕内容」列表展示与点选跳转。
 * 官方 media3 没有这样的接口，{@link com.fongmi.android.tv.player.PlayerManager#getSubtitleContent}
 * 一律返回 {@link #UNSUPPORTED}，设置面板里的「字幕」页签也已隐藏。
 *
 * <p>保留这个类型是为了让 UI 层的字幕内容代码仍能编译，等将来真的需要时再补实现。
 */
public final class SubtitleContent {

    public enum State {
        LOADING, COMPLETE, ERROR, PARTIAL, TRUNCATED, UNSUPPORTED
    }

    public static final SubtitleContent LOADING = new SubtitleContent(State.LOADING, List.of());
    public static final SubtitleContent UNSUPPORTED = new SubtitleContent(State.UNSUPPORTED, List.of());

    public final State state;
    public final List<Entry> entries;

    public SubtitleContent(State state, List<Entry> entries) {
        this.state = state;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record Entry(boolean image, String text, long startTimeUs, long endTimeUs) {
    }
}
