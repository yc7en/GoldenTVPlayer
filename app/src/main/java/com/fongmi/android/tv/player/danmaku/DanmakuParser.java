package com.fongmi.android.tv.player.danmaku;

import android.text.TextUtils;
import android.util.Xml;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 弹幕文件解析：兼容 B 站 XML（{@code <d p="...">}）与常见 JSON 弹幕格式。
 *
 * 原实现位于 fork 的 {@code DanmakuPlayerViewController} 内部，官方 media3
 * 不提供弹幕能力，因此在本仓库内重新实现（ADR-9）。
 */
public final class DanmakuParser {

    private static final int MAX_ITEMS = 200_000;

    private DanmakuParser() {
    }

    public static List<DanmakuItem> parse(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) return List.of();
        String trimmed = raw.trim();
        try {
            if (trimmed.startsWith("<")) return parseXml(trimmed);
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) return parseJson(trimmed);
        } catch (Exception e) {
            return List.of();
        }
        return List.of();
    }

    private static List<DanmakuItem> parseXml(String raw) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(raw));
        List<DanmakuItem> items = new ArrayList<>();
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT && items.size() < MAX_ITEMS; event = parser.next()) {
            if (event != XmlPullParser.START_TAG || !"d".equals(parser.getName())) continue;
            String text = parser.nextText();
            DanmakuItem item = fromBilibili(text, parser.getAttributeValue(null, "p"));
            if (item != null) items.add(item);
        }
        Collections.sort(items, (a, b) -> Long.compare(a.timeMs, b.timeMs));
        return items;
    }

    @Nullable
    private static DanmakuItem fromBilibili(@Nullable String text, @Nullable String p) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(p)) return null;
        String[] parts = p.split(",");
        if (parts.length < 4) return null;
        try {
            long timeMs = Math.round(Double.parseDouble(parts[0]) * 1000);
            int mode = (int) Double.parseDouble(parts[1]);
            float size = (float) Double.parseDouble(parts[2]);
            int color = 0xff000000 | ((int) Double.parseDouble(parts[3]) & 0xffffff);
            return new DanmakuItem(text, Math.max(0, timeMs), normalizeMode(mode), color, size, null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * B 站 mode：1/2/3 滚动、4 底部、5 顶部、6 逆向、7 高级、8 代码、9 BAS。
     * 高级弹幕与代码弹幕需要脚本引擎，降级为滚动文本。
     */
    private static int normalizeMode(int mode) {
        return switch (mode) {
            case 4 -> DanmakuItem.TYPE_BOTTOM;
            case 5 -> DanmakuItem.TYPE_TOP;
            case 6 -> DanmakuItem.TYPE_REVERSE;
            default -> DanmakuItem.TYPE_SCROLL;
        };
    }

    private static List<DanmakuItem> parseJson(String raw) throws Exception {
        JSONArray array = raw.startsWith("[") ? new JSONArray(raw) : firstArray(new JSONObject(raw));
        if (array == null) return List.of();
        List<DanmakuItem> items = new ArrayList<>(array.length());
        for (int i = 0; i < array.length() && items.size() < MAX_ITEMS; i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            DanmakuItem item = fromJson(object);
            if (item != null) items.add(item);
        }
        Collections.sort(items, (a, b) -> Long.compare(a.timeMs, b.timeMs));
        return items;
    }

    @Nullable
    private static JSONArray firstArray(JSONObject object) {
        for (String key : new String[]{"danmaku", "comments", "data", "items"}) {
            JSONArray array = object.optJSONArray(key);
            if (array != null) return array;
        }
        return null;
    }

    /**
     * dplayer 系用 0/1/2 表示右→左 / 顶部 / 底部，B 站系用 1/5/4/6。
     */
    private static int jsonMode(JSONObject object) {
        int type = object.optInt("type", 0);
        return switch (type) {
            case 0 -> DanmakuItem.TYPE_SCROLL;
            case 1 -> DanmakuItem.TYPE_TOP;
            case 2 -> DanmakuItem.TYPE_BOTTOM;
            default -> normalizeMode(type);
        };
    }

    @Nullable
    private static DanmakuItem fromJson(JSONObject object) {
        String text = object.optString("text", object.optString("m", ""));
        if (TextUtils.isEmpty(text)) return null;
        double seconds = object.optDouble("time", object.optDouble("t", 0));
        if (!Double.isFinite(seconds)) return null;
        long timeMs = Math.max(0, Math.round(seconds * 1000));
        int mode = jsonMode(object);
        int color = 0xff000000 | (object.optInt("color", 0xffffff) & 0xffffff);
        float size = (float) object.optDouble("size", 0);
        return new DanmakuItem(text, timeMs, mode, color, size, null);
    }
}
