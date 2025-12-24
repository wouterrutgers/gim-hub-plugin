package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import lombok.Getter;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;

@Getter
public class EquipmentItems implements TrackedItemContainer {
    public static final int SIZE = 14;

    private ItemsOrdered items = null;

    @Override
    public String key() {
        return "equipment";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == InventoryID.WORN) {
            items = new ItemsOrdered(container, itemManager, SIZE);
        }
    }
}
