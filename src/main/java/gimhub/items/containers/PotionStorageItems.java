package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import gimhub.items.ItemsUnordered;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

public class PotionStorageItems implements TrackedItemContainer {
    private static final int POTION_STORE_WIDGET = 786484;
    private static final int VARP_VIALS_IN_POTION_STORAGE = 4286;
    private static final int ITEM_VIAL = 229;

    private static final int ENUM_POTIONS_1 = 4826;
    private static final int ENUM_POTIONS_2 = 4829;
    private static final int SCRIPT_POTION_STORE_GET_DOSES = 3750;

    private ItemsUnordered items = null;

    private boolean dirty = true;
    private Set<Integer> potionStoreVarps = null;

    @Override
    public String key() {
        return "potion_storage";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container.getId() == InventoryID.POTION_STORE_TEMP_INV) {
            dirty = true;
        }
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        if (potionStoreVarps != null && potionStoreVarps.contains(varpId)) {
            dirty = true;
        }
    }

    @Override
    public void onUpdateOften(Client client, ItemManager itemManager) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        final Widget widget = client.getWidget(POTION_STORE_WIDGET);
        if (widget == null) {
            // RuneLite invalidates the cached potions when the bank interface unloads.
            items = null;
            return;
        }

        if (potionStoreVarps == null) {
            final int[] triggers = widget.getVarTransmitTrigger();
            if (triggers != null) {
                potionStoreVarps = new HashSet<>();
                for (final int t : triggers) {
                    potionStoreVarps.add(t);
                }
            }
        }

        if (!dirty) {
            return;
        }

        dirty = false;
        items = readPotionStorage(client, itemManager);
    }

    private static ItemsUnordered readPotionStorage(Client client, ItemManager itemManager) {
        final List<Item> result = new ArrayList<>();

        final int vials = client.getVarpValue(VARP_VIALS_IN_POTION_STORAGE);
        if (vials > 0) {
            result.add(new Item(ITEM_VIAL, vials));
        }

        for (final int potionsEnumId : new int[] {ENUM_POTIONS_1, ENUM_POTIONS_2}) {
            final EnumComposition potionsEnum = client.getEnum(potionsEnumId);
            if (potionsEnum == null || potionsEnum.getIntVals() == null) {
                continue;
            }

            for (final int potionEnumId : potionsEnum.getIntVals()) {
                final EnumComposition potionEnum = client.getEnum(potionEnumId);
                if (potionEnum == null) {
                    continue;
                }

                client.runScript(SCRIPT_POTION_STORE_GET_DOSES, potionEnumId);
                int doses = client.getIntStack()[0];

                if (doses <= 0) {
                    continue;
                }

                for (int dose = 4; dose >= 1 && doses > 0; dose--) {
                    final int itemId = potionEnum.getIntValue(dose);
                    if (itemId <= 0) {
                        continue;
                    }

                    final int quantity = doses / dose;
                    if (quantity == 0) {
                        continue;
                    }

                    doses -= quantity * dose;
                    result.add(new Item(itemId, quantity));
                }
            }
        }

        return new ItemsUnordered(new ItemsOrdered(result, itemManager));
    }
}
