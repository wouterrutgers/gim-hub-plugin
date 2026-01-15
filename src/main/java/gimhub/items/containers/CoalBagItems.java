package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

public class CoalBagItems implements TrackedItemContainer {
    private ItemsUnordered items = null;

    @Override
    public String key() {
        return "coal_bag";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private final ItemContainerInterface ITEM_INTERFACE =
            new ItemContainerInterface(Set.of(ItemID.COAL_BAG, ItemID.COAL_BAG_OPEN), null, 1, 4);

    private static final Set<Integer> ITEM_FILTER = Set.of(ItemID.COAL);

    @Override
    public ItemContainerInterface itemContainerInterface() {
        return ITEM_INTERFACE;
    }

    @Override
    public ItemsUnordered modify(ItemsUnordered itemsToDeposit) {
        final ItemsUnordered filtered = ItemsUnordered.filter(itemsToDeposit, (itemID, quantity) -> {
            if (!ITEM_FILTER.contains(itemID)) return 0;

            return quantity;
        });
        items = ItemsUnordered.add(items, filtered);
        final ItemsUnordered overage = ItemsUnordered.filter(items, (itemID, quantity) -> Math.min(quantity, 0));
        items = ItemsUnordered.subtract(items, overage);
        final ItemsUnordered accepted = ItemsUnordered.subtract(filtered, overage);
        return ItemsUnordered.subtract(itemsToDeposit, accepted);
    }
}
