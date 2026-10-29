package com.fongmi.android.tv.playback;

import android.view.View;
import android.widget.TextView;

import androidx.media3.common.C;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.SpeedSetting;
import com.fongmi.android.tv.utils.ResUtil;

public final class PlaybackAction {

    public static void setPlaybackMode(PlayerManager player, TextView engine) {
        if (engine != null) engine.setText(getEngineText());
    }

    public static float toggleSpeed(PlayerManager player, TextView view) {
        float speed = player.toggleSpeed();
        showSpeedHint(view, speed);
        return speed;
    }

    public static boolean startSpeedPress(PlayerManager player, TextView view) {
        if (!player.startSpeedPress(SpeedSetting.getLongPress())) return false;
        if (view != null) setSpeedHint(view, player.getSpeed());
        return true;
    }

    public static void endSpeedPress(PlayerManager player, TextView view) {
        player.endSpeedPress();
        hideSpeedHint(view);
    }

    public static void showSpeedHint(TextView view, float speed) {
        if (view == null) return;
        setSpeedHint(view, speed);
        view.animate().alpha(0.0f).setStartDelay(900).setDuration(180).withEndAction(() -> clearSpeedHint(view)).start();
    }

    public static void hideSpeedHint(TextView view) {
        if (view == null) return;
        view.animate().cancel();
        clearSpeedHint(view);
    }

    private static void clearSpeedHint(TextView view) {
        view.setVisibility(View.GONE);
        view.setText("");
    }

    private static void setSpeedHint(TextView view, float speed) {
        view.setAlpha(1.0f);
        view.animate().cancel();
        view.setVisibility(View.VISIBLE);
        view.setText(ResUtil.getString(R.string.play_speed_hint, SpeedSetting.format(speed)));
    }

    public static void setTracks(PlayerManager player, View text, View audio, View video) {
        setVisible(text, hasTextTrack(player));
        setVisible(audio, hasAudioTrack(player));
        setVisible(video, hasVideoTrack(player));
    }

    public static void setTracks(PlayerManager player, View text, View audio, View video, View speed) {
        setTracks(player, text, audio, video);
        setVisible(speed, hasSpeed(player));
    }

    public static void setWebPlaybackMode(boolean active, View... nativeOnlyViews) {
        for (View view : nativeOnlyViews) setVisible(view, !active);
    }

    public static String getEngineText() {
        return "ExoPlayer";
    }

    private static boolean hasTextTrack(PlayerManager player) {
        return player != null && (player.haveTrack(C.TRACK_TYPE_TEXT) || player.isVod());
    }

    private static boolean hasAudioTrack(PlayerManager player) {
        return player != null && player.haveTrack(C.TRACK_TYPE_AUDIO);
    }

    private static boolean hasVideoTrack(PlayerManager player) {
        return player != null && player.haveTrack(C.TRACK_TYPE_VIDEO);
    }

    private static boolean hasSpeed(PlayerManager player) {
        return player != null && player.isVod();
    }

    private static void setVisible(View view, boolean visible) {
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
