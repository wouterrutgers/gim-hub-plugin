package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

public class BoltPouchItems implements TrackedItemContainer {
    // Client script 4047 maps each pouch slot through this enum.
    protected static final int BOLT_TYPES = 336;
    protected static final int[] TYPES = {
        VarbitID.XBOWS_POUCH_SLOT1, VarbitID.XBOWS_POUCH_SLOT2,
        VarbitID.XBOWS_POUCH_SLOT3, VarbitID.XBOWS_POUCH_SLOT4
    };
    protected static final int[] QUANTITIES = {
        VarbitID.XBOWS_POUCH_NUM1, VarbitID.XBOWS_POUCH_NUM2,
        VarbitID.XBOWS_POUCH_NUM3, VarbitID.XBOWS_POUCH_NUM4
    };
    protected ItemsUnordered items;

    @Override
    public String key() {
        return "bolt_pouch";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (items == null) {
            update(client, itemManager);
        }
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        for (int index = 0; index < TYPES.length; index++) {
            if (TYPES[index] == varbitId || QUANTITIES[index] == varbitId) {
                update(client, itemManager);
                return;
            }
        }
    }

    protected void update(Client client, ItemManager itemManager) {
        Map<Integer, Integer> contents = new HashMap<>();
        for (int index = 0; index < TYPES.length; index++) {
            int quantity = client.getVarbitValue(QUANTITIES[index]);
            if (quantity > 0) {
                int item = client.getEnum(BOLT_TYPES).getIntValue(client.getVarbitValue(TYPES[index]));
                contents.merge(item, quantity, Integer::sum);
            }
        }
        items = new ItemsUnordered(contents, itemManager);
    }
}
