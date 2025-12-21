package gimhub.items;

import gimhub.APISerializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ItemsUnordered implements APISerializable {
    private final Map<Integer, Integer> itemsQuantityByID;

    public ItemsUnordered() {
        this.itemsQuantityByID = new HashMap<>();
    }

    protected ItemsUnordered(ItemsUnordered other) {
        itemsQuantityByID = new HashMap<>(other.itemsQuantityByID);
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
        itemsQuantityByID = new HashMap<>();
        Item[] contents = container.getItems();
        for (final Item item : contents) {
            if (!ItemsUtilities.isItemValid(item, itemManager)) {
                continue;
            }

            final int itemID = itemManager.canonicalize(item.getId());
            final int quantity = itemsQuantityByID.getOrDefault(itemID, 0) + item.getQuantity();
            itemsQuantityByID.put(itemID, quantity);
        }
    }

    public static ItemsUnordered add(ItemsUnordered left, ItemsUnordered right) {
        if (left == null && right == null) {
            return new ItemsUnordered();
        }

        if (left == null) {
            return new ItemsUnordered(right);
        }

        if (right == null) {
            return new ItemsUnordered(left);
        }

        ItemsUnordered result = new ItemsUnordered();

        final Set<Integer> combinedKeys = Stream.concat(
                        left.itemsQuantityByID.keySet().stream(), right.itemsQuantityByID.keySet().stream())
                .collect(Collectors.toSet());
        for (final Integer itemID : combinedKeys) {
            final int quantity =
                    left.itemsQuantityByID.getOrDefault(itemID, 0) + right.itemsQuantityByID.getOrDefault(itemID, 0);

            result.itemsQuantityByID.put(itemID, quantity);
        }

        return result;
    }

    public static ItemsUnordered subtract(ItemsUnordered left, ItemsUnordered right) {
        if (left == null) {
            return new ItemsUnordered();
        }

        if (right == null) {
            return new ItemsUnordered(left);
        }

        ItemsUnordered result = new ItemsUnordered();

        // Only the keys in the LHS matter, since otherwise the subtraction is always negative.
        for (final Map.Entry<Integer, Integer> entry : left.itemsQuantityByID.entrySet()) {
            final int itemID = entry.getKey();
            if (itemID <= 0) {
                continue;
            }

            final int lhs = entry.getValue();
            final int rhs = right.itemsQuantityByID.getOrDefault(itemID, 0);

            final int subtraction = lhs - rhs;
            if (subtraction <= 0) {
                continue;
            }

            result.itemsQuantityByID.put(itemID, subtraction);
        }

        return result;
    }

    public Object serialize() {
        List<Integer> result = new ArrayList<>(itemsQuantityByID.size() * 2);

        for (final Map.Entry<Integer, Integer> entry : itemsQuantityByID.entrySet()) {
            result.add(entry.getKey());
            result.add(entry.getValue());
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
