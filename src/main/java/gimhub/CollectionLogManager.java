package gimhub;

import gimhub.items.ItemsUnordered;
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
import net.runelite.client.util.Text;

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

    private ItemsUnordered collectionLogItems = null;
    private ItemsUnordered flattenedCollectionLogItems = null;
    private boolean collectionLogItemsDirty = false;
    private boolean automaticCollectionLogRetrieval = false;
    private boolean collectionLogNotificationStarted = false;
    private int collectionLogTransmitTick = -1;

    public synchronized void storeCollectionLogItem(int itemIdentifier, int quantity) {
        if (collectionLogItems == null) {
            collectionLogItems = new ItemsUnordered();
        }

        if (quantity <= 0) return;
        collectionLogItems.getItemsQuantityByID().put(itemIdentifier, quantity);
        collectionLogItemsDirty = true;
    }

    public synchronized void clearCollectionLogItems() {
        collectionLogItems = null;
        flattenedCollectionLogItems = null;
        collectionLogItemsDirty = false;
    }

    public synchronized void flatten(Map<String, APISerializable> flat) {
        if (collectionLogItemsDirty && !automaticCollectionLogRetrieval) {
            flattenedCollectionLogItems = new ItemsUnordered(collectionLogItems);
            collectionLogItemsDirty = false;
        }

        flat.put("collection_log_v2", flattenedCollectionLogItems);
    }

    public void onGameStateChanged(GameStateChanged event) {
        GameState gameState = event.getGameState();

        if (gameState != GameState.HOPPING && gameState != GameState.LOGGED_IN) {
            resetTransientState();
        }
    }

    protected void resetTransientState() {
        automaticCollectionLogRetrieval = false;
        collectionLogNotificationStarted = false;
        collectionLogTransmitTick = -1;
    }

    public void onGameTick(Client client) {
        if (collectionLogTransmitTick == -1
                || collectionLogTransmitTick + COLLECTION_LOG_TRANSMIT_BUFFER_TICKS >= client.getTickCount()) {
            return;
        }

        collectionLogTransmitTick = -1;
        automaticCollectionLogRetrieval = false;
    }

    public void onScriptPreFired(
            Client client, ScriptPreFired event, CollectionLogItemResolver collectionLogItemResolver) {
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
        if (event.getType() != ChatMessageType.GAMEMESSAGE
                || client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM) != 1) {
            return;
        }

        Matcher matcher = NEW_ITEM_MESSAGE_PATTERN.matcher(event.getMessage());
        if (matcher.find()) {
            handleNewCollectionLogItem(matcher.group("itemName"), collectionLogItemResolver);
        }
    }

    protected void handleNewCollectionLogItem(String itemName, CollectionLogItemResolver collectionLogItemResolver) {
        if (IGNORED_NOTIFICATION_ITEMS.contains(itemName)) {
            log.debug("Ignoring collection log item with non-unique name: {}", itemName);
            return;
        }

        Integer itemIdentifier = collectionLogItemResolver.findItemIdentifier(itemName);
        if (itemIdentifier == null) {
            log.debug("Failed to find item ID for: {}", itemName);
            return;
        }

        storeCollectionLogItem(itemIdentifier, 1);
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
