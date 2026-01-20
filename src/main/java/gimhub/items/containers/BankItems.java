package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;

public class BankItems implements TrackedItemContainer {
    private ItemsUnordered items = null;

    @Override
    public String key() {
        return "bank";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == InventoryID.BANK) {
            items = new ItemsUnordered(container, itemManager);
        }
    }

    public void setItems(Map<Integer, Integer> items) {
        Map<Integer, Integer> safeMap = new HashMap<>();
        for (final Map.Entry<Integer, Integer> entry : items.entrySet()) {
            final int itemID = entry.getKey();
            final int quantity = entry.getValue();
            if (quantity <= 0) {
                continue;
            }
            safeMap.put(itemID, quantity);
        }
        this.items = new ItemsUnordered(safeMap);
    }

    public void addItems(Map<Integer, Integer> items) {
        Map<Integer, Integer> safeMap = this.items.getItemsQuantityByID();
        for (final Map.Entry<Integer, Integer> entry : items.entrySet()) {
            final int itemID = entry.getKey();
            final int quantity = entry.getValue();
            if (quantity <= 0) {
                continue;
            }
            safeMap.merge(itemID, quantity, Integer::sum);
        }
        this.items = new ItemsUnordered(safeMap);
    }
}
