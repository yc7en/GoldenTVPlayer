package com.fongmi.android.tv.player.effect.video;

import androidx.media3.effect.SingleColorLut;

/**
 * 用颜色查找表实现「伽马 + 黑位抬升」这类逐通道色调曲线。
 *
 * <p>官方 media3 的 {@code Brightness}/{@code Contrast}/{@code HslAdjustment}/{@code RgbAdjustment}
 * 都是一次线性矩阵运算，表达不了幂函数曲线。色调曲线用 3D LUT 表达：三条轴分别是输入 R/G/B，
 * 表里存的就是输出 RGB，逐通道查表即可得到任意单调曲线。
 */
final class VideoToneCurve {

    /**
     * 每维采样点数。LUT 体积是 size³，24 已经足够平滑（24³ = 13824 个点）。
     */
    private static final int LUT_SIZE = 24;

    private VideoToneCurve() {
    }

    static boolean isNeeded(float gamma, float shadowLift) {
        return gamma != 1.0f || shadowLift != 0.0f;
    }

    static SingleColorLut create(float gamma, float shadowLift) {
        int[] curve = buildCurve(gamma, shadowLift);
        int[][][] cube = new int[LUT_SIZE][LUT_SIZE][LUT_SIZE];
        for (int r = 0; r < LUT_SIZE; r++) {
            for (int g = 0; g < LUT_SIZE; g++) {
                for (int b = 0; b < LUT_SIZE; b++) {
                    cube[r][g][b] = 0xFF000000 | (curve[r] << 16) | (curve[g] << 8) | curve[b];
                }
            }
        }
        return SingleColorLut.createFromCube(cube);
    }

    private static int[] buildCurve(float gamma, float shadowLift) {
        int[] curve = new int[LUT_SIZE];
        float lift = Math.clamp(shadowLift, 0.0f, 0.95f);
        float exponent = gamma <= 0.0f ? 1.0f : gamma;
        for (int i = 0; i < LUT_SIZE; i++) {
            float input = i / (float) (LUT_SIZE - 1);
            float output = lift + (1.0f - lift) * (float) Math.pow(input, exponent);
            curve[i] = Math.round(Math.clamp(output, 0.0f, 1.0f) * 255.0f);
        }
        return curve;
    }
}
