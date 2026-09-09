package gimhub.items.containers;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

public class HerbSackItems extends ChatStorageItems {
    protected static final Map<String, Integer> HERBS = Map.ofEntries(
            Map.entry("guam leaf", ItemID.UNIDENTIFIED_GUAM),
            Map.entry("marrentill", ItemID.UNIDENTIFIED_MARENTILL),
            Map.entry("tarromin", ItemID.UNIDENTIFIED_TARROMIN),
            Map.entry("harralander", ItemID.UNIDENTIFIED_HARRALANDER),
            Map.entry("ranarr weed", ItemID.UNIDENTIFIED_RANARR),
            Map.entry("toadflax", ItemID.UNIDENTIFIED_TOADFLAX),
            Map.entry("irit leaf", ItemID.UNIDENTIFIED_IRIT),
            Map.entry("avantoe", ItemID.UNIDENTIFIED_AVANTOE),
            Map.entry("kwuarm", ItemID.UNIDENTIFIED_KWUARM),
            Map.entry("huasca", ItemID.UNIDENTIFIED_HUASCA),
            Map.entry("snapdragon", ItemID.UNIDENTIFIED_SNAPDRAGON),
            Map.entry("cadantine", ItemID.UNIDENTIFIED_CADANTINE),
            Map.entry("lantadyme", ItemID.UNIDENTIFIED_LANTADYME),
            Map.entry("dwarf weed", ItemID.UNIDENTIFIED_DWARF_WEED),
            Map.entry("torstol", ItemID.UNIDENTIFIED_TORSTOL));
    protected static final Pattern ENTRY = Pattern.compile("(\\d+) x Grimy (.+)", Pattern.CASE_INSENSITIVE);
    protected static final Pattern PICKUP =
            Pattern.compile("You put the Grimy (.+) herb into your herb sack\\.", Pattern.CASE_INSENSITIVE);
    protected Map<Integer, Integer> checking;
    protected boolean invalidCheck;

    public HerbSackItems() {
        super(
                Set.of(
                        ItemID.SLAYER_HERB_SACK,
                        ItemID.SLAYER_HERB_SACK_OPEN,
                        ItemID.SLAYER_HERB_SACK_SILK,
                        ItemID.SLAYER_HERB_SACK_SILK_OPEN),
                Set.copyOf(HERBS.values()));
    }

    @Override
    public String key() {
        return "herb_sack";
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        String message = Text.removeTags(event.getMessage());
        if (confirmDeposit(message, itemManager) || message.equals("The herb sack is empty.")) {
            checking = null;
            setItems(Map.of(), itemManager);
        } else if (message.equals("You look in your herb sack and see:")) {
            checking = new HashMap<>();
            invalidCheck = false;
        } else if (checking != null) {
            Matcher entry = ENTRY.matcher(message);
            if (entry.matches()) {
                Integer itemId = HERBS.get(entry.group(2).toLowerCase(Locale.ROOT));
                if (itemId == null) {
                    invalidCheck = true;
                } else {
                    checking.put(itemId, Integer.parseInt(entry.group(1)));
                }
            }
        } else {
            Matcher pickup = PICKUP.matcher(message);
            if (pickup.matches()) {
                Integer itemId = HERBS.get(pickup.group(1).toLowerCase(Locale.ROOT));
                if (itemId != null) {
                    addItem(itemId, 1, itemManager);
                }
            }
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (checking != null) {
            if (!checking.isEmpty() && !invalidCheck) {
                setItems(checking, itemManager);
            }
            checking = null;
        }
        super.onGameTick(client, itemManager);
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        if (varbitId == VarbitID.EMPTYONDEATH_HERBSACK && client.getVarbitValue(varbitId) == 1) {
            setItems(Map.of(), itemManager);
        }
    }
}
