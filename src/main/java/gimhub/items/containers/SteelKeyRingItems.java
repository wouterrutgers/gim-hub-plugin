package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.game.ItemManager;

public class SteelKeyRingItems implements TrackedItemContainer {
    protected static final int KEYRING_BUILD = 861;
    protected final Map<Integer, Integer> seen = new HashMap<>();
    protected int total = -1;
    protected ItemsUnordered items;
    protected boolean pending;

    @Override
    public String key() {
        return "steel_key_ring";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onScriptPreFired(Client client, ScriptPreFired event) {
        if (event.getScriptId() != KEYRING_BUILD || event.getScriptEvent() == null) {
            return;
        }
        Object[] arguments = event.getScriptEvent().getArguments();
        if (arguments.length != 5) {
            return;
        }
        int count = (Integer) arguments[4];
        if (count != total) {
            seen.clear();
            total = count;
        }
        for (int index = 1; index <= 3; index++) {
            int identifier = (Integer) arguments[index];
            if (identifier > 0) {
                seen.put(identifier, 1);
            }
        }
        pending = true;
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (!pending) {
            return;
        }
        pending = false;
        // shortcut: visit every key to complete a snapshot, until the client exposes the whole ring.
        if (seen.size() == total) {
            items = new ItemsUnordered(seen, itemManager);
        }
    }
}
