package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import gimhub.items.ItemsUnordered;
import java.util.*;
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

    private enum PouchSize {
        SMALL,
        MEDIUM,
        LARGE,
        GIANT,
        COLOSSAL
    }

    private final Map<PouchSize, Item> essencePouchItems = new HashMap<>();

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

    private Item getSmall(Client client) {
        return new Item(
                POUCH_TYPE_TO_ESSENCE_ITEM_ID.getOrDefault(
                        client.getVarbitValue(VarbitID.SMALL_ESSENCE_POUCH_TYPE), -1),
                client.getVarbitValue(VarbitID.SMALL_ESSENCE_POUCH));
    }

    private Item getMedium(Client client) {
        return new Item(
                POUCH_TYPE_TO_ESSENCE_ITEM_ID.getOrDefault(
                        client.getVarbitValue(VarbitID.MEDIUM_ESSENCE_POUCH_TYPE), -1),
                client.getVarbitValue(VarbitID.MEDIUM_ESSENCE_POUCH));
    }

    private Item getLarge(Client client) {
        return new Item(
                POUCH_TYPE_TO_ESSENCE_ITEM_ID.getOrDefault(
                        client.getVarbitValue(VarbitID.LARGE_ESSENCE_POUCH_TYPE), -1),
                client.getVarbitValue(VarbitID.LARGE_ESSENCE_POUCH));
    }

    private Item getGiant(Client client) {
        return new Item(
                POUCH_TYPE_TO_ESSENCE_ITEM_ID.getOrDefault(
                        client.getVarbitValue(VarbitID.GIANT_ESSENCE_POUCH_TYPE), -1),
                client.getVarbitValue(VarbitID.GIANT_ESSENCE_POUCH));
    }

    private Item getColossal(Client client) {
        return new Item(
                POUCH_TYPE_TO_ESSENCE_ITEM_ID.getOrDefault(
                        client.getVarbitValue(VarbitID.COLOSSAL_ESSENCE_POUCH_TYPE), -1),
                client.getVarbitValue(VarbitID.COLOSSAL_ESSENCE_POUCH));
    }

    private void update(Client client, ItemManager itemManager) {
        essencePouchItems.put(PouchSize.SMALL, getSmall(client));
        essencePouchItems.put(PouchSize.MEDIUM, getMedium(client));
        essencePouchItems.put(PouchSize.LARGE, getLarge(client));
        essencePouchItems.put(PouchSize.GIANT, getGiant(client));
        essencePouchItems.put(PouchSize.COLOSSAL, getColossal(client));

        items = new ItemsUnordered(new ItemsOrdered(
                essencePouchItems.values().stream()
                        .filter(item -> item.getQuantity() > 0 && item.getId() >= 0)
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

    @Override
    public Map<Integer, Integer> onDepositContainers(
            Client client, ItemManager itemManager, Set<Integer> inventoryIDs) {
        final List<Item> depositedItems = new ArrayList<>();

        if (inventoryIDs.contains(ItemID.RCU_POUCH_SMALL)) {
            depositedItems.add(essencePouchItems.remove(PouchSize.SMALL));
        }
        if (inventoryIDs.contains(ItemID.RCU_POUCH_MEDIUM) || inventoryIDs.contains(ItemID.RCU_POUCH_MEDIUM_DEGRADE)) {
            depositedItems.add(essencePouchItems.remove(PouchSize.MEDIUM));
        }
        if (inventoryIDs.contains(ItemID.RCU_POUCH_LARGE) || inventoryIDs.contains(ItemID.RCU_POUCH_LARGE_DEGRADE)) {
            depositedItems.add(essencePouchItems.remove(PouchSize.LARGE));
        }
        if (inventoryIDs.contains(ItemID.RCU_POUCH_GIANT) || inventoryIDs.contains(ItemID.RCU_POUCH_LARGE_DEGRADE)) {
            depositedItems.add(essencePouchItems.remove(PouchSize.GIANT));
        }
        if (inventoryIDs.contains(ItemID.RCU_POUCH_COLOSSAL)
                || inventoryIDs.contains(ItemID.RCU_POUCH_COLOSSAL_DEGRADE)) {
            depositedItems.add(essencePouchItems.remove(PouchSize.COLOSSAL));
        }

        final Map<Integer, Integer> result = new HashMap<>();
        for (final Item item : depositedItems) {
            if (item == null) continue;

            result.merge(item.getId(), item.getQuantity(), Integer::sum);
        }

        items = new ItemsUnordered(new ItemsOrdered(
                essencePouchItems.values().stream()
                        .filter(item -> item.getQuantity() > 0 && item.getId() >= 0)
                        .collect(Collectors.toList()),
                itemManager));

        return result;
    }
}
