package com.fongmi.android.tv.player.danmaku;

import android.graphics.Typeface;

import androidx.annotation.Nullable;

/**
 * 弹幕渲染配置。
 *
 * 原本由 fork 的 {@code androidx.media3.ui.danmaku.DanmakuConfig} 承载，
 * 官方 media3 没有弹幕能力（ADR-9），因此改为本仓库自有类型。
 */
public final class DanmakuConfig {

    public static final int STYLE_NONE = 0;
    public static final int STYLE_STROKE = 1;
    public static final int STYLE_SHADOW = 2;
    public static final int STYLE_PROJECTION = 3;

    public static final int COLOR_MODE_DEFAULT = 0;
    public static final int COLOR_MODE_COLORFUL = 1;
    public static final int COLOR_MODE_GRADIENT = 2;

    public static final DanmakuConfig DEFAULT = new Builder().build();

    public final float textScale;
    public final float transparency;
    public final boolean textBold;
    @Nullable
    public final Typeface typeface;
    public final int styleMode;
    public final float shadowTransparency;
    public final float strokeWidthMultiplier;
    public final float projectionOffsetXMultiplier;
    public final float projectionOffsetYMultiplier;
    public final float projectionTransparency;
    public final int colorMode;
    public final long durationMs;
    public final long fixedDurationMs;
    public final long timeOffsetMs;
    public final int maxOnScreen;
    public final float scrollAreaRatio;
    public final float scrollGapRatio;
    public final float lineSpacing;
    public final int maxScrollLines;
    public final int maxTopLines;
    public final int maxBottomLines;
    public final boolean showScroll;
    public final boolean showTop;
    public final boolean showBottom;
    public final boolean showReverse;
    public final boolean showPositioned;
    public final boolean showSubtitle;
    public final boolean showSpecial;

    private DanmakuConfig(Builder builder) {
        this.textScale = builder.textScale;
        this.transparency = builder.transparency;
        this.textBold = builder.textBold;
        this.typeface = builder.typeface;
        this.styleMode = builder.styleMode;
        this.shadowTransparency = builder.shadowTransparency;
        this.strokeWidthMultiplier = builder.strokeWidthMultiplier;
        this.projectionOffsetXMultiplier = builder.projectionOffsetXMultiplier;
        this.projectionOffsetYMultiplier = builder.projectionOffsetYMultiplier;
        this.projectionTransparency = builder.projectionTransparency;
        this.colorMode = builder.colorMode;
        this.durationMs = builder.durationMs;
        this.fixedDurationMs = builder.fixedDurationMs;
        this.timeOffsetMs = builder.timeOffsetMs;
        this.maxOnScreen = builder.maxOnScreen;
        this.scrollAreaRatio = builder.scrollAreaRatio;
        this.scrollGapRatio = builder.scrollGapRatio;
        this.lineSpacing = builder.lineSpacing;
        this.maxScrollLines = builder.maxScrollLines;
        this.maxTopLines = builder.maxTopLines;
        this.maxBottomLines = builder.maxBottomLines;
        this.showScroll = builder.showScroll;
        this.showTop = builder.showTop;
        this.showBottom = builder.showBottom;
        this.showReverse = builder.showReverse;
        this.showPositioned = builder.showPositioned;
        this.showSubtitle = builder.showSubtitle;
        this.showSpecial = builder.showSpecial;
    }

    public static final class Builder {

        private float textScale = 1.0f;
        private float transparency = 0.0f;
        private boolean textBold = false;
        @Nullable
        private Typeface typeface;
        private int styleMode = STYLE_STROKE;
        private float shadowTransparency = 0.1f;
        private float strokeWidthMultiplier = 0.12f;
        private float projectionOffsetXMultiplier = 0.08f;
        private float projectionOffsetYMultiplier = 0.08f;
        private float projectionTransparency = 0.2f;
        private int colorMode = COLOR_MODE_DEFAULT;
        private long durationMs = 8000L;
        private long fixedDurationMs = 5000L;
        private long timeOffsetMs = 0L;
        private int maxOnScreen = 150;
        private float scrollAreaRatio = 0.5f;
        private float scrollGapRatio = 0f;
        private float lineSpacing = 1.4f;
        private int maxScrollLines = 0;
        private int maxTopLines = 0;
        private int maxBottomLines = 0;
        private boolean showScroll = true;
        private boolean showTop = true;
        private boolean showBottom = true;
        private boolean showReverse = true;
        private boolean showPositioned = true;
        private boolean showSubtitle = true;
        private boolean showSpecial = true;

        public Builder setTextScale(float value) {
            this.textScale = value;
            return this;
        }

        public Builder setTransparency(float value) {
            this.transparency = value;
            return this;
        }

        public Builder setTextBold(boolean value) {
            this.textBold = value;
            return this;
        }

        public Builder setTypeface(@Nullable Typeface value) {
            this.typeface = value;
            return this;
        }

        public Builder setStyleMode(int value) {
            this.styleMode = value;
            return this;
        }

        public Builder setShadowTransparency(float value) {
            this.shadowTransparency = value;
            return this;
        }

        public Builder setStrokeWidthMultiplier(float value) {
            this.strokeWidthMultiplier = value;
            return this;
        }

        public Builder setProjectionOffsetXMultiplier(float value) {
            this.projectionOffsetXMultiplier = value;
            return this;
        }

        public Builder setProjectionOffsetYMultiplier(float value) {
            this.projectionOffsetYMultiplier = value;
            return this;
        }

        public Builder setProjectionTransparency(float value) {
            this.projectionTransparency = value;
            return this;
        }

        public Builder setColorMode(int value) {
            this.colorMode = value;
            return this;
        }

        public Builder setDurationMs(long value) {
            this.durationMs = value;
            return this;
        }

        public Builder setFixedDurationMs(long value) {
            this.fixedDurationMs = value;
            return this;
        }

        public Builder setTimeOffsetMs(long value) {
            this.timeOffsetMs = value;
            return this;
        }

        public Builder setMaxOnScreen(int value) {
            this.maxOnScreen = value;
            return this;
        }

        public Builder setScrollAreaRatio(float value) {
            this.scrollAreaRatio = value;
            return this;
        }

        public Builder setScrollGapRatio(float value) {
            this.scrollGapRatio = value;
            return this;
        }

        public Builder setLineSpacing(float value) {
            this.lineSpacing = value;
            return this;
        }

        public Builder setMaxScrollLines(int value) {
            this.maxScrollLines = value;
            return this;
        }

        public Builder setMaxTopLines(int value) {
            this.maxTopLines = value;
            return this;
        }

        public Builder setMaxBottomLines(int value) {
            this.maxBottomLines = value;
            return this;
        }

        public Builder setShowScroll(boolean value) {
            this.showScroll = value;
            return this;
        }

        public Builder setShowTop(boolean value) {
            this.showTop = value;
            return this;
        }

        public Builder setShowBottom(boolean value) {
            this.showBottom = value;
            return this;
        }

        public Builder setShowReverse(boolean value) {
            this.showReverse = value;
            return this;
        }

        public Builder setShowPositioned(boolean value) {
            this.showPositioned = value;
            return this;
        }

        public Builder setShowSubtitle(boolean value) {
            this.showSubtitle = value;
            return this;
        }

        public Builder setShowSpecial(boolean value) {
            this.showSpecial = value;
            return this;
        }

        public DanmakuConfig build() {
            return new DanmakuConfig(this);
        }
    }
}
