package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import gimhub.items.ItemsUnordered;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

@Slf4j
public class EssencePouchesItems implements TrackedItemContainer {
    private ItemsUnordered items = null;

    @Override
    public String key() {
        return "essence_pouches";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private static boolean isRelevantVarbit(int varbitId) {
        return varbitId == VarbitID.SMALL_ESSENCE_POUCH
                || varbitId == VarbitID.SMALL_ESSENCE_POUCH_TYPE
                || varbitId == VarbitID.MEDIUM_ESSENCE_POUCH
                || varbitId == VarbitID.MEDIUM_ESSENCE_POUCH_TYPE
                || varbitId == VarbitID.LARGE_ESSENCE_POUCH
                || varbitId == VarbitID.LARGE_ESSENCE_POUCH_TYPE
                || varbitId == VarbitID.GIANT_ESSENCE_POUCH
                || varbitId == VarbitID.GIANT_ESSENCE_POUCH_TYPE
                || varbitId == VarbitID.COLOSSAL_ESSENCE_POUCH
                || varbitId == VarbitID.COLOSSAL_ESSENCE_POUCH_TYPE;
    }

    /*
     * Mapping between
     *   - hardcoded integer value obtained from ###_ESSENCE_POUCH_TYPE varbit
     *   - itemID of the essence in that pouch
     *
     * These values are obtained from client script 3197
     */
    private static final Map<Integer, Integer> POUCH_TYPE_TO_ESSENCE_ITEM_ID = Map.ofEntries(
            Map.entry(1, ItemID.BLANKRUNE),
            Map.entry(2, ItemID.BLANKRUNE_HIGH),
            Map.entry(3, ItemID.BLANKRUNE_DAEYALT),
            Map.entry(4, ItemID.GOTR_GUARDIAN_ESSENCE));

    private void update(Client client, ItemManager itemManager) {
        final ArrayList<Item> essencePouchItems = new ArrayList<>();

        essencePouchItems.add(new Item(
                POUCH_TYPE_TO_ESSENCE_ITEM_ID.getOrDefault(client.getVarbitValue(VarbitID.SMALL_ESSENCE_POUCH_TYPE), 0),
                client.getVarbitValue(VarbitID.SMALL_ESSENCE_POUCH)));
        essencePouchItems.add(new Item(
                POUCH_TYPE_TO_ESSENCE_ITEM_ID.getOrDefault(
                        client.getVarbitValue(VarbitID.MEDIUM_ESSENCE_POUCH_TYPE), 0),
                client.getVarbitValue(VarbitID.MEDIUM_ESSENCE_POUCH)));
        essencePouchItems.add(new Item(
                POUCH_TYPE_TO_ESSENCE_ITEM_ID.getOrDefault(client.getVarbitValue(VarbitID.LARGE_ESSENCE_POUCH_TYPE), 0),
                client.getVarbitValue(VarbitID.LARGE_ESSENCE_POUCH)));
        essencePouchItems.add(new Item(
                POUCH_TYPE_TO_ESSENCE_ITEM_ID.getOrDefault(client.getVarbitValue(VarbitID.GIANT_ESSENCE_POUCH_TYPE), 0),
                client.getVarbitValue(VarbitID.GIANT_ESSENCE_POUCH)));
        essencePouchItems.add(new Item(
                POUCH_TYPE_TO_ESSENCE_ITEM_ID.getOrDefault(
                        client.getVarbitValue(VarbitID.COLOSSAL_ESSENCE_POUCH_TYPE), 0),
                client.getVarbitValue(VarbitID.COLOSSAL_ESSENCE_POUCH)));
        items = new ItemsUnordered(new ItemsOrdered(
                essencePouchItems.stream()
                        .filter(item -> item.getQuantity() > 0)
                        .collect(Collectors.toList()),
                itemManager));
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
