package gimhub;

import net.runelite.api.Client;

public class AchievementDiaryState implements ConsumableState {
    private final String playerName;

    private static final int[][] DIARY_VARBITS = {
        {3566, 3567, 3568, 3569, 3570, 3571, 3572, 3573, 3574, 3575}, // Karamja Easy
        {
            3579, 3580, 3581, 3582, 3583, 3584, 3596, 3586, 3587, 3588, 3589, 3590, 3591, 3592, 3593, 3594, 3595, 3597,
            3585
        }, // Karamja Medium
        {3600, 3601, 3602, 3603, 3604, 3605, 3606, 3607, 3608, 3609} // Karamja Hard
    };

    private static final int[][] DIARY_VARPS = {
        {1196, 1197}, // Ardougne
        {1198, 1199}, // Desert
        {1186, 1187}, // Falador
        {1184, 1185}, // Fremennik
        {1178, 1179}, // Kandarin
        {1200}, // Karamja Elite
        {2085, 2086}, // Kourend & Kebos
        {1194, 1195}, // Lumbridge & Draynor
        {1180, 1181}, // Morytania
        {1176, 1177}, // Varrock
        {1182, 1183}, // Western Provinces
        {1192, 1193} // Wilderness
    };

    private final int[] diaryVarValues;

    public AchievementDiaryState(String playerName, Client client) {
        this.playerName = playerName;

        int totalLength = 0;
        for (int[] group : DIARY_VARPS) {
            totalLength += group.length;
        }
        for (int[] group : DIARY_VARBITS) {
            totalLength += group.length;
        }

        diaryVarValues = new int[totalLength];
        int index = 0;

        for (int[] group : DIARY_VARPS) {
            for (int varp : group) {
                diaryVarValues[index++] = client.getVarpValue(varp);
            }
        }

        for (int[] group : DIARY_VARBITS) {
            for (int varbit : group) {
                diaryVarValues[index++] = client.getVarbitValue(varbit);
            }
        }
    }

    @Override
    public Object get() {
        return diaryVarValues;
    }

    @Override
    public String whoOwnsThis() {
        return playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AchievementDiaryState)) return false;

        AchievementDiaryState other = (AchievementDiaryState) o;
        for (int i = 0; i < diaryVarValues.length; ++i) {
            if (diaryVarValues[i] != other.diaryVarValues[i]) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int value : diaryVarValues) {
            result = 31 * result + value;
        }

        return result;
    }
}
