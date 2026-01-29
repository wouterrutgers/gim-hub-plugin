package gimhub.items;

import gimhub.APISerializable;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ItemsUnordered implements APISerializable {
    @Getter
    private final Map<Integer, Integer> itemsQuantityByID;

    public ItemsUnordered() {
        this.itemsQuantityByID = new HashMap<>();
    }

    public ItemsUnordered(ItemsOrdered items) {
        this.itemsQuantityByID = new HashMap<>();

        if (items == null) {
            return;
        }

        for (final ItemContainerItem item : items.getItems()) {
            final int itemID = item.getId();
            final int quantity = itemsQuantityByID.getOrDefault(itemID, 0) + item.getQuantity();
            itemsQuantityByID.put(itemID, quantity);
        }
    }

    public ItemsUnordered(ItemContainer container, ItemManager itemManager) {
        itemsQuantityByID = ItemsUtilities.convertToSafeMap(container, itemManager);
    }

    public ItemsUnordered(Map<Integer, Integer> itemsQuantityByID, ItemManager itemManager) {
        this.itemsQuantityByID = ItemsUtilities.convertToSafeMap(itemsQuantityByID, itemManager);
    }

    @Override
    public Object serialize() {
        List<Integer> result = new ArrayList<>(itemsQuantityByID.size() * 2);

        for (final Map.Entry<Integer, Integer> entry : itemsQuantityByID.entrySet()) {
            final int itemID = entry.getKey();
            final int quantity = entry.getValue();

            if (quantity == 0) {
                continue;
            }

            result.add(itemID);
            result.add(quantity);
        }

        return result;
    }

    @Override
    public APISerializable diff(APISerializable newer) {
        final ItemsUnordered result = new ItemsUnordered();

        if (newer == this) return result;
        if (!(newer instanceof ItemsUnordered)) return result;
        final ItemsUnordered newerCast = (ItemsUnordered) newer;

        for (final Map.Entry<Integer, Integer> entry : this.itemsQuantityByID.entrySet()) {
            final int itemID = entry.getKey();
            final int olderQuantity = entry.getValue();

            result.itemsQuantityByID.put(itemID, newerCast.itemsQuantityByID.getOrDefault(itemID, 0) - olderQuantity);
        }
        for (final Map.Entry<Integer, Integer> entry : newerCast.itemsQuantityByID.entrySet()) {
            final int itemID = entry.getKey();

            if (result.itemsQuantityByID.containsKey(itemID)) {
                continue;
            }

            final int newerQuantity = entry.getValue();

            result.itemsQuantityByID.put(itemID, newerQuantity - this.itemsQuantityByID.getOrDefault(itemID, 0));
        }

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ItemsUnordered)) return false;
        ItemsUnordered other = (ItemsUnordered) o;
        return other.itemsQuantityByID.equals(this.itemsQuantityByID);
    }
}
