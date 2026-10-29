package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class DecodeSetting {

    public static boolean isAudioPassThrough() {
        return Prefers.getBoolean("decode_audio_pass_through", Prefers.getBoolean("audio_pass_through", true));
    }

    public static void putAudioPassThrough(boolean audioPassThrough) {
        Prefers.put("decode_audio_pass_through", audioPassThrough);
    }

    public static boolean isPreferAAC() {
        return Prefers.getBoolean("decode_prefer_aac", Prefers.getBoolean("prefer_aac"));
    }

    public static void putPreferAAC(boolean preferAAC) {
        Prefers.put("decode_prefer_aac", preferAAC);
    }

    public static boolean isTunnel() {
        return Prefers.getBoolean("decode_tunnel", Prefers.getBoolean("tunnel"));
    }

    public static void putTunnel(boolean tunnel) {
        Prefers.put("decode_tunnel", tunnel);
    }

    /**
     * 官方 media3 的播放输出固定为 SurfaceView（{@code app:surface_type}），
     * 隧道模式是否可用只剩设置项本身一个条件。
     */
    public static boolean isTunnelingEnabled() {
        return isTunnel();
    }
}
