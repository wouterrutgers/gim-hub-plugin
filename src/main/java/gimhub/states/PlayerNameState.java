package gimhub.states;

import gimhub.APISerializable;

public class PlayerNameState implements APISerializable {
    private final String name;

    public PlayerNameState(String name) {
        this.name = name;
    }

    @Override
    public Object serialize() {
        return name;
    }

    @Override
    public boolean isBaselineProperty() {
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof PlayerNameState)) return false;

        PlayerNameState otherPlayerNameState = (PlayerNameState) other;
        return name.equals(otherPlayerNameState.name);
    }
}
