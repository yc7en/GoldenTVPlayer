package com.fongmi.android.tv.player.engine;

import androidx.media3.common.Player;

import com.fongmi.android.tv.player.exo.ExoPlayerEngine;
import com.fongmi.android.tv.player.media.PlaySpec;

public final class PlayerEngineFactory {

    public static PlayerEngine create(Player.Listener listener) {
        return new ExoPlayerEngine(listener);
    }

    public static PlayerEngine create(PlaySpec spec, Player.Listener listener) {
        return new ExoPlayerEngine(listener);
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return engine != null && engine.getType() == PlayerEngine.Type.EXO;
    }

    /**
     * 只剩一种引擎后，任何地址都可以走预加载。
     */
    public static boolean canPreload(PlaySpec spec) {
        return true;
    }
}
