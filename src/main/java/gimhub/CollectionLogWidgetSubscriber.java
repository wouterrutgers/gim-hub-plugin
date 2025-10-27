package gimhub;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
@Singleton
public class CollectionLogWidgetSubscriber {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private CollectionLogManager collectionLogManager;

    private boolean searchTriggered = false;
    private int searchTriggeredTick = -1;

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        GameState s = e.getGameState();
        if (s != GameState.HOPPING && s != GameState.LOGGED_IN) {
            searchTriggered = false;
            searchTriggeredTick = -1;
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (searchTriggeredTick != -1) {
            int currentTick = client.getTickCount();

            if (currentTick - searchTriggeredTick >= 500) {
                searchTriggered = false;
                searchTriggeredTick = -1;
            }
        }
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired pre) {
        // Script 4100 fires when collection log items are enumerated via search
        if (pre.getScriptId() == 4100) {
            // Arguments: [widgetId, itemId, qty]
            Object[] args = pre.getScriptEvent().getArguments();
            if (args != null && args.length >= 3) {
                try {
                    int itemId = (int) args[1];
                    int quantity = (int) args[2];
                    collectionLogManager.storeClogItem(itemId, quantity);
                } catch (Exception ignored) {
                    //
                }
            }
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired post) {
        final int COLLECTION_LOG_SETUP = 7797;
        if (post.getScriptId() == COLLECTION_LOG_SETUP) {
            if (searchTriggered) return;
            boolean isAdventureLog = client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
            if (isAdventureLog) return;

            searchTriggered = true;
            searchTriggeredTick = client.getTickCount();
            client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
            final int COLLECTION_INIT = 2240;
            client.runScript(COLLECTION_INIT);
        }
    }
}
