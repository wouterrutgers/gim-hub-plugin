package gimhub.activity;

import gimhub.APISerializable;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Skill;

public class Skills implements APISerializable {
    private final Map<String, Integer> skillXpMap;

    public Skills(Client client) {
        this.skillXpMap = new HashMap<>();
        for (Skill skill : Skill.values()) {
            skillXpMap.put(skill.getName(), client.getSkillExperience(skill));
        }
    }

    @Override
    public Object serialize() {
        return new int[] {
            skillXpMap.get("Agility"),
            skillXpMap.get("Attack"),
            skillXpMap.get("Construction"),
            skillXpMap.get("Cooking"),
            skillXpMap.get("Crafting"),
            skillXpMap.get("Defence"),
            skillXpMap.get("Farming"),
            skillXpMap.get("Firemaking"),
            skillXpMap.get("Fishing"),
            skillXpMap.get("Fletching"),
            skillXpMap.get("Herblore"),
            skillXpMap.get("Hitpoints"),
            skillXpMap.get("Hunter"),
            skillXpMap.get("Magic"),
            skillXpMap.get("Mining"),
            skillXpMap.get("Prayer"),
            skillXpMap.get("Ranged"),
            skillXpMap.get("Runecraft"),
            skillXpMap.get("Slayer"),
            skillXpMap.get("Smithing"),
            skillXpMap.get("Strength"),
            skillXpMap.get("Thieving"),
            skillXpMap.get("Woodcutting"),
            skillXpMap.get("Sailing"),
        };
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Skills)) return false;

        Skills other = (Skills) o;
        return skillXpMap.equals(other.skillXpMap);
    }
}
