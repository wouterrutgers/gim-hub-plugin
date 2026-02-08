package gimhub.items;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

public final class ItemsUtilities {
    private ItemsUtilities() {}

    public static boolean isItemValid(Item item, ItemManager itemManager) {
        if (item == null) return false;
        final int id = item.getId();
        if (itemManager != null) {
            final boolean isPlaceholder = itemManager.getItemComposition(id).getPlaceholderTemplateId() != -1;

            return id >= 0 && !isPlaceholder;
        }
        return false;
    }

    public static Map<Integer, Integer> convertToSafeMap(
            @Nullable Map<Integer, Integer> itemsQuantityByID, ItemManager itemManager) {
        final Map<Integer, Integer> resultQuantityByID = new HashMap<>();
        if (itemsQuantityByID == null) {
            return resultQuantityByID;
        }

        for (final Map.Entry<Integer, Integer> entry : itemsQuantityByID.entrySet()) {
            final Item item = new Item(entry.getKey(), entry.getValue());
            if (!ItemsUtilities.isItemValid(item, itemManager)) {
                continue;
            }

            final int itemID = itemManager.canonicalize(item.getId());
            resultQuantityByID.merge(itemID, item.getQuantity(), Integer::sum);
        }

        return resultQuantityByID;
    }

    public static Map<Integer, Integer> convertToSafeMap(@Nullable ItemContainer container, ItemManager itemManager) {
        final Map<Integer, Integer> resultQuantityByID = new HashMap<>();
        if (container == null) {
            return resultQuantityByID;
        }

        Item[] contents = container.getItems();
        for (final Item item : contents) {
            if (!ItemsUtilities.isItemValid(item, itemManager)) {
                continue;
            }

            final int itemID = itemManager.canonicalize(item.getId());
            resultQuantityByID.merge(itemID, item.getQuantity(), Integer::sum);
        }

        return resultQuantityByID;
    }
}
