package gimhub.achievement;

import gimhub.APISerializable;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;

public class CombatAchievementsProgress implements APISerializable {
    private static final int[] TASK_VARPS = {
        VarPlayerID.CA_TASK_COMPLETED_0,
        VarPlayerID.CA_TASK_COMPLETED_1,
        VarPlayerID.CA_TASK_COMPLETED_2,
        VarPlayerID.CA_TASK_COMPLETED_3,
        VarPlayerID.CA_TASK_COMPLETED_4,
        VarPlayerID.CA_TASK_COMPLETED_5,
        VarPlayerID.CA_TASK_COMPLETED_6,
        VarPlayerID.CA_TASK_COMPLETED_7,
        VarPlayerID.CA_TASK_COMPLETED_8,
        VarPlayerID.CA_TASK_COMPLETED_9,
        VarPlayerID.CA_TASK_COMPLETED_10,
        VarPlayerID.CA_TASK_COMPLETED_11,
        VarPlayerID.CA_TASK_COMPLETED_12,
        VarPlayerID.CA_TASK_COMPLETED_13,
        VarPlayerID.CA_TASK_COMPLETED_14,
        VarPlayerID.CA_TASK_COMPLETED_15,
        VarPlayerID.CA_TASK_COMPLETED_16,
        VarPlayerID.CA_TASK_COMPLETED_17,
        VarPlayerID.CA_TASK_COMPLETED_18,
        VarPlayerID.CA_TASK_COMPLETED_19,
        VarPlayerID.CA_TASK_COMPLETED_20
    };

    private final List<Integer> completedTasks = new ArrayList<>();

    public CombatAchievementsProgress(Client client) {
        for (int index = 0; index < TASK_VARPS.length; index++) {
            int value = client.getVarpValue(TASK_VARPS[index]);
            for (int bit = 0; bit < Integer.SIZE; bit++) {
                if ((value & (1 << bit)) != 0) {
                    completedTasks.add(index * Integer.SIZE + bit);
                }
            }
        }
    }

    @Override
    public Object serialize() {
        return completedTasks;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CombatAchievementsProgress
                && completedTasks.equals(((CombatAchievementsProgress) other).completedTasks);
    }

    @Override
    public int hashCode() {
        return completedTasks.hashCode();
    }
}
