package gimhub;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DataState {
    private final AtomicReference<ConsumableState> state = new AtomicReference<>();
    private ConsumableState previousState;
    private final String key;

    DataState(String key) {
        this.key = key;
    }

    public void update(ConsumableState o) {
        if(o.equals(previousState)) {
            return;
        }

        previousState = o;
        state.set(previousState);
    }

    public void consumeState(Map<String, Object> output) {
        final ConsumableState consumedState = state.getAndSet(null);
        final Object whoIsUpdating = output.get("name");
        if (consumedState != null) {
            final String whoOwnsThis = consumedState.whoOwnsThis();
            if (whoOwnsThis != null && whoOwnsThis.equals(whoIsUpdating)) {
                Object c = consumedState.get();
                output.put(key, c);
            }
        }
    }

    public ConsumableState mostRecentState() {
        return this.previousState;
    }

    public void restoreState() {
        state.compareAndSet(null, previousState);
    }
}
