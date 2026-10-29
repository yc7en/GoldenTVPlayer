package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;
import androidx.media3.common.Player;

import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.SpeedSetting;

/**
 * 播放指令执行器。指令语法为「动作 + 可选参数」，由用户自定义按钮绑定。
 *
 * <p>只覆盖官方 media3 能表达的属性：
 *
 * <ul>
 *   <li>{@code seek} —— 绝对/百分比/相对跳转
 *   <li>{@code pause} —— 播放暂停切换
 *   <li>{@code loop-file} —— 单曲循环
 *   <li>{@code speed} —— 倍速
 *   <li>{@code volume} —— 音量
 *   <li>{@code sub-delay} —— 字幕偏移
 * </ul>
 *
 * <p>去掉的指令及原因：{@code video-scale-x/y}、{@code video-aspect-override} 需要 media3 分支
 * 才有的 {@code VideoTransform} 与 {@code PlayerView} 变换接口；{@code chapter} 依赖分支的章节模型；
 * {@code audio-delay} 官方没有音频偏移接口。这些指令解析直接返回 null，
 * 因此 {@link #isSupported} 为 false，自定义按钮面板会自动把它们过滤掉。
 */
public final class ExoCommand {

    public enum Result {
        APPLIED, UNAVAILABLE, UNSUPPORTED
    }

    private ExoCommand() {
    }

    public static boolean isSupported(String commandText) {
        return parse(commandText) != null;
    }

    public static Result execute(PlayerManager manager, String commandText) {
        Parsed parsed = parse(commandText);
        if (parsed == null) return Result.UNSUPPORTED;
        Player player = manager.getPlayer();
        if (player == null || manager.isEmpty()) return Result.UNAVAILABLE;
        switch (parsed.property) {
            case "seek" -> {
                if (!player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) || !player.isCurrentMediaItemSeekable()) return Result.UNAVAILABLE;
                long targetMs = seekPosition(parsed, player.getCurrentPosition(), player.getDuration());
                if (targetMs == C.TIME_UNSET) return Result.UNAVAILABLE;
                manager.seekTo(targetMs);
            }
            case "pause" -> {
                if (!player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) return Result.UNAVAILABLE;
                boolean pause = parsed.mode.equals("cycle") ? player.getPlayWhenReady()
                        : propertyValue(parsed, player.getPlayWhenReady() ? 0 : 1) != 0;
                if (pause) manager.pause();
                else manager.play();
            }
            case "loop-file" -> {
                if (!player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) return Result.UNAVAILABLE;
                boolean repeat = propertyValue(parsed, manager.isRepeatOne() ? 1 : 0) != 0;
                manager.setRepeatOne(repeat);
            }
            case "speed" -> {
                if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return Result.UNAVAILABLE;
                float speed = (float) propertyValue(parsed, manager.getSpeed());
                manager.setSpeed(speed);
            }
            case "volume" -> {
                if (!player.isCommandAvailable(Player.COMMAND_SET_VOLUME)) return Result.UNAVAILABLE;
                boolean readCurrent = parsed.mode.equals("add") || parsed.values != null;
                if (readCurrent && !player.isCommandAvailable(Player.COMMAND_GET_VOLUME)) return Result.UNAVAILABLE;
                double volume = propertyValue(parsed, readCurrent ? player.getVolume() * 100.0 : 0);
                player.setVolume((float) (Math.clamp(volume, 0, 100) / 100));
            }
            case "sub-delay" -> {
                return executeSubtitleOffset(manager, parsed);
            }
            default -> {
                return Result.UNSUPPORTED;
            }
        }
        return Result.APPLIED;
    }

    /**
     * 字幕偏移以毫秒为单位，指令参数是秒。
     */
    private static Result executeSubtitleOffset(PlayerManager manager, Parsed parsed) {
        long currentOffsetMs = manager.getSubtitleOffsetMs();
        double offsetMs = parsed.values == null ? currentOffsetMs + parsed.number * 1000
                : propertyValue(parsed, currentOffsetMs / 1000.0) * 1000;
        if (!isMillis(offsetMs)) return Result.UNAVAILABLE;
        manager.setSubtitleOffsetMs(Math.round(offsetMs));
        return Result.APPLIED;
    }

    static long seekPosition(String commandText, long currentPositionMs, long durationMs) {
        Parsed parsed = parse(commandText);
        return parsed == null || !parsed.property.equals("seek") ? C.TIME_UNSET : seekPosition(parsed, currentPositionMs, durationMs);
    }

    private static long seekPosition(Parsed parsed, long currentPositionMs, long durationMs) {
        if (currentPositionMs == C.TIME_UNSET) return C.TIME_UNSET;
        if (durationMs == C.TIME_UNSET && (parsed.mode.contains("percent") || parsed.mode.equals("absolute") && parsed.number < 0)) return C.TIME_UNSET;
        double target = switch (parsed.mode) {
            case "absolute" -> parsed.number < 0 ? durationMs + parsed.number * 1000 : parsed.number * 1000;
            case "absolute-percent" -> durationMs * parsed.number / 100;
            case "relative-percent" -> currentPositionMs + durationMs * parsed.number / 100;
            default -> currentPositionMs + parsed.number * 1000;
        };
        if (!isMillis(target)) return C.TIME_UNSET;
        long result = Math.max(0, Math.round(target));
        return durationMs == C.TIME_UNSET ? result : Math.min(durationMs, result);
    }

    private static boolean isMillis(double milliseconds) {
        return Double.isFinite(milliseconds) && milliseconds > Long.MIN_VALUE && milliseconds < Long.MAX_VALUE;
    }

    static double propertyValue(String commandText, double current) {
        Parsed parsed = parse(commandText);
        return parsed == null ? Double.NaN : propertyValue(parsed, current);
    }

    private static double propertyValue(Parsed parsed, double current) {
        if (parsed.values == null) return parsed.mode.equals("add") ? current + parsed.number : parsed.number;
        for (int i = 0; i < parsed.values.length; i++) if (sameValue(parsed.property, current, parsed.values[i])) return parsed.values[(i + (parsed.reverse ? parsed.values.length - 1 : 1)) % parsed.values.length];
        return parsed.values[parsed.reverse ? parsed.values.length - 1 : 0];
    }

    private static boolean sameValue(String property, double current, double value) {
        return switch (property) {
            case "speed" -> (float) current == (float) value;
            case "volume" -> (float) (current / 100) == (float) (value / 100);
            case "sub-delay" -> Math.round(current * 1000) == Math.round(value * 1000);
            default -> current == value;
        };
    }

    private static Parsed parse(String commandText) {
        if (commandText == null) return null;
        String[] parts = commandText.strip().split("\\s+");
        if (parts.length >= 3 && parts[0].equals("cycle-values")) return parseCycleValues(parts);
        if (parts.length == 3 && parts[0].equals("set")) return parsePropertyValue(parts[1], parts[2]);
        if (parts.length == 2 && parts[0].equals("seek")) return parseNumeric("seek", "relative", parts[1]);
        if (parts.length == 3 && parts[0].equals("seek") && (parts[2].equals("relative") || parts[2].equals("absolute") || parts[2].equals("absolute-percent") || parts[2].equals("relative-percent"))) return parseNumeric("seek", parts[2], parts[1]);
        if (parts.length == 2 && parts[0].equals("cycle") && parts[1].equals("pause")) return new Parsed("pause", "cycle", 0);
        if (parts.length == 3 && parts[0].equals("add") && (parts[1].equals("speed") || parts[1].equals("volume") || parts[1].equals("sub-delay"))) return parseNumeric(parts[1], parts[0], parts[2]);
        return null;
    }

    private static Parsed parseCycleValues(String[] parts) {
        boolean reverse = parts[1].equals("!reverse");
        int propertyIndex = reverse ? 2 : 1;
        if (parts.length <= propertyIndex + 1) return null;
        double[] values = new double[parts.length - propertyIndex - 1];
        for (int i = propertyIndex + 1; i < parts.length; i++) {
            Parsed value = parsePropertyValue(parts[propertyIndex], parts[i]);
            if (value == null) return null;
            values[i - propertyIndex - 1] = value.number;
        }
        return new Parsed(parts[propertyIndex], "cycle-values", values[0], values, reverse);
    }

    private static Parsed parsePropertyValue(String property, String text) {
        if (property.equals("pause") || property.equals("loop-file")) {
            if (text.equals("no")) return new Parsed(property, "set", 0);
            if (text.equals("yes") || property.equals("loop-file") && text.equals("inf")) return new Parsed(property, "set", 1);
            return null;
        }
        if (!property.equals("speed") && !property.equals("volume") && !property.equals("sub-delay")) return null;
        Parsed parsed = parseNumeric(property, "set", text);
        if (parsed == null) return null;
        double value = parsed.number;
        if (property.equals("speed") && ((float) value < SpeedSetting.MIN || (float) value > SpeedSetting.MAX)) return null;
        if (property.equals("volume") && (value < 0 || value > 100)) return null;
        if (property.equals("sub-delay") && !isMillis(value * 1000)) return null;
        return parsed;
    }

    private static Parsed parseNumeric(String property, String mode, String numericText) {
        if (!numericText.matches("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?")) return null;
        try {
            double number = Double.parseDouble(numericText);
            return Double.isFinite(number) ? new Parsed(property, mode, number) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Parsed(String property, String mode, double number, double[] values, boolean reverse) {
        Parsed(String property, String mode, double number) {
            this(property, mode, number, null, false);
        }
    }
}
