package gimhub.items;

import gimhub.APISerializable;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ItemsOrdered implements APISerializable {
    private final List<ItemContainerItem> items;

    public ItemsOrdered(Iterable<Item> container, ItemManager itemManager) {
        this.items = new ArrayList<>();

        for (final Item item : container) {
            if (!ItemsUtilities.isItemValid(item, itemManager)) {
                items.add(new ItemContainerItem(0, 0));
            } else {
                items.add(new ItemContainerItem(itemManager.canonicalize(item.getId()), item.getQuantity()));
            }
        }
    }

    /**
     * Specifying the size is required, since Runescape does not pad inventory and equipment. For example, if you do not
     * have ammo equipped, the equipment ItemContainer will have 13 items instead of 14 as we require.
     */
    public ItemsOrdered(ItemContainer container, ItemManager itemManager, int size) {
        this.items = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            Item item = container.getItem(i);

            if (!ItemsUtilities.isItemValid(item, itemManager)) {
                items.add(new ItemContainerItem(0, 0));
            } else {
                items.add(new ItemContainerItem(itemManager.canonicalize(item.getId()), item.getQuantity()));
            }
        }
    }

    public Iterable<ItemContainerItem> getItems() {
        return items;
    }

    public Object serialize() {
        List<Integer> result = new ArrayList<>(items.size() * 2);

        for (ItemContainerItem item : items) {
            result.add(item.getId());
            result.add(item.getQuantity());
        }

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ItemsOrdered)) return false;
        ItemsOrdered other = (ItemsOrdered) o;
        return other.items.equals(items);
    }
}
