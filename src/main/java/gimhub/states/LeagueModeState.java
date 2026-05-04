package gimhub.states;

import gimhub.APISerializable;
import gimhub.activity.LeagueMode;
import net.runelite.client.config.RuneScapeProfileType;

public class LeagueModeState implements APISerializable {
    private final boolean enabled;

    public LeagueModeState(RuneScapeProfileType profileType) {
        this.enabled = LeagueMode.isEnabled(profileType);
    }

    @Override
    public Object serialize() {
        return new int[] {enabled ? 1 : 0};
    }

    @Override
    public boolean isBaselineProperty() {
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof LeagueModeState)) return false;

        LeagueModeState otherLeagueModeState = (LeagueModeState) other;
        return enabled == otherLeagueModeState.enabled;
    }
}
