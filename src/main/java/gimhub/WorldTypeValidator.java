package gimhub;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.WorldType;

public class WorldTypeValidator {
    private static final Set<WorldType> INVALID_WORLD_TYPES = EnumSet.of(
            WorldType.SEASONAL,
            WorldType.DEADMAN,
            WorldType.TOURNAMENT_WORLD,
            WorldType.PVP_ARENA,
            WorldType.BETA_WORLD,
            WorldType.QUEST_SPEEDRUNNING);

    public static boolean isValidWorldType(EnumSet<WorldType> worldTypes) {
        for (WorldType worldType : worldTypes) {
            if (INVALID_WORLD_TYPES.contains(worldType)) {
                return false;
            }
        }

        return true;
    }
}
