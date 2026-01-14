package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
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

    @Override
    public ItemsUnordered modify(ItemsUnordered itemsToDeposit) {
        items = ItemsUnordered.add(items, itemsToDeposit);
        final ItemsUnordered overage = ItemsUnordered.filter(items, (itemID, quantity) -> Math.min(quantity, 0));
        items = ItemsUnordered.subtract(items, overage);
        return overage;
    }
}
