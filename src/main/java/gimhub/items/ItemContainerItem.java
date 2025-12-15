package gimhub.items;

import lombok.Getter;

public class ItemContainerItem {
    @Getter
    public final int id;

    @Getter
    public final int quantity;

    public ItemContainerItem(int id, int quantity) {
        this.id = id;
        this.quantity = quantity;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ItemContainerItem)) return false;
        ItemContainerItem other = (ItemContainerItem) o;

        return other.id == id && other.quantity == quantity;
    }
}
