package gimhub.achievement;

import gimhub.APISerializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Client;

public class DiariesProgress implements APISerializable {
    private static final Integer[][] DIARY_VARPS = {
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
    private static final List<Integer> diaryVarpIDsInAPIOrder =
            Arrays.stream(DIARY_VARPS).flatMap(Arrays::stream).collect(Collectors.toUnmodifiableList());

    private static final Integer[][] DIARY_VARBITS = {
        {3566, 3567, 3568, 3569, 3570, 3571, 3572, 3573, 3574, 3575}, // Karamja Easy
        {
            3579, 3580, 3581, 3582, 3583, 3584, 3596, 3586, 3587, 3588, 3589, 3590, 3591, 3592, 3593, 3594, 3595, 3597,
            3585
        }, // Karamja Medium
        {3600, 3601, 3602, 3603, 3604, 3605, 3606, 3607, 3608, 3609} // Karamja Hard
    };
    private static final List<Integer> diaryVarbitIDsInAPIOrder =
            Arrays.stream(DIARY_VARBITS).flatMap(Arrays::stream).collect(Collectors.toUnmodifiableList());

    private final ArrayList<Integer> diaryVarValues;

    public DiariesProgress(Client client) {
        diaryVarValues = new ArrayList<>();
        for (Integer varpID : diaryVarpIDsInAPIOrder) {
            diaryVarValues.add(client.getVarpValue(varpID));
        }

        for (Integer varbitID : diaryVarbitIDsInAPIOrder) {
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
