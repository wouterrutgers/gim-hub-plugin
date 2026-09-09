package gimhub.items.containers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

public class SeedBoxItems extends ContainerItems {
    protected static final Map<String, Integer> SEEDS = Map.ofEntries(
            Map.entry("potato seed", ItemID.POTATO_SEED),
            Map.entry("onion seed", ItemID.ONION_SEED),
            Map.entry("cabbage seed", ItemID.CABBAGE_SEED),
            Map.entry("tomato seed", ItemID.TOMATO_SEED),
            Map.entry("sweetcorn seed", ItemID.SWEETCORN_SEED),
            Map.entry("strawberry seed", ItemID.STRAWBERRY_SEED),
            Map.entry("watermelon seed", ItemID.WATERMELON_SEED),
            Map.entry("snape grass seed", ItemID.SNAPE_GRASS_SEED),
            Map.entry("marigold seed", ItemID.MARIGOLD_SEED),
            Map.entry("rosemary seed", ItemID.ROSEMARY_SEED),
            Map.entry("nasturtium seed", ItemID.NASTURTIUM_SEED),
            Map.entry("woad seed", ItemID.WOAD_SEED),
            Map.entry("limpwurt seed", ItemID.LIMPWURT_SEED),
            Map.entry("white lily seed", ItemID.WHITE_LILY_SEED),
            Map.entry("guam seed", ItemID.GUAM_SEED),
            Map.entry("marrentill seed", ItemID.MARRENTILL_SEED),
            Map.entry("tarromin seed", ItemID.TARROMIN_SEED),
            Map.entry("harralander seed", ItemID.HARRALANDER_SEED),
            Map.entry("gout tuber", ItemID.VILLAGE_RARE_TUBER),
            Map.entry("ranarr seed", ItemID.RANARR_SEED),
            Map.entry("toadflax seed", ItemID.TOADFLAX_SEED),
            Map.entry("irit seed", ItemID.IRIT_SEED),
            Map.entry("avantoe seed", ItemID.AVANTOE_SEED),
            Map.entry("kwuarm seed", ItemID.KWUARM_SEED),
            Map.entry("snapdragon seed", ItemID.SNAPDRAGON_SEED),
            Map.entry("cadantine seed", ItemID.CADANTINE_SEED),
            Map.entry("lantadyme seed", ItemID.LANTADYME_SEED),
            Map.entry("dwarf weed seed", ItemID.DWARF_WEED_SEED),
            Map.entry("torstol seed", ItemID.TORSTOL_SEED),
            Map.entry("barley seed", ItemID.BARLEY_SEED),
            Map.entry("hammerstone seed", ItemID.HAMMERSTONE_HOP_SEED),
            Map.entry("asgarnian seed", ItemID.ASGARNIAN_HOP_SEED),
            Map.entry("jute seed", ItemID.JUTE_SEED),
            Map.entry("yanillian seed", ItemID.YANILLIAN_HOP_SEED),
            Map.entry("krandorian seed", ItemID.KRANDORIAN_HOP_SEED),
            Map.entry("wildblood seed", ItemID.WILDBLOOD_HOP_SEED),
            Map.entry("redberry seed", ItemID.REDBERRY_BUSH_SEED),
            Map.entry("cadavaberry seed", ItemID.CADAVABERRY_BUSH_SEED),
            Map.entry("dwellberry seed", ItemID.DWELLBERRY_BUSH_SEED),
            Map.entry("jangerberry seed", ItemID.JANGERBERRY_BUSH_SEED),
            Map.entry("whiteberry seed", ItemID.WHITEBERRY_BUSH_SEED),
            Map.entry("poison ivy seed", ItemID.POISONIVY_BUSH_SEED),
            Map.entry("acorn", ItemID.ACORN),
            Map.entry("willow seed", ItemID.WILLOW_SEED),
            Map.entry("maple seed", ItemID.MAPLE_SEED),
            Map.entry("yew seed", ItemID.YEW_SEED),
            Map.entry("magic seed", ItemID.MAGIC_TREE_SEED),
            Map.entry("apple tree seed", ItemID.APPLE_TREE_SEED),
            Map.entry("banana tree seed", ItemID.BANANA_TREE_SEED),
            Map.entry("orange tree seed", ItemID.ORANGE_TREE_SEED),
            Map.entry("curry tree seed", ItemID.CURRY_TREE_SEED),
            Map.entry("pineapple seed", ItemID.PINEAPPLE_TREE_SEED),
            Map.entry("papaya tree seed", ItemID.PAPAYA_TREE_SEED),
            Map.entry("palm tree seed", ItemID.PALM_TREE_SEED),
            Map.entry("dragonfruit tree seed", ItemID.DRAGONFRUIT_TREE_SEED),
            Map.entry("seaweed spore", ItemID.SEAWEED_SEED),
            Map.entry("grape seed", ItemID.GRAPE_SEED),
            Map.entry("mushroom spore", ItemID.MUSHROOM_SEED),
            Map.entry("belladonna seed", ItemID.BELLADONNA_SEED),
            Map.entry("hespori seed", ItemID.HESPORI_SEED),
            Map.entry("kronos seed", ItemID.KRONOS_SEED),
            Map.entry("iasor seed", ItemID.IASOR_SEED),
            Map.entry("attas seed", ItemID.ATTAS_SEED),
            Map.entry("teak seed", ItemID.TEAK_SEED),
            Map.entry("mahogany seed", ItemID.MAHOGANY_SEED),
            Map.entry("calquat tree seed", ItemID.CALQUAT_TREE_SEED),
            Map.entry("crystal acorn", ItemID.CRYSTAL_TREE_SEED),
            Map.entry("spirit seed", ItemID.SPIRIT_TREE_SEED),
            Map.entry("celastrus seed", ItemID.CELASTRUS_TREE_SEED),
            Map.entry("redwood tree seed", ItemID.REDWOOD_TREE_SEED),
            Map.entry("cactus seed", ItemID.CACTUS_SEED),
            Map.entry("potato cactus seed", ItemID.POTATO_CACTUS_SEED),
            Map.entry("huasca seed", ItemID.HUASCA_SEED));
    protected static final Pattern ADD = Pattern.compile(
            "(?:Stored|You put) (\\d+) x (.+) (?:in your seed box|straight into your open seed box)\\.");
    protected static final Pattern STOLEN =
            Pattern.compile("The following stolen loot gets added to your seed box: (.+) x (\\d+)\\.");
    protected static final Pattern SINGLE = Pattern.compile("You put the stolen (.+) into your seed box\\.");
    protected static final Pattern REMOVE = Pattern.compile("Emptied (\\d+) x (.+) to your inventory\\.");
    protected final Map<Integer, Integer> changes = new HashMap<>();

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        super.onChatMessage(client, event, itemManager);
        if (items == null
                || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)) {
            return;
        }
        String message = Text.removeTags(event.getMessage());
        Matcher add = ADD.matcher(message);
        Matcher stolen = STOLEN.matcher(message);
        Matcher single = SINGLE.matcher(message);
        Matcher remove = REMOVE.matcher(message);
        if (add.matches()) {
            change(add.group(2), Integer.parseInt(add.group(1)));
        } else if (stolen.matches()) {
            change(stolen.group(1), Integer.parseInt(stolen.group(2)));
        } else if (single.matches()) {
            change(single.group(1), 1);
        } else if (remove.matches()) {
            change(remove.group(2), -Integer.parseInt(remove.group(1)));
        }
    }

    protected void change(String name, int quantity) {
        Integer itemId = SEEDS.get(name.toLowerCase(Locale.ROOT));
        if (itemId != null) {
            changes.merge(itemId, quantity, Integer::sum);
        }
    }

    public SeedBoxItems() {
        super(InventoryID.SEED_BOX, InterfaceID.HOSIDIUS_SEEDBOX, ItemID.SEED_BOX, ItemID.SEED_BOX_OPEN);
    }

    @Override
    public String key() {
        return "seed_box";
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        boolean inspect = pending || firstTick;
        super.onGameTick(client, itemManager);
        if (!authoritativeUpdate && items != null && !changes.isEmpty()) {
            Map<Integer, Integer> contents = new HashMap<>(items.getItemsQuantityByID());
            changes.forEach((itemId, quantity) ->
                    contents.put(itemId, Math.max(0, contents.getOrDefault(itemId, 0) + quantity)));
            items = new gimhub.items.ItemsUnordered(contents, itemManager);
        }
        changes.clear();
        authoritativeUpdate = false;
        if (!inspect) {
            return;
        }
        Widget widget = client.getWidget(InterfaceID.HosidiusSeedbox.SEED_LAYER);
        if (widget != null
                && widget.getChildren() != null
                && widget.getChildren().length == 6
                && Arrays.stream(widget.getChildren()).allMatch(child -> child != null && child.getItemId() == -1)) {
            items = new gimhub.items.ItemsUnordered();
            pending = false;
        }
    }
}
