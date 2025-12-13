package gimhub.achievement;

import gimhub.APISerializable;
import java.util.ArrayList;
import net.runelite.api.Client;

public class DiariesProgress implements APISerializable {
    private final ArrayList<Integer> diaryVarValues;

    public DiariesProgress(Client client) {
        int totalLength = AchievementUtilities.diaryVarpIDsInAPIOrder.size()
                + AchievementUtilities.diaryVarbitIDsInAPIOrder.size();

        diaryVarValues = new ArrayList<>();
        for (Integer varpID : AchievementUtilities.diaryVarpIDsInAPIOrder) {
            diaryVarValues.add(client.getVarpValue(varpID));
        }

        for (Integer varbitID : AchievementUtilities.diaryVarbitIDsInAPIOrder) {
            diaryVarValues.add(client.getVarbitValue(varbitID));
        }
    }

    @Override
    public Object serialize() {
        return diaryVarValues;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DiariesProgress)) return false;

        DiariesProgress other = (DiariesProgress) o;
        return other.diaryVarValues.equals(diaryVarValues);
    }
}
