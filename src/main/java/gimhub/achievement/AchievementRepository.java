package gimhub.achievement;

import gimhub.APIConsumable;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import net.runelite.api.Client;

public class AchievementRepository {
    private static class SynchronizedState {
        public final String ownedPlayer;

        SynchronizedState(String ownedPlayer) {
            this.ownedPlayer = ownedPlayer;
        }

        public final APIConsumable<QuestsProgress> quests = new APIConsumable<>("quests", false);
        public final APIConsumable<DiariesProgress> diaries = new APIConsumable<>("diary_vars", false);

        public Iterable<APIConsumable<?>> getAllContainers() {
            final ArrayList<APIConsumable<?>> result = new ArrayList<>();

            result.add(quests);
            result.add(diaries);

            return result;
        }
    }

    private final AtomicReference<SynchronizedState> stateRef = new AtomicReference<>();

    @Nullable private SynchronizedState safeGetOrResetIfNewPlayer(String player) {
        SynchronizedState state = stateRef.get();
        if (state != null && state.ownedPlayer.equals(player)) {
            return state;
        }

        SynchronizedState newState = new SynchronizedState(player);
        if (!stateRef.compareAndSet(state, newState)) {
            return null;
        }

        return newState;
    }

    public void update(Client client) {
        final String player = client.getLocalPlayer().getName();

        SynchronizedState state = safeGetOrResetIfNewPlayer(player);
        if (state == null) return;

        state.quests.update(new QuestsProgress(client));
        state.diaries.update(new DiariesProgress(client));
    }

    public void consumeAllStates(String player, Map<String, Object> updates) {
        SynchronizedState state = safeGetOrResetIfNewPlayer(player);
        if (state == null) return;

        for (final APIConsumable<?> container : state.getAllContainers()) {
            container.consumeState(updates);
        }
    }

    public void restoreAllStates(String player) {
        SynchronizedState state = safeGetOrResetIfNewPlayer(player);
        if (state == null) return;

        for (final APIConsumable<?> container : state.getAllContainers()) {
            container.restoreState();
        }
    }
}
