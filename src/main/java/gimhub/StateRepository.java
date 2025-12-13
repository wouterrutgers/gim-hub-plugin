package gimhub;

import java.util.Map;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class StateRepository {
    @Getter
    private final DataState resources = new DataState("stats");

    @Getter
    private final DataState skills = new DataState("skills");

    @Getter
    private final DataState position = new DataState("coordinates");

    @Getter
    private final DataState interacting = new DataState("interacting");

    public void consumeAllStates(Map<String, Object> updates) {
        resources.consumeState(updates);
        skills.consumeState(updates);
        position.consumeState(updates);
        interacting.consumeState(updates);
    }

    public void restoreAllStates() {
        resources.restoreState();
        skills.restoreState();
        position.restoreState();
        interacting.restoreState();
    }
}
