package gimhub;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread-safe writing and reading of data that can be serialized to our API, up to one write-thread (the RuneLite
 * client) and one read-thread (the thread performing internet requests). State is consumed on read, but this can be
 * rolled back without overwriting newer data that came in on the write-thread. The underlying data is not synchronized
 * and should not be mutated.
 */
@Slf4j
public class APIConsumable<S extends APISerializable> {
    private final AtomicReference<S> state = new AtomicReference<>();

    @Getter
    private S previousState;

    private final String key;
    private final boolean transactionBased;

    public APIConsumable(String key, boolean transactionBased) {
        this.key = key;
        this.transactionBased = transactionBased;
    }

    public void update(S o) {
        if (o.equals(previousState)) {
            return;
        }

        // It's important to set previousState first, so that restoreState does not clobber this method.
        previousState = o;

        if (!transactionBased) {
            state.set(previousState);
        }
    }

    public void commitTransaction() {
        if (!transactionBased) {
            return;
        }

        state.set(previousState);
    }

    public void reset() {
        // It's important to set previousState first, so that restoreState does not clobber this method.
        previousState = null;
        state.set(null);
    }

    public void consumeState(Map<String, Object> output) {
        final S consumedState = state.getAndSet(null);
        if (consumedState == null) {
            return;
        }

        output.put(key, consumedState.serialize());
    }

    public void restoreState() {
        state.compareAndSet(null, previousState);
    }
}
