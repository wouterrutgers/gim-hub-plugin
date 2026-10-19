package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;

public class ScarEssenceMineCofferItems implements TrackedItemContainer {
    private ItemsUnordered items;

    @Override
    public String key() {
        return "scar_essence_mine_coffer";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private void update(Client client, ItemManager itemManager) {
        items = new ItemsUnordered(
                Map.of(ItemID.COINS, client.getVarpValue(VarPlayerID.SCAR_ESSENCEMINE_COFFER)), itemManager);
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        if (varpId == VarPlayerID.SCAR_ESSENCEMINE_COFFER) {
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
