package men.groupiron;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Singleton
public class PlayerDataService {
    // item id -> quantity
    private final Map<Integer, Integer> clogItems = new HashMap<>();

    public synchronized void storeClogItem(int itemId, int quantity) {
        if (quantity <= 0) return;
        clogItems.put(itemId, quantity);
    }

    public synchronized void writeClogItems(Map<String, Object> updates) {
        if (clogItems.isEmpty()) return;
        // Send collection log entries as an id->quantity map under 'collection_log'
        updates.put("collection_log", new HashMap<>(clogItems));
    }

    public synchronized void clearClogItems() {
        clogItems.clear();
    }

    @Subscribe
    public synchronized void onGameStateChanged(GameStateChanged ev) {
        if (ev.getGameState() != GameState.LOGGED_IN) {
            clogItems.clear();
        }
    }

    public synchronized Map<Integer, Integer> snapshotItems() {
        return Collections.unmodifiableMap(new HashMap<>(clogItems));
    }
}
