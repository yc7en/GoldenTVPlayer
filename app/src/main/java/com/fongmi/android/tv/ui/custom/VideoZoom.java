package com.fongmi.android.tv.ui.custom;

import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.media3.ui.PlayerView;

/**
 * 视频画面缩放/平移。
 *
 * <p>原来的 {@code androidx.media3.ui.PlayerView#setVideoTransform(VideoTransform)} 是 media3 分支
 * 的接口。官方没有，但画面缩放本质就是对承载视频的 SurfaceView 做 View 级变换
 * （缩放 + 平移，枢轴取中心），所以这里直接用 {@link View} 的变换属性实现，
 * minSdk 24 起 SurfaceView 支持 View 变换。
 *
 * <p>{@code panX/panY} 沿用原有语义：相对「内容尺寸 × 缩放后尺寸」的比例位移，
 * 这样不同分辨率下同一手势的比例位移表现一致。
 */
public final class VideoZoom {

    private VideoZoom() {
    }

    public record Transform(float scaleX, float scaleY, float panX, float panY) {

        public static final Transform DEFAULT = new Transform(1.0f, 1.0f, 0.0f, 0.0f);

        public boolean isDefault() {
            return scaleX == 1.0f && scaleY == 1.0f && panX == 0.0f && panY == 0.0f;
        }
    }

    @Nullable
    private static View surface(PlayerView playerView) {
        return playerView == null ? null : playerView.getVideoSurfaceView();
    }

    public static Transform get(PlayerView playerView) {
        View view = surface(playerView);
        if (view == null) return Transform.DEFAULT;
        float scaleX = view.getScaleX();
        float scaleY = view.getScaleY();
        float width = view.getWidth();
        float height = view.getHeight();
        float panX = width > 0 && scaleX != 0 ? view.getTranslationX() / (width * scaleX) : 0.0f;
        float panY = height > 0 && scaleY != 0 ? view.getTranslationY() / (height * scaleY) : 0.0f;
        return new Transform(scaleX, scaleY, panX, panY);
    }

    public static void set(PlayerView playerView, Transform transform) {
        View view = surface(playerView);
        if (view == null) return;
        view.animate().cancel();
        float width = view.getWidth();
        float height = view.getHeight();
        view.setPivotX(width / 2.0f);
        view.setPivotY(height / 2.0f);
        view.setScaleX(transform.scaleX());
        view.setScaleY(transform.scaleY());
        view.setTranslationX(transform.panX() * width * transform.scaleX());
        view.setTranslationY(transform.panY() * height * transform.scaleY());
    }

    public static void animate(PlayerView playerView, Transform transform, long durationMs) {
        View view = surface(playerView);
        if (view == null) return;
        float width = view.getWidth();
        float height = view.getHeight();
        view.setPivotX(width / 2.0f);
        view.setPivotY(height / 2.0f);
        view.animate()
                .scaleX(transform.scaleX())
                .scaleY(transform.scaleY())
                .translationX(transform.panX() * width * transform.scaleX())
                .translationY(transform.panY() * height * transform.scaleY())
                .setDuration(durationMs)
                .start();
    }

    public static void finishAnimation(PlayerView playerView) {
        View view = surface(playerView);
        if (view != null) view.animate().cancel();
    }

    /**
     * 视频内容在容器里的实际占位。SurfaceView 由 AspectRatioFrameLayout 按视频宽高比排版，
     * 因此它自身的边界就是内容边界。
     */
    public static RectF getContentBounds(PlayerView playerView) {
        View view = surface(playerView);
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return new RectF(0, 0, playerView == null ? 0 : playerView.getWidth(), playerView == null ? 0 : playerView.getHeight());
        }
        return new RectF(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
    }
}
