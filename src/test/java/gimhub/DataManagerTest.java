package gimhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

public class DataManagerTest {
    private Client client;
    private Player player;
    private HttpRequestService httpRequestService;
    private DataManager dataManager;

    @Before
    public void setUp() throws ReflectiveOperationException {
        client = mock(Client.class);
        player = mock(Player.class);
        GimHubConfig configuration = mock(GimHubConfig.class);
        ApiUrlBuilder apiUrlBuilder = mock(ApiUrlBuilder.class);
        httpRequestService = mock(HttpRequestService.class);
        dataManager = new DataManager();
        setField(dataManager, "httpRequestService", httpRequestService);
        setField(dataManager, "config", configuration);
        setField(dataManager, "apiUrlBuilder", apiUrlBuilder);

        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("Player one");
        when(configuration.authorizationToken()).thenReturn(" group-token ");
        when(apiUrlBuilder.getMembershipCheckUrl("Player one")).thenReturn("https://gim-hub.test/membership");
        when(apiUrlBuilder.getUpdateGroupMemberUrl()).thenReturn("https://gim-hub.test/update");
        when(httpRequestService.get("https://gim-hub.test/membership", "group-token"))
                .thenReturn(response(true, 200));
        when(httpRequestService.post(eq("https://gim-hub.test/update"), eq("group-token"), anyMap()))
                .thenReturn(response(true, 200));
    }

    @Test
    public void returnsNullForInvalidPlayersAndReusesValidState() {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        assertNull(dataManager.getMaybeResetState(client));

        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        assertSame(state, dataManager.getMaybeResetState(client));

        when(player.getName()).thenReturn("Player two");
        assertNotSame(state, dataManager.getMaybeResetState(client));
    }

    @Test
    public void transmitsNewStorageTypesAndResetsAllTrackersWhenTheAccountChanges() {
        ItemManager itemManager = mock(ItemManager.class);
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getPlaceholderTemplateId()).thenReturn(-1);
        when(itemManager.getItemComposition(ArgumentMatchers.anyInt())).thenReturn(composition);
        when(itemManager.canonicalize(ArgumentMatchers.anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.getAccountHash()).thenReturn(123L);
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        for (int inventoryId :
                new int[] {InventoryID.LOOTING_BAG, InventoryID.SEED_BOX, InventoryID.PREPOT_DEVICE_INV}) {
            ItemContainer container = mock(ItemContainer.class);
            when(container.getId()).thenReturn(inventoryId);
            when(container.getItems()).thenReturn(new Item[] {new Item(199, 2)});
            state.itemRepository.onItemContainerChanged(container, itemManager);
        }
        for (String message : new String[] {
            "The herb sack is empty.", "Sapphires: 2 / Emeralds: 0 / Rubies: 0 / Diamonds: 0 / Dragonstones: 0"
        }) {
            state.itemRepository.onChatMessage(
                    client, new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", message, "", 0), itemManager);
        }
        // A successful individual interaction also enters the same batched property upload.
        EnumComposition names = mock(EnumComposition.class);
        when(names.getStringValue(34736)).thenReturn("Gypsy tent entrance");
        EnumComposition slots = mock(EnumComposition.class);
        when(slots.getIntValue(34736)).thenReturn(-1);
        EnumComposition beginner = mock(EnumComposition.class);
        when(beginner.getIntVals()).thenReturn(new int[] {34736});
        when(client.getEnum(1531)).thenReturn(names);
        when(client.getEnum(1525)).thenReturn(slots);
        when(client.getEnum(2317)).thenReturn(beginner);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3206, 3422, 0));
        state.itemRepository.onChatMessage(
                client,
                new ChatMessage(
                        null, ChatMessageType.GAMEMESSAGE, "", "You deposit your items into the STASH unit.", "", 0),
                itemManager);
        when(client.getEnum(EnumID.RUNEPOUCH_RUNE)).thenReturn(mock(EnumComposition.class));
        when(client.getVarbitValue(VarbitID.FARMING_TOOLS_WATERINGCAN)).thenReturn(-1);
        state.itemRepository.onGameTick(client, itemManager);
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");
        ArgumentCaptor<Map<String, Object>> updates = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService, times(1))
                .post(eq("https://gim-hub.test/update"), eq("group-token"), updates.capture());
        for (String property :
                List.of("herb_sack", "looting_bag", "seed_box", "gem_bag", "chugging_barrel", "stash_units")) {
            assertTrue(updates.getValue().containsKey(property));
        }
        JsonObject serialized = new Gson().toJsonTree(updates.getValue()).getAsJsonObject();
        assertEquals(
                34736,
                serialized
                        .getAsJsonArray("stash_units")
                        .get(0)
                        .getAsJsonObject()
                        .get("id")
                        .getAsInt());
        assertEquals(
                "filled",
                serialized
                        .getAsJsonArray("stash_units")
                        .get(0)
                        .getAsJsonObject()
                        .get("state")
                        .getAsString());

        when(client.getAccountHash()).thenReturn(456L);
        DataManager.PlayerState otherAccount = dataManager.getMaybeResetState(client);
        assertNotSame(state, otherAccount);
        Map<String, APISerializable> properties = new HashMap<>();
        otherAccount.itemRepository.flatten(properties);
        for (String property :
                List.of("herb_sack", "looting_bag", "seed_box", "gem_bag", "chugging_barrel", "stash_units")) {
            assertNull(properties.get(property));
        }
        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        dataManager.onGameStateChanged(logout);
        assertNull(dataManager.getActivePlayerName());
        assertSame(otherAccount, dataManager.getMaybeResetState(client));
    }

    @Test
    public void baselineOnlyStateDoesNotPost() {
        dataManager.getMaybeResetState(client);
        dataManager.stageForSubmitToAPI();

        dataManager.submitToApi("Player one");

        verify(httpRequestService).get("https://gim-hub.test/membership", "group-token");
        verify(httpRequestService, never()).post(eq("https://gim-hub.test/update"), eq("group-token"), anyMap());
    }

    @Test
    public void postsChangedFieldsAndSuppressesAnUnchangedSecondSnapshot() {
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        state.activityRepository.updateResources(client);
        dataManager.stageForSubmitToAPI();

        dataManager.submitToApi("Player one");
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");

        ArgumentCaptor<Map<String, Object>> updates = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService, times(1))
                .post(eq("https://gim-hub.test/update"), eq("group-token"), updates.capture());
        assertTrue(updates.getValue().containsKey("name"));
        assertTrue(updates.getValue().containsKey("league_mode"));
        assertTrue(updates.getValue().containsKey("timezone"));
        assertTrue(updates.getValue().containsKey("stats"));
    }

    @Test
    public void mergesSnapshotsStagedBeforeTheRequestThreadConsumesThem() {
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        state.activityRepository.updateResources(client);
        dataManager.stageForSubmitToAPI();
        state.activityRepository.updateSkills(client);
        dataManager.stageForSubmitToAPI();

        dataManager.submitToApi("Player one");

        ArgumentCaptor<Map<String, Object>> updates = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService).post(eq("https://gim-hub.test/update"), eq("group-token"), updates.capture());
        assertTrue(updates.getValue().containsKey("stats"));
        assertTrue(updates.getValue().containsKey("skills"));
    }

    @Test
    public void retainsEveryCollectionUpdateStagedBeforeUploadInOrder() {
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        state.collectionLogManager.storeCollectionLogItem(6739, 2);
        dataManager.stageForSubmitToAPI();
        drop(state);
        dataManager.stageForSubmitToAPI();
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService, times(1))
                .post(eq("https://gim-hub.test/update"), eq("group-token"), payload.capture());
        List<Map<String, Object>> updates =
                (List<Map<String, Object>>) payload.getValue().get("collection_log_updates");
        assertEquals(2, updates.size());
        assertEquals("scan", updates.get(0).get("type"));
        assertEquals("drop", updates.get(1).get("type"));
        assertTrue(!payload.getValue().containsKey("collection_log_v2"));
    }

    @Test
    public void pendingUnlocksSurviveLogoutAndAreUploadedOnceAfterTheSameAccountReturns() {
        when(client.getTickCount()).thenReturn(100);
        when(client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM)).thenReturn(1);
        CollectionLogItemResolver resolver = mock(CollectionLogItemResolver.class);
        when(resolver.findItemIdentifier("Phoenix")).thenReturn(20693);
        dataManager
                .getMaybeResetState(client)
                .collectionLogManager
                .onChatMessage(
                        client,
                        new ChatMessage(
                                null,
                                ChatMessageType.GAMEMESSAGE,
                                "",
                                "New item added to your collection log: Phoenix",
                                "",
                                0),
                        resolver);
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");
        verify(httpRequestService, never()).post(eq("https://gim-hub.test/update"), eq("group-token"), anyMap());

        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        dataManager.onGameStateChanged(logout);
        assertNull(dataManager.getActivePlayerName());
        assertNull(dataManager.getMaybeResetState(client));

        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getTickCount()).thenReturn(1);
        dataManager.getMaybeResetState(client).collectionLogManager.onGameTick(client);
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService, times(1))
                .post(eq("https://gim-hub.test/update"), eq("group-token"), payload.capture());
        List<Map<String, Object>> updates =
                (List<Map<String, Object>>) payload.getValue().get("collection_log_updates");
        assertEquals(1, updates.size());
        assertEquals("unlock", updates.get(0).get("type"));
        assertEquals(
                List.of(Map.of("item_id", 20693, "quantity", 1)), updates.get(0).get("items"));
    }

    @Test
    public void stagedDropsSurviveLogoutUntilTheSameAccountUploadsThem() {
        drop(dataManager.getMaybeResetState(client));
        dataManager.stageForSubmitToAPI();
        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        dataManager.onGameStateChanged(logout);
        dataManager.getMaybeResetState(client);
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService, times(1))
                .post(eq("https://gim-hub.test/update"), eq("group-token"), payload.capture());
        List<Map<String, Object>> updates =
                (List<Map<String, Object>>) payload.getValue().get("collection_log_updates");
        assertEquals(1, updates.size());
        assertEquals("drop", updates.get(0).get("type"));
        assertEquals(
                List.of(Map.of("item_id", 6739, "quantity", 1)), updates.get(0).get("items"));
    }

    @Test
    public void logoutDoesNotCarryStagedOrPendingUpdatesAcrossAccountHashes() {
        when(client.getAccountHash()).thenReturn(123L);
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        drop(state);
        dataManager.stageForSubmitToAPI();
        state.collectionLogManager.storeCollectionLogItem(4151, 4);
        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        dataManager.onGameStateChanged(logout);

        when(client.getAccountHash()).thenReturn(456L);
        state = dataManager.getMaybeResetState(client);
        state.activityRepository.updateResources(client);
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService).post(eq("https://gim-hub.test/update"), eq("group-token"), payload.capture());
        assertTrue(!payload.getValue().containsKey("collection_log_updates"));
    }

    @Test
    public void doesNotCarryCollectionUpdatesAcrossPlayers() throws ReflectiveOperationException {
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        drop(state);
        dataManager.stageForSubmitToAPI();
        when(player.getName()).thenReturn("Player two");
        state = dataManager.getMaybeResetState(client);
        state.activityRepository.updateResources(client);
        dataManager.stageForSubmitToAPI();
        ApiUrlBuilder builder = (ApiUrlBuilder) getField(dataManager, "apiUrlBuilder");
        when(builder.getMembershipCheckUrl("Player two")).thenReturn("https://gim-hub.test/membership");
        dataManager.submitToApi("Player two");

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService).post(eq("https://gim-hub.test/update"), eq("group-token"), payload.capture());
        assertTrue(!payload.getValue().containsKey("collection_log_updates"));
    }

    @Test
    public void doesNotCarryCollectionUpdatesAcrossProfilesOfTheSamePlayer() {
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        drop(state);
        dataManager.stageForSubmitToAPI();

        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL));
        state = dataManager.getMaybeResetState(client);
        state.collectionLogManager.storeCollectionLogItem(4151, 4);
        dataManager.stageForSubmitToAPI();
        for (int attempt = 0; attempt < 11; attempt++) dataManager.submitToApi("Player one");

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService, times(1))
                .post(eq("https://gim-hub.test/update"), eq("group-token"), payload.capture());
        List<Map<String, Object>> updates =
                (List<Map<String, Object>>) payload.getValue().get("collection_log_updates");
        assertEquals(1, updates.size());
        assertEquals(
                List.of(Map.of("item_id", 4151, "quantity", 4)), updates.get(0).get("items"));
    }

    private void drop(DataManager.PlayerState state) {
        ItemManager itemManager = mock(ItemManager.class);
        when(itemManager.canonicalize(6739)).thenReturn(6739);
        state.collectionLogManager.onLootReceived(
                client,
                new LootReceived("Dagannoth Rex", 0, LootRecordType.NPC, List.of(new ItemStack(6739, 1)), 1, null),
                itemManager);
    }

    private static HttpRequestService.HttpResponse response(boolean successful, int code) {
        return new HttpRequestService.HttpResponse(successful, code, "");
    }

    private static Object getField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
