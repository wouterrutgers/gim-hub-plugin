package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import gimhub.items.ItemsUtilities;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;

public class BankItems implements TrackedItemContainer {
    private ItemsUnordered items = null;
    private boolean known = false;

    public Map<Integer, Integer> getBankItems() {
        if (items == null) {
            return new HashMap<>();
        }
        return new HashMap<>(items.getItemsQuantityByID());
    }

    @Override
    public String key() {
        if (!known) {
            return "bank_partial";
        }
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
            known = true;
        }
    }

    public void setItems(Map<Integer, Integer> items, ItemManager itemManager) {
        if (this.items == null && items.isEmpty()) {
            return;
        }

        this.items = new ItemsUnordered(items, itemManager);
    }

    public void addItems(Map<Integer, Integer> items, ItemManager itemManager) {
        final Map<Integer, Integer> additionalItemsSafe = ItemsUtilities.convertToSafeMap(items, itemManager);
        final Map<Integer, Integer> resultItems = this.items.getItemsQuantityByID();

        for (final Map.Entry<Integer, Integer> entry : additionalItemsSafe.entrySet()) {
            final int itemID = entry.getKey();
            final int quantity = entry.getValue();
            if (quantity <= 0) {
                continue;
            }
            resultItems.merge(itemID, quantity, Integer::sum);
        }
        this.items = new ItemsUnordered(resultItems, itemManager);
    }
}
