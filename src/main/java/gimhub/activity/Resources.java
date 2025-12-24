package gimhub.activity;

import gimhub.APISerializable;
import net.runelite.api.Client;
import net.runelite.api.Skill;

public class Resources implements APISerializable {
    private static class CurrentMax {
        private final int current;
        private final int max;

        CurrentMax(int current, int max) {
            this.current = current;
            this.max = max;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof CurrentMax)) return false;

            CurrentMax other = (CurrentMax) o;

            return current == other.current && max == other.max;
        }
    }

    private final CurrentMax hitpoints;
    private final CurrentMax prayer;
    private final CurrentMax energy;
    private final int world;

    Resources(Client client) {
        this.hitpoints =
                new CurrentMax(client.getBoostedSkillLevel(Skill.HITPOINTS), client.getRealSkillLevel(Skill.HITPOINTS));
        this.prayer = new CurrentMax(client.getBoostedSkillLevel(Skill.PRAYER), client.getRealSkillLevel(Skill.PRAYER));
        this.energy = new CurrentMax(client.getEnergy(), 100);
        this.world = client.getWorld();
    }

    @Override
    public Object serialize() {
        return new int[] {
            hitpoints.current, hitpoints.max, prayer.current, prayer.max, energy.current, energy.max, world
        };
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Resources)) return false;

        Resources other = (Resources) o;

        return other.world == world
                && other.hitpoints.equals(hitpoints)
                && other.prayer.equals(prayer)
                && other.energy.equals(energy);
    }
}
