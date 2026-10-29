package com.fongmi.android.tv.player.subtitle;

import java.util.Objects;

/**
 * 字幕时间偏移（毫秒，正数表示字幕延后出现）。
 *
 * <p>原来的 {@code androidx.media3.common.text.SubtitleOffsets} 是 media3 分支才有的类型，
 * 支持主/副两路字幕各自偏移。官方 media3 只有一条字幕轨，因此 {@link #secondaryMs}
 * 恒为 0，保留字段只为兼容调用方与设置面板的既有代码。
 */
public final class SubtitleOffsets {

    public static final SubtitleOffsets ZERO = new SubtitleOffsets(0, 0);

    public final long primaryMs;
    public final long secondaryMs;

    public SubtitleOffsets(long primaryMs, long secondaryMs) {
        this.primaryMs = primaryMs;
        this.secondaryMs = secondaryMs;
    }

    public boolean isZero() {
        return primaryMs == 0 && secondaryMs == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SubtitleOffsets other)) return false;
        return primaryMs == other.primaryMs && secondaryMs == other.secondaryMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(primaryMs, secondaryMs);
    }

    @Override
    public String toString() {
        return "SubtitleOffsets{" + primaryMs + ", " + secondaryMs + "}";
    }
}
