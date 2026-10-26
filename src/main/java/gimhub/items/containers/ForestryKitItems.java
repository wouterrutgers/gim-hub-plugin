package gimhub.items.containers;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

public class ForestryKitItems extends ContainerItems {
    public ForestryKitItems() {
        super(
                InventoryID.FORESTRY_KIT,
                InterfaceID.ForestryKitMain.UNIVERSE,
                ItemID.FORESTRY_KIT,
                ItemID.FORESTRY_BASKET_CLOSED,
                ItemID.FORESTRY_BASKET_OPEN);
    }

    @Override
    public String key() {
        return "forestry_kit";
    }
}
