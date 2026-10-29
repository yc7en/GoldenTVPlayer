package com.fongmi.android.tv.player.danmaku;

import androidx.annotation.Nullable;

/**
 * 一条弹幕。字段可直接访问，供播放内容面板等只读场景使用。
 */
public final class DanmakuItem {

    public static final int TYPE_SCROLL = 1;
    public static final int TYPE_BOTTOM = 4;
    public static final int TYPE_TOP = 5;
    public static final int TYPE_REVERSE = 6;

    public final String text;
    /** 出现时间，单位毫秒。 */
    public final long timeMs;
    public final int type;
    public final int color;
    /** 0 表示跟随全局缩放。 */
    public final float size;
    @Nullable
    public final int[] colors;

    public DanmakuItem(String text, long timeMs, int type, int color, float size, @Nullable int[] colors) {
        this.text = text;
        this.timeMs = timeMs;
        this.type = type;
        this.color = color;
        this.size = size;
        this.colors = colors;
    }

    public boolean isScroll() {
        return type == TYPE_SCROLL || type == TYPE_REVERSE;
    }

    public boolean isReverse() {
        return type == TYPE_REVERSE;
    }

    public boolean isTop() {
        return type == TYPE_TOP;
    }

    public boolean isBottom() {
        return type == TYPE_BOTTOM;
    }
}
