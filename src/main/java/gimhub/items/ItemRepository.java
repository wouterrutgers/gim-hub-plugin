package gimhub.items;

import gimhub.APISerializable;
import java.util.ArrayList;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ItemRepository {
    private ItemsOrdered inventory = null;
    private ItemsOrdered equipment = null;

    private ItemsUnordered sharedBank = null;
    private ItemsUnordered sharedBankUncommited = null;

    private ItemsUnordered bank = null;
    private ItemsUnordered seedVault = null;
    private ItemsUnordered pohCostumeRoom = null;

    private ItemsOrdered runePouch = null;
    private ItemsOrdered quiver = null;

    private ItemsUnordered deposited = null;
    private ItemsUnordered inventoryPreDeposit = null;
    private ItemsUnordered equipmentPreDeposit = null;

    private static final int INVENTORY_ID_COSTUME_ROOM = 33405;

    private static final int GAME_TICKS_FOR_DEPOSIT_DETECTION = 2;

    private static final int CONTAINER_SIZE_INVENTORY = 28;
    private static final int CONTAINER_SIZE_EQUIPMENT = 14;

    private int gameTicksToLogDeposits = 0;

    public void updateRunepouch(Client client, ItemManager itemManager) {
        final EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
        final ArrayList<Item> runepouchItems = new ArrayList<>();
        runepouchItems.add(new Item(
                runepouchEnum.getIntValue(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_1)),
                client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)));
        runepouchItems.add(new Item(
                runepouchEnum.getIntValue(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_2)),
                client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_2)));
        runepouchItems.add(new Item(
                runepouchEnum.getIntValue(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_3)),
                client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_3)));
        runepouchItems.add(new Item(
                runepouchEnum.getIntValue(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_4)),
                client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_4)));
        runePouch = new ItemsOrdered(runepouchItems, itemManager);
    }

    public void updateQuiver(Client client, ItemManager itemManager) {
        final ArrayList<Item> quiverItems = new ArrayList<>();
        quiverItems.add(new Item(
                client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO),
                client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)));
        quiver = new ItemsOrdered(quiverItems, itemManager);
    }

    public void commitSharedBank() {
        sharedBank = sharedBankUncommited;
        sharedBankUncommited = null;
    }

    public void onGameTick() {
        if (gameTicksToLogDeposits > 0) {
            --gameTicksToLogDeposits;
        }
    }

    private void updateDeposited() {
        final ItemsUnordered depositedEquipment =
                ItemsUnordered.subtract(equipmentPreDeposit, new ItemsUnordered(equipment));
        final ItemsUnordered depositedItems =
                ItemsUnordered.subtract(inventoryPreDeposit, new ItemsUnordered(inventory));
        deposited = ItemsUnordered.add(depositedEquipment, depositedItems);
    }

    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        final int id = container.getId();

        if (id == InventoryID.BANK) {
            deposited = null;
            bank = new ItemsUnordered(container, itemManager);

        } else if (id == InventoryID.SEED_VAULT) {
            seedVault = new ItemsUnordered(container, itemManager);

        } else if (id == INVENTORY_ID_COSTUME_ROOM) {
            pohCostumeRoom = new ItemsUnordered(container, itemManager);

        } else if (id == InventoryID.INV) {
            inventory = new ItemsOrdered(container, itemManager, CONTAINER_SIZE_INVENTORY);
            if (gameTicksToLogDeposits > 0) {
                updateDeposited();
            }
        } else if (id == InventoryID.WORN) {
            equipment = new ItemsOrdered(container, itemManager, CONTAINER_SIZE_EQUIPMENT);
            if (gameTicksToLogDeposits > 0) {
                updateDeposited();
            }
        } else if (id == InventoryID.INV_GROUP_TEMP) {
            sharedBankUncommited = new ItemsUnordered(container, itemManager);
        }
    }

    public void itemsMayHaveBeenDeposited() {
        final boolean isAlreadyChecking = gameTicksToLogDeposits > 0;
        if (!isAlreadyChecking) {
            inventoryPreDeposit = new ItemsUnordered(inventory);
            equipmentPreDeposit = new ItemsUnordered(equipment);
        }
        gameTicksToLogDeposits = GAME_TICKS_FOR_DEPOSIT_DETECTION;
    }

    public void flatten(Map<String, APISerializable> flat) {
        flat.put("inventory", inventory);
        flat.put("equipment", equipment);

        flat.put("shared_bank", sharedBank);

        flat.put("bank", bank);
        flat.put("seed_vault", seedVault);
        flat.put("poh_costume_room", pohCostumeRoom);

        flat.put("rune_pouch", runePouch);
        flat.put("quiver", quiver);

        flat.put("deposited", deposited);
    }
}
