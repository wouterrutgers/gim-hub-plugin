package gimhub.activity;

import gimhub.APISerializable;
import net.runelite.api.coords.WorldPoint;

public class WorldLocation implements APISerializable {
    private final int x;
    private final int y;
    private final int plane;
    private final boolean isOnBoat;

    WorldLocation(WorldPoint worldPoint, boolean isOnBoat) {
        this.x = worldPoint.getX();
        this.y = worldPoint.getY();
        this.plane = worldPoint.getPlane();
        this.isOnBoat = isOnBoat;
    }

    @Override
    public Object serialize() {
        return new int[] {x, y, plane, isOnBoat ? 1 : 0};
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof WorldLocation)) return false;

        WorldLocation other = (WorldLocation) o;

        return (x == other.x) && (y == other.y) && (plane == other.plane) && (isOnBoat == other.isOnBoat);
    }

    @Override
    public String toString() {
        return String.format("{ x: %d, y: %d, plane: %d, isOnBoat: %b }", x, y, plane, isOnBoat);
    }
}
