package gimhub.items;

import net.runelite.api.Item;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

public final class ItemsUtilities {
    private ItemsUtilities() {}

    public static boolean isItemValid(Item item, ItemManager itemManager) {
        if (item == null) return false;
        final int id = item.getId();
        final int quantity = item.getQuantity();
        if (itemManager != null) {
            final boolean isPlaceholder = itemManager.getItemComposition(id).getPlaceholderTemplateId() != -1;

            return id >= 0 && quantity >= 0 && !isPlaceholder;
        }
        return false;
    }

    public static boolean isQuiver(int varpID) {
        return varpID == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO || varpID == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT;
    }

    public static boolean isRunePouch(int varbitID) {
        return varbitID == VarbitID.RUNE_POUCH_TYPE_1
                || varbitID == VarbitID.RUNE_POUCH_QUANTITY_1
                || varbitID == VarbitID.RUNE_POUCH_TYPE_2
                || varbitID == VarbitID.RUNE_POUCH_QUANTITY_2
                || varbitID == VarbitID.RUNE_POUCH_TYPE_3
                || varbitID == VarbitID.RUNE_POUCH_QUANTITY_3
                || varbitID == VarbitID.RUNE_POUCH_TYPE_4
                || varbitID == VarbitID.RUNE_POUCH_QUANTITY_4;
    }
}
