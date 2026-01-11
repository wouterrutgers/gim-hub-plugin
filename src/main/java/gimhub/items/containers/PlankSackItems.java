package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import java.util.ArrayList;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

public class PlankSackItems implements TrackedItemContainer {
    private ItemsOrdered items = null;

    private static final Map<Integer, Integer> PLANK_VARBIT_ID_TO_ITEM_ID = Map.ofEntries(
            Map.entry(VarbitID.PLANK_SACK_PLAIN, ItemID.WOODPLANK),
            Map.entry(VarbitID.PLANK_SACK_OAK, ItemID.PLANK_OAK),
            Map.entry(VarbitID.PLANK_SACK_TEAK, ItemID.PLANK_TEAK),
            Map.entry(VarbitID.PLANK_SACK_MAHOGANY, ItemID.PLANK_MAHOGANY),
            Map.entry(VarbitID.PLANK_SACK_CAMPHOR, ItemID.PLANK_CAMPHOR),
            Map.entry(VarbitID.PLANK_SACK_IRONWOOD, ItemID.PLANK_IRONWOOD),
            Map.entry(VarbitID.PLANK_SACK_ROSEWOOD, ItemID.PLANK_ROSEWOOD));

    @Override
    public String key() {
        return "plank_sack";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private void update(Client client, ItemManager itemManager) {
        final ArrayList<Item> sackItems = new ArrayList<>();
        for (final Map.Entry<Integer, Integer> kvp : PLANK_VARBIT_ID_TO_ITEM_ID.entrySet()) {
            final int varbitID = kvp.getKey();
            final int itemID = kvp.getValue();

            final int quantity = client.getVarbitValue(varbitID);

            if (quantity > 0) {
                sackItems.add(new Item(itemID, quantity));
            }
        }
        items = new ItemsOrdered(sackItems, itemManager);
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        if (!PLANK_VARBIT_ID_TO_ITEM_ID.containsKey(varbitId)) {
            return;
        }

        update(client, itemManager);
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        update(client, itemManager);
    }
}
