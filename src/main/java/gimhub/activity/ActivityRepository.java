package gimhub.activity;

import gimhub.APIConsumable;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.coords.WorldPoint;

public class ActivityRepository {
    private static class SynchronizedState {
        public final String ownedPlayer;

        SynchronizedState(String ownedPlayer) {
            this.ownedPlayer = ownedPlayer;
        }

        public final APIConsumable<Resources> resources = new APIConsumable<>("stats", false);
        public final APIConsumable<Skills> skills = new APIConsumable<>("skills", false);
        public final APIConsumable<WorldLocation> position = new APIConsumable<>("coordinates", false);
        public final APIConsumable<Interaction> interacting = new APIConsumable<>("interacting", false);

        public Iterable<APIConsumable<?>> getAllContainers() {
            final ArrayList<APIConsumable<?>> result = new ArrayList<>();

            result.add(resources);
            result.add(skills);
            result.add(position);
            result.add(interacting);

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

    public void updateResources(Client client) {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        final String playerName = client.getLocalPlayer().getName();

        SynchronizedState state = safeGetOrResetIfNewPlayer(playerName);
        if (state == null) return;

        state.resources.update(new Resources(client));
    }

    public void updateSkills(Client client) {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        final String playerName = client.getLocalPlayer().getName();

        SynchronizedState state = safeGetOrResetIfNewPlayer(playerName);
        if (state == null) return;

        state.skills.update(new Skills(client));
    }

    public void updateLocation(Client client) {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        final String playerName = client.getLocalPlayer().getName();

        SynchronizedState state = safeGetOrResetIfNewPlayer(playerName);
        if (state == null) return;

        final int worldViewID = player.getWorldView().getId();
        final boolean isOnBoat = worldViewID != -1;
        WorldPoint location = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
        if (isOnBoat) {
            WorldEntity worldEntity =
                    client.getTopLevelWorldView().worldEntities().byIndex(worldViewID);
            location = WorldPoint.fromLocalInstance(client, worldEntity.getLocalLocation());
        }

        state.position.update(new WorldLocation(location, isOnBoat));
    }

    public void updateInteracting(Client client) {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        final String playerName = player.getName();

        SynchronizedState state = safeGetOrResetIfNewPlayer(playerName);
        if (state == null) return;

        Actor actor = player.getInteracting();
        if (actor == null) return;

        state.interacting.update(new Interaction(actor, client));
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
