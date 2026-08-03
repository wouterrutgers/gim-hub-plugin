package gimhub;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class CollectionLogItemResolver {
    private static final String ITEM_CACHE_BASE_URL = "https://static.runelite.net/cache/item/";

    private final Map<String, Integer> itemIdentifierByName = Collections.synchronizedMap(new HashMap<>(16384));

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    @Inject
    protected void initialize() {
        queryNamesByIdentifier()
                .thenAcceptBothAsync(queryNotedItemIdentifiers(), this::populate)
                .exceptionally(exception -> {
                    log.error("Failed to initialize collection log item names", exception);
                    return null;
                });
    }

    @Nullable public Integer findItemIdentifier(@NonNull String name) {
        return itemIdentifierByName.get(name);
    }

    protected void populate(Map<Integer, String> namesByIdentifier, Set<Integer> notedItemIdentifiers) {
        namesByIdentifier.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> !notedItemIdentifiers.contains(entry.getKey()))
                .forEach(entry -> itemIdentifierByName.putIfAbsent(entry.getValue(), entry.getKey()));

        log.debug("Initialized collection log item names with {} entries", itemIdentifierByName.size());
    }

    protected CompletableFuture<Map<Integer, String>> queryNamesByIdentifier() {
        return queryCache("names.json", new TypeToken<Map<Integer, String>>() {});
    }

    protected CompletableFuture<Set<Integer>> queryNotedItemIdentifiers() {
        return queryCache("notes.json", new TypeToken<Map<Integer, Integer>>() {})
                .thenApply(Map::keySet);
    }

    protected <Type> CompletableFuture<Type> queryCache(String fileName, TypeToken<Type> typeToken) {
        CompletableFuture<Type> future = new CompletableFuture<>();
        Request request = new Request.Builder()
                .url(String.format("%s%s", ITEM_CACHE_BASE_URL, fileName))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException exception) {
                future.completeExceptionally(exception);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeableResponse = response;
                        Reader reader =
                                Objects.requireNonNull(closeableResponse.body()).charStream()) {
                    future.complete(gson.fromJson(reader, typeToken.getType()));
                } catch (Exception exception) {
                    future.completeExceptionally(exception);
                }
            }
        });

        return future;
    }
}
