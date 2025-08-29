package gimhub;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

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
    private HttpRequestService httpRequestService;

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

    private Map<Integer, String> fetchNamesById() throws Exception {
        String url = ITEM_CACHE_BASE_URL + "names.json";
        HttpRequestService.HttpResponse response = httpRequestService.get(url, null);
        if (!response.isSuccessful()) {
            throw new Exception("HTTP " + response.getCode() + " for " + url);
        }
        Type type = new TypeToken<Map<Integer, String>>() {}.getType();
        Map<Integer, String> map = gson.fromJson(response.getBody(), type);

        return map != null ? map : Collections.emptyMap();
    }

    private Set<Integer> fetchNotedIds() throws Exception {
        String url = ITEM_CACHE_BASE_URL + "notes.json";
        HttpRequestService.HttpResponse response = httpRequestService.get(url, null);
        if (!response.isSuccessful()) {
            throw new Exception("HTTP " + response.getCode() + " for " + url);
        }
        Type type = new TypeToken<Map<Integer, Integer>>() {}.getType();
        Map<Integer, Integer> notes = gson.fromJson(response.getBody(), type);

        return notes != null ? notes.keySet() : Collections.emptySet();
    }
}
