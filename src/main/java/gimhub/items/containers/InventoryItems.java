package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;

public class InventoryItems implements TrackedItemContainer {
    public static final int SIZE = 28;

    private ItemsOrdered items = null;

    @Override
    public String key() {
        return "inventory";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    public ItemsOrdered getItems() {
        return items;
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == InventoryID.INV) {
            items = new ItemsOrdered(container, itemManager, SIZE);
        }
    }
}
