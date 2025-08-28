package gimhub;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.gameval.VarbitID;

public class RunePouchState implements ConsumableState {
    private final ItemContainerItem rune1;
    private final ItemContainerItem rune2;
    private final ItemContainerItem rune3;
    private final ItemContainerItem rune4;
    private final transient String playerName;

    public RunePouchState(String playerName, Client client) {
        this.playerName = playerName;
        final EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
        rune1 = itemForRune(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_1), client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1), runepouchEnum);
        rune2 = itemForRune(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_2), client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_2), runepouchEnum);
        rune3 = itemForRune(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_3), client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_3), runepouchEnum);
        rune4 = itemForRune(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_4), client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_4), runepouchEnum);
    }

    private ItemContainerItem itemForRune(int runeId, int amount, EnumComposition runepouchEnum) {
        return new ItemContainerItem(runepouchEnum.getIntValue(runeId), amount);
    }

    @Override
    public Object get() {
        return new int[] {
                rune1.getId(), rune1.getQuantity(),
                rune2.getId(), rune2.getQuantity(),
                rune3.getId(), rune3.getQuantity(),
                rune4.getId(), rune4.getQuantity()
        };
    }

    @Override
    public String whoOwnsThis() {
        return playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof RunePouchState)) return false;
        RunePouchState other = (RunePouchState) o;

        return rune1.equals(other.rune1) && rune2.equals(other.rune2) && rune3.equals(other.rune3) && rune4.equals(other.rune4);
    }
}
