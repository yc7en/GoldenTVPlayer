package com.fongmi.android.tv.player.danmaku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 弹幕绘制层：滚动 / 顶部 / 底部三类轨道，按播放位置推进。
 *
 * 取代 fork 的 {@code androidx.media3.ui.danmaku.DanmakuPlayerViewController}
 * 中的渲染部分（ADR-9）。绘制完全基于 Android Canvas，不依赖播放器扩展。
 */
public final class DanmakuView extends View {

    private static final int TINT_COLORS = 8;
    private static final long SEEK_THRESHOLD_MS = 2000L;

    private final TextPaint fillPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Active> active = new ArrayList<>();
    private final List<DanmakuItem> items = new ArrayList<>();
    private final int[] tintPalette = new int[TINT_COLORS];

    private DanmakuConfig config = DanmakuConfig.DEFAULT;
    private long positionMs;
    private int cursor;
    private boolean playing;
    private long lastFrameNs;
    private float lineHeight;

    public DanmakuView(Context context) {
        super(context);
        fillPaint.setSubpixelText(true);
        strokePaint.setStyle(Paint.Style.STROKE);
        buildPalette();
        applyTypography();
    }

    private void buildPalette() {
        for (int i = 0; i < TINT_COLORS; i++) tintPalette[i] = Color.HSVToColor(new float[]{i * 360f / TINT_COLORS, 0.55f, 1f});
    }

    public void setConfig(@Nullable DanmakuConfig config) {
        this.config = config == null ? DanmakuConfig.DEFAULT : config;
        applyTypography();
        active.clear();
        cursor = lowerBound(positionMs - this.config.durationMs);
        invalidate();
    }

    private void applyTypography() {
        fillPaint.setTypeface(config.typeface == null ? Typeface.DEFAULT : config.typeface);
        fillPaint.setFakeBoldText(config.textBold);
        fillPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, Math.max(8f, 15f * config.textScale), getResources().getDisplayMetrics()));
        Paint.FontMetrics metrics = fillPaint.getFontMetrics();
        lineHeight = (metrics.bottom - metrics.top) * config.lineSpacing;
    }

    public void setItems(@Nullable List<DanmakuItem> source) {
        items.clear();
        if (source != null) items.addAll(source);
        Collections.sort(items, (a, b) -> Long.compare(a.timeMs, b.timeMs));
        active.clear();
        cursor = Math.max(0, lowerBound(positionMs - config.durationMs));
        invalidate();
    }

    public List<DanmakuItem> getItems() {
        return items;
    }

    /** 追加上一条弹幕（本机发送 / 直播推流）。 */
    public void append(@Nullable DanmakuItem item) {
        if (item == null) return;
        int index = lowerBound(item.timeMs);
        items.add(index, item);
        if (index < cursor) cursor++;
        if (item.timeMs <= positionMs && active.size() < config.maxOnScreen) {
            Active candidate = create(item);
            if (candidate != null) active.add(candidate);
        }
        invalidate();
    }

    public void setPlaying(boolean playing) {
        if (this.playing == playing) return;
        this.playing = playing;
        lastFrameNs = 0;
        invalidate();
    }

    /** 播放位置跳变（seek / 切集）时重建轨道。 */
    public void seekTo(long positionMs) {
        this.positionMs = Math.max(0, positionMs);
        active.clear();
        cursor = lowerBound(this.positionMs - config.durationMs);
        invalidate();
    }

    /**
     * 与播放器同步位置。控制器每 200ms 左右调用一次即可，渲染帧由 View 自己推进。
     */
    public void updatePosition(long positionMs) {
        if (Math.abs(positionMs - this.positionMs) > SEEK_THRESHOLD_MS) {
            seekTo(positionMs);
            return;
        }
        this.positionMs = Math.max(0, positionMs);
        invalidate();
    }

    private int lowerBound(long targetMs) {
        int low = 0;
        int high = items.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (items.get(mid).timeMs < targetMs) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth == 0 || viewHeight == 0) return;

        if (playing) {
            long now = System.nanoTime();
            if (lastFrameNs != 0) positionMs += (long) Math.min(64f, (now - lastFrameNs) / 1_000_000f);
            lastFrameNs = now;
            spawnDue();
        }

        float baselineOffset = -fillPaint.getFontMetrics().top;
        for (Iterator<Active> iterator = active.iterator(); iterator.hasNext(); ) {
            Active entry = iterator.next();
            float progress = entry.progress(positionMs, config);
            if (progress >= 1f) {
                iterator.remove();
                continue;
            }
            drawEntry(canvas, entry, progress, viewWidth, viewHeight, baselineOffset);
        }

        if (playing) postInvalidateOnAnimation();
    }

    private void drawEntry(Canvas canvas, Active entry, float progress, int viewWidth, int viewHeight, float baselineOffset) {
        float x = entry.x(progress, viewWidth);
        float y = entry.y(viewHeight, baselineOffset);
        int alpha = Math.round(255 * (1f - config.transparency));

        if (config.colorMode == DanmakuConfig.COLOR_MODE_GRADIENT && entry.item.colors != null && entry.item.colors.length >= 2) {
            fillPaint.setShader(new LinearGradient(x, y, x + entry.width, y, entry.item.colors, null, Shader.TileMode.CLAMP));
        } else {
            fillPaint.setShader(null);
        }
        fillPaint.setColor(entry.color);
        fillPaint.setAlpha(alpha);
        fillPaint.clearShadowLayer();
        strokePaint.setShader(null);

        switch (config.styleMode) {
            case DanmakuConfig.STYLE_STROKE -> {
                strokePaint.setStrokeWidth(Math.max(1f, fillPaint.getTextSize() * config.strokeWidthMultiplier));
                strokePaint.setColor(Color.BLACK);
                strokePaint.setAlpha(Math.round(alpha * (1f - config.shadowTransparency)));
                canvas.drawText(entry.item.text, x, y, strokePaint);
            }
            case DanmakuConfig.STYLE_SHADOW -> fillPaint.setShadowLayer(Math.max(1f, fillPaint.getTextSize() * 0.08f), fillPaint.getTextSize() * 0.05f, fillPaint.getTextSize() * 0.05f, Color.argb(Math.round(alpha * (1f - config.shadowTransparency)), 0, 0, 0));
            case DanmakuConfig.STYLE_PROJECTION -> fillPaint.setShadowLayer(Math.max(1f, fillPaint.getTextSize() * 0.14f), fillPaint.getTextSize() * config.projectionOffsetXMultiplier, fillPaint.getTextSize() * config.projectionOffsetYMultiplier, Color.argb(Math.round(alpha * config.projectionTransparency), 0, 0, 0));
            default -> fillPaint.clearShadowLayer();
        }

        canvas.drawText(entry.item.text, x, y, fillPaint);
        fillPaint.setShader(null);
    }

    private void spawnDue() {
        long visibleFrom = positionMs;
        int guard = 0;
        while (cursor < items.size() && items.get(cursor).timeMs <= visibleFrom) {
            if (guard++ > 512) break;
            DanmakuItem item = items.get(cursor++);
            if (active.size() >= config.maxOnScreen) continue;
            Active candidate = create(item);
            if (candidate != null) active.add(candidate);
        }
    }

    @Nullable
    private Active create(DanmakuItem item) {
        if (item.isTop() && !config.showTop) return null;
        if (item.isBottom() && !config.showBottom) return null;
        if (item.isScroll() && !config.showScroll) return null;
        if (item.isReverse() && !config.showReverse) return null;
        if (TextUtils.isEmpty(item.text)) return null;

        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, item.size > 0 ? item.size : 15f * config.textScale, getResources().getDisplayMetrics());
        fillPaint.setTextSize(Math.max(8f, textSize));
        Paint.FontMetrics metrics = fillPaint.getFontMetrics();
        float entryLineHeight = (metrics.bottom - metrics.top) * config.lineSpacing;
        float width = fillPaint.measureText(item.text);
        int lane = allocateLane(item, entryLineHeight);
        if (lane < 0) return null;
        long duration = item.isScroll() ? config.durationMs : config.fixedDurationMs;
        return new Active(item, resolveColor(item), width, entryLineHeight, lane, duration);
    }

    private int resolveColor(DanmakuItem item) {
        if (config.colorMode == DanmakuConfig.COLOR_MODE_COLORFUL) return tintPalette[Math.floorMod(item.text.hashCode(), TINT_COLORS)];
        if (item.colors != null && item.colors.length > 0) return item.colors[0];
        return item.color;
    }

    private int laneCount(boolean scroll) {
        float usable = getHeight() * config.scrollAreaRatio;
        int available = Math.max(1, (int) (usable / Math.max(1f, lineHeight)));
        if (!scroll) return available;
        return config.maxScrollLines > 0 ? Math.min(config.maxScrollLines, available) : available;
    }

    private int fixedLaneCount(boolean top) {
        int limit = top ? config.maxTopLines : config.maxBottomLines;
        int available = laneCount(false);
        return limit > 0 ? Math.min(limit, available) : available;
    }

    private int allocateLane(DanmakuItem item, float entryLineHeight) {
        int limit = item.isScroll() ? laneCount(true) : fixedLaneCount(item.isTop());
        for (int lane = 0; lane < limit; lane++) {
            if (isLaneFree(lane, item, entryLineHeight)) return lane;
        }
        return -1;
    }

    private boolean isLaneFree(int lane, DanmakuItem item, float entryLineHeight) {
        Active latest = null;
        for (Active entry : active) {
            if (entry.lane != lane) continue;
            if (entry.item.isScroll() != item.isScroll()) continue;
            if (latest == null || entry.startMs > latest.startMs) latest = entry;
        }
        if (latest == null) return true;

        float progress = latest.progress(positionMs, config);
        if (progress >= 1f) return true;
        if (!item.isScroll()) return false;

        float x = latest.x(progress, getWidth());
        float gap = Math.max(0f, config.scrollGapRatio) * latest.width;
        return item.isReverse() ? x - gap > 0f : x + latest.width + gap < getWidth();
    }

    private final class Active {

        final DanmakuItem item;
        final int color;
        final float width;
        final float height;
        final int lane;
        final long durationMs;
        final long startMs;

        Active(DanmakuItem item, int color, float width, float height, int lane, long durationMs) {
            this.item = item;
            this.color = color;
            this.width = width;
            this.height = height;
            this.lane = lane;
            this.durationMs = Math.max(500L, durationMs);
            this.startMs = item.timeMs - config.timeOffsetMs;
        }

        float progress(long currentMs, DanmakuConfig config) {
            return (currentMs - startMs) / (float) durationMs;
        }

        float x(float progress, int viewWidth) {
            if (!item.isScroll()) return (viewWidth - width) / 2f;
            float clamped = Math.clamp(progress, 0f, 1f);
            float travel = viewWidth + width;
            return item.isReverse() ? -width + clamped * travel : viewWidth - clamped * travel;
        }

        float y(int viewHeight, float baselineOffset) {
            float top = item.isBottom() ? viewHeight - height * (lane + 1) : height * lane;
            return top + baselineOffset;
        }
    }
}
