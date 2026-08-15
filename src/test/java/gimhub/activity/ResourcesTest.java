package gimhub.activity;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

public class ResourcesTest {
    @Test
    public void serializesAllResourceValuesInApiOrder() {
        Client client = mock(Client.class);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(82);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(45);
        when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(77);
        when(client.getEnergy()).thenReturn(64);
        when(client.getWorld()).thenReturn(420);
        when(client.getVarpValue(VarPlayerID.SA_ENERGY)).thenReturn(750);

        assertArrayEquals(new int[] {82, 99, 45, 77, 64, 100, 420, 75}, (int[]) new Resources(client).serialize());
    }
}
