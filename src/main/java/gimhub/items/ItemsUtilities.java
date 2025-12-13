package gimhub.items;

import net.runelite.api.Item;
import net.runelite.client.game.ItemManager;

public final class ItemsUtilities {
    private ItemsUtilities() {}

    public static boolean isItemValid(Item item, ItemManager itemManager) {
        if (item == null) return false;
        final int id = item.getId();
        final int quantity = item.getQuantity();
        if (itemManager != null) {
            final boolean isPlaceholder = itemManager.getItemComposition(id).getPlaceholderTemplateId() != -1;

            return id >= 0 && quantity >= 0 && !isPlaceholder;
        }
        return false;
    }
}
