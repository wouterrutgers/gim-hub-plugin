package gimhub.items.containers;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

public class LogBasketItems extends CheckedStorageItems {
    protected static final Pattern COLLECTED = Pattern.compile("You get some (.+)\\.");
    protected static final Set<Integer> OPEN_BASKETS = Set.of(ItemID.LOG_BASKET_OPEN, ItemID.FORESTRY_BASKET_OPEN);
    protected static final Set<Integer> INFERNAL_AXES =
            Set.of(ItemID.INFERNAL_AXE, ItemID.TRAILBLAZER_AXE, ItemID.TRAILBLAZER_RELOADED_AXE);
    protected Integer collectedLogIdentifier;
    protected int collectedLogQuantity;
    protected int burnedLogQuantity;

    public LogBasketItems() {
        super(
                "log_basket",
                "basket",
                Set.of(
                        ItemID.LOG_BASKET_CLOSED,
                        ItemID.LOG_BASKET_OPEN,
                        ItemID.FORESTRY_BASKET_CLOSED,
                        ItemID.FORESTRY_BASKET_OPEN),
                Set.of(
                        ItemID.LOGS,
                        ItemID.ACHEY_TREE_LOGS,
                        ItemID.OAK_LOGS,
                        ItemID.WILLOW_LOGS,
                        ItemID.TEAK_LOGS,
                        ItemID.MAPLE_LOGS,
                        ItemID.MAHOGANY_LOGS,
                        ItemID.ARCTIC_PINE_LOG,
                        ItemID.YEW_LOGS,
                        ItemID.MAGIC_LOGS,
                        ItemID.REDWOOD_LOGS,
                        ItemID.ROSEWOOD_LOGS,
                        ItemID.IRONWOOD_LOGS,
                        ItemID.CAMPHOR_LOGS,
                        ItemID.BLISTERWOOD_LOGS,
                        ItemID.JUNIPER_LOGS));
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        super.onChatMessage(client, event, itemManager);
        if (event.getType() != ChatMessageType.SPAM || !carryingAny(client, OPEN_BASKETS)) {
            return;
        }
        String message = Text.removeTags(event.getMessage());
        Matcher collected = COLLECTED.matcher(message);
        if (collected.matches()) {
            for (int identifier : contentIds) {
                if (itemManager.getItemComposition(identifier).getName().equalsIgnoreCase(collected.group(1))) {
                    collectedLogIdentifier = identifier;
                    collectedLogQuantity++;
                    return;
                }
            }
        }
        if (collectedLogIdentifier != null
                && (message.equals("Your Kandarin headgear provides you with an additional log.")
                        || message.equals("The nature offerings enabled you to chop an extra log."))) {
            collectedLogQuantity++;
        }
    }

    @Override
    public void onStatChanged(StatChanged event, ItemManager itemManager) {
        if (event.getSkill() == Skill.FIREMAKING) {
            burnedLogQuantity++;
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (collectedLogIdentifier != null && items != null) {
            int quantity = collectedLogQuantity;
            // Infernal axe burns also emit Firemaking XP during the collection tick.
            if (burnedLogQuantity > 0 && carryingAny(client, INFERNAL_AXES)) {
                quantity = Math.max(0, quantity - burnedLogQuantity);
            }
            int available = 28
                    - items.getItemsQuantityByID().values().stream()
                            .mapToInt(Integer::intValue)
                            .sum();
            if (quantity > 0 && available > 0) {
                addItem(collectedLogIdentifier, Math.min(quantity, available), itemManager);
            }
        }
        collectedLogIdentifier = null;
        collectedLogQuantity = 0;
        burnedLogQuantity = 0;
        super.onGameTick(client, itemManager);
    }

    protected boolean carryingAny(Client client, Set<Integer> identifiers) {
        for (int inventory : new int[] {InventoryID.INV, InventoryID.WORN}) {
            ItemContainer container = client.getItemContainer(inventory);
            if (container != null) {
                for (Item item : container.getItems()) {
                    if (identifiers.contains(item.getId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
