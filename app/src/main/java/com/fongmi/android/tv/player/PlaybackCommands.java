package com.fongmi.android.tv.player;

import com.github.catvod.utils.Prefers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 播放界面自定义指令按钮的持久化。
 *
 * 指令语法为「动作 + 可选参数」（如 {@code pause}、{@code seek 30}），由
 * {@code ExoCommand} 解析执行；本类只负责存取与校验，不涉及播放器。
 */
public final class PlaybackCommands {

    public static final String PREFERENCE_KEY = "playback_commands";
    public static final int MAX_TITLE_LENGTH = 64;
    public static final int MAX_COMMAND_LENGTH = 1024;
    private static final String ID_PATTERN = "[a-f0-9]{32}";

    private PlaybackCommands() {
    }

    public static List<Item> read() throws JSONException {
        JSONArray array = new JSONArray(Prefers.getString(PREFERENCE_KEY, "[]"));
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) items.add(readItem(array.getJSONObject(i)));
        return items;
    }

    private static Item readItem(JSONObject object) throws JSONException {
        String id = object.getString("id");
        if (!id.matches(ID_PATTERN)) throw new JSONException("Invalid command ID");
        return new Item(id, validateTitle(object.getString("title")), validateCommand(object.getString("command")), object.getBoolean("enabled"), object.optBoolean("hidden", false));
    }

    private static void write(List<Item> items) throws JSONException {
        JSONArray array = new JSONArray();
        for (Item item : items) array.put(writeItem(item));
        Prefers.put(PREFERENCE_KEY, array.toString());
    }

    private static JSONObject writeItem(Item item) throws JSONException {
        return new JSONObject().put("id", item.id).put("title", item.title).put("command", item.command).put("enabled", item.enabled).put("hidden", item.hidden);
    }

    public static synchronized Item add(String title, String command) throws JSONException {
        List<Item> items = read();
        Item item = new Item(UUID.randomUUID().toString().replace("-", ""), validateTitle(title), validateCommand(command), true, false);
        items.add(item);
        write(items);
        return item;
    }

    public static synchronized void update(String id, String title, String command) throws JSONException {
        String validatedTitle = validateTitle(title);
        String validatedCommand = validateCommand(command);
        List<Item> items = read();
        int index = requireIndex(items, id);
        Item item = items.get(index);
        items.set(index, new Item(item.id, validatedTitle, validatedCommand, item.enabled, item.hidden));
        write(items);
    }

    public static synchronized void rename(String id, String title) throws JSONException {
        String validatedTitle = validateTitle(title);
        List<Item> items = read();
        int index = requireIndex(items, id);
        Item item = items.get(index);
        items.set(index, new Item(item.id, validatedTitle, item.command, item.enabled, item.hidden));
        write(items);
    }

    public static synchronized void setEnabled(String id, boolean enabled) throws JSONException {
        List<Item> items = read();
        int index = requireIndex(items, id);
        Item item = items.get(index);
        items.set(index, new Item(item.id, item.title, item.command, enabled, item.hidden));
        write(items);
    }

    public static synchronized void setHidden(String id, boolean hidden) throws JSONException {
        List<Item> items = read();
        int index = requireIndex(items, id);
        Item item = items.get(index);
        items.set(index, new Item(item.id, item.title, item.command, item.enabled, hidden));
        write(items);
    }

    public static synchronized void delete(String id) throws JSONException {
        List<Item> items = read();
        items.remove(requireIndex(items, id));
        write(items);
    }

    public static synchronized void move(String id, String targetId) throws JSONException {
        List<Item> items = read();
        int from = requireIndex(items, id);
        int to = requireIndex(items, targetId);
        items.add(to, items.remove(from));
        write(items);
    }

    private static int requireIndex(List<Item> items, String id) throws JSONException {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(id)) return i;
        throw new JSONException("Unknown command");
    }

    private static String validateTitle(String title) throws JSONException {
        title = title.strip();
        if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH || title.indexOf('\0') >= 0) throw new JSONException("Invalid title");
        return title;
    }

    private static String validateCommand(String command) throws JSONException {
        command = command.strip();
        if (command.isEmpty() || command.length() > MAX_COMMAND_LENGTH || command.chars().anyMatch(Character::isISOControl)) throw new JSONException("Invalid command");
        return command;
    }

    public record Item(String id, String title, String command, boolean enabled, boolean hidden) {
    }
}
