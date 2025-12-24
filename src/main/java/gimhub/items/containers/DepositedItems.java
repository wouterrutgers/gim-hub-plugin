package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import gimhub.items.ItemsUnordered;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;

public class DepositedItems implements TrackedItemContainer {
    private static final int GAME_TICKS_FOR_DEPOSIT_DETECTION = 2;

    private final InventoryItems inventory;
    private final EquipmentItems equipment;

    private ItemsUnordered deposited = null;
    private ItemsUnordered inventoryPreDeposit = null;
    private ItemsUnordered equipmentPreDeposit = null;

    private int gameTicksToLogDeposits = 0;

    public DepositedItems(InventoryItems inventory, EquipmentItems equipment) {
        this.inventory = inventory;
        this.equipment = equipment;
    }

    @Override
    public String key() {
        return "deposited";
    }

    @Override
    public APISerializable get() {
        return deposited;
    }

    @Override
    public void onDepositTriggered() {
        final boolean isAlreadyChecking = gameTicksToLogDeposits > 0;
        if (!isAlreadyChecking) {
            inventoryPreDeposit = new ItemsUnordered(getInventory());
            equipmentPreDeposit = new ItemsUnordered(getEquipment());
        }
        gameTicksToLogDeposits = GAME_TICKS_FOR_DEPOSIT_DETECTION;
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (gameTicksToLogDeposits > 0) {
            --gameTicksToLogDeposits;
        }
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        final int id = container.getId();

        if (id == InventoryID.BANK) {
            deposited = null;
            return;
        }

        if (gameTicksToLogDeposits <= 0) {
            return;
        }

        if (id == InventoryID.INV || id == InventoryID.WORN) {
            updateDeposited();
        }
    }

    private ItemsOrdered getInventory() {
        return inventory.getItems();
    }

    private ItemsOrdered getEquipment() {
        return equipment.getItems();
    }

    private void updateDeposited() {
        final ItemsUnordered depositedEquipment =
                ItemsUnordered.subtract(equipmentPreDeposit, new ItemsUnordered(getEquipment()));
        final ItemsUnordered depositedItems =
                ItemsUnordered.subtract(inventoryPreDeposit, new ItemsUnordered(getInventory()));
        deposited = ItemsUnordered.add(depositedEquipment, depositedItems);
    }
}
