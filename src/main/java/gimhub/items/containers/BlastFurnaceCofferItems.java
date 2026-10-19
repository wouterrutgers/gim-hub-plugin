package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

public class BlastFurnaceCofferItems implements TrackedItemContainer {
    private ItemsUnordered items;

    @Override
    public String key() {
        return "blast_furnace_coffer";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private void update(Client client, ItemManager itemManager) {
        items = new ItemsUnordered(
                Map.of(ItemID.COINS, client.getVarbitValue(VarbitID.BLAST_FURNACE_COFFER)), itemManager);
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        if (varbitId == VarbitID.BLAST_FURNACE_COFFER) {
            update(client, itemManager);
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (items == null) {
            update(client, itemManager);
        }
    }
}
