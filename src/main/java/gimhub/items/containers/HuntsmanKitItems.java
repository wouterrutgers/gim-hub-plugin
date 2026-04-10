package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;

public class HuntsmanKitItems implements TrackedItemContainer {
    private ItemsUnordered items = null;
    private boolean known = false;

    public Map<Integer, Integer> getHuntsmanKitItems() {
        if (items == null) {
            return new HashMap<>();
        }
        return new HashMap<>(items.getItemsQuantityByID());
    }

    @Override
    public String key() {
        if (!known) {
            return "huntsman_kit_partial";
        }
        return "huntsman_kit";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == InventoryID.HUNTSMANS_KIT) {
            items = new ItemsUnordered(container, itemManager);
            known = true;
        }
    }

    public void setItems(Map<Integer, Integer> items, ItemManager itemManager) {
        if (this.items == null && items.isEmpty()) {
            return;
        }

        this.items = new ItemsUnordered(items, itemManager);
    }

    public static Set<Integer> getItemFilter() {
        return ITEM_FILTER;
    }

    private static final Set<Integer> ITEM_FILTER = Set.of(
            ItemID.PACK_OJIBWAY_BIRD_SNARE,
            ItemID.NOOSE_WAND,
            ItemID.HUNTING_BUTTERFLY_NET,
            ItemID.II_MAGIC_BUTTERFLY_NET,
            ItemID.BUTTERFLY_JAR,
            ItemID.HUNTING_BOX_TRAP,
            ItemID.HUNTING_SNARE, // Rabbit Snare
            ItemID.NET, // Small fishing net
            ItemID.MAGIC_IMP_BOX,
            ItemID.HUNTING_TEASING_STICK,
            ItemID.HG_HUNTER_HOOD,
            ItemID.HG_HUNTER_TOP,
            ItemID.HG_HUNTER_LEGS,
            ItemID.HG_HUNTER_BOOTS,
            ItemID.RING_OF_PURSUIT,
            ItemID.HORN_OF_PLENTY,
            ItemID.GRYPHON_FEATHER,
            ItemID.TORCH_UNLIT,
            ItemID.ROPE,
            ItemID.II_IMPLING_JAR,
            ItemID.HORN_OF_PLENTY_UNCHARGED,
            ItemID.MAGIC_IMP_BOX_HALF,
            ItemID.MAGIC_IMP_BOX_FULL,
            ItemID.HG_HUNTER_SPEAR,
            ItemID.HUNTING_CAMOFLAUGE_ROBE_WOOD,
            ItemID.HUNTING_TROUSERS_WOOD,
            ItemID.HUNTING_CAMOFLAUGE_ROBE_JUNGLE,
            ItemID.HUNTING_TROUSERS_JUNGLE,
            ItemID.HUNTING_CAMOFLAUGE_ROBE_POLAR,
            ItemID.HUNTING_TROUSERS_POLAR,
            ItemID.HUNTING_CAMOFLAUGE_ROBE_DESERT,
            ItemID.HUNTING_TROUSERS_DESERT,
            ItemID.HUNTING_HAT_JAGUAR,
            ItemID.HUNTING_TORSO_JAGUAR,
            ItemID.HUNTING_TROUSERS_JAGUAR,
            ItemID.HUNTING_HAT_TIGER,
            ItemID.HUNTING_TORSO_TIGER,
            ItemID.HUNTING_TROUSERS_TIGER,
            ItemID.HUNTING_HAT_LEOPARD,
            ItemID.HUNTING_TORSO_LEOPARD,
            ItemID.HUNTING_TROUSERS_LEOPARD,
            ItemID.SKILLCAPE_HUNTING_HOOD,
            ItemID.SKILLCAPE_HUNTING,
            ItemID.SKILLCAPE_HUNTING_TRIMMED);
}
