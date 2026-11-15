package gimhub.companion;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

public class ActivityReadState {
    private final ConfigManager configManager;
    private final Gson gson;
    private final String key;
    private final Map<Long, Integer> revisions = new HashMap<>();
    private final Map<Long, Integer> overlapRevisions = new HashMap<>();
    private final Set<Long> unread = new HashSet<>();
    private final Set<Long> highlights = new HashSet<>();
    private String cursor;
    private boolean viewing;

    public ActivityReadState(ConfigManager configManager, Gson gson, String key) {
        this.configManager = configManager;
        this.gson = gson;
        this.key = key;
        String saved = configManager.getConfiguration("GimHub", key);
        if (saved != null) {
            JsonObject state = gson.fromJson(saved, JsonObject.class);
            cursor = state.get("cursor").getAsString();
            state.getAsJsonObject("revisions")
                    .entrySet()
                    .forEach(entry -> revisions.put(
                            Long.parseLong(entry.getKey()), entry.getValue().getAsInt()));
            overlapRevisions.putAll(revisions);
        }
    }

    public synchronized void receive(JsonArray entries, String nextCursor) {
        overlapRevisions.clear();
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            long identifier = entry.get("id").getAsLong();
            int revision = entry.get("revision").getAsInt();
            if (cursor != null && revision > revisions.getOrDefault(identifier, 0)) {
                unread.add(identifier);
            }
            revisions.merge(identifier, revision, Math::max);
            overlapRevisions.put(identifier, revision);
        }
        cursor = nextCursor;
    }

    public synchronized void viewed() {
        if (viewing) {
            acknowledge();
        }
    }

    public synchronized void setViewing(boolean viewing) {
        if (this.viewing == viewing) {
            return;
        }
        this.viewing = viewing;
        highlights.clear();
        if (viewing) {
            acknowledge();
        }
    }

    private void acknowledge() {
        highlights.addAll(unread);
        unread.clear();
        if (cursor == null) {
            return;
        }
        JsonObject saved = new JsonObject();
        saved.addProperty("cursor", cursor);
        saved.add("revisions", gson.toJsonTree(overlapRevisions));
        configManager.setConfiguration("GimHub", key, gson.toJson(saved));
    }

    public synchronized String getCursor() {
        return cursor;
    }

    public synchronized int getUnreadCount() {
        return unread.size();
    }

    public synchronized Set<Long> getHighlights() {
        return new HashSet<>(highlights);
    }
}
