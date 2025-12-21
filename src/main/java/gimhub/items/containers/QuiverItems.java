package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import java.util.ArrayList;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;

public class QuiverItems implements TrackedItemContainer {
    private ItemsOrdered items = null;

    @Override
    public String key() {
        return "quiver";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private static boolean isRelevantVarp(int varpId) {
        return varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO || varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT;
    }

    private void update(Client client, ItemManager itemManager) {
        final ArrayList<Item> quiverItems = new ArrayList<>();
        quiverItems.add(new Item(
                client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO),
                client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)));
        items = new ItemsOrdered(quiverItems, itemManager);
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        if (isRelevantVarp(varpId)) {
            update(client, itemManager);
        }
    }

    @Override
    public void onUpdateOften(Client client, ItemManager itemManager) {
        update(client, itemManager);
    }
}
