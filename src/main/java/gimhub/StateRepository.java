package gimhub;

import java.util.Map;
import javax.inject.Singleton;
import lombok.Getter;

@Singleton
public class StateRepository {
    @Getter
    private final DataState resources = new DataState("stats");

    @Getter
    private final DataState skills = new DataState("skills");

    @Getter
    private final DataState quests = new DataState("quests");

    @Getter
    private final DataState position = new DataState("coordinates");

    @Getter
    private final DataState interacting = new DataState("interacting");

    @Getter
    private final DataState achievementDiary = new DataState("diary_vars");

    public void consumeAllStates(Map<String, Object> updates) {
        resources.consumeState(updates);
        skills.consumeState(updates);
        quests.consumeState(updates);
        position.consumeState(updates);
        interacting.consumeState(updates);
        achievementDiary.consumeState(updates);
    }

    public void restoreAllStates() {
        resources.restoreState();
        skills.restoreState();
        quests.restoreState();
        position.restoreState();
        interacting.restoreState();
        achievementDiary.restoreState();
    }
}
