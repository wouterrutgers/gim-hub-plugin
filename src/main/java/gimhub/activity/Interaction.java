package gimhub.activity;

import gimhub.APISerializable;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;

public class Interaction implements APISerializable {
    private final String name;
    private final int scale;
    private final int ratio;
    private final WorldLocation location;

    private final transient int tick;

    private static String sanitizeText(String text) {
        if (text == null) {
            return "";
        }

        return text.replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public Interaction(Actor actor, Client client) {
        this.name = sanitizeText(actor.getName());
        this.scale = actor.getHealthScale();
        this.ratio = actor.getHealthRatio();

        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, actor.getLocalLocation());
        this.location = new WorldLocation(worldPoint, false);

        this.tick = client.getTickCount();
    }

    @Override
    public Object serialize() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Interaction)) return false;

        Interaction other = (Interaction) o;

        return tick == other.tick
                && location.equals(other.location)
                && scale == other.scale
                && ratio == other.ratio
                && name.equals(other.name);
    }
}
