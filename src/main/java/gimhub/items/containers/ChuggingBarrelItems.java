package gimhub.items.containers;

import gimhub.items.ItemsUnordered;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

public class ChuggingBarrelItems extends ContainerItems {
    protected static final Pattern DEPOSIT =
            Pattern.compile("Your chugging barrel has been filled with (\\d+) doses? of (.+)\\.");
    protected static final Pattern REMAINING = Pattern.compile("You have (\\d+) doses? of (.+) left in your barrel\\.");
    protected static final Pattern EMPTY = Pattern.compile("You have finished all doses of (.+) in your barrel\\.");

    protected static final Pattern DRINK = Pattern.compile("You drink (?:some of )?(?:the|your)? (.*)\\.");
    protected boolean drinking;
    protected boolean inspectDisassembly;

    @Override
    public void onMenuOptionClicked(Client client, MenuOptionClicked event, ItemManager itemManager) {
        if (event.getItemId() == ItemID.MM_PREPOT_DEVICE
                && event.getMenuOption().equals("Drink")) {
            drinking = true;
        }
    }

    @Override
    public void onWidgetLoaded(WidgetLoaded event) {
        super.onWidgetLoaded(event);
        if (event.getGroupId() == InterfaceID.OBJECTBOX_DOUBLE) {
            inspectDisassembly = true;
        }
    }

    protected final List<String> messages = new ArrayList<>();

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        super.onGameTick(client, itemManager);
        if (!authoritativeUpdate) {
            for (String message : messages) {
                processMessage(client, message, itemManager);
            }
        }
        authoritativeUpdate = false;
        messages.clear();
        drinking = false;
        if (inspectDisassembly) {
            Widget text = client.getWidget(InterfaceID.ObjectboxDouble.TEXT);
            if (text != null && text.getText().equals("You disassemble the Chugging barrel.")) {
                items = new ItemsUnordered();
            }
            inspectDisassembly = false;
        }
    }

    public ChuggingBarrelItems() {
        super(InventoryID.PREPOT_DEVICE_INV, InterfaceID.PREPOT_DEVICE);
    }

    @Override
    public String key() {
        return "chugging_barrel";
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        if (items == null
                || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)) {
            return;
        }
        messages.add(Text.removeTags(event.getMessage()));
    }

    protected void processMessage(Client client, String message, ItemManager itemManager) {
        Matcher drink = DRINK.matcher(message);
        Matcher deposit = DEPOSIT.matcher(message);
        Matcher remaining = REMAINING.matcher(message);
        Matcher empty = EMPTY.matcher(message);
        if (drinking && drink.matches()) {
            List<Integer> candidates = ChuggingBarrelPotions.BY_DRINK_MESSAGE.get(drink.group(1));
            if (candidates != null) {
                List<Integer> stored = candidates.stream()
                        .filter(id -> items.getItemsQuantityByID().getOrDefault(id, 0) > 0)
                        .collect(Collectors.toList());
                // A generic drink message can describe multiple brews. Wait for exact game data in that case.
                if (stored.size() == 1) {
                    Map<Integer, Integer> contents = new HashMap<>(items.getItemsQuantityByID());
                    contents.computeIfPresent(stored.get(0), (id, quantity) -> quantity - 1);
                    items = new ItemsUnordered(contents, itemManager);
                }
            }
        } else if (deposit.matches()) {
            updatePotion(client, itemManager, deposit.group(2), Integer.parseInt(deposit.group(1)), true);
        } else if (remaining.matches()) {
            updatePotion(client, itemManager, remaining.group(2), Integer.parseInt(remaining.group(1)), false);
        } else if (empty.matches()) {
            updatePotion(client, itemManager, empty.group(1), 0, false);
        }
    }

    protected void updatePotion(Client client, ItemManager itemManager, String name, int quantity, boolean add) {
        Integer itemId = ChuggingBarrelPotions.BY_NAME.get(name);
        if (itemId != null) {
            Map<Integer, Integer> contents = new HashMap<>(items.getItemsQuantityByID());
            contents.put(itemId, quantity + (add ? contents.getOrDefault(itemId, 0) : 0));
            items = new ItemsUnordered(contents, itemManager);
        }
    }
}
