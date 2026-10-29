package com.fongmi.android.tv.player.effect.audio;

/**
 * 声道模式。
 *
 * <p>只对单声道与立体声输入生效：多声道（5.1/7.1）不做混音，避免破坏音轨本身的定位信息。
 * 因此 {@link #isAvailable(int)} 只对 1/2 声道返回 true，其余情况一律回落到 {@link #AUTO}。
 */
public final class AudioChannelMode {

    public static final int AUTO = 0;
    public static final int STEREO = 1;
    public static final int MONO = 2;
    public static final int REVERSE = 3;

    private AudioChannelMode() {
    }

    public static int clamp(int mode) {
        return mode < AUTO || mode > REVERSE ? AUTO : mode;
    }

    public static boolean isAuto(int mode) {
        return clamp(mode) == AUTO;
    }

    /**
     * 声道混音是否对当前声道数可用。多于双声道时不做混音。
     */
    public static boolean isAvailable(int channelCount) {
        return channelCount == 1 || channelCount == 2;
    }

    /**
     * 指定模式是否对当前声道数可用。不可用时调用方应回落到 {@link #AUTO}。
     */
    public static boolean isAvailable(int mode, int channelCount) {
        if (!isAvailable(channelCount)) return false;
        return clamp(mode) != REVERSE || channelCount == 2;
    }

    /**
     * 该模式下是否允许左右平衡调整。单声道无左右之分；强制立体声由单声道上混，也无左右之分。
     */
    public static boolean canBalance(int mode) {
        int value = clamp(mode);
        return value == AUTO || value == REVERSE;
    }

    /**
     * 把配置值折算成当前声道数下真正会生效的模式。
     */
    public static int resolve(int mode, int channelCount) {
        return isAvailable(mode, channelCount) ? clamp(mode) : AUTO;
    }
}
