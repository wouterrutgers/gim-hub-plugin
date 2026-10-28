package gimhub.items.containers;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

public class GemBagItems extends ChatStorageItems {
    protected static final Map<String, Integer> GEMS = Map.of(
            "opal",
            ItemID.UNCUT_OPAL,
            "jade",
            ItemID.UNCUT_JADE,
            "red topaz",
            ItemID.UNCUT_RED_TOPAZ,
            "sapphire",
            ItemID.UNCUT_SAPPHIRE,
            "emerald",
            ItemID.UNCUT_EMERALD,
            "ruby",
            ItemID.UNCUT_RUBY,
            "diamond",
            ItemID.UNCUT_DIAMOND,
            "dragonstone",
            ItemID.UNCUT_DRAGONSTONE);
    protected static final Set<Integer> SACK_ONLY_GEMS =
            Set.of(ItemID.UNCUT_OPAL, ItemID.UNCUT_JADE, ItemID.UNCUT_RED_TOPAZ);
    protected static final int[] CHECK_ITEMS = {
        ItemID.UNCUT_OPAL,
        ItemID.UNCUT_JADE,
        ItemID.UNCUT_RED_TOPAZ,
        ItemID.UNCUT_SAPPHIRE,
        ItemID.UNCUT_EMERALD,
        ItemID.UNCUT_RUBY,
        ItemID.UNCUT_DIAMOND,
        ItemID.UNCUT_DRAGONSTONE
    };
    protected static final Pattern CHECK = Pattern.compile(
            "(?:Left in (?:bag|sack): )?(?:Opal: (\\d+)[ /]+Jade: (\\d+)[ /]+Red topaz: (\\d+)[ /]+)?Sapphires?: (\\d+)[ /]+Emeralds?: (\\d+)[ /]+Rub(?:y|ies): (\\d+)[ /]+Diamonds?: (\\d+)[ /]+Dragonstones?: (\\d+)",
            Pattern.CASE_INSENSITIVE);
    protected static final Pattern STOLEN = Pattern.compile(
            "The following stolen loot gets added to your gem (?:bag|sack): Uncut (.+) x (\\d+)\\.",
            Pattern.CASE_INSENSITIVE);
    protected static final Pattern SINGLE = Pattern.compile(
            "(?:You put the stolen Uncut (.+) into your gem (?:bag|sack)|You steal an uncut (.+) and add it to your gem (?:bag|sack))\\.",
            Pattern.CASE_INSENSITIVE);

    protected static final Pattern MINED = Pattern.compile("You just (?:found|mined) (?:a|an) (.+)!");

    public GemBagItems() {
        super(
                Set.of(ItemID.GEM_BAG, ItemID.GEM_BAG_OPEN, ItemID.GEM_SACK, ItemID.GEM_SACK_OPEN),
                Set.copyOf(GEMS.values()));
    }

    @Override
    public String key() {
        return "gem_bag";
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        String message = Text.removeTags(event.getMessage().replace("<br>", " "));
        Matcher check = CHECK.matcher(message);
        Matcher stolen = STOLEN.matcher(message);
        Matcher single = SINGLE.matcher(message);
        Matcher mined = MINED.matcher(message);
        if (confirmDeposit(message, itemManager)
                || message.matches("The gem (?:bag|sack) is(?: now)? empty\\.")
                || message.matches("You empty (?:the|your) gem (?:bag|sack) into the bank\\.")) {
            setItems(Map.of(), itemManager);
        } else if (check.matches()) {
            Map<Integer, Integer> contents = new HashMap<>();
            for (int index = 0; index < CHECK_ITEMS.length; index++) {
                if (check.group(index + 1) != null) {
                    contents.put(CHECK_ITEMS[index], Integer.parseInt(check.group(index + 1)));
                }
            }
            setItems(contents, itemManager);
        } else if (mined.matches() && items != null) {
            Integer itemId = GEMS.get(mined.group(1).toLowerCase(Locale.ROOT));
            ItemContainer inventory = client.getItemContainer(InventoryID.INV);
            if (itemId != null
                    && items.getItemsQuantityByID().getOrDefault(itemId, 0) < 60
                    && inventory != null
                    && (inventory.contains(ItemID.GEM_SACK_OPEN)
                            || (inventory.contains(ItemID.GEM_BAG_OPEN) && !SACK_ONLY_GEMS.contains(itemId)))) {
                addItem(itemId, 1, itemManager);
            }
        } else if (stolen.matches()) {
            addGem(stolen.group(1), Integer.parseInt(stolen.group(2)), itemManager);
        } else if (single.matches()) {
            addGem(single.group(1) == null ? single.group(2) : single.group(1), 1, itemManager);
        }
    }

    protected void addGem(String name, int quantity, ItemManager itemManager) {
        Integer itemId = GEMS.get(name.toLowerCase(Locale.ROOT));
        if (itemId != null) {
            addItem(itemId, quantity, itemManager);
        }
    }
}
