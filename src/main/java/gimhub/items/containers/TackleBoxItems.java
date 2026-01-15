package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.Set;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;

public class TackleBoxItems implements TrackedItemContainer {
    private ItemsUnordered items = null;

    @Override
    public String key() {
        return "tackle_box";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == InventoryID.TACKLE_BOX) {
            items = new ItemsUnordered(container, itemManager);
        }
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

    private final ItemContainerInterface ITEM_INTERFACE =
            new ItemContainerInterface(Set.of(ItemID.TACKLE_BOX), 1, 2, 3);

    @Override
    public ItemContainerInterface itemContainerInterface() {
        return ITEM_INTERFACE;
    }

    @Override
    public ItemsUnordered modify(ItemsUnordered itemsToDeposit) {
        final ItemsUnordered filtered = ItemsUnordered.filter(itemsToDeposit, (itemID, quantity) -> {
            if (!ITEM_FILTER.contains(itemID)) return 0;

            return quantity;
        });
        items = ItemsUnordered.add(items, filtered);
        final ItemsUnordered overage = ItemsUnordered.filter(items, (itemID, quantity) -> Math.min(quantity, 0));
        items = ItemsUnordered.subtract(items, overage);
        final ItemsUnordered accepted = ItemsUnordered.subtract(filtered, overage);
        return ItemsUnordered.subtract(itemsToDeposit, accepted);
    }
}
