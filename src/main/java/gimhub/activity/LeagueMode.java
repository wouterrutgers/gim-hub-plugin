package gimhub.activity;

import net.runelite.client.config.RuneScapeProfileType;

public class LeagueMode {
    public static boolean isEnabled(RuneScapeProfileType profileType) {
        return profileType.name().endsWith("_LEAGUE");
    }
}
