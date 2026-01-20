package gimhub.items;

import gimhub.APISerializable;
import gimhub.items.containers.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.*;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ItemRepository {
    private final TrackedItemContainer[] containers;

    private static final class TrackedMenuOptionClicked {
        public final int itemOp;
        public final int itemId;
        public final int param0;
        public final int param1;
        public final int identifier;
        public final MenuAction menuAction;
        public final boolean bankIsOpen;
        public final boolean depositIsOpen;
        public final int serverTick;

        public TrackedMenuOptionClicked(
                MenuOptionClicked event, boolean bankIsOpen, boolean depositIsOpen, int serverTick) {
            this.itemOp = event.getItemOp();
            this.itemId = event.getItemId();
            this.param0 = event.getParam0();
            this.param1 = event.getParam1();
            this.menuAction = event.getMenuAction();
            this.identifier = event.getId();
            this.bankIsOpen = bankIsOpen;
            this.depositIsOpen = depositIsOpen;
            this.serverTick = serverTick;
        }
    }

    private final List<TrackedMenuOptionClicked> itemOpQueue;

    private static final int GAME_TICKS_FOR_DEPOSIT_DETECTION = 2;
    private final BankItems bank;
    private final TackleBoxItems tackleBox;

    private static final class BankInventoryEquipment {
        public final Map<Integer, Integer> bank;
        public final Map<Integer, Integer> equipment;
        public final Map<Integer, Integer> inventory;

        public BankInventoryEquipment(Client client, ItemManager itemManager) {
            this.bank =
                    new ItemsUnordered(client.getItemContainer(InventoryID.BANK), itemManager).getItemsQuantityByID();
            this.equipment =
                    new ItemsUnordered(client.getItemContainer(InventoryID.WORN), itemManager).getItemsQuantityByID();
            this.inventory =
                    new ItemsUnordered(client.getItemContainer(InventoryID.INV), itemManager).getItemsQuantityByID();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof BankInventoryEquipment)) return false;
            BankInventoryEquipment other = (BankInventoryEquipment) o;

            return bank.equals(other.bank) && equipment.equals(other.equipment) && inventory.equals(other.inventory);
        }
    }

    private BankInventoryEquipment bankInventoryEquipmentOld = null;

    public ItemRepository() {
        bank = new BankItems();
        InventoryItems inventory = new InventoryItems();
        EquipmentItems equipment = new EquipmentItems();
        tackleBox = new TackleBoxItems();

        FishBarrelItems fishBarrel = new FishBarrelItems();
        fishBarrel.setBank(bank);

        containers = new TrackedItemContainer[] {
            bank,
            inventory,
            equipment,
            new SharedBankItems(),
            new PotionStorageItems(),
            new SeedVaultItems(),
            new PohCostumeRoomItems(),
            new RunePouchItems(),
            new QuiverItems(),
            new PlankSackItems(),
            new MasterScrollBookItems(),
            new EssencePouchesItems(),
            tackleBox,
            fishBarrel,
            new CoalBagItems(),
        };

        itemOpQueue = new ArrayList<>();
    }

    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        for (TrackedItemContainer tracked : containers) {
            tracked.onItemContainerChanged(container, itemManager);
        }
    }

    public void onStatChanged(StatChanged event, ItemManager itemManager) {
        for (TrackedItemContainer tracked : containers) {
            tracked.onStatChanged(event, itemManager);
        }
    }

    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        for (TrackedItemContainer tracked : containers) {
            tracked.onVarbitChanged(client, varpId, varbitId, itemManager);
        }
    }

    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        for (TrackedItemContainer tracked : containers) {
            tracked.onChatMessage(client, event, itemManager);
        }
    }

    private static final class TrackedContainers {
        Map<Integer, Integer> inventory = new HashMap<>();
        Map<Integer, Integer> equipment = new HashMap<>();
        Map<Integer, Integer> bank = null;
        Map<Integer, Integer> tackleBox = null;

        boolean changesOccurred = false;

        @Nullable private Map<Integer, Integer> copyMapPassThruNull(@Nullable Map<Integer, Integer> map) {
            if (map == null) return null;

            return new HashMap<>(map);
        }

        TrackedContainers deepClone() {
            TrackedContainers result = new TrackedContainers();
            result.equipment = copyMapPassThruNull(this.equipment);
            result.inventory = copyMapPassThruNull(this.inventory);
            result.bank = copyMapPassThruNull(this.bank);
            result.tackleBox = copyMapPassThruNull(this.tackleBox);
            result.changesOccurred = changesOccurred;

            return result;
        }
    }

    public void onGameTick(Client client, ItemManager itemManager) {
        final int serverTick = client.getTickCount();
        // Delete operations more than 2 ticks old, at which point we assume clicking the item resulted in no inventory
        // changes.
        final Set<TrackedMenuOptionClicked> outdatedEvents = itemOpQueue.stream()
                .filter(event -> event.serverTick < serverTick - GAME_TICKS_FOR_DEPOSIT_DETECTION)
                .collect(Collectors.toSet());
        itemOpQueue.removeAll(outdatedEvents);

        BankInventoryEquipment bankInventoryEquipmentNew = new BankInventoryEquipment(client, itemManager);

        final boolean bankIsOpen = client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null;

        final int depositXQuantity = client.getVarpValue(VarPlayerID.DEPOSITBOX_REQUESTEDQUANTITY);

        /*
         * When the user makes multiple item transfer inputs by clicking various menuOptions and widget buttons,
         * and then we observe item container changes the very same tick or some later tick, it is difficult to discern
         * which inputs have been processed by the server resulting in item movement.
         *
         * This poses a problem for containers that have no chat message or VarBits indicating their contents.
         *
         * So, we perform a step-by-step simulation of the user's inputs (always assuming they were successful) and
         * then attribute any discrepancies as a transfer to an unobserved container. Right now that is just the
         * tackle box and the bank.
         */
        if (bankInventoryEquipmentOld != null && !itemOpQueue.isEmpty()) {
            final TrackedContainers knownStart = new TrackedContainers();
            knownStart.equipment.putAll(bankInventoryEquipmentOld.equipment);
            knownStart.inventory.putAll(bankInventoryEquipmentOld.inventory);
            knownStart.bank = new HashMap<>(bankInventoryEquipmentOld.bank);
            knownStart.tackleBox = new HashMap<>(tackleBox.getTackleBoxItems());

            final TrackedContainers knownEnd = new TrackedContainers();
            knownEnd.equipment.putAll(bankInventoryEquipmentNew.equipment);
            knownEnd.inventory.putAll(bankInventoryEquipmentNew.inventory);
            if (bankIsOpen) {
                knownEnd.bank = new HashMap<>(bankInventoryEquipmentNew.bank);
            }

            TrackedContainers assumedNow = knownStart.deepClone();

            final ArrayList<TrackedContainers> assumedStateAtEachCheckpoint = new ArrayList<>(itemOpQueue.size());
            final ArrayList<Boolean> checkpointIsBalancedFlags = new ArrayList<>(itemOpQueue.size());
            for (final TrackedMenuOptionClicked op : itemOpQueue) {
                if (op.param1 == InterfaceID.Bankside.ITEMS) {
                    boolean isDeposit = op.identifier >= 2 && op.identifier <= 7;
                    boolean isUseFillEmpty = op.identifier == 9;
                    if (isDeposit) {
                        final int depositedItemID = op.itemId;
                        Integer attemptedDepositQuantity = null;
                        switch (op.identifier) {
                            case 2: // Deposit all
                                attemptedDepositQuantity = Integer.MAX_VALUE;
                                break;
                            case 3: // Deposit 1
                                attemptedDepositQuantity = 1;
                                break;
                            case 4: // Deposit 5
                                attemptedDepositQuantity = 5;
                                break;
                            case 5: // Deposit 10
                                attemptedDepositQuantity = 10;
                                break;
                            case 6: // Deposit X (actual)
                                attemptedDepositQuantity = depositXQuantity;
                                break;
                            case 7: // Deposit X (with prompt for amount)
                                // TODO
                                break;
                        }

                        if (attemptedDepositQuantity != null) {
                            final int inventoryQuantityNow = assumedNow.inventory.getOrDefault(depositedItemID, 0);
                            final int actualQuantityDeposited =
                                    Math.min(inventoryQuantityNow, attemptedDepositQuantity);
                            if (actualQuantityDeposited > 0) {
                                log.debug(
                                        "Performed a deposit. ID: {} QUANTITY: {}",
                                        depositedItemID,
                                        actualQuantityDeposited);
                                assumedNow.inventory.merge(depositedItemID, -actualQuantityDeposited, Integer::sum);
                                assumedNow.bank.merge(depositedItemID, actualQuantityDeposited, Integer::sum);
                                assumedNow.changesOccurred = true;
                            }
                        }
                    } else if (isUseFillEmpty && op.itemId == ItemID.TACKLE_BOX) {
                        assumedNow = settleInferredAmbiguousFillEmpty(assumedNow, knownEnd, tackleBox.getItemFilter());
                    }
                } else if (op.param1 == InterfaceID.Bankmain.ITEMS) {
                    boolean isWithdraw = op.identifier >= 1 && op.identifier <= 6;
                    if (isWithdraw) {
                        final int withdrawnItemID = op.itemId;
                        Integer attemptedWithdrawQuantity = null;
                        switch (op.identifier) {
                            case 1: // Withdraw all
                                attemptedWithdrawQuantity = Integer.MAX_VALUE;
                                break;
                            case 2: // Withdraw 1
                                attemptedWithdrawQuantity = 1;
                                break;
                            case 3: // Withdraw 5
                                attemptedWithdrawQuantity = 5;
                                break;
                            case 4: // Withdraw 10
                                attemptedWithdrawQuantity = 10;
                                break;
                            case 5: // Withdraw X (actual)
                                attemptedWithdrawQuantity = depositXQuantity;
                                break;
                            case 6: // Withdraw X (with prompt for amount)
                                // TODO
                                break;
                        }

                        if (attemptedWithdrawQuantity != null) {
                            final int bankQuantityNow = assumedNow.bank.getOrDefault(withdrawnItemID, 0);
                            final int actualQuantityWithdrawn = Math.min(bankQuantityNow, attemptedWithdrawQuantity);
                            log.debug(
                                    "Performed a withdrawal. ID: {} QUANTITY: {}",
                                    withdrawnItemID,
                                    actualQuantityWithdrawn);
                            assumedNow.inventory.merge(withdrawnItemID, actualQuantityWithdrawn, Integer::sum);
                            assumedNow.bank.merge(withdrawnItemID, -actualQuantityWithdrawn, Integer::sum);
                            assumedNow.changesOccurred = true;
                        }
                    }
                } else if (op.param1 == InterfaceID.Inventory.ITEMS && op.itemId == ItemID.TACKLE_BOX) {
                    final int FILL = 3;
                    final int EMPTY = 4;
                    if (op.identifier == FILL) {
                        assumedNow = performFill(assumedNow, knownEnd, tackleBox.getItemFilter());
                    } else if (op.identifier == EMPTY) {
                        assumedNow = performEmpty(assumedNow, knownEnd, tackleBox.getItemFilter());
                    }
                } else {
                    return;
                }

                {
                    boolean trackedItemsAreSettled = true;
                    for (final int itemID : tackleBox.getItemFilter()) {
                        final int quantityExpectedBank = assumedNow.bank.getOrDefault(itemID, 0);
                        final int quantityExpectedInventory = assumedNow.inventory.getOrDefault(itemID, 0);
                        final int quantityExpectedEquipment = assumedNow.equipment.getOrDefault(itemID, 0);

                        final int quantityObservedInventory = knownEnd.inventory.getOrDefault(itemID, 0);
                        final int quantityObservedEquipment = knownEnd.equipment.getOrDefault(itemID, 0);

                        if (knownEnd.bank != null) {
                            final int quantityObservedBank = knownEnd.bank.getOrDefault(itemID, 0);
                            if (quantityExpectedBank != quantityObservedBank
                                    || quantityExpectedEquipment != quantityObservedEquipment
                                    || quantityExpectedInventory != quantityObservedInventory) {
                                trackedItemsAreSettled = false;
                                break;
                            }
                        } else {
                            if (quantityExpectedEquipment != quantityObservedEquipment
                                    || quantityExpectedInventory != quantityObservedInventory) {
                                trackedItemsAreSettled = false;
                                break;
                            }
                        }
                    }
                    checkpointIsBalancedFlags.add(trackedItemsAreSettled);
                }

                assumedStateAtEachCheckpoint.add(assumedNow.deepClone());
            }

            {
                int idxOfLastBalancedCheckpoint = -1;
                for (int opIdx = 0; opIdx < checkpointIsBalancedFlags.size(); opIdx++) {
                    if (checkpointIsBalancedFlags.get(opIdx)) {
                        idxOfLastBalancedCheckpoint = opIdx;
                    }
                }
                while (idxOfLastBalancedCheckpoint >= 0
                        && !assumedStateAtEachCheckpoint.get(idxOfLastBalancedCheckpoint).changesOccurred) {
                    idxOfLastBalancedCheckpoint -= 1;
                }
                if (idxOfLastBalancedCheckpoint >= 0) {
                    final TrackedContainers finalState = assumedStateAtEachCheckpoint.get(idxOfLastBalancedCheckpoint);
                    if (knownEnd.bank == null) {
                        bank.setItems(finalState.bank);
                    }
                    final boolean tackleBoxIsOpen = client.getWidget(InterfaceID.TackleBoxMain.UNIVERSE) != null;
                    if (!tackleBoxIsOpen) {
                        tackleBox.setItems(finalState.tackleBox);
                    }

                    itemOpQueue.removeAll(itemOpQueue.subList(0, idxOfLastBalancedCheckpoint + 1));
                }
            }
        }

        bankInventoryEquipmentOld = bankInventoryEquipmentNew;

        for (TrackedItemContainer tracked : containers) {
            tracked.onGameTick(client, itemManager);
        }
    }

    private static TrackedContainers performFill(
            TrackedContainers nowImmutable, TrackedContainers endImmutable, Set<Integer> transferableItemIds) {
        final Map<Integer, Integer> containerDifference = new HashMap<>();

        for (final Integer itemID : transferableItemIds) {
            final int observedInventoryDifference =
                    endImmutable.inventory.getOrDefault(itemID, 0) - nowImmutable.inventory.getOrDefault(itemID, 0);
            final int singleItemContainerDifference = -observedInventoryDifference;
            if (singleItemContainerDifference <= 0) {
                continue;
            }

            containerDifference.put(itemID, singleItemContainerDifference);
        }

        final TrackedContainers result = nowImmutable.deepClone();
        final Map<Integer, Integer> resultContainer = result.tackleBox;

        for (final Map.Entry<Integer, Integer> entry : containerDifference.entrySet()) {
            final int itemID = entry.getKey();
            final int containerQuantityDifference = entry.getValue();

            if (containerQuantityDifference == 0) {
                continue;
            }
            result.changesOccurred = true;
            resultContainer.merge(itemID, containerQuantityDifference, Integer::sum);
            result.inventory.merge(itemID, -containerQuantityDifference, Integer::sum);
        }

        log.debug("Fill Performed");

        return result;
    }

    private static TrackedContainers performEmpty(
            TrackedContainers nowImmutable, TrackedContainers endImmutable, Set<Integer> transferableItemIds) {
        final Map<Integer, Integer> containerDifference = new HashMap<>();

        for (final Integer itemID : transferableItemIds) {
            final int observedInventoryDifference =
                    endImmutable.inventory.getOrDefault(itemID, 0) - nowImmutable.inventory.getOrDefault(itemID, 0);
            final int singleItemContainerDifference = -observedInventoryDifference;
            if (singleItemContainerDifference >= 0) {
                continue;
            }

            containerDifference.put(itemID, singleItemContainerDifference);
        }

        final TrackedContainers result = nowImmutable.deepClone();
        final Map<Integer, Integer> resultContainer = result.tackleBox;

        for (final Map.Entry<Integer, Integer> entry : containerDifference.entrySet()) {
            final int itemID = entry.getKey();
            final int containerQuantityDifference = entry.getValue();

            if (containerQuantityDifference == 0) {
                continue;
            }
            result.changesOccurred = true;
            resultContainer.merge(itemID, containerQuantityDifference, Integer::sum);
            result.inventory.merge(itemID, -containerQuantityDifference, Integer::sum);
        }

        log.debug("Empty Performed");

        return result;
    }

    private static TrackedContainers settleInferredAmbiguousFillEmpty(
            TrackedContainers nowImmutable, TrackedContainers endImmutable, Set<Integer> transferableItemIds) {
        int emptyCount = 0;
        int fillCount = 0;

        final Map<Integer, Integer> containerDifference = new HashMap<>();

        for (final Integer itemID : transferableItemIds) {
            final int observedInventoryDifference =
                    endImmutable.inventory.getOrDefault(itemID, 0) - nowImmutable.inventory.getOrDefault(itemID, 0);
            if (observedInventoryDifference == 0) {
                continue;
            }

            final int singleItemContainerDifference = -observedInventoryDifference;
            containerDifference.put(itemID, singleItemContainerDifference);
            if (singleItemContainerDifference > 0) {
                fillCount++;
            } else {
                emptyCount++;
            }
        }

        final TrackedContainers result = nowImmutable.deepClone();
        if (fillCount == 0 && emptyCount == 0) {
            return result;
        }
        final Map<Integer, Integer> resultContainer = result.tackleBox;

        final boolean containerWasOverallFilled = fillCount >= emptyCount;
        for (final Map.Entry<Integer, Integer> entry : containerDifference.entrySet()) {
            final int itemID = entry.getKey();
            final int containerQuantityDifference = entry.getValue();

            if (containerQuantityDifference == 0) {
                continue;
            }
            result.changesOccurred = true;

            final boolean thisItemWasFilled = containerQuantityDifference > 0;
            if (containerWasOverallFilled != thisItemWasFilled) {
                continue;
            }

            resultContainer.merge(itemID, containerQuantityDifference, Integer::sum);
            result.inventory.merge(itemID, -containerQuantityDifference, Integer::sum);
        }

        log.debug("Ambiguous transfer was performed. WAS FILL: {}", containerWasOverallFilled);

        return result;
    }

    public void onMenuOptionClicked(Client client, MenuOptionClicked event, ItemManager itemManager) {
        for (TrackedItemContainer tracked : containers) {
            tracked.onMenuOptionClicked(client, event, itemManager);
        }

        final boolean bankIsOpen = client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null;
        final boolean depositBoxIsOpen = client.getWidget(InterfaceID.BankDepositbox.UNIVERSE) != null;

        final TrackedMenuOptionClicked op =
                new TrackedMenuOptionClicked(event, bankIsOpen, depositBoxIsOpen, client.getTickCount());

        if (op.param1 == InterfaceID.Bankside.ITEMS) {
            boolean isDeposit = op.identifier >= 2 && op.identifier <= 7;
            boolean isUseFillEmpty = op.identifier == 9;
            if (!isDeposit && !isUseFillEmpty) {
                return;
            }
        } else if (op.param1 == InterfaceID.Bankmain.ITEMS) {
            boolean isWithdrawal = op.identifier >= 1 && op.identifier <= 6;
            if (!isWithdrawal) {
                return;
            }
        } else if (op.param1 == InterfaceID.Inventory.ITEMS) {
            final int FILL = 3;
            final int EMPTY = 4;
            if (op.identifier != FILL && op.identifier != EMPTY) {
                return;
            }
        } else {
            return;
        }

        itemOpQueue.add(op);
    }

    public void flatten(Map<String, APISerializable> flat) {
        for (TrackedItemContainer tracked : containers) {
            flat.put(tracked.key(), tracked.get());
        }
    }
}
