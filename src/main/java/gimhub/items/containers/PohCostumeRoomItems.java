package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

public class PohCostumeRoomItems implements TrackedItemContainer {
    private static final int INVENTORY_ID_COSTUME_ROOM = 33405;

    private ItemsUnordered items = null;

    @Override
    public String key() {
        return "poh_costume_room";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == INVENTORY_ID_COSTUME_ROOM) {
            items = new ItemsUnordered(container, itemManager);
        }
    }
}
