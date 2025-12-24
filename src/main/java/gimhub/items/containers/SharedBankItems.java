package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

public class SharedBankItems implements TrackedItemContainer {
    private static final int WIDGET_GROUP_STORAGE_LOADER_PARENT = 293;
    private static final int WIDGET_GROUP_STORAGE_LOADER_TEXT_CHILD = 1;

    private ItemsUnordered committed = null;
    private ItemsUnordered uncommitted = null;

    @Override
    public String key() {
        return "shared_bank";
    }

    @Override
    public APISerializable get() {
        return committed;
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == InventoryID.INV_GROUP_TEMP) {
            uncommitted = new ItemsUnordered(container, itemManager);
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        Widget groupStorageLoaderText =
                client.getWidget(WIDGET_GROUP_STORAGE_LOADER_PARENT, WIDGET_GROUP_STORAGE_LOADER_TEXT_CHILD);
        if (groupStorageLoaderText != null && groupStorageLoaderText.getText().equalsIgnoreCase("saving...")) {
            committed = uncommitted;
            uncommitted = null;
        }
    }
}
