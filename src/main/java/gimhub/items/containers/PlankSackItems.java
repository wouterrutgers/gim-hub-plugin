package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import java.util.ArrayList;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

public class PlankSackItems implements TrackedItemContainer {
    private ItemsOrdered items = null;

    private static final Map<Integer, Integer> PLANK_VARBIT_ID_TO_ITEM_ID = Map.ofEntries(
            Map.entry(VarbitID.PLANK_SACK_PLAIN, 960),
            Map.entry(VarbitID.PLANK_SACK_OAK, 8778),
            Map.entry(VarbitID.PLANK_SACK_TEAK, 8780),
            Map.entry(VarbitID.PLANK_SACK_MAHOGANY, 8782),
            Map.entry(VarbitID.PLANK_SACK_CAMPHOR, 31432),
            Map.entry(VarbitID.PLANK_SACK_IRONWOOD, 31435),
            Map.entry(VarbitID.PLANK_SACK_ROSEWOOD, 31438));

    @Override
    public String key() {
        return "plank_sack";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private static boolean isRelevantVarp(int varpId) {
        return varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO || varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT;
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
