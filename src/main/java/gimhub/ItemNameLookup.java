package gimhub;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Lightweight name -> id lookup using RuneLite's public item cache. Fetches id->name and noted->unnoted mappings and
 * builds a case-sensitive name->id map excluding noted variants.
 */
@Slf4j
@Singleton
public class ItemNameLookup {
    private static final String ITEM_CACHE_BASE_URL = "https://static.runelite.net/cache/item/";

    private final Map<String, Integer> nameToId = new ConcurrentHashMap<>(16384);

    @Inject
    private OkHttpClient httpClient;

    @Inject
    private Gson gson;

    public void startUp() {
        new Thread(this::load).start();
    }

    public void shutDown() {
        nameToId.clear();
    }

    public Integer findItemId(@NonNull String name) {
        return nameToId.get(name);
    }

    private void load() {
        try {
            Map<Integer, String> namesById = fetchNamesById();
            Set<Integer> notedIds = fetchNotedIds();

            namesById.forEach((id, n) -> {
                if (!notedIds.contains(id)) {
                    nameToId.putIfAbsent(n, id);
                }
            });

            log.debug("ItemNameLookup initialized with {} entries", nameToId.size());
        } catch (Exception e) {
            log.error("ItemNameLookup initialization failed: {}", e.toString());
        }
    }

    private Map<Integer, String> fetchNamesById() throws IOException {
        String url = ITEM_CACHE_BASE_URL + "names.json";
        String json = httpGet(url);
        Type type = new TypeToken<Map<Integer, String>>() {}.getType();
        Map<Integer, String> map = gson.fromJson(json, type);
        return map != null ? map : Collections.emptyMap();
    }

    private Set<Integer> fetchNotedIds() throws IOException {
        String url = ITEM_CACHE_BASE_URL + "notes.json";
        String json = httpGet(url);
        Type type = new TypeToken<Map<Integer, Integer>>() {}.getType();
        Map<Integer, Integer> notes = gson.fromJson(json, type);
        return notes != null ? notes.keySet() : Collections.emptySet();
    }

    private String httpGet(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        Call call = httpClient.newCall(request);
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            return Objects.requireNonNull(response.body()).string();
        }
    }
}
