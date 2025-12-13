package gimhub.achievement;

import gimhub.APISerializable;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.Quest;

public class QuestsProgress implements APISerializable {
    private final Map<Integer, net.runelite.api.QuestState> questStateMap;

    public QuestsProgress(Client client) {
        this.questStateMap = new HashMap<>();
        for (Quest quest : Quest.values()) {
            questStateMap.put(quest.getId(), quest.getState(client));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof QuestsProgress)) return false;

        QuestsProgress other = (QuestsProgress) o;

        return other.questStateMap.equals(questStateMap);
    }

    @Override
    public Object serialize() {
        List<Integer> result = new ArrayList<>(questStateMap.size());
        for (Integer questId : AchievementUtilities.sortedQuestIDs) {
            result.add(questStateMap.get(questId).ordinal());
        }

        return result;
    }
}
