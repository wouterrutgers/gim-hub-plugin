package gimhub.activity;

import gimhub.APISerializable;
import java.util.Map;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.coords.WorldPoint;

public class ActivityRepository {
    public Resources resources = null;
    public Skills skills = null;
    public WorldLocation position = null;
    public Interaction interacting = null;

    public void updateResources(Client client) {
        resources = new Resources(client);
    }

    public void updateSkills(Client client) {
        skills = new Skills(client);
    }

    public void updateLocation(Client client) {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        final int worldViewID = player.getWorldView().getId();
        final boolean isOnBoat = worldViewID != -1;
        WorldPoint location = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
        if (isOnBoat) {
            WorldEntity worldEntity =
                    client.getTopLevelWorldView().worldEntities().byIndex(worldViewID);
            location = WorldPoint.fromLocalInstance(client, worldEntity.getLocalLocation());
        }

        position = new WorldLocation(location, isOnBoat);
    }

    public void updateInteracting(Client client) {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        Actor actor = player.getInteracting();
        if (actor == null) return;

        interacting = new Interaction(actor, client);
    }

    public void flatten(Map<String, APISerializable> flat) {
        flat.put("stats", resources);
        flat.put("skills", skills);
        flat.put("coordinates", position);
        flat.put("interacting", interacting);
    }
}
