package gimhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gimhub.items.ItemsUnordered;
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
import org.junit.Before;
import org.junit.Test;

public class CollectionLogManagerTest {
    private Client client;
    private CollectionLogItemResolver itemResolver;
    private CollectionLogManager collectionLogManager;

    @Before
    public void setUp() {
        client = mock(Client.class);
        itemResolver = mock(CollectionLogItemResolver.class);
        collectionLogManager = new CollectionLogManager();
        when(client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)).thenReturn(0);
    }

    @Test
    public void storesPositiveQuantitiesAndPublishesThem() {
        collectionLogManager.storeCollectionLogItem(100, 2);
        collectionLogManager.storeCollectionLogItem(200, 0);

        assertEquals(Map.of(100, 2), flattenedItems());
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

    private APISerializable flattenedValue() {
        Map<String, APISerializable> flattened = new HashMap<>();
        collectionLogManager.flatten(flattened);
        return flattened.get("collection_log_v2");
    }

    private Map<Integer, Integer> flattenedItems() {
        ItemsUnordered items = (ItemsUnordered) flattenedValue();
        List<Integer> serialized = (List<Integer>) items.serialize();
        Map<Integer, Integer> result = new HashMap<>();
        for (int index = 0; index < serialized.size(); index += 2) {
            result.put(serialized.get(index), serialized.get(index + 1));
        }
        return result;
    }
}
