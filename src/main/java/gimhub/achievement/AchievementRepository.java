package gimhub.achievement;

import gimhub.APISerializable;
import java.util.Map;
import net.runelite.api.Client;

public class AchievementRepository {
    public QuestsProgress quests = null;
    public DiariesProgress diaries = null;
    public CombatAchievementsProgress combatAchievements = null;

    public void update(Client client) {
        quests = new QuestsProgress(client);
        diaries = new DiariesProgress(client);
        combatAchievements = new CombatAchievementsProgress(client);
    }

    public void flatten(Map<String, APISerializable> flat) {
        flat.put("quests", quests);
        flat.put("diary_vars", diaries);
        flat.put("combat_achievements", combatAchievements);
    }
}
