package gimhub.items.containers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gimhub.APISerializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

public class TrackedItemContainersTest {
    private Client client;
    private ItemManager itemManager;

    @Before
    public void setUp() {
        client = mock(Client.class);
        itemManager = mock(ItemManager.class);
        when(itemManager.canonicalize(anyInt())).thenAnswer((InvocationOnMock invocation) -> invocation.getArgument(0));
        when(itemManager.getItemComposition(anyInt())).thenAnswer((InvocationOnMock invocation) -> validComposition());
    }

    @Test
    public void runePouchPreservesItsFourSlots() {
        EnumComposition runePouchEnum = mock(EnumComposition.class);
        when(client.getEnum(EnumID.RUNEPOUCH_RUNE)).thenReturn(runePouchEnum);
        when(runePouchEnum.getIntValue(1)).thenReturn(100);
        when(runePouchEnum.getIntValue(2)).thenReturn(200);
        when(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_1)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)).thenReturn(50);
        when(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_2)).thenReturn(2);
        when(client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_2)).thenReturn(75);

        RunePouchItems runePouchItems = new RunePouchItems();
        runePouchItems.onGameTick(client, itemManager);

        assertEquals(List.of(100, 50, 200, 75, 0, 0, 0, 0), runePouchItems.get().serialize());
    }

    @Test
    public void plankSackPublishesAndClearsDepositedPlanks() {
        when(client.getVarbitValue(VarbitID.PLANK_SACK_PLAIN)).thenReturn(5);
        when(client.getVarbitValue(VarbitID.PLANK_SACK_OAK)).thenReturn(3);
        PlankSackItems plankSackItems = new PlankSackItems();
        plankSackItems.onGameTick(client, itemManager);

        assertEquals(Map.of(ItemID.WOODPLANK, 5, ItemID.PLANK_OAK, 3), serializedPairs(plankSackItems.get()));
        assertEquals(
                Map.of(ItemID.WOODPLANK, 5, ItemID.PLANK_OAK, 3),
                plankSackItems.onDepositContainers(client, itemManager, Set.of(ItemID.PLANK_SACK)));
        assertEquals(Map.of(), serializedPairs(plankSackItems.get()));
    }

    @Test
    public void sharedBankCommitsOnlyWhenTheSavingIndicatorAppears() {
        SharedBankItems sharedBankItems = new SharedBankItems();
        ItemContainer container = mock(ItemContainer.class);
        when(container.getId()).thenReturn(InventoryID.INV_GROUP_TEMP);
        when(container.getItems()).thenReturn(new Item[] {new Item(100, 2)});
        sharedBankItems.onItemContainerChanged(container, itemManager);
        assertNull(sharedBankItems.get());

        Widget widget = mock(Widget.class);
        when(widget.getText()).thenReturn("Saving...");
        when(client.getWidget(anyInt(), anyInt())).thenReturn(widget);
        sharedBankItems.onGameTick(client, itemManager);

        assertEquals(Map.of(100, 2), serializedPairs(sharedBankItems.get()));
    }

    @Test
    public void potionStoragePublishesVialsAndClearsWhenTheInterfaceCloses() {
        PotionStorageItems potionStorageItems = new PotionStorageItems();
        Widget widget = mock(Widget.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.POTIONSTORE_ITEMS))
                .thenReturn(widget);
        when(client.getVarpValue(VarPlayerID.POTIONSTORE_VIALS)).thenReturn(3);
        when(client.getEnum(EnumID.POTIONSTORE_POTIONS)).thenReturn(null);
        when(client.getEnum(EnumID.POTIONSTORE_UNFINISHED_POTIONS)).thenReturn(null);

        potionStorageItems.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.VIAL_EMPTY, 3), serializedPairs(potionStorageItems.get()));

        when(client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.POTIONSTORE_ITEMS))
                .thenReturn(null);
        potionStorageItems.onGameTick(client, itemManager);
        assertNull(potionStorageItems.get());
    }

    @Test
    public void fishBarrelParsesItsCheckMessageAndClearsDepositedFish() {
        FishBarrelItems fishBarrelItems = new FishBarrelItems();
        Widget widget = mock(Widget.class);
        when(widget.getText()).thenReturn("The barrel contains: 2 x trout, 3 x lobster");
        when(client.getWidget(193, 2)).thenReturn(widget);

        fishBarrelItems.onGameTick(client, itemManager);

        assertEquals(Map.of(ItemID.RAW_TROUT, 2, ItemID.RAW_LOBSTER, 3), serializedPairs(fishBarrelItems.get()));
        assertEquals(
                Map.of(ItemID.RAW_TROUT, 2, ItemID.RAW_LOBSTER, 3),
                fishBarrelItems.onDepositContainers(client, itemManager, Set.of(ItemID.FISH_BARREL_CLOSED)));
        assertEquals(Map.of(), serializedPairs(fishBarrelItems.get()));
    }

    @Test
    public void coalBagParsesItsContentsAndClearsDepositedCoal() {
        CoalBagItems coalBagItems = new CoalBagItems();
        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("The coal bag contains 12 pieces of coal.");

        coalBagItems.onChatMessage(client, event, itemManager);

        assertEquals(Map.of(ItemID.COAL, 12), serializedPairs(coalBagItems.get()));
        assertEquals(
                Map.of(ItemID.COAL, 12),
                coalBagItems.onDepositContainers(client, itemManager, Set.of(ItemID.COAL_BAG)));
        assertEquals(Map.of(), serializedPairs(coalBagItems.get()));
    }

    private static ItemComposition validComposition() {
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getPlaceholderTemplateId()).thenReturn(-1);
        return composition;
    }

    private static Map<Integer, Integer> serializedPairs(APISerializable items) {
        List<Integer> serialized = (List<Integer>) items.serialize();
        Map<Integer, Integer> result = new HashMap<>();
        for (int index = 0; index < serialized.size(); index += 2) {
            result.put(serialized.get(index), serialized.get(index + 1));
        }
        return result;
    }
}
