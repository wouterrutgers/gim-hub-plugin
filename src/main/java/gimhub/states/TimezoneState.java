package gimhub.states;

import gimhub.APISerializable;
import java.time.ZoneId;

public class TimezoneState implements APISerializable {
    private final String timezone;

    public TimezoneState() {
        this.timezone = ZoneId.systemDefault().getId();
    }

    @Override
    public Object serialize() {
        return timezone;
    }

    @Override
    public boolean isBaselineProperty() {
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof TimezoneState)) return false;

        TimezoneState otherState = (TimezoneState) other;
        return timezone.equals(otherState.timezone);
    }
}
