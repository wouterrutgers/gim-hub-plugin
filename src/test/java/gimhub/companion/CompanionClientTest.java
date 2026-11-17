package gimhub.companion;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gimhub.ApiUrlBuilder;
import gimhub.DataManager;
import gimhub.GimHubConfig;
import gimhub.HttpRequestService;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CompanionClientTest {
    private final Gson gson = new Gson();
    private final List<RecordedRequest> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger groupReads = new AtomicInteger();
    private final AtomicBoolean holdOldGroup = new AtomicBoolean();
    private final CompletableFuture<Void> oldRequest = new CompletableFuture<>();
    private final CompletableFuture<Void> releaseOldRequest = new CompletableFuture<>();
    private final AtomicBoolean holdPinSave = new AtomicBoolean();
    private final AtomicBoolean failPinSave = new AtomicBoolean();
    private final CompletableFuture<Void> pinSaveRequested = new CompletableFuture<>();
    private final CompletableFuture<Void> releasePinSave = new CompletableFuture<>();
    private volatile CompletableFuture<Void> refreshed;
    private volatile BooleanSupplier refreshReady;
    private MockWebServer server;
    private CompanionClient companion;
    private GimHubConfig config;
    private DataManager dataManager;
    private ItemManager itemManager;
    private JsonObject preferences;

    @Before
    public void setUp() throws Exception {
        preferences = gson.fromJson("{preferences:{pinnedItemIds:[]}}", JsonObject.class);
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                received.add(request);
                String path = request.getRequestUrl().encodedPath();
                String response;
                if ("/api/game-data".equals(request.getPath())) {
                    response = "{quests:{0:{name:'Example quest',member:false,points:1}},"
                            + "diaries:{Ardougne:{Easy:[{task:'Example task'}]}},diary_tasks:{Ardougne:{Easy:[{bits:[[0,0]]}]}}}";
                } else if (path.endsWith("/get-group-data")) {
                    if (path.contains("/other/")) {
                        response = "[" + player(4, "Charlie", false) + "]";
                    } else {
                        if (holdOldGroup.get()) {
                            oldRequest.complete(null);
                            try {
                                releaseOldRequest.get(5, TimeUnit.SECONDS);
                            } catch (java.util.concurrent.ExecutionException
                                    | java.util.concurrent.TimeoutException error) {
                                throw new AssertionError(error);
                            }
                        }
                        boolean incremental = groupReads.getAndIncrement() > 0;
                        String alice = player(1, "Alice", incremental);
                        String bob = player(2, "Bob", false);
                        response = "[" + (incremental ? bob + "," + alice : alice + "," + bob)
                                + ",{id:3,name:'@SHARED',bank:[4151,3],last_updated:'2026-11-15T10:00:00Z'}]";
                    }
                } else if (path.endsWith("/activity")) {
                    response = request.getRequestUrl().queryParameter("cursor") == null
                            ? "{entries:[{id:1,member_id:1,revision:1,type:'level',is_weekly:false,started_at:1794736800000,occurred_at:1794736800000,"
                                    + "data:{skill:'Crafting',before:1,after:2}},"
                                    + "{id:2,member_id:1,revision:1,type:'loot',is_weekly:false,started_at:1794736790000,occurred_at:1794736790000,"
                                    + "data:{item_id:11804,quantity:1,source:'Example boss',value:1000000,new_collection_item:false}}],"
                                    + "from_time:'2026-11-15T10:00:00Z',older_cursor:'older'}"
                            : "{entries:[{id:3,member_id:1,revision:1,type:'collection',is_weekly:false,started_at:1794736700000,occurred_at:1794736700000,"
                                    + "data:{item_id:6585,quantity:1,source:'Example boss',value:200000,new_collection_item:false}}],"
                                    + "from_time:'2026-11-15T10:00:00Z',older_cursor:null}";
                } else if (path.endsWith("/preferences")) {
                    if (request.getMethod().equals("PATCH")) {
                        if (failPinSave.compareAndSet(true, false)) {
                            return new MockResponse().setResponseCode(503);
                        }
                        if (holdPinSave.compareAndSet(true, false)) {
                            pinSaveRequested.complete(null);
                            try {
                                releasePinSave.get(5, TimeUnit.SECONDS);
                            } catch (java.util.concurrent.ExecutionException
                                    | java.util.concurrent.TimeoutException error) {
                                throw new AssertionError(error);
                            }
                        }
                        preferences.add(
                                "preferences", gson.fromJson(request.getBody().readUtf8(), JsonObject.class));
                    }
                    response = gson.toJson(preferences);
                } else {
                    return new MockResponse().setResponseCode(404);
                }
                return new MockResponse().setBody(response);
            }
        });
        server.start();
        config = mock(GimHubConfig.class);
        when(config.groupName()).thenReturn("iron friends");
        when(config.authorizationToken()).thenReturn("test-group-token");
        when(config.baseUrlOverride()).thenReturn(server.url("/").toString());
        dataManager = mock(DataManager.class);
        when(dataManager.getActivePlayerName()).thenReturn("Alice");
        HttpRequestService http = new HttpRequestService();
        inject(http, "okHttpClient", new OkHttpClient());
        inject(http, "config", config);
        inject(http, "gson", gson);
        ApiUrlBuilder urls = new ApiUrlBuilder();
        inject(urls, "config", config);
        inject(urls, "httpRequestService", http);
        itemManager = mock(ItemManager.class);
        when(itemManager.getItemPrice(4151)).thenReturn(100L);
        when(itemManager.getItemPrice(385)).thenReturn(1L);
        Map<Integer, String> names =
                Map.of(4151, "Abyssal whip", 385, "Shark", 11804, "Bandos godsword", 6585, "Amulet of fury");
        when(itemManager.getItemComposition(anyInt())).thenAnswer(call -> {
            String name = names.get(call.<Integer>getArgument(0));
            ItemComposition item = mock(ItemComposition.class);
            when(item.getMembersName()).thenReturn(name);
            when(item.getName()).thenReturn(name + " (Members)");
            return item;
        });
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(call -> {
                    ((BooleanSupplier) call.getArgument(0)).getAsBoolean();
                    return null;
                })
                .when(clientThread)
                .invokeLater(any(BooleanSupplier.class));
        companion = new CompanionClient();
        inject(companion, "config", config);
        inject(companion, "http", http);
        inject(companion, "urls", urls);
        inject(companion, "dataManager", dataManager);
        inject(companion, "configManager", mock(ConfigManager.class));
        inject(companion, "gson", gson);
        inject(companion, "client", client);
        inject(companion, "clientThread", clientThread);
        inject(companion, "itemManager", itemManager);
        refreshReady = () -> companion.getStatus().isEmpty() && companion.canPin();
        companion.start(() -> {
            if (refreshed != null && refreshReady.getAsBoolean()) {
                refreshed.complete(null);
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        releaseOldRequest.complete(null);
        releasePinSave.complete(null);
        companion.stop();
        server.shutdown();
    }

    @Test
    public void readsExistingGroupFeaturesAndSyncsPinsWithoutLosingUnchangedStorage() throws Exception {
        refreshed = new CompletableFuture<>();
        companion.setVisible(true);
        refreshed.get(5, TimeUnit.SECONDS);
        CompanionData data = companion.getData();

        assertEquals(7, data.items("All players", "All storage", "whip").get(0).total());
        assertEquals(List.of(data.player("Alice"), data.player("Bob")), data.players());
        assertEquals(2, data.activities().size());
        assertEquals("Abyssal whip", data.itemName(4151));
        assertTrue(data.activityText(data.activities().get(1)).contains("Bandos godsword"));
        assertEquals(300, data.playerSkills("Alice").get("Crafting").getExperience());
        assertTrue(data.quests("Alice").get(0).isComplete());
        assertFalse(data.quests("Bob").get(0).isComplete());
        assertEquals(1, data.diaries("Alice").get(0).completed());
        assertEquals(0, data.diaries("Bob").get(0).completed());
        assertEquals(385, data.items("All players", "All storage", "").get(0).getIdentifier());

        refreshed = new CompletableFuture<>();
        refreshReady = () -> !companion.isLoadingOlder();
        companion.loadOlder();
        refreshed.get(5, TimeUnit.SECONDS);
        assertTrue(data.activityText(data.activities().get(2)).contains("Amulet of fury"));

        when(itemManager.getItemPrice(4151)).thenReturn(1000L);
        refreshReady = () -> data.player("Alice").getAsJsonArray("inventory").size() == 0
                && companion.getStatus().isEmpty();
        refreshed = new CompletableFuture<>();
        companion.refresh();
        refreshed.get(5, TimeUnit.SECONDS);
        assertEquals(List.of(data.player("Bob"), data.player("Alice")), data.players());
        assertEquals(385, data.items("All players", "All storage", "").get(0).getIdentifier());

        preferences
                .getAsJsonObject("preferences")
                .add("pinnedItemIds", gson.toJsonTree(Collections.singletonList(385)));
        refreshReady = () -> data.pinnedItems().equals(Collections.singleton(385));
        refreshed = new CompletableFuture<>();
        refreshReady = () -> !companion.isPinPending(4151);
        companion.togglePin(4151);
        refreshed.get(5, TimeUnit.SECONDS);
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(385, 4151)), data.pinnedItems());
        assertEquals(1, preferences.getAsJsonObject("preferences").size());

        preferences
                .getAsJsonObject("preferences")
                .add("pinnedItemIds", gson.toJsonTree(Collections.singletonList(385)));
        refreshed = new CompletableFuture<>();
        companion.refresh();
        refreshed.get(5, TimeUnit.SECONDS);
        assertEquals(6, data.items("All players", "All storage", "whip").get(0).total());
        assertEquals(Collections.singleton(385), data.pinnedItems());
        assertTrue(received.stream()
                .anyMatch(request -> request.getPath().startsWith("/api/group/iron%20friends/get-group-data?")
                        && request.getRequestUrl().queryParameter("from_time").equals("2026-11-15T10:00:00Z")));
        for (RecordedRequest request : received) {
            String expectedToken = "/api/game-data".equals(request.getPath()) ? null : "test-group-token";
            assertEquals(expectedToken, request.getHeader("Authorization"));
        }
    }

    @Test
    public void loadsNewGroupWithoutWaitingForPreviousGroupResponse() throws Exception {
        refreshed = new CompletableFuture<>();
        companion.setVisible(true);
        refreshed.get(5, TimeUnit.SECONDS);
        holdOldGroup.set(true);
        companion.refresh();
        oldRequest.get(5, TimeUnit.SECONDS);

        when(config.groupName()).thenReturn("other");
        when(dataManager.getActivePlayerName()).thenReturn("Charlie");
        refreshed = new CompletableFuture<>();
        companion.refresh();
        refreshed.get(5, TimeUnit.SECONDS);

        assertNull(companion.getData().player("Alice"));
        assertNotNull(companion.getData().player("Charlie"));
        assertEquals(0, companion.getUnreadCount());
    }

    @Test
    public void savesRapidPinPressesWhileAnotherSaveIsStillPending() throws Exception {
        refreshed = new CompletableFuture<>();
        companion.setVisible(true);
        refreshed.get(5, TimeUnit.SECONDS);

        holdPinSave.set(true);
        companion.togglePin(4151);
        pinSaveRequested.get(5, TimeUnit.SECONDS);
        companion.togglePin(385);
        companion.togglePin(4151);

        assertTrue(companion.isPinned(385));
        assertFalse(companion.isPinned(4151));
        assertTrue(companion.isPinPending(385));
        assertTrue(companion.isPinPending(4151));

        refreshReady = () -> !companion.isPinPending(385) && !companion.isPinPending(4151);
        refreshed = new CompletableFuture<>();
        releasePinSave.complete(null);
        refreshed.get(5, TimeUnit.SECONDS);

        assertEquals(Collections.singleton(385), companion.getData().pinnedItems());
    }

    @Test
    public void retriesPendingPinWhileSidebarIsHidden() throws Exception {
        refreshed = new CompletableFuture<>();
        companion.setVisible(true);
        refreshed.get(5, TimeUnit.SECONDS);

        failPinSave.set(true);
        refreshReady = () -> !companion.getStatus().isEmpty();
        refreshed = new CompletableFuture<>();
        companion.togglePin(4151);
        refreshed.get(5, TimeUnit.SECONDS);
        assertTrue(companion.isPinPending(4151));
        companion.setVisible(false);

        refreshReady =
                () -> !companion.isPinPending(4151) && companion.getStatus().isEmpty();
        refreshed = new CompletableFuture<>();
        companion.refresh();
        refreshed.get(5, TimeUnit.SECONDS);

        assertEquals(Collections.singleton(4151), companion.getData().pinnedItems());
        assertEquals(1, groupReads.get());
    }

    private String player(int identifier, String name, boolean incremental) {
        boolean completed = name.equals("Alice");
        JsonObject player = new JsonObject();
        player.addProperty("id", identifier);
        player.addProperty("name", name);
        player.addProperty("last_updated", "2026-11-15T10:00:00Z");
        player.addProperty("last_online_at", Instant.now().toString());
        player.add(
                "bank",
                incremental
                        ? com.google.gson.JsonNull.INSTANCE
                        : gson.fromJson("[4151," + (completed ? 1 : 2) + ",385,1000]", JsonArray.class));
        player.add("inventory", gson.fromJson(completed && !incremental ? "[4151,1]" : "[]", JsonArray.class));
        player.add("equipment", new JsonArray());
        player.add(
                "stash_units",
                gson.fromJson("[{id:1,name:'Empty STASH',state:'empty',items:[4151,99]}]", JsonArray.class));
        player.add("skills", skills(300));
        player.add("quests", gson.fromJson(completed ? "[2]" : "[1]", JsonArray.class));
        player.add("diary_vars", gson.fromJson(completed ? "[1]" : "[0]", JsonArray.class));
        return gson.toJson(player);
    }

    private JsonArray skills(int experience) {
        JsonArray skills = new JsonArray();
        for (int index = 0; index < CompanionData.SKILLS.size(); index++) {
            skills.add(experience);
        }
        return skills;
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
