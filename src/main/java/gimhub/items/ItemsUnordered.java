package gimhub.items;

import gimhub.APISerializable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ItemsUnordered implements APISerializable {
    @Getter
    private final Map<Integer, Integer> itemsQuantityByID;

    public ItemsUnordered() {
        this.itemsQuantityByID = new HashMap<>();
    }

    public ItemsUnordered(Map<Integer, Integer> itemsQuantityByID) {
        this.itemsQuantityByID = itemsQuantityByID;
    }

    protected ItemsUnordered(ItemsUnordered other) {
        itemsQuantityByID = new HashMap<>(other.itemsQuantityByID);
    }

    public boolean isEmpty() {
        return itemsQuantityByID.isEmpty();
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

    public static Map<Integer, Integer> from(@Nullable ItemContainer container, ItemManager itemManager) {
        final Map<Integer, Integer> itemsQuantityByID = new HashMap<>();
        if (container == null) {
            return itemsQuantityByID;
        }

        Item[] contents = container.getItems();
        for (final Item item : contents) {
            if (!ItemsUtilities.isItemValid(item, itemManager)) {
                continue;
            }

            final int itemID = itemManager.canonicalize(item.getId());
            final int quantity = itemsQuantityByID.getOrDefault(itemID, 0) + item.getQuantity();
            itemsQuantityByID.put(itemID, quantity);
        }

        return itemsQuantityByID;
    }

    public ItemsUnordered(ItemContainer container, ItemManager itemManager) {
        itemsQuantityByID = from(container, itemManager);
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

            if (quantity == 0) {
                continue;
            }

            result.itemsQuantityByID.put(itemID, quantity);
        }

        return result;
    }

    public static Map<Integer, Integer> subtract(Map<Integer, Integer> leftItems, Map<Integer, Integer> rightItems) {
        Map<Integer, Integer> resultItems = new HashMap<>();

        final Set<Integer> allKeys = Stream.concat(leftItems.keySet().stream(), rightItems.keySet().stream())
                .collect(Collectors.toSet());
        for (final Integer itemID : allKeys) {
            if (itemID <= 0) {
                continue;
            }

            final int lhs = leftItems.getOrDefault(itemID, 0);
            final int rhs = rightItems.getOrDefault(itemID, 0);

            final int subtraction = lhs - rhs;
            if (subtraction == 0) {
                continue;
            }

            resultItems.put(itemID, subtraction);
        }

        return resultItems;
    }

    public static ItemsUnordered subtract(ItemsUnordered left, ItemsUnordered right) {
        Map<Integer, Integer> leftItems = new HashMap<>();
        Map<Integer, Integer> rightItems = new HashMap<>();
        if (left != null) {
            leftItems = left.itemsQuantityByID;
        }
        if (right != null) {
            rightItems = right.itemsQuantityByID;
        }

        return new ItemsUnordered(subtract(leftItems, rightItems));
    }

    public static ItemsUnordered filter(ItemsUnordered items, BiFunction<Integer, Integer, Integer> filter) {
        ItemsUnordered result = new ItemsUnordered();

        for (final Map.Entry<Integer, Integer> entry : items.itemsQuantityByID.entrySet()) {
            final int itemID = entry.getKey();
            final int quantity = entry.getValue();

            final int quantityFiltered = filter.apply(itemID, quantity);

            if (quantityFiltered == 0) {
                continue;
            }

            result.itemsQuantityByID.put(itemID, quantityFiltered);
        }

        return result;
    }

    public static ItemsUnordered filterKeepPositive(ItemsUnordered items) {
        return ItemsUnordered.filter(items, (itemID, quantity) -> Math.max(quantity, 0));
    }

    public static ItemsUnordered filterKeepNegative(ItemsUnordered items) {
        return ItemsUnordered.filter(items, (itemID, quantity) -> Math.min(quantity, 0));
    }

    public Object serialize() {
        List<Integer> result = new ArrayList<>(itemsQuantityByID.size() * 2);

        for (final Map.Entry<Integer, Integer> entry : itemsQuantityByID.entrySet()) {
            result.add(entry.getKey());
            result.add(Math.max(0, entry.getValue()));
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
