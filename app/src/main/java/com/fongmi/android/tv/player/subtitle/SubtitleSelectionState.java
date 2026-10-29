package com.fongmi.android.tv.player.subtitle;

import androidx.annotation.Nullable;
import androidx.media3.common.TrackSelectionOverride;

import java.util.List;
import java.util.Objects;

/**
 * 当前字幕轨选择状态。
 *
 * <p>原来的 {@code androidx.media3.common.text.SubtitleSelectionState} 属于 media3 分支，
 * 描述「主字幕 + 副字幕（双语）」两路选择。官方 media3 只有一个文字渲染器，
 * 只能同时渲染一条字幕轨，因此这里的副字幕相关字段恒为空：
 *
 * <ul>
 *   <li>{@link #secondaryCandidates} 恒为空列表 —— 设置面板据此隐藏整个「高级」页签（双语字幕）。
 *   <li>{@link #activeSecondarySelection}、{@link #requestedSecondarySelection} 恒为 null。
 * </ul>
 *
 * 保留这些字段是为了让设置面板的双语相关代码不必整体删除，等将来真的需要双语时
 * 只需换回真正的实现，UI 层不用再改一遍。
 */
public final class SubtitleSelectionState {

    public static final SubtitleSelectionState EMPTY = new SubtitleSelectionState(null, null, List.of(), null, false);

    @Nullable
    public final TrackSelectionOverride primarySelection;
    @Nullable
    public final TrackSelectionOverride activeSecondarySelection;
    public final List<TrackSelectionOverride> secondaryCandidates;
    @Nullable
    public final TrackSelectionOverride requestedSecondarySelection;
    public final boolean secondaryPromotedToPrimary;

    public SubtitleSelectionState(@Nullable TrackSelectionOverride primarySelection,
                                  @Nullable TrackSelectionOverride activeSecondarySelection,
                                  List<TrackSelectionOverride> secondaryCandidates,
                                  @Nullable TrackSelectionOverride requestedSecondarySelection,
                                  boolean secondaryPromotedToPrimary) {
        this.primarySelection = primarySelection;
        this.activeSecondarySelection = activeSecondarySelection;
        this.secondaryCandidates = secondaryCandidates == null ? List.of() : List.copyOf(secondaryCandidates);
        this.requestedSecondarySelection = requestedSecondarySelection;
        this.secondaryPromotedToPrimary = secondaryPromotedToPrimary;
    }

    public static SubtitleSelectionState of(@Nullable TrackSelectionOverride primarySelection) {
        return new SubtitleSelectionState(primarySelection, null, List.of(), null, false);
    }

    public boolean isPrimary(TrackSelectionOverride selection) {
        return Objects.equals(selection, primarySelection);
    }

    public boolean isActiveSecondary(TrackSelectionOverride selection) {
        return Objects.equals(selection, activeSecondarySelection);
    }
}
