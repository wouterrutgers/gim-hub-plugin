package gimhub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

@Slf4j
public class CollectionLogManager {
    private static final int COLLECTION_DELAYED_TRANSMIT_SCRIPT = 4100;
    private static final int COLLECTION_LOG_SETUP_SCRIPT = 7797;
    private static final int COLLECTION_INITIALIZATION_SCRIPT = 2240;
    private static final int COLLECTION_LOG_TRANSMIT_BUFFER_TICKS = 2;
    private static final int COLLECTION_LOG_NOTIFICATION_PREFIX_LENGTH = "New item:".length();
    private static final Pattern NEW_ITEM_MESSAGE_PATTERN =
            Pattern.compile("New item added to your collection log: (?<itemName>(.*))");
    private static final Set<String> IGNORED_NOTIFICATION_ITEMS = Set.of(
            "Chompy bird hat",
            "Decorative sword",
            "Decorative armour",
            "Decorative helm",
            "Decorative shield",
            "Castlewars hood",
            "Castlewars cloak",
            "Rum",
            "Ancient page",
            "Graceful hood",
            "Graceful cape",
            "Graceful top",
            "Graceful legs",
            "Graceful gloves",
            "Graceful boots",
            "Mysterious page",
            "Decorative boots",
            "Decorative full helm",
            "Medallion fragment");

    private static final int UNLOCK_MATCH_TICKS = 10;
    private final List<PendingUpdate> pendingUpdates = new ArrayList<>();
    private final Map<Integer, Integer> recentDropTicks = new HashMap<>();
    private final Set<Integer> notifiedItems = new HashSet<>();
    private int currentTick;

    private static class PendingUpdate {
        private final String type;
        private final Map<Integer, Integer> items;
        private int tick;

        private PendingUpdate(String type, Map<Integer, Integer> items, int tick) {
            this.type = type;
            this.items = new LinkedHashMap<>(items);
            this.tick = tick;
        }
    }

    private boolean automaticCollectionLogRetrieval = false;
    private boolean collectionLogNotificationStarted = false;
    private int collectionLogTransmitTick = -1;

    public synchronized void storeCollectionLogItem(int itemIdentifier, int quantity) {
        if (quantity < 0) return;
        if (!pendingUpdates.isEmpty()) {
            PendingUpdate last = pendingUpdates.get(pendingUpdates.size() - 1);
            if (last.type.equals("scan")) {
                last.items.put(itemIdentifier, quantity);
                return;
            }
        }
        pendingUpdates.add(new PendingUpdate("scan", Map.of(itemIdentifier, quantity), currentTick));
    }

    public synchronized void clearCollectionLogItems() {
        pendingUpdates.removeIf(update -> update.type.equals("scan"));
    }

    public synchronized void flatten(Map<String, APISerializable> flat) {
        if (automaticCollectionLogRetrieval) return;

        List<CollectionLogUpdates.Update> ready = new ArrayList<>();
        Iterator<PendingUpdate> iterator = pendingUpdates.iterator();
        while (iterator.hasNext()) {
            PendingUpdate update = iterator.next();
            if (update.type.equals("unlock") && currentTick <= update.tick + UNLOCK_MATCH_TICKS) break;
            ready.add(new CollectionLogUpdates.Update(update.type, update.items));
            iterator.remove();
        }
        if (!ready.isEmpty()) flat.put("collection_log_updates", new CollectionLogUpdates(ready));
    }

    public synchronized void onLootReceived(Client client, LootReceived event, ItemManager itemManager) {
        if (event.getType() != LootRecordType.NPC
                && event.getType() != LootRecordType.EVENT
                && event.getType() != LootRecordType.PICKPOCKET) return;
        if (event.getName().equals("Loot Chest")) return;
        currentTick = client.getTickCount();

        Map<Integer, Integer> items = new LinkedHashMap<>();
        for (ItemStack item : event.getItems()) {
            int identifier = itemManager.canonicalize(item.getId());
            items.merge(identifier, item.getQuantity(), Integer::sum);
            recentDropTicks.put(identifier, currentTick);
        }
        items.keySet().removeIf(this::reconcileUnlock);
        if (!items.isEmpty()) pendingUpdates.add(new PendingUpdate("drop", items, currentTick));
    }

    /** Returns whether a scan after the matching unlock already includes this acquisition. */
    protected boolean reconcileUnlock(int itemIdentifier) {
        boolean matchedUnlock = false;
        boolean alreadyScanned = false;
        Iterator<PendingUpdate> iterator = pendingUpdates.iterator();
        while (iterator.hasNext()) {
            PendingUpdate update = iterator.next();
            if (update.type.equals("unlock")
                    && update.items.containsKey(itemIdentifier)
                    && currentTick <= update.tick + UNLOCK_MATCH_TICKS) {
                matchedUnlock = true;
                iterator.remove();
            } else if (matchedUnlock
                    && update.type.equals("scan")
                    && update.items.getOrDefault(itemIdentifier, 0) > 0) {
                alreadyScanned = true;
            }
        }
        return alreadyScanned;
    }

    public void onGameStateChanged(GameStateChanged event) {
        GameState gameState = event.getGameState();

        if (gameState != GameState.HOPPING && gameState != GameState.LOGGED_IN) {
            resetTransientState();
        }
    }

    protected synchronized void resetTransientState() {
        automaticCollectionLogRetrieval = false;
        collectionLogNotificationStarted = false;
        collectionLogTransmitTick = -1;
        pendingUpdates.stream()
                .filter(update -> update.type.equals("unlock"))
                .forEach(update -> update.tick = -UNLOCK_MATCH_TICKS - 1);
        recentDropTicks.clear();
    }

    public synchronized void onGameTick(Client client) {
        currentTick = client.getTickCount();
        recentDropTicks.values().removeIf(tick -> currentTick > tick + UNLOCK_MATCH_TICKS);
        if (collectionLogTransmitTick == -1
                || collectionLogTransmitTick + COLLECTION_LOG_TRANSMIT_BUFFER_TICKS >= client.getTickCount()) {
            return;
        }

        collectionLogTransmitTick = -1;
        automaticCollectionLogRetrieval = false;
    }

    public void onScriptPreFired(
            Client client, ScriptPreFired event, CollectionLogItemResolver collectionLogItemResolver) {
        currentTick = client.getTickCount();
        if (event.getScriptId() == COLLECTION_DELAYED_TRANSMIT_SCRIPT) {
            if (isAdventureLogOpen(client)) {
                return;
            }

            collectionLogTransmitTick = client.getTickCount();

            Object[] arguments = event.getScriptEvent().getArguments();
            int itemIdentifier = (int) arguments[1];
            int quantity = (int) arguments[2];

            storeCollectionLogItem(itemIdentifier, quantity);
            return;
        }

        if (event.getScriptId() == ScriptID.NOTIFICATION_START) {
            collectionLogNotificationStarted = true;
            return;
        }

        if (event.getScriptId() != ScriptID.NOTIFICATION_DELAY) {
            return;
        }

        String title = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE);
        boolean notificationStarted = collectionLogNotificationStarted;
        collectionLogNotificationStarted = false;

        if (!notificationStarted || !"Collection log".equalsIgnoreCase(title)) {
            return;
        }

        String message = sanitize(client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN));
        handleNewCollectionLogItem(
                message.substring(COLLECTION_LOG_NOTIFICATION_PREFIX_LENGTH).trim(), collectionLogItemResolver);
    }

    public void onScriptPostFired(Client client, ScriptPostFired event) {
        if (event.getScriptId() != COLLECTION_LOG_SETUP_SCRIPT) {
            return;
        }

        if (isAdventureLogOpen(client)) {
            clearCollectionLogItems();
            return;
        }

        if (automaticCollectionLogRetrieval) {
            return;
        }

        automaticCollectionLogRetrieval = true;
        collectionLogTransmitTick = client.getTickCount();
        client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
        client.runScript(COLLECTION_INITIALIZATION_SCRIPT);
    }

    public void onVarbitChanged(Client client, VarbitChanged event) {
        if (event.getVarbitId() == VarbitID.COLLECTION_POH_HOST_BOOK_OPEN && isAdventureLogOpen(client)) {
            log.debug("Collection log opened from adventure log, clearing stored items to avoid incorrect updates.");
            clearCollectionLogItems();
        }
    }

    public void onChatMessage(Client client, ChatMessage event, CollectionLogItemResolver collectionLogItemResolver) {
        currentTick = client.getTickCount();
        if (event.getType() != ChatMessageType.GAMEMESSAGE
                || client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM) != 1) {
            return;
        }

        Matcher matcher = NEW_ITEM_MESSAGE_PATTERN.matcher(sanitize(event.getMessage()));
        if (matcher.find()) {
            handleNewCollectionLogItem(matcher.group("itemName"), collectionLogItemResolver);
        }
    }

    protected synchronized void handleNewCollectionLogItem(
            String itemName, CollectionLogItemResolver collectionLogItemResolver) {
        if (IGNORED_NOTIFICATION_ITEMS.contains(itemName)) {
            log.debug("Ignoring collection log item with non-unique name: {}", itemName);
            return;
        }

        Integer itemIdentifier = collectionLogItemResolver.findItemIdentifier(itemName);
        if (itemIdentifier == null) {
            log.debug("Failed to find item ID for: {}", itemName);
            return;
        }

        if (!notifiedItems.add(itemIdentifier)) return;
        if (recentDropTicks.containsKey(itemIdentifier)
                && currentTick <= recentDropTicks.get(itemIdentifier) + UNLOCK_MATCH_TICKS) return;
        pendingUpdates.add(new PendingUpdate("unlock", Map.of(itemIdentifier, 1), currentTick));
    }

    protected boolean isAdventureLogOpen(Client client) {
        return client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
    }

    protected String sanitize(String value) {
        if (value == null || value.isEmpty()) return "";
        return Text.removeTags(value.replace("<br>", "\n"))
                .replace('\u00A0', ' ')
                .trim();
    }
}
