package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import gimhub.items.ItemsUnordered;
import gimhub.items.ItemsUtilities;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;

@Slf4j
public class CoalBagItems implements TrackedItemContainer {
    private ItemsUnordered items = null;

    private boolean hasOpenCoalBag = false;

    private Integer bagCoalAmount = null;
    private Integer inventoryCoalAmount = null;
    private Integer inventoryCatalystAmount = null;

    private static final int ACTION_LISTEN_TICKS = 2;
    private int minedCoalWithOpenBagTick = Integer.MIN_VALUE;

    private static final class SuperheatTrigger {
        final int tick;
        final int coalUsage;

        public SuperheatTrigger(int tick, int coalUsage) {
            this.tick = tick;
            this.coalUsage = coalUsage;
        }
    }

    private SuperheatTrigger superheatTrigger = null;

    private static final Pattern EMPTY_PATTERN = Pattern.compile("The coal bag is.* empty\\.");
    private static final Pattern ONE_LEFT_PATTERN = Pattern.compile("The coal bag.* contains one piece of coal\\.");
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("The coal bag.* contains (?<amount>\\d+) pieces of coal\\.");

    private static final Pattern MINE_PATTERN = Pattern.compile("You manage to mine some (?<ore>\\w+)\\.");
    private static final String MINE_PATTERN_COAL_NAME = "coal";

    private static final Pattern MINE_ADDITIONAL_PATTERN = Pattern.compile(".* you to mine an additional ore\\.");

    private static final Pattern SUPERHEAT_PATTERN = Pattern.compile(
            "(?:<[^<>]*>)?Superheat Item(?:</[^<>]*>)?(?:<[^<>]*>)? -> (?:<[^<>]*>)?(?<spellTarget>[\\w ]*)(?:<[^<>]*>)?");

    @Override
    public String key() {
        return "coal_bag";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private void rebuildItems(ItemManager itemManager) {
        if (bagCoalAmount == null || bagCoalAmount <= 0) {
            items = new ItemsUnordered();
        } else {
            items = new ItemsUnordered(new ItemsOrdered(List.of(new Item(ItemID.COAL, bagCoalAmount)), itemManager));
        }
    }

    private void updateInventoryCoal(ItemContainer container, ItemManager itemManager) {
        hasOpenCoalBag = false;
        inventoryCoalAmount = 0;
        inventoryCatalystAmount = 0;

        for (final Item item : container.getItems()) {
            if (!ItemsUtilities.isItemValid(item, itemManager)) {
                continue;
            }

            final int itemId = itemManager.canonicalize(item.getId());
            if (itemId == ItemID.COAL) {
                inventoryCoalAmount += item.getQuantity();
            } else if (itemId == ItemID.COAL_BAG_OPEN) {
                hasOpenCoalBag = true;
            } else if (itemId == ItemID.SMITHING_CATALYST) {
                inventoryCatalystAmount += item.getQuantity();
            }
        }
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == InventoryID.INV) {
            updateInventoryCoal(container, itemManager);
        }
    }

    @Override
    public void onMenuOptionClicked(Client client, MenuOptionClicked event, ItemManager itemManager) {
        if (inventoryCoalAmount == null || bagCoalAmount == null) {
            return;
        }

        final String target = event.getMenuTarget();
        log.debug("target {}", target);

        final Matcher matcher = SUPERHEAT_PATTERN.matcher(target);
        if (!matcher.matches()) {
            return;
        }

        final String spellTarget = matcher.group("spellTarget").toLowerCase();
        final int currentTick = client.getTickCount();
        int coalUsage = 0;

        final boolean hasCatalyst = inventoryCatalystAmount > 0;
        switch (spellTarget) {
            case "lovakite ore":
            case "iron ore":
                if (hasCatalyst) {
                    coalUsage = 1;
                } else {
                    coalUsage = 2;
                }
                break;
            case "mithril ore":
                if (hasCatalyst) {
                    coalUsage = 2;
                } else {
                    coalUsage = 4;
                }
                break;
            case "adamantite ore":
                if (hasCatalyst) {
                    coalUsage = 3;
                } else {
                    coalUsage = 6;
                }
                break;
            case "runite ore":
                if (hasCatalyst) {
                    coalUsage = 4;
                } else {
                    coalUsage = 8;
                }
                break;
        }

        coalUsage -= inventoryCoalAmount;
        if (coalUsage > 0) {
            superheatTrigger = new SuperheatTrigger(currentTick, coalUsage);
        }
    }

    @Override
    public void onStatChanged(StatChanged event, ItemManager itemManager) {
        if (event.getSkill() != Skill.SMITHING || superheatTrigger == null) {
            return;
        }

        if (superheatTrigger.coalUsage > 0) {
            bagCoalAmount -= superheatTrigger.coalUsage;
        }

        rebuildItems(itemManager);

        superheatTrigger = null;
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (superheatTrigger == null) {
            return;
        }

        final boolean isRecent = superheatTrigger.tick >= client.getTickCount() - ACTION_LISTEN_TICKS;
        if (!isRecent) {
            superheatTrigger = null;
        }
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        ChatMessageType messageType = event.getType();
        String message = event.getMessage();
        if (messageType != ChatMessageType.GAMEMESSAGE || message == null) {
            return;
        }

        {
            final Matcher matcher = EMPTY_PATTERN.matcher(message);
            if (matcher.matches()) {
                bagCoalAmount = 0;
            }
        }
        {
            final Matcher matcher = ONE_LEFT_PATTERN.matcher(message);
            if (matcher.matches()) {
                bagCoalAmount = 1;
            }
        }
        {
            final Matcher matcher = AMOUNT_PATTERN.matcher(message);
            if (matcher.matches()) {
                try {
                    bagCoalAmount = Integer.parseInt(matcher.group("amount"));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (hasOpenCoalBag) {
            final Matcher matcher = MINE_PATTERN.matcher(message);
            if (matcher.matches()) {
                final String ore = matcher.group("ore");
                if (Objects.equals(ore, MINE_PATTERN_COAL_NAME)) {
                    minedCoalWithOpenBagTick = client.getTickCount();
                    bagCoalAmount += 1;
                }
            }
        }

        final boolean hasMinedCoalRecently = minedCoalWithOpenBagTick >= client.getTickCount() - ACTION_LISTEN_TICKS;
        if (hasMinedCoalRecently) {
            final Matcher matcher = MINE_ADDITIONAL_PATTERN.matcher(message);
            if (matcher.matches()) {
                bagCoalAmount += 1;
            }
        }

        rebuildItems(itemManager);
    }
}
