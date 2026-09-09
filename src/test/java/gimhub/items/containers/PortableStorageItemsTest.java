package gimhub.items.containers;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import gimhub.APISerializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PortableStorageItemsTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> containers() {
        return Arrays.asList(
                new Object[][] {{"herb_sack"}, {"looting_bag"}, {"seed_box"}, {"gem_bag"}, {"chugging_barrel"}});
    }

    @Parameterized.Parameter
    public String type;

    private Client client;
    private ItemManager itemManager;
    private TrackedItemContainer tracker;

    @Before
    public void setUp() {
        client = mock(Client.class);
        itemManager = mock(ItemManager.class);
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getPlaceholderTemplateId()).thenReturn(-1);
        when(itemManager.getItemComposition(anyInt())).thenReturn(composition);
        when(itemManager.canonicalize(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        switch (type) {
            case "herb_sack":
                tracker = new HerbSackItems();
                break;
            case "looting_bag":
                tracker = new LootingBagItems();
                break;
            case "seed_box":
                tracker = new SeedBoxItems();
                break;
            case "gem_bag":
                tracker = new GemBagItems();
                break;
            default:
                tracker = new ChuggingBarrelItems();
        }
    }

    @Test
    public void readsRepresentativeContentsAndSerializesItemQuantities() {
        observe(12);
        assertEquals(type, tracker.key());
        assertEquals(Map.of(itemId(), 12), pairs(tracker.get()));
    }

    @Test
    public void confirmedEmptyClearsAnEarlierSnapshot() {
        observe(12);
        observe(0);
        assertEquals(List.of(), tracker.get().serialize());
    }

    @Test
    public void missingDataAndClosedInterfacesPreserveKnownContents() {
        assertNull(tracker.get());
        tracker.onGameTick(client, itemManager);
        assertNull(tracker.get());
        observe(12);
        APISerializable known = tracker.get();
        tracker.onGameTick(client, itemManager);
        tracker.onItemContainerChanged(container(InventoryID.BANK, new Item[0]), itemManager);
        tracker.onGameTick(client, itemManager);
        assertEquals(known, tracker.get());
    }

    @Test
    public void identicalObservationsAreEqualAndDoNotAppendItems() {
        observe(12);
        APISerializable first = tracker.get();
        observe(12);
        assertEquals(first, tracker.get());
        assertEquals(Map.of(itemId(), 12), pairs(tracker.get()));
    }

    private void observe(int quantity) {
        if (type.equals("herb_sack")) {
            if (quantity == 0) {
                chat("The herb sack is empty.");
            } else {
                chat("You look in your herb sack and see:");
                chat(quantity + " x Grimy ranarr weed");
            }
            tracker.onGameTick(client, itemManager);
        } else if (type.equals("gem_bag")) {
            chat("Sapphires: " + quantity + " / Emeralds: 0 / Rubies: 0 / Diamonds: 0 / Dragonstones: 0");
        } else {
            tracker.onItemContainerChanged(
                    container(
                            ((ContainerItems) tracker).inventoryId,
                            quantity == 0 ? new Item[0] : new Item[] {new Item(itemId(), quantity)}),
                    itemManager);
        }
    }

    private int itemId() {
        switch (type) {
            case "herb_sack":
                return ItemID.UNIDENTIFIED_RANARR;
            case "gem_bag":
                return ItemID.UNCUT_SAPPHIRE;
            case "seed_box":
                return ItemID.RANARR_SEED;
            case "chugging_barrel":
                return ItemID._1DOSE2RESTORE;
            default:
                return ItemID.COINS;
        }
    }

    private void chat(String message) {
        tracker.onChatMessage(
                client, new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", message, "", 0), itemManager);
    }

    static ItemContainer container(int identifier, Item... items) {
        ItemContainer container = mock(ItemContainer.class);
        when(container.getId()).thenReturn(identifier);
        when(container.getItems()).thenReturn(items);
        when(container.getItem(anyInt())).thenAnswer(invocation -> {
            int index = invocation.getArgument(0);
            return index < items.length ? items[index] : null;
        });
        return container;
    }

    static Map<Integer, Integer> pairs(APISerializable contents) {
        List<?> serialized = (List<?>) contents.serialize();
        Map<Integer, Integer> result = new HashMap<>();
        for (int index = 0; index < serialized.size(); index += 2) {
            result.put((Integer) serialized.get(index), (Integer) serialized.get(index + 1));
        }
        return result;
    }
}
