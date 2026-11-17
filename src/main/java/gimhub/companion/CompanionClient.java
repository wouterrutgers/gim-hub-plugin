package gimhub.companion;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gimhub.ApiUrlBuilder;
import gimhub.DataManager;
import gimhub.GimHubConfig;
import gimhub.HttpRequestService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import okhttp3.HttpUrl;

@Slf4j
@Singleton
public class CompanionClient {
    private static final int ITEM_LOOKUPS_PER_CALLBACK = 16;
    private static final String SETUP_STATUS = "Enter your group name and token in settings.";

    @Inject
    private GimHubConfig config;

    @Inject
    private HttpRequestService http;

    @Inject
    private ApiUrlBuilder urls;

    @Inject
    private DataManager dataManager;

    @Inject
    private ConfigManager configManager;

    @Inject
    private Gson gson;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ItemManager itemManager;

    private final Set<CompletableFuture<?>> requests = new HashSet<>();
    private final CompanionData emptyData = new CompanionData();
    private Session session;
    private Runnable listener;
    private boolean running;
    private boolean visible;
    private boolean activityViewing;
    private boolean panelUpdateQueued;
    private String status = SETUP_STATUS;

    private static class Session {
        private final String context;
        private final String player;
        private final CompanionData data = new CompanionData();
        private final ActivityReadState readState;
        private boolean refreshing;
        private boolean loadingOlder;
        private boolean savingPins;
        private final Map<Integer, Boolean> desiredPins = new LinkedHashMap<>();
        private boolean pinSaveFailed;
        private String olderCursor;
        private boolean activityLoaded;
        private boolean preferencesLoaded;
        private long preferencesRevision;

        private Session(String context, String player, ActivityReadState readState) {
            this.context = context;
            this.player = player;
            this.readState = readState;
        }
    }

    private static class HttpFailure extends RuntimeException {
        private final int code;

        private HttpFailure(int code) {
            super("HTTP " + code);
            this.code = code;
        }

        private boolean isAuthorizationFailure() {
            return code == 401 || code == 403;
        }
    }

    public synchronized void start(Runnable listener) {
        this.listener = listener;
        running = true;
    }

    public synchronized void stop() {
        running = false;
        visible = false;
        activityViewing = false;
        cancelRequests();
        session = null;
        listener = null;
    }

    public synchronized void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            setActivityViewing(false);
        } else {
            refresh();
        }
    }

    public synchronized void setActivityViewing(boolean viewing) {
        activityViewing = viewing;
        if (session != null) {
            session.readState.setViewing(viewing && session.data.hasMetadata());
        }
    }

    public synchronized void activityViewed() {
        if (session != null && session.data.hasMetadata() && activityViewing) {
            session.readState.setViewing(true);
            session.readState.viewed();
        }
    }

    public synchronized void refresh() {
        if (!running) {
            return;
        }
        if (config.groupName().trim().isEmpty()
                || config.authorizationToken().trim().isEmpty()) {
            cancelRequests();
            session = null;
            status = SETUP_STATUS;
            notifyPanel();
            return;
        }
        String player = Objects.toString(dataManager.getActivePlayerName(), "");
        String context = context(player);
        if (session == null || !session.context.equals(context)) {
            cancelRequests();
            String key = "activitySeen_" + Hashing.sha256().hashString(context, StandardCharsets.UTF_8);
            session = new Session(context, player, new ActivityReadState(configManager, gson, key));
            status = "Loading your group...";
            notifyPanel();
        }
        Session current = session;
        savePins(current);
        if (!visible || current.refreshing) {
            return;
        }
        current.refreshing = true;
        List<CompletableFuture<?>> updates = new ArrayList<>();
        try {
            if (!current.data.hasMetadata()) {
                HttpUrl metadataUrl = HttpUrl.get(http.getBaseUrl())
                        .newBuilder()
                        .addPathSegments("api/game-data")
                        .build();
                updates.add(request(
                        current,
                        http.getAsync(metadataUrl.toString(), null),
                        result -> current.data.setMetadata(result.getAsJsonObject())));
            }
            HttpUrl groupUrl = urls.getGroupUrl("get-group-data")
                    .newBuilder()
                    .addQueryParameter("from_time", current.data.groupCursor())
                    .build();
            updates.add(get(current, groupUrl, result -> current.data.mergeMembers(result.getAsJsonArray()))
                    .thenCompose(ignored -> CompletableFuture.allOf(loadPreferences(current), loadItemData(current))));

            String cursor = current.readState.getCursor();
            if (!current.activityLoaded) {
                updates.add(getActivity(current, urls.getGroupUrl("activity"), page -> {
                    current.olderCursor = nullableString(page, "older_cursor");
                    if (cursor == null) {
                        current.readState.receive(
                                page.getAsJsonArray("entries"),
                                page.get("from_time").getAsString());
                    }
                    current.activityLoaded = true;
                }));
            }
            if (cursor != null) {
                updates.add(getActivity(
                        current,
                        urls.getGroupUrl("activity")
                                .newBuilder()
                                .addQueryParameter("from_time", cursor)
                                .build(),
                        page -> current.readState.receive(
                                page.getAsJsonArray("entries"),
                                page.get("from_time").getAsString())));
            }

            CompletableFuture.allOf(updates.toArray(new CompletableFuture<?>[0]))
                    .whenComplete((ignored, error) -> {
                        synchronized (CompanionClient.this) {
                            if (!isCurrent(current)) {
                                return;
                            }
                            current.refreshing = false;
                            report(error);
                        }
                    });
        } catch (RuntimeException error) {
            current.refreshing = false;
            report(error);
        }
    }

    private CompletableFuture<Void> getActivity(Session current, HttpUrl url, Consumer<JsonObject> acceptPage) {
        Set<Integer> dropItems = new HashSet<>();
        return get(current, url, result -> {
                    JsonObject page = result.getAsJsonObject();
                    JsonArray entries = page.getAsJsonArray("entries");
                    current.data.mergeActivity(entries);
                    for (JsonElement element : entries) {
                        JsonObject entry = element.getAsJsonObject();
                        String type = entry.get("type").getAsString();
                        if (type.equals("loot") || type.equals("collection")) {
                            dropItems.add(
                                    entry.getAsJsonObject("data").get("item_id").getAsInt());
                        }
                    }
                    acceptPage.accept(page);
                })
                .thenCompose(ignored -> lookupItems(
                        current, dropItems, identifier -> current.data.updateItemName(identifier, this::itemName)));
    }

    private CompletableFuture<Void> loadItemData(Session current) {
        return lookupItems(current, current.data.itemsNeedingPrices(), identifier -> {
            current.data.updateItemName(identifier, this::itemName);
            current.data.setItemPrice(identifier, itemManager.getItemPrice(identifier));
        });
    }

    private String itemName(int identifier) {
        return itemManager.getItemComposition(identifier).getMembersName();
    }

    private CompletableFuture<Void> lookupItems(Session current, Collection<Integer> identifiers, IntConsumer lookup) {
        if (identifiers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Iterator<Integer> remaining = identifiers.iterator();
        CompletableFuture<Void> updated = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            synchronized (CompanionClient.this) {
                if (!isCurrent(current)) {
                    updated.complete(null);
                    return true;
                }
                if (client.getGameState().ordinal() < GameState.LOGIN_SCREEN.ordinal()) {
                    return false;
                }
                try {
                    for (int count = 0; count < ITEM_LOOKUPS_PER_CALLBACK && remaining.hasNext(); count++) {
                        lookup.accept(remaining.next());
                    }
                    if (remaining.hasNext()) {
                        return false;
                    }
                    updated.complete(null);
                } catch (RuntimeException error) {
                    updated.completeExceptionally(error);
                }
                return true;
            }
        });
        return updated;
    }

    private synchronized CompletableFuture<Void> loadPreferences(Session current) {
        if (!isCurrent(current) || current.savingPins) {
            return CompletableFuture.completedFuture(null);
        }
        JsonObject player = current.data.player(current.player);
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        long revision = current.preferencesRevision;
        return get(current, urls.getGroupUrl("members/" + player.get("id").getAsInt() + "/preferences"), result -> {
            if (revision == current.preferencesRevision && !current.savingPins) {
                current.data.setPreferences(result.getAsJsonObject());
                current.preferencesLoaded = true;
            }
        });
    }

    public synchronized void loadOlder() {
        Session current = session;
        if (current == null || current.olderCursor == null || current.loadingOlder) {
            return;
        }
        current.loadingOlder = true;
        notifyPanel();
        getActivity(
                        current,
                        urls.getGroupUrl("activity")
                                .newBuilder()
                                .addQueryParameter("cursor", current.olderCursor)
                                .build(),
                        page -> current.olderCursor = nullableString(page, "older_cursor"))
                .whenComplete((ignored, error) -> {
                    synchronized (CompanionClient.this) {
                        if (isCurrent(current)) {
                            current.loadingOlder = false;
                            report(error);
                        }
                    }
                });
    }

    public synchronized void togglePin(int identifier) {
        Session current = session;
        if (!canPin()) {
            return;
        }
        current.desiredPins.put(identifier, !isPinned(identifier));
        current.preferencesRevision++;
        notifyPanel();
        savePins(current);
    }

    private synchronized void savePins(Session current) {
        if (!isCurrent(current) || current.savingPins || current.desiredPins.isEmpty()) {
            return;
        }
        JsonObject player = current.data.player(current.player);
        if (player == null) {
            return;
        }
        Map<Integer, Boolean> changesToSave = new LinkedHashMap<>(current.desiredPins);
        HttpUrl url = urls.getGroupUrl("members/" + player.get("id").getAsInt() + "/preferences");
        current.savingPins = true;
        notifyPanel();
        get(current, url, result -> current.data.setPreferences(result.getAsJsonObject()))
                .thenCompose(ignored -> {
                    synchronized (CompanionClient.this) {
                        if (!isCurrent(current)) {
                            return CompletableFuture.completedFuture(null);
                        }
                        Set<Integer> pins = current.data.pinnedItems();
                        changesToSave.forEach((identifier, pin) -> {
                            if (pin) {
                                pins.add(identifier);
                            } else {
                                pins.remove(identifier);
                            }
                        });
                        JsonObject changes = new JsonObject();
                        changes.add("pinnedItemIds", gson.toJsonTree(pins));
                        return request(
                                current,
                                http.patchAsync(url.toString(), config.authorizationToken(), changes),
                                result -> {
                                    current.data.setPreferences(result.getAsJsonObject());
                                    current.preferencesLoaded = true;
                                    changesToSave.forEach(
                                            (identifier, pin) -> current.desiredPins.remove(identifier, pin));
                                });
                    }
                })
                .whenComplete((ignored, error) -> {
                    synchronized (CompanionClient.this) {
                        if (isCurrent(current)) {
                            current.savingPins = false;
                            current.pinSaveFailed = error != null;
                            if (error != null) {
                                log.debug("Could not save pinned items", error);
                            }
                            notifyPanel();
                            if (error == null) {
                                savePins(current);
                            }
                        }
                    }
                });
    }

    private CompletableFuture<Void> get(Session current, HttpUrl url, Consumer<JsonElement> accept) {
        return request(current, http.getAsync(url.toString(), config.authorizationToken()), accept);
    }

    private synchronized CompletableFuture<Void> request(
            Session current,
            CompletableFuture<HttpRequestService.HttpResponse> response,
            Consumer<JsonElement> accept) {
        requests.add(response);
        return response.thenAccept(result -> {
                    if (!result.isSuccessful()) {
                        throw new HttpFailure(result.getCode());
                    }
                    JsonElement json = gson.fromJson(result.getBody(), JsonElement.class);
                    synchronized (CompanionClient.this) {
                        if (isCurrent(current)) {
                            accept.accept(json);
                        }
                    }
                })
                .whenComplete((ignored, error) -> {
                    synchronized (CompanionClient.this) {
                        requests.remove(response);
                    }
                });
    }

    private boolean isCurrent(Session current) {
        return running
                && session == current
                && current.context.equals(context(Objects.toString(dataManager.getActivePlayerName(), "")));
    }

    private String context(String player) {
        return http.getBaseUrl() + "\n" + config.groupName().trim() + "\n" + config.authorizationToken() + "\n"
                + player;
    }

    private void cancelRequests() {
        List<CompletableFuture<?>> pending = new ArrayList<>(requests);
        requests.clear();
        session = null;
        pending.forEach(future -> future.cancel(true));
    }

    private void report(Throwable error) {
        while (error != null && error.getCause() != null) {
            error = error.getCause();
        }
        if (error == null) {
            status = "";
        } else {
            log.debug("Could not refresh the GIM Hub companion", error);
            status = error instanceof HttpFailure && ((HttpFailure) error).isAuthorizationFailure()
                    ? "Check the group name and token in settings."
                    : "Could not load data.";
        }
        notifyPanel();
    }

    private synchronized void notifyPanel() {
        if (panelUpdateQueued) {
            return;
        }
        panelUpdateQueued = true;
        SwingUtilities.invokeLater(() -> {
            synchronized (CompanionClient.this) {
                panelUpdateQueued = false;
                if (running && listener != null) {
                    listener.run();
                }
            }
        });
    }

    public synchronized CompanionData getData() {
        return session == null ? emptyData : session.data;
    }

    public synchronized String getStatus() {
        return status.isEmpty() && session != null && session.pinSaveFailed ? "Could not save pins." : status;
    }

    public synchronized int getUnreadCount() {
        return session == null ? 0 : session.readState.getUnreadCount();
    }

    public synchronized Set<Long> getActivityHighlights() {
        return session == null ? new HashSet<>() : session.readState.getHighlights();
    }

    public synchronized boolean canLoadOlder() {
        return session != null && session.olderCursor != null && !session.loadingOlder;
    }

    public synchronized boolean isLoadingOlder() {
        return session != null && session.loadingOlder;
    }

    public synchronized boolean canPin() {
        return session != null && session.preferencesLoaded && session.data.player(session.player) != null;
    }

    public synchronized boolean isPinned(int identifier) {
        return session != null && session.desiredPins.getOrDefault(identifier, session.data.isPinned(identifier));
    }

    public synchronized boolean isPinPending(int identifier) {
        return session != null && session.desiredPins.containsKey(identifier);
    }

    public synchronized String getPinPlayerName() {
        return session == null || session.data.player(session.player) == null ? null : session.player;
    }

    private static String nullableString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString()
                : null;
    }
}
