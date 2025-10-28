package gimhub;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DataState {
    private final AtomicReference<ConsumableState> state = new AtomicReference<>();
    private ConsumableState previousState;
    private final String key;
    private final boolean transactionBased;

    DataState(String key, boolean transactionBased) {
        this.key = key;
        this.transactionBased = transactionBased;
    }

    public void update(ConsumableState o) {
        if (!o.equals(previousState)) {
            previousState = o;

            if (!transactionBased) {
                state.set(o);
            }
        }
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

    public void commitTransaction() {
        state.set(previousState);
    }
}
