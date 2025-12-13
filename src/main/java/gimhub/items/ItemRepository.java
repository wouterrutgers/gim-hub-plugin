package gimhub.items;

import gimhub.APIConsumable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ItemRepository {
    @Inject
    private ItemManager itemManager;

    private static class SynchronizedState {
        public final String ownedPlayer;

        SynchronizedState(String ownedPlayer) {
            this.ownedPlayer = ownedPlayer;
        }

        public final APIConsumable<ItemsOrdered> inventory = new APIConsumable<>("inventory", false);
        public final APIConsumable<ItemsOrdered> equipment = new APIConsumable<>("equipment", false);

        public final APIConsumable<ItemsUnordered> sharedBank = new APIConsumable<>("shared_bank", true);

        public final APIConsumable<ItemsUnordered> bank = new APIConsumable<>("bank", false);
        public final APIConsumable<ItemsUnordered> seedVault = new APIConsumable<>("seed_vault", false);
        public final APIConsumable<ItemsUnordered> pohCostumeRoom = new APIConsumable<>("poh_costume_room", false);

        public final APIConsumable<ItemsOrdered> runePouch = new APIConsumable<>("rune_pouch", false);
        public final APIConsumable<ItemsOrdered> quiver = new APIConsumable<>("quiver", false);

        public final APIConsumable<ItemsUnordered> deposited = new APIConsumable<>("deposited", false);

        public Iterable<APIConsumable<?>> getAllContainers() {
            final ArrayList<APIConsumable<?>> result = new ArrayList<>();

            result.add(inventory);
            result.add(equipment);

            result.add(sharedBank);

            result.add(bank);
            result.add(seedVault);
            result.add(pohCostumeRoom);

            result.add(runePouch);
            result.add(quiver);

            result.add(deposited);

            return result;
        }
    }

    private static final int INVENTORY_ID_COSTUME_ROOM = 33405;

    private static final int GAME_TICKS_FOR_DEPOSIT_DETECTION = 2;

    private static final int CONTAINER_SIZE_INVENTORY = 28;
    private static final int CONTAINER_SIZE_EQUIPMENT = 14;

    private AtomicReference<SynchronizedState> stateRef;
    private int gameTicksToLogDeposits = 0;

    private void reset(String player) {
        gameTicksToLogDeposits = 0;

        stateRef.set(new SynchronizedState(player));
    }

    public void updateRunepouch(Client client) {
        final String player = client.getLocalPlayer().getName();

        if (!Objects.equals(stateRef.get().ownedPlayer, player)) {
            reset(player);
        }
        SynchronizedState state = stateRef.get();

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
        state.runePouch.update(new ItemsOrdered(runepouchItems, itemManager));
    }

    public void updateQuiver(Client client) {
        final String player = client.getLocalPlayer().getName();

        if (!Objects.equals(stateRef.get().ownedPlayer, player)) {
            reset(player);
        }
        SynchronizedState state = stateRef.get();

        final ArrayList<Item> quiverItems = new ArrayList<>();
        quiverItems.add(new Item(
                client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO),
                client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)));
        state.quiver.update(new ItemsOrdered(quiverItems, itemManager));
    }

    public void commitSharedBank(String player) {
        if (!Objects.equals(stateRef.get().ownedPlayer, player)) {
            reset(player);
        }
        SynchronizedState state = stateRef.get();

        state.sharedBank.commitTransaction();
    }

    public void onGameTick() {
        if (gameTicksToLogDeposits > 0) {
            --gameTicksToLogDeposits;
        }
    }

    public void onItemContainerChanged(String player, ItemContainer container) {
        if (!Objects.equals(stateRef.get().ownedPlayer, player)) {
            reset(player);
        }
        SynchronizedState state = stateRef.get();

        final int id = container.getId();

        if (id == InventoryID.BANK) {
            state.deposited.reset();
            state.bank.update(new ItemsUnordered(container, itemManager));

        } else if (id == InventoryID.SEED_VAULT) {
            state.seedVault.update(new ItemsUnordered(container, itemManager));

        } else if (id == INVENTORY_ID_COSTUME_ROOM) {
            state.pohCostumeRoom.update(new ItemsUnordered(container, itemManager));

        } else if (id == InventoryID.INV) {
            ItemsOrdered newInventoryItems = new ItemsOrdered(container, itemManager, CONTAINER_SIZE_INVENTORY);
            if (gameTicksToLogDeposits > 0) {
                ItemsOrdered oldInventoryItems = state.inventory.getPreviousState();
                ItemsUnordered oldDeposited = state.deposited.getPreviousState();
                ItemsUnordered itemsRemoved = ItemsUnordered.subtract(
                        new ItemsUnordered(oldInventoryItems), new ItemsUnordered(newInventoryItems));
                state.deposited.update(ItemsUnordered.add(oldDeposited, itemsRemoved));
            }
            state.inventory.update(newInventoryItems);

        } else if (id == InventoryID.WORN) {
            ItemsOrdered newEquipmentItems = new ItemsOrdered(container, itemManager, CONTAINER_SIZE_EQUIPMENT);
            if (gameTicksToLogDeposits > 0) {
                ItemsOrdered oldEquipmentItems = state.equipment.getPreviousState();
                ItemsUnordered oldDeposited = state.deposited.getPreviousState();
                ItemsUnordered itemsRemoved = ItemsUnordered.subtract(
                        new ItemsUnordered(oldEquipmentItems), new ItemsUnordered(newEquipmentItems));
                state.deposited.update(ItemsUnordered.add(oldDeposited, itemsRemoved));
            }
            state.equipment.update(newEquipmentItems);

        } else if (id == InventoryID.INV_GROUP_TEMP) {
            state.sharedBank.update(new ItemsUnordered(container, itemManager));
        }
    }

    public void itemsMayHaveBeenDeposited(String player) {
        if (!Objects.equals(stateRef.get().ownedPlayer, player)) {
            reset(player);
        }

        gameTicksToLogDeposits = GAME_TICKS_FOR_DEPOSIT_DETECTION;
    }

    public void consumeAllStates(String player, Map<String, Object> updates) {
        SynchronizedState state = stateRef.get();
        if (!Objects.equals(state.ownedPlayer, player)) {
            return;
        }

        for (final APIConsumable<?> container : state.getAllContainers()) {
            container.consumeState(updates);
        }
    }

    public void restoreAllStates(String player) {
        SynchronizedState state = stateRef.get();
        if (!Objects.equals(state.ownedPlayer, player)) {
            return;
        }

        for (final APIConsumable<?> container : state.getAllContainers()) {
            container.restoreState();
        }
    }
}
