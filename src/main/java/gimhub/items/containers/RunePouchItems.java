package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import java.util.ArrayList;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

public class RunePouchItems implements TrackedItemContainer {
    private ItemsOrdered items = null;

    @Override
    public String key() {
        return "rune_pouch";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private static boolean isRelevantVarbit(int varbitId) {
        return varbitId == VarbitID.RUNE_POUCH_TYPE_1
                || varbitId == VarbitID.RUNE_POUCH_QUANTITY_1
                || varbitId == VarbitID.RUNE_POUCH_TYPE_2
                || varbitId == VarbitID.RUNE_POUCH_QUANTITY_2
                || varbitId == VarbitID.RUNE_POUCH_TYPE_3
                || varbitId == VarbitID.RUNE_POUCH_QUANTITY_3
                || varbitId == VarbitID.RUNE_POUCH_TYPE_4
                || varbitId == VarbitID.RUNE_POUCH_QUANTITY_4;
    }

    private void update(Client client, ItemManager itemManager) {
        final EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
        final ArrayList<Item> runepouchItems = new ArrayList<>();
        runepouchItems.add(new Item(
                runepouchEnum.getIntValue(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_1)),
                client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)));
        runepouchItems.add(new Item(
                runepouchEnum.getIntValue(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_2)),
                client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_2)));
        runepouchItems.add(new Item(
                runepouchEnum.getIntValue(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_3)),
                client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_3)));
        runepouchItems.add(new Item(
                runepouchEnum.getIntValue(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_4)),
                client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_4)));
        items = new ItemsOrdered(runepouchItems, itemManager);
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        if (isRelevantVarbit(varbitId)) {
            update(client, itemManager);
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        update(client, itemManager);
    }
}
