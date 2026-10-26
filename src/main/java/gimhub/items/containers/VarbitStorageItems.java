package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

public class VarbitStorageItems implements TrackedItemContainer {
    protected final String key;
    protected final Map<Integer, Integer> itemVarbits;
    protected ItemsUnordered items;

    public VarbitStorageItems(String key, Map<Integer, Integer> itemVarbits) {
        this.key = key;
        this.itemVarbits = itemVarbits;
    }

    @Override
    public String key() {
        return key;
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
        if (itemVarbits.containsValue(varbitId)) {
            update(client, itemManager);
        }
    }

    protected void update(Client client, ItemManager itemManager) {
        Map<Integer, Integer> quantities = new HashMap<>();
        itemVarbits.forEach((item, varbit) -> quantities.put(item, client.getVarbitValue(varbit)));
        items = new ItemsUnordered(quantities, itemManager);
    }

    public static VarbitStorageItems blastFurnace() {
        return new VarbitStorageItems(
                "blast_furnace",
                Map.ofEntries(
                        Map.entry(ItemID.BRONZE_BAR, VarbitID.BLAST_FURNACE_BRONZE_BARS),
                        Map.entry(ItemID.IRON_BAR, VarbitID.BLAST_FURNACE_IRON_BARS),
                        Map.entry(ItemID.STEEL_BAR, VarbitID.BLAST_FURNACE_STEEL_BARS),
                        Map.entry(ItemID.MITHRIL_BAR, VarbitID.BLAST_FURNACE_MITHRIL_BARS),
                        Map.entry(ItemID.ADAMANTITE_BAR, VarbitID.BLAST_FURNACE_ADAMANTITE_BARS),
                        Map.entry(ItemID.RUNITE_BAR, VarbitID.BLAST_FURNACE_RUNITE_BARS),
                        Map.entry(ItemID.GOLD_BAR, VarbitID.BLAST_FURNACE_GOLD_BARS),
                        Map.entry(ItemID.SILVER_BAR, VarbitID.BLAST_FURNACE_SILVER_BARS),
                        Map.entry(ItemID.COAL, VarbitID.BLAST_FURNACE_COAL),
                        Map.entry(ItemID.TIN_ORE, VarbitID.BLAST_FURNACE_TIN_ORE),
                        Map.entry(ItemID.IRON_ORE, VarbitID.BLAST_FURNACE_IRON_ORE),
                        Map.entry(ItemID.MITHRIL_ORE, VarbitID.BLAST_FURNACE_MITHRIL_ORE),
                        Map.entry(ItemID.ADAMANTITE_ORE, VarbitID.BLAST_FURNACE_ADAMANTITE_ORE),
                        Map.entry(ItemID.RUNITE_ORE, VarbitID.BLAST_FURNACE_RUNITE_ORE),
                        Map.entry(ItemID.GOLD_ORE, VarbitID.BLAST_FURNACE_GOLD_ORE),
                        Map.entry(ItemID.SILVER_ORE, VarbitID.BLAST_FURNACE_SILVER_ORE),
                        Map.entry(ItemID.PERFECT_GOLD_ORE, VarbitID.BLAST_FURNACE_PERFECT_GOLD_ORE),
                        Map.entry(ItemID.PERFECT_GOLD_BAR, VarbitID.BLAST_FURNACE_PERFECT_GOLD_BARS),
                        Map.entry(ItemID.COPPER_ORE, VarbitID.BLAST_FURNACE_COPPER_ORE),
                        Map.entry(ItemID.LEAD_ORE, VarbitID.BLAST_FURNACE_LEAD_ORE),
                        Map.entry(ItemID.NICKEL_ORE, VarbitID.BLAST_FURNACE_NICKEL_ORE),
                        Map.entry(ItemID.LEAD_BAR, VarbitID.BLAST_FURNACE_LEAD_BARS),
                        Map.entry(ItemID.CUPRONICKEL_BAR, VarbitID.BLAST_FURNACE_CUPRONICKEL_BARS)));
    }

    public static VarbitStorageItems fossilStorage() {
        return new VarbitStorageItems(
                "fossil_storage",
                Map.ofEntries(
                        Map.entry(ItemID.FOSSIL_SMALL_UNID, VarbitID.FOSSIL_STORAGE_SMALL_UNID),
                        Map.entry(ItemID.FOSSIL_MEDIUM_UNID, VarbitID.FOSSIL_STORAGE_MEDIUM_UNID),
                        Map.entry(ItemID.FOSSIL_LARGE_UNID, VarbitID.FOSSIL_STORAGE_LARGE_UNID),
                        Map.entry(ItemID.FOSSIL_RARE_UNID, VarbitID.FOSSIL_STORAGE_RARE_UNID),
                        Map.entry(ItemID.FOSSIL_SMALL_1, VarbitID.FOSSIL_STORAGE_SMALL_1),
                        Map.entry(ItemID.FOSSIL_SMALL_2, VarbitID.FOSSIL_STORAGE_SMALL_2),
                        Map.entry(ItemID.FOSSIL_SMALL_3, VarbitID.FOSSIL_STORAGE_SMALL_3),
                        Map.entry(ItemID.FOSSIL_SMALL_4, VarbitID.FOSSIL_STORAGE_SMALL_4),
                        Map.entry(ItemID.FOSSIL_SMALL_5, VarbitID.FOSSIL_STORAGE_SMALL_5),
                        Map.entry(ItemID.FOSSIL_MEDIUM_1, VarbitID.FOSSIL_STORAGE_MEDIUM_1),
                        Map.entry(ItemID.FOSSIL_MEDIUM_2, VarbitID.FOSSIL_STORAGE_MEDIUM_2),
                        Map.entry(ItemID.FOSSIL_MEDIUM_3, VarbitID.FOSSIL_STORAGE_MEDIUM_3),
                        Map.entry(ItemID.FOSSIL_MEDIUM_4, VarbitID.FOSSIL_STORAGE_MEDIUM_4),
                        Map.entry(ItemID.FOSSIL_MEDIUM_5, VarbitID.FOSSIL_STORAGE_MEDIUM_5),
                        Map.entry(ItemID.FOSSIL_LARGE_1, VarbitID.FOSSIL_STORAGE_LARGE_1),
                        Map.entry(ItemID.FOSSIL_LARGE_2, VarbitID.FOSSIL_STORAGE_LARGE_2),
                        Map.entry(ItemID.FOSSIL_LARGE_3, VarbitID.FOSSIL_STORAGE_LARGE_3),
                        Map.entry(ItemID.FOSSIL_LARGE_4, VarbitID.FOSSIL_STORAGE_LARGE_4),
                        Map.entry(ItemID.FOSSIL_LARGE_5, VarbitID.FOSSIL_STORAGE_LARGE_5),
                        Map.entry(ItemID.FOSSIL_PLANT_1, VarbitID.FOSSIL_STORAGE_PLANT_1),
                        Map.entry(ItemID.FOSSIL_PLANT_2, VarbitID.FOSSIL_STORAGE_PLANT_2),
                        Map.entry(ItemID.FOSSIL_PLANT_3, VarbitID.FOSSIL_STORAGE_PLANT_3),
                        Map.entry(ItemID.FOSSIL_PLANT_4, VarbitID.FOSSIL_STORAGE_PLANT_4),
                        Map.entry(ItemID.FOSSIL_PLANT_5, VarbitID.FOSSIL_STORAGE_PLANT_5),
                        Map.entry(ItemID.FOSSIL_RARE_1, VarbitID.FOSSIL_STORAGE_RARE_1),
                        Map.entry(ItemID.FOSSIL_RARE_2, VarbitID.FOSSIL_STORAGE_RARE_2),
                        Map.entry(ItemID.FOSSIL_RARE_3, VarbitID.FOSSIL_STORAGE_RARE_3),
                        Map.entry(ItemID.FOSSIL_RARE_4, VarbitID.FOSSIL_STORAGE_RARE_4),
                        Map.entry(ItemID.FOSSIL_RARE_5, VarbitID.FOSSIL_STORAGE_RARE_5),
                        Map.entry(ItemID.FOSSIL_RARE_6, VarbitID.FOSSIL_STORAGE_RARE_6)));
    }

    public static VarbitStorageItems hallowedSepulchre() {
        return new VarbitStorageItems(
                "hallowed_sepulchre",
                Map.ofEntries(
                        Map.entry(ItemID.HALLOWED_TOKEN, VarbitID.HALLOWED_STORAGE_TOKEN),
                        Map.entry(ItemID.HALLOWED_GRAPPLE, VarbitID.HALLOWED_STORAGE_GRAPPLE),
                        Map.entry(ItemID.HALLOWED_FOCUS, VarbitID.HALLOWED_STORAGE_FOCUS),
                        Map.entry(ItemID.HALLOWED_SYMBOL, VarbitID.HALLOWED_STORAGE_SYMBOL),
                        Map.entry(ItemID.HALLOWED_HAMMER, VarbitID.HALLOWED_STORAGE_HAMMER),
                        Map.entry(ItemID.HALLOWED_RING, VarbitID.HALLOWED_STORAGE_RING)));
    }
}
