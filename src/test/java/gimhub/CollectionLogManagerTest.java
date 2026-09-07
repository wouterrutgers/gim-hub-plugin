package gimhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Before;
import org.junit.Test;

public class CollectionLogManagerTest {
    private Client client;
    private CollectionLogItemResolver itemResolver;
    private CollectionLogManager collectionLogManager;
    private ItemManager itemManager;

    @Before
    public void setUp() {
        client = mock(Client.class);
        itemResolver = mock(CollectionLogItemResolver.class);
        collectionLogManager = new CollectionLogManager();
        itemManager = mock(ItemManager.class);
        when(itemManager.canonicalize(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(0);
    }

    @Test
    public void storesScannedQuantitiesIncludingZeroAndPublishesThem() {
        collectionLogManager.storeCollectionLogItem(100, 2);
        collectionLogManager.storeCollectionLogItem(200, 0);

        assertEquals(Map.of(100, 2, 200, 0), flattenedItems());
    }

    @Test
    public void automaticScanPublishesOnlyAfterTheQuietTickBoundary() {
        when(client.getTickCount()).thenReturn(10);
        collectionLogManager.onScriptPostFired(client, new ScriptPostFired(7797));
        collectionLogManager.storeCollectionLogItem(100, 2);
        assertNull(flattenedValue());

        when(client.getTickCount()).thenReturn(12);
        collectionLogManager.onGameTick(client);
        assertNull(flattenedValue());

        when(client.getTickCount()).thenReturn(13);
        collectionLogManager.onGameTick(client);
        assertEquals(Map.of(100, 2), flattenedItems());
    }

    @Test
    public void repeatedSetupDoesNotRestartTheAutomaticScan() {
        when(client.getTickCount()).thenReturn(10);

        collectionLogManager.onScriptPostFired(client, new ScriptPostFired(7797));
        collectionLogManager.onScriptPostFired(client, new ScriptPostFired(7797));

        verify(client, times(1))
                .menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
        verify(client, times(1)).runScript(2240);
    }

    @Test
    public void collectionTransmitStoresTheScriptItem() {
        when(client.getTickCount()).thenReturn(20);
        ScriptEvent scriptEvent = mock(ScriptEvent.class);
        when(scriptEvent.getArguments()).thenReturn(new Object[] {null, 100, 3});
        ScriptPreFired event = new ScriptPreFired(4100);
        event.setScriptEvent(scriptEvent);

        collectionLogManager.onScriptPreFired(client, event, itemResolver);

        assertEquals(Map.of(100, 3), flattenedItems());
    }

    @Test
    public void chatNotificationStoresResolvedItem() {
        when(client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM)).thenReturn(1);
        when(itemResolver.findItemIdentifier("Dragon axe")).thenReturn(6739);
        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("New item added to your collection log: Dragon axe");

        collectionLogManager.onChatMessage(client, event, itemResolver);

        when(client.getTickCount()).thenReturn(11);
        collectionLogManager.onGameTick(client);
        assertEquals(Map.of(6739, 1), flattenedItems());
    }

    @Test
    public void popupNotificationRequiresStartAndSanitizesItsItemName() {
        when(client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE)).thenReturn("Collection log");
        when(client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN))
                .thenReturn("<col=ff0000>New item:</col><br>Dragon axe");
        when(itemResolver.findItemIdentifier("Dragon axe")).thenReturn(6739);

        collectionLogManager.onScriptPreFired(client, new ScriptPreFired(ScriptID.NOTIFICATION_DELAY), itemResolver);
        assertNull(flattenedValue());

        collectionLogManager.onScriptPreFired(client, new ScriptPreFired(ScriptID.NOTIFICATION_START), itemResolver);
        collectionLogManager.onScriptPreFired(client, new ScriptPreFired(ScriptID.NOTIFICATION_DELAY), itemResolver);

        when(client.getTickCount()).thenReturn(11);
        collectionLogManager.onGameTick(client);
        assertEquals(Map.of(6739, 1), flattenedItems());
    }

    @Test
    public void ignoresAmbiguousAndUnknownNotificationItems() {
        when(itemResolver.findItemIdentifier("Unknown item")).thenReturn(null);

        collectionLogManager.handleNewCollectionLogItem("Chompy bird hat", itemResolver);
        collectionLogManager.handleNewCollectionLogItem("Unknown item", itemResolver);

        verify(itemResolver, never()).findItemIdentifier("Chompy bird hat");
        verify(itemResolver).findItemIdentifier("Unknown item");
        assertNull(flattenedValue());
    }

    @Test
    public void adventureLogClearsStoredCollectionData() {
        collectionLogManager.storeCollectionLogItem(100, 2);
        assertEquals(Map.of(100, 2), flattenedItems());

        VarbitChanged event = mock(VarbitChanged.class);
        when(event.getVarbitId()).thenReturn(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN);
        when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(1);

        collectionLogManager.onVarbitChanged(client, event);

        assertNull(flattenedValue());
    }

    @Test
    public void leavingTheGameReleasesPendingCollectionData() {
        when(client.getTickCount()).thenReturn(10);
        collectionLogManager.onScriptPostFired(client, new ScriptPostFired(7797));
        collectionLogManager.storeCollectionLogItem(100, 2);
        assertNull(flattenedValue());

        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        collectionLogManager.onGameStateChanged(event);

        assertEquals(Map.of(100, 2), flattenedItems());
    }

    @Test
    public void repeatDropsPublishActualStackQuantitiesWithoutAScan() {
        loot(6739, 2);
        loot(6739, 1);
        List<Map<String, Object>> updates = flattenedUpdates();
        assertEquals(2, updates.size());
        assertEquals("drop", updates.get(0).get("type"));
        assertEquals(
                List.of(Map.of("item_id", 6739, "quantity", 2)), updates.get(0).get("items"));
        assertEquals(
                List.of(Map.of("item_id", 6739, "quantity", 1)), updates.get(1).get("items"));
        assertNull(flattenedValue());
    }

    @Test
    public void unlockBeforeLootDoesNotAddAnExtraAcquisition() {
        when(itemResolver.findItemIdentifier("Dragon axe")).thenReturn(6739);
        collectionLogManager.handleNewCollectionLogItem("Dragon axe", itemResolver);
        assertNull(flattenedValue());
        when(client.getTickCount()).thenReturn(2);
        loot(6739, 1);
        List<Map<String, Object>> updates = flattenedUpdates();
        assertEquals(1, updates.size());
        assertEquals("drop", updates.get(0).get("type"));
    }

    @Test
    public void lootBeforeUnlockDoesNotAddAnExtraAcquisitionEvenAfterUpload() {
        loot(6739, 1);
        assertEquals(1, flattenedUpdates().size());
        when(itemResolver.findItemIdentifier("Dragon axe")).thenReturn(6739);
        collectionLogManager.handleNewCollectionLogItem("Dragon axe", itemResolver);
        assertNull(flattenedValue());
    }

    @Test
    public void chatAndPopupOnlyPublishOneUnlock() {
        when(itemResolver.findItemIdentifier("Dragon axe")).thenReturn(6739);
        collectionLogManager.handleNewCollectionLogItem("Dragon axe", itemResolver);
        collectionLogManager.handleNewCollectionLogItem("Dragon axe", itemResolver);
        when(client.getTickCount()).thenReturn(11);
        collectionLogManager.onGameTick(client);
        assertEquals(1, flattenedUpdates().size());
    }

    @Test
    public void scanCountsStayOrderedWithDropsOnEitherSide() {
        loot(6739, 1);
        collectionLogManager.storeCollectionLogItem(6739, 5);
        loot(6739, 2);
        List<Map<String, Object>> updates = flattenedUpdates();
        assertEquals(
                List.of("drop", "scan", "drop"),
                updates.stream().map(update -> update.get("type")).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    public void fullScanWaitsUntilCompleteWithoutDiscardingDropsDuringIt() {
        when(client.getTickCount()).thenReturn(10);
        collectionLogManager.onScriptPostFired(client, new ScriptPostFired(7797));
        collectionLogManager.storeCollectionLogItem(6739, 5);
        loot(6739, 1);
        assertNull(flattenedValue());
        when(client.getTickCount()).thenReturn(13);
        collectionLogManager.onGameTick(client);
        assertEquals(2, flattenedUpdates().size());
    }

    @Test
    public void adventureLogDoesNotDiscardPendingPersonalDrops() {
        loot(6739, 1);
        collectionLogManager.storeCollectionLogItem(6739, 100);
        collectionLogManager.clearCollectionLogItems();
        assertEquals("drop", flattenedUpdates().get(0).get("type"));
    }

    @Test
    public void normalizesNotedItemsAndAcceptsActivityAndPickpocketLoot() {
        when(itemManager.canonicalize(6740)).thenReturn(6739);
        collectionLogManager.onLootReceived(
                client,
                new LootReceived("Barrows", 0, LootRecordType.EVENT, List.of(new ItemStack(6740, 2)), 1, null),
                itemManager);
        collectionLogManager.onLootReceived(
                client,
                new LootReceived("Elf", 0, LootRecordType.PICKPOCKET, List.of(new ItemStack(23962, 2)), 1, null),
                itemManager);
        List<Map<String, Object>> updates = flattenedUpdates();
        assertEquals(
                List.of(Map.of("item_id", 6739, "quantity", 2)), updates.get(0).get("items"));
        assertEquals(2, updates.size());
    }

    @Test
    public void excludesPlayerLootAndLootKeyChests() {
        collectionLogManager.onLootReceived(
                client,
                new LootReceived("Player", 0, LootRecordType.PLAYER, List.of(new ItemStack(6739, 1)), 1, null),
                itemManager);
        collectionLogManager.onLootReceived(
                client,
                new LootReceived("Loot Chest", 0, LootRecordType.EVENT, List.of(new ItemStack(6739, 1)), 1, null),
                itemManager);
        assertNull(flattenedValue());
    }

    @Test
    public void anUnlockWithoutLootDoesNotSuppressALaterRepeatDrop() {
        when(itemResolver.findItemIdentifier("Dragon axe")).thenReturn(6739);
        collectionLogManager.handleNewCollectionLogItem("Dragon axe", itemResolver);
        when(client.getTickCount()).thenReturn(11);
        collectionLogManager.onGameTick(client);
        assertEquals("unlock", flattenedUpdates().get(0).get("type"));
        when(client.getTickCount()).thenReturn(12);
        loot(6739, 1);
        assertEquals("drop", flattenedUpdates().get(0).get("type"));
    }

    @Test
    public void logoutReleasesPendingUnlocksEvenWhenTheClientTickCounterRestarts() {
        when(client.getTickCount()).thenReturn(100);
        collectionLogManager.onGameTick(client);
        when(itemResolver.findItemIdentifier("Dragon axe")).thenReturn(6739);
        collectionLogManager.handleNewCollectionLogItem("Dragon axe", itemResolver);
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        collectionLogManager.onGameStateChanged(event);
        when(client.getTickCount()).thenReturn(0);
        collectionLogManager.onGameTick(client);
        assertEquals("unlock", flattenedUpdates().get(0).get("type"));
    }

    @Test
    public void aScanAfterAnUnlockAlreadyIncludesTheLateLootNotification() {
        when(itemResolver.findItemIdentifier("Dragon axe")).thenReturn(6739);
        collectionLogManager.handleNewCollectionLogItem("Dragon axe", itemResolver);
        collectionLogManager.storeCollectionLogItem(6739, 1);
        loot(6739, 1);
        List<Map<String, Object>> updates = flattenedUpdates();
        assertEquals(1, updates.size());
        assertEquals("scan", updates.get(0).get("type"));
        assertEquals(
                List.of(Map.of("item_id", 6739, "quantity", 1)), updates.get(0).get("items"));
        loot(6739, 1);
        assertEquals("drop", flattenedUpdates().get(0).get("type"));
    }

    private void loot(int identifier, int quantity) {
        collectionLogManager.onLootReceived(
                client,
                new LootReceived(
                        "Dagannoth Rex", 0, LootRecordType.NPC, List.of(new ItemStack(identifier, quantity)), 1, null),
                itemManager);
    }

    private APISerializable flattenedValue() {
        Map<String, APISerializable> flattened = new HashMap<>();
        collectionLogManager.flatten(flattened);
        return flattened.get("collection_log_updates");
    }

    private List<Map<String, Object>> flattenedUpdates() {
        return (List<Map<String, Object>>) flattenedValue().serialize();
    }

    private Map<Integer, Integer> flattenedItems() {
        Map<Integer, Integer> result = new HashMap<>();
        for (Map<String, Object> update : flattenedUpdates()) {
            for (Map<String, Integer> item : (List<Map<String, Integer>>) update.get("items")) {
                result.put(item.get("item_id"), item.get("quantity"));
            }
        }
        return result;
    }
}
