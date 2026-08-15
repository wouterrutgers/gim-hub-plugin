package gimhub.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

public class ItemsTest {
    private ItemManager itemManager;

    @Before
    public void setUp() {
        itemManager = mock(ItemManager.class);
        when(itemManager.canonicalize(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer((InvocationOnMock invocation) -> invocation.getArgument(0));
        when(itemManager.getItemComposition(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer((InvocationOnMock invocation) -> validComposition());
    }

    @Test
    public void orderedItemsPreserveSlotsAndClampNegativeQuantities() {
        ItemsOrdered items = new ItemsOrdered(Arrays.asList(new Item(100, 2), null, new Item(200, -3)), itemManager);

        assertEquals(List.of(100, 2, 0, 0, 200, 0), items.serialize());
    }

    @Test
    public void orderedItemsPadContainersToTheRequestedSize() {
        ItemContainer container = mock(ItemContainer.class);
        when(container.getItem(0)).thenReturn(new Item(100, 2));
        when(container.getItem(1)).thenReturn(null);
        when(container.getItem(2)).thenReturn(new Item(200, 3));

        ItemsOrdered items = new ItemsOrdered(container, itemManager, 4);

        assertEquals(List.of(100, 2, 0, 0, 200, 3, 0, 0), items.serialize());
    }

    @Test
    public void unorderedItemsSkipZeroQuantities() {
        ItemsUnordered items = new ItemsUnordered();
        items.getItemsQuantityByID().put(100, 2);
        items.getItemsQuantityByID().put(200, 0);

        assertEquals(Map.of(100, 2), serializedPairs(items));
    }

    @Test
    public void unorderedDiffIncludesAddedRemovedAndChangedQuantities() {
        ItemsUnordered older = new ItemsUnordered();
        older.getItemsQuantityByID().putAll(Map.of(100, 5, 200, 3));
        ItemsUnordered newer = new ItemsUnordered();
        newer.getItemsQuantityByID().putAll(Map.of(100, 7, 300, 4));

        ItemsUnordered difference = (ItemsUnordered) older.diff(newer);

        assertEquals(Map.of(100, 2, 200, -3, 300, 4), serializedPairs(difference));
    }

    @Test
    public void safeMapCanonicalizesAndMergesItems() {
        when(itemManager.canonicalize(101)).thenReturn(100);

        assertEquals(Map.of(100, 5), ItemsUtilities.convertToSafeMap(Map.of(100, 2, 101, 3), itemManager));
    }

    @Test
    public void safeMapRejectsNegativeAndPlaceholderItems() {
        ItemComposition placeholder = mock(ItemComposition.class);
        when(placeholder.getPlaceholderTemplateId()).thenReturn(1);
        when(itemManager.getItemComposition(200)).thenReturn(placeholder);

        Map<Integer, Integer> items = ItemsUtilities.convertToSafeMap(Map.of(-1, 2, 100, 3, 200, 4), itemManager);

        assertEquals(Map.of(100, 3), items);
        assertTrue(ItemsUtilities.convertToSafeMap((Map<Integer, Integer>) null, itemManager)
                .isEmpty());
    }

    private static ItemComposition validComposition() {
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getPlaceholderTemplateId()).thenReturn(-1);
        return composition;
    }

    private static Map<Integer, Integer> serializedPairs(ItemsUnordered items) {
        List<Integer> serialized = (List<Integer>) items.serialize();
        java.util.HashMap<Integer, Integer> result = new java.util.HashMap<>();
        for (int index = 0; index < serialized.size(); index += 2) {
            result.put(serialized.get(index), serialized.get(index + 1));
        }
        return result;
    }
}
