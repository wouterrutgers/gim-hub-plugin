package gimhub.items.containers;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

public class HuntsmansKitItems extends ContainerItems {
    public HuntsmansKitItems() {
        super(InventoryID.HUNTSMANS_KIT, InterfaceID.HuntsmansKit.UNIVERSE, ItemID.HUNTSMANS_KIT);
    }

    @Override
    public String key() {
        return "huntsmans_kit";
    }
}
