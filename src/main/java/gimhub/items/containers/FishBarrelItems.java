package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import gimhub.items.ItemsUnordered;
import gimhub.items.ItemsUtilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

public class FishBarrelItems implements TrackedItemContainer {
    private static final int CAPACITY = 28;
    private static final int GAME_TICK_MARGIN = 3;

    private static final int CHECK_WIDGET_GROUP_ID = 193;
    private static final int CHECK_WIDGET_CHILD_ID = 2;

    private static final String EMPTY_MESSAGE = "The barrel is empty.";
    private static final String CONTENTS_PREFIX = "The barrel contains:";
    private static final Pattern ENTRY_PATTERN = Pattern.compile("(\\d+)\\s*x\\s*([a-zA-Z ]+)");

    private static final Pattern FISH_CAUGHT_MESSAGE = Pattern.compile(
            "^You catch (an?|some)(?: raw)? ([a-zA-Z ]+)[.!]?( It hardens as you handle it with your ice gloves\\.)?$");

    private static final String RADA_DOUBLE_CATCH_MESSAGE = "Rada's blessing enabled you to catch an extra fish.";
    private static final String FLAKES_DOUBLE_CATCH_MESSAGE = "The spirit flakes enabled you to catch an extra fish.";
    private static final String CORMORANT_CATCH_MESSAGE = "Your cormorant returns with its catch.";

    private static final Set<Integer> BARREL_IDS = Set.of(ItemID.FISH_BARREL_CLOSED, ItemID.FISH_BARREL_OPEN, 25583);

    private static final Map<String, Integer> FISH_TYPES_BY_NAME = Map.ofEntries(
            Map.entry("shrimp", ItemID.RAW_SHRIMP),
            Map.entry("sardine", ItemID.RAW_SARDINE),
            Map.entry("herring", ItemID.RAW_HERRING),
            Map.entry("anchovies", ItemID.RAW_ANCHOVIES),
            Map.entry("mackerel", ItemID.RAW_MACKEREL),
            Map.entry("trout", ItemID.RAW_TROUT),
            Map.entry("cod", ItemID.RAW_COD),
            Map.entry("pike", ItemID.RAW_PIKE),
            Map.entry("slimy swamp eel", ItemID.MORT_SLIMEY_EEL),
            Map.entry("slimy eel", ItemID.MORT_SLIMEY_EEL),
            Map.entry("salmon", ItemID.RAW_SALMON),
            Map.entry("tuna", ItemID.RAW_TUNA),
            Map.entry("rainbow fish", ItemID.HUNTING_RAW_FISH_SPECIAL),
            Map.entry("cave eel", ItemID.RAW_CAVE_EEL),
            Map.entry("lobster", ItemID.RAW_LOBSTER),
            Map.entry("bass", ItemID.RAW_BASS),
            Map.entry("leaping trout", ItemID.BRUT_SPAWNING_TROUT),
            Map.entry("swordfish", ItemID.RAW_SWORDFISH),
            Map.entry("swordtip squid", ItemID.RAW_SWORDTIP_SQUID),
            Map.entry("lava eel", ItemID.RAW_LAVA_EEL),
            Map.entry("leaping salmon", ItemID.BRUT_SPAWNING_SALMON),
            Map.entry("monkfish", ItemID.RAW_MONKFISH),
            Map.entry("leaping sturgeon", ItemID.BRUT_STURGEON),
            Map.entry("shark", ItemID.RAW_SHARK),
            Map.entry("infernal eel", ItemID.INFERNAL_EEL),
            Map.entry("anglerfish", ItemID.RAW_ANGLERFISH),
            Map.entry("dark crab", ItemID.RAW_DARK_CRAB),
            Map.entry("sacred eel", ItemID.SNAKEBOSS_EEL),
            Map.entry("sea turtle", ItemID.RAW_SEATURTLE),
            Map.entry("manta ray", ItemID.RAW_MANTARAY),
            Map.entry("jumbo squid", ItemID.RAW_JUMBO_SQUID),
            Map.entry("giant krill", ItemID.RAW_GIANT_KRILL),
            Map.entry("haddock", ItemID.RAW_HADDOCK),
            Map.entry("yellowfin", ItemID.RAW_YELLOWFIN),
            Map.entry("halibut", ItemID.RAW_HALIBUT),
            Map.entry("bluefin", ItemID.RAW_BLUEFIN),
            Map.entry("marlin", ItemID.RAW_MARLIN),
            Map.entry("guppy", ItemID.RAW_GUPPY),
            Map.entry("cavefish", ItemID.RAW_CAVEFISH),
            Map.entry("tetra", ItemID.RAW_TETRA),
            Map.entry("catfish", ItemID.RAW_CATFISH),
            Map.entry("giant carp", ItemID.RAW_GIANT_CARP));

    private static final Set<Integer> ALL_FISH_IDS = Set.copyOf(FISH_TYPES_BY_NAME.values());

    private final Map<Integer, Integer> barrel = new HashMap<>();
    private ItemsUnordered items = null;
    private boolean known = false;

    private boolean hasBarrel = false;
    private final Map<Integer, Integer> inventoryFish = new HashMap<>();
    private Map<Integer, Integer> inventoryFishPrev = new HashMap<>();

    private final Map<Integer, Integer> caughtFishThisTick = new HashMap<>();
    private Integer lastCaughtFishId = null;

    private int lastFillTick = -999;
    private int lastEmptyTick = -999;

    private BankItems bank;

    public void setBank(BankItems bank) {
        this.bank = bank;
    }

    @Override
    public String key() {
        return "fish_barrel";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private static boolean recentlyActioned(int actionTick, int nowTick) {
        return actionTick > nowTick - GAME_TICK_MARGIN;
    }

    private int totalInBarrel() {
        int total = 0;
        for (final int qty : barrel.values()) {
            total += qty;
        }
        return total;
    }

    private void rebuildItems(ItemManager itemManager) {
        final ArrayList<Item> result = new ArrayList<>(barrel.size());
        for (final Map.Entry<Integer, Integer> e : barrel.entrySet()) {
            if (e.getValue() <= 0) continue;
            result.add(new Item(e.getKey(), e.getValue()));
        }

        if (result.isEmpty()) {
            items = new ItemsUnordered();
        } else {
            items = new ItemsUnordered(new ItemsOrdered(result, itemManager));
        }
    }

    private void addToBarrel(int itemId, int quantity, ItemManager itemManager) {
        if (!known || quantity <= 0) {
            return;
        }

        final int allowed = CAPACITY - totalInBarrel();
        if (allowed <= 0) {
            return;
        }

        final int accepted = Math.min(allowed, quantity);
        barrel.put(itemId, barrel.getOrDefault(itemId, 0) + accepted);
        rebuildItems(itemManager);
    }

    private static String normalizeFishName(String name) {
        String n = name.toLowerCase().trim().replaceAll("\\s+", " ");
        if (n.startsWith("raw ")) {
            n = n.substring(4);
        }
        if (!FISH_TYPES_BY_NAME.containsKey(n) && n.endsWith("s") && !n.endsWith("ss")) {
            n = n.substring(0, n.length() - 1);
        }
        return n;
    }

    private static Integer fishNameToItemId(String fishName) {
        final String normalized = normalizeFishName(fishName);
        final Integer itemId = FISH_TYPES_BY_NAME.get(normalized);
        if (itemId != null) {
            return itemId;
        }
        if (normalized.endsWith("s") && !normalized.endsWith("ss")) {
            return FISH_TYPES_BY_NAME.get(normalized.substring(0, normalized.length() - 1));
        }
        return null;
    }

    private void updateFromCheckWidget(Widget widget, ItemManager itemManager) {
        String message = widget.getText();
        if (message == null) {
            return;
        }

        message = message.replace("<br>", " ").replaceAll("<[^>]+>", "").trim();

        if (EMPTY_MESSAGE.equals(message)) {
            barrel.clear();
            known = true;
            rebuildItems(itemManager);
            return;
        }

        if (!message.startsWith(CONTENTS_PREFIX)) {
            return;
        }

        final Matcher matcher = ENTRY_PATTERN.matcher(message);
        final Map<Integer, Integer> parsed = new HashMap<>();
        while (matcher.find()) {
            final int qty;
            try {
                qty = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return;
            }

            String fishName = matcher.group(2).trim();
            if (fishName.endsWith(",")) {
                fishName = fishName.substring(0, fishName.length() - 1).trim();
            }

            final Integer itemId = fishNameToItemId(fishName);
            if (itemId == null) {
                return;
            }

            parsed.put(itemId, parsed.getOrDefault(itemId, 0) + qty);
        }

        if (parsed.isEmpty()) {
            return;
        }

        barrel.clear();
        barrel.putAll(parsed);
        known = true;
        rebuildItems(itemManager);
    }

    private void updateInventoryFish(ItemContainer container, ItemManager itemManager) {
        hasBarrel = false;
        inventoryFish.clear();

        for (final Item item : container.getItems()) {
            if (!ItemsUtilities.isItemValid(item, itemManager)) {
                continue;
            }

            final int itemId = itemManager.canonicalize(item.getId());
            if (BARREL_IDS.contains(itemId)) {
                hasBarrel = true;
            }

            if (ALL_FISH_IDS.contains(itemId)) {
                inventoryFish.put(itemId, inventoryFish.getOrDefault(itemId, 0) + item.getQuantity());
            }
        }
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == InventoryID.INV) {
            updateInventoryFish(container, itemManager);
        }
    }

    @Override
    public void onMenuOptionClicked(Client client, MenuOptionClicked event, ItemManager itemManager) {
        final int itemId = itemManager.canonicalize(event.getItemId());
        if (!BARREL_IDS.contains(itemId)) {
            return;
        }

        String option = event.getMenuOption();
        if (option == null && event.getMenuEntry() != null) {
            option = event.getMenuEntry().getOption();
        }
        if (option == null) {
            return;
        }

        option = option.replaceAll("<[^>]+>", "");

        final int tick = client.getTickCount();

        if ("Fill".equalsIgnoreCase(option)) {
            lastFillTick = tick;
            return;
        }

        if ("Empty".equalsIgnoreCase(option) || "Empty to bank".equalsIgnoreCase(option)) {
            final boolean depositBoxOpen = client.getWidget(InterfaceID.BankDepositbox.INVENTORY) != null;
            final boolean mainBankOpen = client.getItemContainer(InventoryID.BANK) != null;

            final boolean shouldCreditBank =
                    "Empty to bank".equalsIgnoreCase(option) || (depositBoxOpen && "Empty".equalsIgnoreCase(option));
            if (shouldCreditBank && !mainBankOpen && bank != null && known && items != null) {
                bank.modify(items);
            }

            lastEmptyTick = tick;
            barrel.clear();
            known = true;
            rebuildItems(itemManager);
        }
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        if (!hasBarrel) {
            return;
        }

        final ChatMessageType type = event.getType();
        if (type != ChatMessageType.SPAM && type != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        final String msg = event.getMessage();
        if (msg == null) {
            return;
        }

        final Matcher matcher = FISH_CAUGHT_MESSAGE.matcher(msg);
        if (matcher.matches()) {
            final String fishName = matcher.group(2);
            final Integer fishItemId = fishNameToItemId(fishName);
            if (fishItemId != null) {
                caughtFishThisTick.put(fishItemId, caughtFishThisTick.getOrDefault(fishItemId, 0) + 1);
                lastCaughtFishId = fishItemId;
            }
            return;
        }

        if ((RADA_DOUBLE_CATCH_MESSAGE.equals(msg)
                        || FLAKES_DOUBLE_CATCH_MESSAGE.equals(msg)
                        || CORMORANT_CATCH_MESSAGE.equals(msg))
                && lastCaughtFishId != null) {
            caughtFishThisTick.put(lastCaughtFishId, caughtFishThisTick.getOrDefault(lastCaughtFishId, 0) + 1);
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        final Widget widget = client.getWidget(CHECK_WIDGET_GROUP_ID, CHECK_WIDGET_CHILD_ID);
        if (widget != null) {
            updateFromCheckWidget(widget, itemManager);
        }

        if (hasBarrel && known) {
            final int tick = client.getTickCount();

            if (recentlyActioned(lastEmptyTick, tick)) {
                barrel.clear();
                rebuildItems(itemManager);
            }

            if (recentlyActioned(lastFillTick, tick)) {
                for (final int fishId : ALL_FISH_IDS) {
                    final int removed = Math.max(
                            0, inventoryFishPrev.getOrDefault(fishId, 0) - inventoryFish.getOrDefault(fishId, 0));
                    if (removed > 0) {
                        addToBarrel(fishId, removed, itemManager);
                    }
                }
            }

            for (final Map.Entry<Integer, Integer> e : caughtFishThisTick.entrySet()) {
                final int fishId = e.getKey();
                final int caught = e.getValue();

                final int invDelta =
                        Math.max(0, inventoryFish.getOrDefault(fishId, 0) - inventoryFishPrev.getOrDefault(fishId, 0));
                final int toBarrel = caught - Math.min(caught, invDelta);
                if (toBarrel > 0) {
                    addToBarrel(fishId, toBarrel, itemManager);
                }
            }
        }

        inventoryFishPrev = new HashMap<>(inventoryFish);
        caughtFishThisTick.clear();
        lastCaughtFishId = null;
    }
}
