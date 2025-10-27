package gimhub;

import java.util.Map;
import javax.inject.Singleton;
import lombok.Getter;

@Singleton
public class StateRepository {
    @Getter
    private final DataState inventory = new DataState("inventory", false);

    @Getter
    private final DataState bank = new DataState("bank", false);

    @Getter
    private final DataState equipment = new DataState("equipment", false);

    @Getter
    private final DataState sharedBank = new DataState("shared_bank", true);

    @Getter
    private final DataState resources = new DataState("stats", false);

    @Getter
    private final DataState skills = new DataState("skills", false);

    @Getter
    private final DataState quests = new DataState("quests", false);

    @Getter
    private final DataState position = new DataState("coordinates", false);

    @Getter
    private final DataState runePouch = new DataState("rune_pouch", false);

    @Getter
    private final DataState quiver = new DataState("quiver", false);

    @Getter
    private final DataState interacting = new DataState("interacting", false);

    @Getter
    private final DataState seedVault = new DataState("seed_vault", false);

    @Getter
    private final DataState achievementDiary = new DataState("diary_vars", false);

    @Getter
    private final DepositedItems deposited = new DepositedItems();

    public void consumeAllStates(Map<String, Object> updates) {
        inventory.consumeState(updates);
        bank.consumeState(updates);
        equipment.consumeState(updates);
        sharedBank.consumeState(updates);
        resources.consumeState(updates);
        skills.consumeState(updates);
        quests.consumeState(updates);
        position.consumeState(updates);
        runePouch.consumeState(updates);
        quiver.consumeState(updates);
        interacting.consumeState(updates);
        seedVault.consumeState(updates);
        achievementDiary.consumeState(updates);
        deposited.consumeState(updates);
    }

    public void restoreAllStates() {
        inventory.restoreState();
        bank.restoreState();
        equipment.restoreState();
        sharedBank.restoreState();
        resources.restoreState();
        skills.restoreState();
        quests.restoreState();
        position.restoreState();
        runePouch.restoreState();
        quiver.restoreState();
        interacting.restoreState();
        seedVault.restoreState();
        achievementDiary.restoreState();
        deposited.restoreState();
    }
}
