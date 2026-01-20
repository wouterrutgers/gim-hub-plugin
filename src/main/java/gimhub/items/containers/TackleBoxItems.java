package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import gimhub.items.ItemsUnordered;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;

public class TackleBoxItems implements TrackedItemContainer {
    private ItemsUnordered items = null;

    @Getter
    private Map<Integer, Integer> tackleBoxItems = new HashMap<>();

    @Override
    public String key() {
        return "tackle_box";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private void rebuildItems(ItemManager itemManager) {
        final ArrayList<Item> result = new ArrayList<>(tackleBoxItems.size());
        for (final Map.Entry<Integer, Integer> e : tackleBoxItems.entrySet()) {
            if (e.getValue() <= 0) continue;
            result.add(new Item(e.getKey(), e.getValue()));
        }

        if (result.isEmpty()) {
            items = new ItemsUnordered();
        } else {
            items = new ItemsUnordered(new ItemsOrdered(result, itemManager));
        }
    }

    public void setItems(Map<Integer, Integer> items) {
        Map<Integer, Integer> safeMap = new HashMap<>();
        for (final Map.Entry<Integer, Integer> entry : items.entrySet()) {
            final int itemID = entry.getKey();
            final int quantity = entry.getValue();
            if (quantity <= 0) {
                continue;
            }
            safeMap.put(itemID, quantity);
        }
        this.items = new ItemsUnordered(safeMap);
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == InventoryID.TACKLE_BOX) {
            tackleBoxItems = new ItemsUnordered(container, itemManager).getItemsQuantityByID();
            rebuildItems(itemManager);
        }
    }

    public Set<Integer> getItemFilter() {
        return ITEM_FILTER;
    }

    private static final Set<Integer> ITEM_FILTER = Set.of(
            ItemID.TRAWLER_REWARD_HAT,
            ItemID.TRAWLER_REWARD_TOP,
            ItemID.TRAWLER_REWARD_LEGS,
            ItemID.TRAWLER_REWARD_BOOTS,
            ItemID.SPIRIT_ANGLER_HAT,
            ItemID.SPIRIT_ANGLER_TOP,
            ItemID.SPIRIT_ANGLER_LEGS,
            ItemID.SPIRIT_ANGLER_BOOTS,
            ItemID.SPIRIT_FLAKES,
            ItemID.MUDSKIPPER_FLIPPERS,
            ItemID.HUNDRED_PIRATE_DIVING_HELMET,
            ItemID.HUNDRED_PIRATE_DIVING_BACKPACK,
            ItemID.DARK_FLIPPERS,
            ItemID.TINY_NET,
            ItemID.ZEAH_BLESSING_HARD,
            ItemID.ZEAH_BLESSING_ELITE,
            ItemID.ZEAH_BLESSING_EASY,
            ItemID.ZEAH_BLESSING_MEDIUM,
            ItemID.HARPOON,
            ItemID.HUNTING_BARBED_HARPOON,
            ItemID.DRAGON_HARPOON,
            ItemID.INFERNAL_HARPOON,
            ItemID.INFERNAL_HARPOON_EMPTY,
            ItemID.TRAILBLAZER_RELOADED_HARPOON_NO_INFERNAL,
            ItemID.TRAILBLAZER_RELOADED_HARPOON,
            ItemID.TRAILBLAZER_RELOADED_HARPOON_EMPTY,
            ItemID.CRYSTAL_HARPOON,
            ItemID.CRYSTAL_HARPOON_INACTIVE,
            ItemID.MERFOLK_TRIDENT,
            ItemID.FISHING_ROD,
            ItemID.FLY_FISHING_ROD,
            ItemID.OILY_FISHING_ROD,
            ItemID.BRUT_FISHING_ROD,
            ItemID.FISHINGROD_PEARL,
            ItemID.FISHINGROD_PEARL_FLY,
            ItemID.FISHINGROD_PEARL_OILY,
            ItemID.FISHINGROD_PEARL_BRUT,
            ItemID.NET,
            ItemID.BIG_NET,
            ItemID.FOSSIL_DRIFT_NET,
            ItemID.LOBSTER_POT,
            ItemID.TBWT_KARAMBWAN_VESSEL,
            ItemID.TBWT_KARAMBWAN_VESSEL_LOADED_WITH_KARAMBWANJI,
            ItemID.TBWT_RAW_KARAMBWANJI,
            ItemID.FISHING_BAIT,
            ItemID.WILDERNESS_FISHING_BAIT,
            ItemID.FEATHER,
            ItemID.HUNTING_STRIPY_BIRD_FEATHER,
            ItemID.BRUT_FISH_CUTS,
            ItemID.SAILING_FINE_FISH_OFFCUTS,
            ItemID.AERIAL_FISHING_PEARL,
            ItemID.SHARK_LURE,
            ItemID.PISCARILIUS_SANDWORMS,
            ItemID.DIABOLIC_WORMS,
            ItemID._1DOSEFISHERSPOTION,
            ItemID._2DOSEFISHERSPOTION,
            ItemID._3DOSEFISHERSPOTION,
            ItemID._4DOSEFISHERSPOTION,
            ItemID.OIL_LANTERN_LIT,
            ItemID.BULLSEYE_LANTERN_LIT,
            ItemID.CANDLE_LANTERN_LIT // white candle variant
            );
}
