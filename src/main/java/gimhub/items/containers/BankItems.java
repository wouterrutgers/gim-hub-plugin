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
        // If we haven't opened the bank yet this session, we don't have authoritative contents.
        // In that case, ignore modifications (but report no overage).
        if (items == null) {
            return new ItemsUnordered();
        }

        items = ItemsUnordered.add(items, itemsToDeposit);
        final ItemsUnordered overage = ItemsUnordered.filter(items, (itemID, quantity) -> Math.min(quantity, 0));
        items = ItemsUnordered.subtract(items, overage);
        return overage;
    }
}
