package gimhub.achievement;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

public class CombatAchievementsProgressTest {
    @Test
    public void serializesTaskIdsAcrossBitfieldBoundaries() {
        Client client = mock(Client.class);
        when(client.getVarpValue(VarPlayerID.CA_TASK_COMPLETED_0)).thenReturn(Integer.MIN_VALUE | 1);
        when(client.getVarpValue(VarPlayerID.CA_TASK_COMPLETED_1)).thenReturn(1);
        when(client.getVarpValue(VarPlayerID.CA_TASK_COMPLETED_13)).thenReturn(1);
        when(client.getVarpValue(VarPlayerID.CA_TASK_COMPLETED_20)).thenReturn(1 << 14);

        assertEquals(List.of(0, 31, 32, 416, 654), new CombatAchievementsProgress(client).serialize());
    }
}
