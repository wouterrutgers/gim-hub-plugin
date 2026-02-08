package gimhub.items;

import gimhub.items.containers.TackleBoxItems;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

@Slf4j
public class ItemTransferQueue {
    public static final class TrackedContainers {
        public final Map<Integer, Integer> inventory;
        public final Map<Integer, Integer> equipment;
        public final Map<Integer, Integer> bank;
        public final Map<Integer, Integer> tackleBox;

        TrackedContainers(
                Map<Integer, Integer> inventory,
                Map<Integer, Integer> equipment,
                Map<Integer, Integer> bank,
                Map<Integer, Integer> tackleBox) {
            this.inventory = inventory;
            this.equipment = equipment;
            this.bank = bank;
            this.tackleBox = tackleBox;
        }

        private TrackedContainers deepClone() {
            return new TrackedContainers(
                    new HashMap<>(inventory), new HashMap<>(equipment), new HashMap<>(bank), new HashMap<>(tackleBox));
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof TrackedContainers)) return false;
            final TrackedContainers other = (TrackedContainers) o;

            final boolean inventoryEquals = inventory.equals(other.inventory);
            final boolean equipmentEquals = equipment.equals(other.equipment);
            final boolean bankEquals = bank.equals(other.bank);
            final boolean tackleBoxEquals = tackleBox.equals(other.tackleBox);

            return inventoryEquals && equipmentEquals && bankEquals && tackleBoxEquals;
        }
    }

    private static final class TrackedMenuOptionClicked {
        public final int itemOp;
        public final int itemId;
        public final int param0;
        public final int param1;
        public final int identifier;

        @Getter
        private int tickCount;

        @Getter
        private Integer queryXQuantity = null;

        public void setQueryXQuantity(int quantity, int tickCount) {
            // Refresh the tick count, since the timer for when we expect to observe it starts NOW
            this.tickCount = tickCount;
            this.queryXQuantity = quantity;
        }

        // Indicates that this event represents a "withdraw/deposit X" option in an item menu that pulls up the modal.
        public boolean isQueryXAction() {
            final boolean isBankside = param1 == InterfaceID.Bankside.ITEMS && identifier == 7;
            final boolean isBankmain = param1 == InterfaceID.Bankmain.ITEMS && identifier == 6;
            final boolean isBankDepositbox = param1 == InterfaceID.BankDepositbox.INVENTORY && identifier == 5;

            return isBankside || isBankmain || isBankDepositbox;
        }

        public TrackedMenuOptionClicked(MenuOptionClicked event, int tickCount) {
            this.itemOp = event.getItemOp();
            this.itemId = event.getItemId();
            this.param0 = event.getParam0();
            this.param1 = event.getParam1();
            this.identifier = event.getId();
            this.tickCount = tickCount;
        }
    }

    private static final int TICK_COUNT_FOR_ITEM_TRANSFER_DETECTION = 2;
    private static final Map<Integer, Integer> IDENTIFIER_FILL_BY_ITEM_ID =
            Map.ofEntries(Map.entry(ItemID.TACKLE_BOX, 3));
    private static final Map<Integer, Integer> IDENTIFIER_EMPTY_BY_ITEM_ID =
            Map.ofEntries(Map.entry(ItemID.TACKLE_BOX, 4));

    private TrackedContainers previousTickState;
    private final List<TrackedMenuOptionClicked> itemOpQueue = new ArrayList<>();

    public static final class ContainersToUpdate {
        Map<Integer, Integer> bank = null;
        Map<Integer, Integer> tackleBox = null;
    }

    public static final class BankSettings {
        final int depositXQuantity;
        final int bankDefaultDepositQuantity;
        final int depositBoxDefaultDepositQuantity;

        public BankSettings(Client client) {
            this.depositXQuantity = client.getVarpValue(VarPlayerID.DEPOSITBOX_REQUESTEDQUANTITY);

            switch (client.getVarbitValue(VarbitID.BANK_QUANTITY_TYPE)) {
                case 0:
                    this.bankDefaultDepositQuantity = 1;
                    break;
                case 1:
                    this.bankDefaultDepositQuantity = 5;
                    break;
                case 2:
                    this.bankDefaultDepositQuantity = 10;
                    break;
                case 3:
                    this.bankDefaultDepositQuantity = this.depositXQuantity;
                    break;
                case 4:
                    this.bankDefaultDepositQuantity = Integer.MAX_VALUE;
                    break;
                default:
                    this.bankDefaultDepositQuantity = 0;
                    break;
            }

            switch (client.getVarbitValue(VarbitID.DEPOSITBOX_MODE)) {
                case 0:
                    this.depositBoxDefaultDepositQuantity = 1;
                    break;
                case 1:
                    this.depositBoxDefaultDepositQuantity = 5;
                    break;
                case 2:
                    this.depositBoxDefaultDepositQuantity = Integer.MAX_VALUE;
                    break;
                case 3:
                    this.depositBoxDefaultDepositQuantity = this.depositXQuantity;
                    break;
                case 4:
                    this.depositBoxDefaultDepositQuantity = 10;
                    break;
                default:
                    this.depositBoxDefaultDepositQuantity = 0;
                    break;
            }
        }
    }

    public ContainersToUpdate onGameTick(
            TrackedContainers knownState,
            BankSettings bankSettings,
            int tickCount,
            boolean isBankOpen,
            boolean isTackleBoxOpen) {
        itemOpQueue.removeIf(op -> {
            final boolean isUnfinishedQueryXAction = op.isQueryXAction() && op.getQueryXQuantity() == null;
            if (isUnfinishedQueryXAction) {
                return false;
            }

            return op.tickCount < tickCount - TICK_COUNT_FOR_ITEM_TRANSFER_DETECTION;
        });

        if (previousTickState == null || itemOpQueue.isEmpty()) {
            previousTickState = knownState.deepClone();
            return new ContainersToUpdate();
        }

        final TrackedContainers assumedStart = previousTickState.deepClone();
        TrackedContainers assumedNow = assumedStart.deepClone();
        final ArrayList<TrackedContainers> assumedCheckpoints = new ArrayList<>();

        for (final TrackedMenuOptionClicked op : itemOpQueue) {
            if (op.param1 == InterfaceID.Bankside.ITEMS) {
                boolean isDeposit = op.identifier >= 2 && op.identifier <= 8;
                boolean isUseFillEmpty = op.identifier == 9;
                if (isDeposit) {
                    final int depositedItemID = op.itemId;
                    Integer attemptedDepositQuantity = null;
                    switch (op.identifier) {
                        case 2:
                            attemptedDepositQuantity = bankSettings.bankDefaultDepositQuantity;
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
                            attemptedDepositQuantity = bankSettings.depositXQuantity;
                            break;
                        case 7: // Deposit X (with prompt for amount)
                            attemptedDepositQuantity = op.getQueryXQuantity();
                            break;
                        case 8: // Deposit all
                            attemptedDepositQuantity = Integer.MAX_VALUE;
                            break;
                    }

                    if (attemptedDepositQuantity != null) {
                        final int inventoryQuantityNow = assumedNow.inventory.getOrDefault(depositedItemID, 0);
                        final int actualQuantityDeposited = Math.min(inventoryQuantityNow, attemptedDepositQuantity);

                        // We do not track containers that deposit directly from the bank, so this inference is mostly
                        // safe.
                        final int bankDiscrepancy = knownState.bank.getOrDefault(depositedItemID, 0)
                                - assumedNow.bank.getOrDefault(depositedItemID, 0);
                        final boolean depositOccurred = bankDiscrepancy >= actualQuantityDeposited;

                        if (actualQuantityDeposited > 0 && depositOccurred) {
                            assumedNow.inventory.merge(depositedItemID, -actualQuantityDeposited, Integer::sum);
                            assumedNow.bank.merge(depositedItemID, actualQuantityDeposited, Integer::sum);
                        }
                    }
                } else if (isUseFillEmpty && op.itemId == ItemID.TACKLE_BOX) {
                    final TransferDirection inventoryExpectedChanges = determineNetLaterMinusNow(
                            assumedNow.inventory, knownState.inventory, TackleBoxItems.getItemFilter());
                    final Map<Integer, Integer> transferQuantities = filterLaterMinusNow(
                            assumedNow.inventory,
                            knownState.inventory,
                            TackleBoxItems.getItemFilter(),
                            inventoryExpectedChanges);
                    performTransfer(assumedNow.tackleBox, assumedNow.inventory, transferQuantities);
                }
            } else if (op.param1 == InterfaceID.BankDepositbox.INVENTORY) {
                boolean isDeposit = op.identifier >= 1 && op.identifier <= 6;
                boolean isUseFillEmpty = op.identifier == 9;
                if (isDeposit) {
                    final int depositedItemID = op.itemId;
                    Integer attemptedDepositQuantity = null;
                    switch (op.identifier) {
                        case 1: // Default
                            attemptedDepositQuantity = bankSettings.depositBoxDefaultDepositQuantity;
                            break;
                        case 2: // Deposit 1
                            attemptedDepositQuantity = 1;
                            break;
                        case 3: // Deposit 5
                            attemptedDepositQuantity = 5;
                            break;
                        case 4: // Deposit 10
                            attemptedDepositQuantity = 10;
                            break;
                        case 5: // Deposit X (with prompt for amount)
                            attemptedDepositQuantity = op.getQueryXQuantity();
                            break;
                        case 6: // Deposit all
                            attemptedDepositQuantity = Integer.MAX_VALUE;
                            break;
                    }

                    if (attemptedDepositQuantity != null) {
                        final int inventoryQuantityNow = assumedNow.inventory.getOrDefault(depositedItemID, 0);
                        final int actualQuantityDeposited = Math.min(inventoryQuantityNow, attemptedDepositQuantity);

                        // Sometimes clicking an item does not deposit it. This may be confused with filling a container
                        // while depositing, but that case seems rare enough to not matter.
                        final int inventoryDiscrepancy =
                                knownState.inventory.getOrDefault(depositedItemID, 0) - inventoryQuantityNow;
                        final boolean depositOccurred = inventoryDiscrepancy <= -actualQuantityDeposited;

                        if (actualQuantityDeposited > 0 && depositOccurred) {
                            assumedNow.inventory.merge(depositedItemID, -actualQuantityDeposited, Integer::sum);
                            assumedNow.bank.merge(depositedItemID, actualQuantityDeposited, Integer::sum);
                        }
                    }
                } else if (isUseFillEmpty && op.itemId == ItemID.TACKLE_BOX) {
                    final TransferDirection inventoryExpectedChanges = determineNetLaterMinusNow(
                            assumedNow.inventory, knownState.inventory, TackleBoxItems.getItemFilter());
                    final Map<Integer, Integer> transferQuantities = filterLaterMinusNow(
                            assumedNow.inventory,
                            knownState.inventory,
                            TackleBoxItems.getItemFilter(),
                            inventoryExpectedChanges);
                    performTransfer(assumedNow.tackleBox, assumedNow.inventory, transferQuantities);
                }
            } else if (op.param1 == InterfaceID.Bankmain.ITEMS) {
                boolean isWithdraw = op.identifier >= 1 && op.identifier <= 8;
                if (isWithdraw) {
                    final int withdrawnItemID = op.itemId;
                    Integer attemptedWithdrawQuantity = null;
                    switch (op.identifier) {
                        case 1: // Withdraw default
                            attemptedWithdrawQuantity = bankSettings.bankDefaultDepositQuantity;
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
                            attemptedWithdrawQuantity = bankSettings.depositXQuantity;
                            break;
                        case 6: // Withdraw X (with prompt for amount)
                            attemptedWithdrawQuantity = op.getQueryXQuantity();
                            break;
                        case 7: // Withdraw all
                            attemptedWithdrawQuantity = Integer.MAX_VALUE;
                            break;
                        case 8: // Withdraw all but 1
                            attemptedWithdrawQuantity = assumedNow.bank.getOrDefault(withdrawnItemID, 0) - 1;
                            break;
                    }

                    if (attemptedWithdrawQuantity != null && attemptedWithdrawQuantity > 0) {
                        final int bankQuantityNow = assumedNow.bank.getOrDefault(withdrawnItemID, 0);
                        final int actualQuantityWithdrawn = Math.min(bankQuantityNow, attemptedWithdrawQuantity);

                        // We do not track containers that draw directly from the bank, so this inference is mostly
                        // safe.
                        final int bankDiscrepancy = knownState.bank.getOrDefault(withdrawnItemID, 0) - bankQuantityNow;
                        final boolean withdrawalOccurred = bankDiscrepancy <= -actualQuantityWithdrawn;

                        if (actualQuantityWithdrawn > 0 && withdrawalOccurred) {
                            assumedNow.inventory.merge(withdrawnItemID, actualQuantityWithdrawn, Integer::sum);
                            assumedNow.bank.merge(withdrawnItemID, -actualQuantityWithdrawn, Integer::sum);
                        }
                    }
                }
            } else if (op.param1 == InterfaceID.Inventory.ITEMS) {
                final int FILL = IDENTIFIER_FILL_BY_ITEM_ID.get(op.itemId);
                final int EMPTY = IDENTIFIER_EMPTY_BY_ITEM_ID.get(op.itemId);
                final TransferDirection inventoryExpectedChanges;
                if (op.identifier == FILL) {
                    inventoryExpectedChanges = TransferDirection.LOSS;
                } else if (op.identifier == EMPTY) {
                    inventoryExpectedChanges = TransferDirection.GAIN;
                } else {
                    inventoryExpectedChanges = TransferDirection.NONE;
                }

                if (inventoryExpectedChanges != TransferDirection.NONE) {
                    final Map<Integer, Integer> transferQuantities = filterLaterMinusNow(
                            assumedNow.inventory,
                            knownState.inventory,
                            TackleBoxItems.getItemFilter(),
                            inventoryExpectedChanges);
                    performTransfer(assumedNow.tackleBox, assumedNow.inventory, transferQuantities);
                }
            } else if (op.param1 == InterfaceID.BankDepositbox.DEPOSIT_INV) {
                for (final int itemID : assumedNow.inventory.keySet()) {
                    final int quantityTransferred = assumedNow.inventory.getOrDefault(itemID, 0);
                    assumedNow.inventory.merge(itemID, -quantityTransferred, Integer::sum);
                    assumedNow.bank.merge(itemID, quantityTransferred, Integer::sum);
                }
            } else if (op.param1 == InterfaceID.BankDepositbox.DEPOSIT_WORN) {
                for (final int itemID : assumedNow.equipment.keySet()) {
                    final int quantityTransferred = assumedNow.equipment.getOrDefault(itemID, 0);
                    assumedNow.equipment.merge(itemID, -quantityTransferred, Integer::sum);
                    assumedNow.bank.merge(itemID, quantityTransferred, Integer::sum);
                }
            } else if (op.param1 == InterfaceID.Bankmain.DEPOSITINV) {
                for (final int itemID : assumedNow.inventory.keySet()) {
                    final int quantityTransferred = assumedNow.inventory.getOrDefault(itemID, 0);
                    assumedNow.inventory.merge(itemID, -quantityTransferred, Integer::sum);
                    assumedNow.bank.merge(itemID, quantityTransferred, Integer::sum);
                }
            } else if (op.param1 == InterfaceID.Bankmain.DEPOSITWORN) {
                for (final int itemID : assumedNow.equipment.keySet()) {
                    final int quantityTransferred = assumedNow.equipment.getOrDefault(itemID, 0);
                    assumedNow.equipment.merge(itemID, -quantityTransferred, Integer::sum);
                    assumedNow.bank.merge(itemID, quantityTransferred, Integer::sum);
                }
            } else {
                continue;
            }

            assumedCheckpoints.add(assumedNow.deepClone());
        }

        if (assumedCheckpoints.isEmpty()) {
            previousTickState = knownState.deepClone();
            return new ContainersToUpdate();
        }

        int idxOfLastBalancedCheckpoint = -1;

        final Set<Integer> itemsRequiredToBeBalanced = Set.copyOf(TackleBoxItems.getItemFilter());
        for (int checkpointIdx = assumedCheckpoints.size() - 1; checkpointIdx >= 0; checkpointIdx--) {
            final TrackedContainers checkpoint = assumedCheckpoints.get(checkpointIdx);

            final boolean checkpointIsIdenticalToPrevious;
            if (checkpointIdx > 0) {
                checkpointIsIdenticalToPrevious = checkpoint.equals(assumedCheckpoints.get(checkpointIdx - 1));
            } else {
                checkpointIsIdenticalToPrevious = checkpoint.equals(assumedStart);
            }

            if (checkpointIsIdenticalToPrevious) {
                continue;
            }

            boolean checkpointIsBalancedWithKnownState = itemsRequiredToBeBalanced.stream()
                    .allMatch(itemID -> {
                        final boolean isInventorySettled = Objects.equals(
                                checkpoint.inventory.getOrDefault(itemID, 0),
                                knownState.inventory.getOrDefault(itemID, 0));
                        final boolean isEquipmentSettled = Objects.equals(
                                checkpoint.equipment.getOrDefault(itemID, 0),
                                knownState.equipment.getOrDefault(itemID, 0));
                        final boolean isBankSettled = !isBankOpen
                                || Objects.equals(
                                        checkpoint.bank.getOrDefault(itemID, 0),
                                        knownState.bank.getOrDefault(itemID, 0));

                        return isInventorySettled && isEquipmentSettled && isBankSettled;
                    });

            if (checkpointIsBalancedWithKnownState) {
                idxOfLastBalancedCheckpoint = checkpointIdx;
                break;
            }
        }

        if (idxOfLastBalancedCheckpoint < 0 || idxOfLastBalancedCheckpoint >= assumedCheckpoints.size()) {
            previousTickState = knownState.deepClone();
            return new ContainersToUpdate();
        }

        TrackedContainers mostUpToDateCheckpoint = assumedCheckpoints.get(idxOfLastBalancedCheckpoint);

        ContainersToUpdate result = new ContainersToUpdate();

        result.bank = mostUpToDateCheckpoint.bank.entrySet().stream()
                .filter(entry -> entry.getValue() != 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        result.tackleBox = mostUpToDateCheckpoint.tackleBox.entrySet().stream()
                .filter(entry -> entry.getValue() != 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        itemOpQueue.subList(0, idxOfLastBalancedCheckpoint + 1).clear();

        /*
         * This may cause issues when the player opens the bank/tacklebox, since that could cause the next
         * invocation of this method to attempt to attribute that massive influx/outflux of items to some
         * user input.
         *
         * Hopefully not an issue: the returned ContainersToUpdate should only be copied out when the bank/tacklebox
         * are closed.
         *
         * Copying from knownState where possible helps smooth out desync.
         */
        previousTickState = new TrackedContainers(
                knownState.inventory,
                knownState.equipment,
                isBankOpen ? knownState.bank : result.bank,
                isTackleBoxOpen ? knownState.tackleBox : result.tackleBox);
        return result;
    }

    // Given a filter of item IDs, determines if there was a net loss or gain of those items from now until end.
    private enum TransferDirection {
        LOSS,
        GAIN,
        NONE
    }

    private static TransferDirection determineNetLaterMinusNow(
            Map<Integer, Integer> now, Map<Integer, Integer> later, Set<Integer> filterItemIDs) {
        int lossCount = 0;
        int gainCount = 0;

        for (final Integer itemID : filterItemIDs) {
            final int difference = later.getOrDefault(itemID, 0) - now.getOrDefault(itemID, 0);

            if (difference > 0) {
                gainCount++;
            } else if (difference < 0) {
                lossCount++;
            }
        }

        if (gainCount > lossCount) {
            return TransferDirection.GAIN;
        }

        if (gainCount < lossCount) {
            return TransferDirection.LOSS;
        }

        return TransferDirection.NONE;
    }

    private static Map<Integer, Integer> filterLaterMinusNow(
            Map<Integer, Integer> now,
            Map<Integer, Integer> later,
            Set<Integer> filterItemIDs,
            TransferDirection filterDirection) {
        final Map<Integer, Integer> result = new HashMap<>();

        if (filterDirection == TransferDirection.NONE) {
            return result;
        }

        for (final Integer itemID : filterItemIDs) {
            final int difference = later.getOrDefault(itemID, 0) - now.getOrDefault(itemID, 0);

            switch (filterDirection) {
                case LOSS:
                    if (difference < 0) {
                        result.put(itemID, difference);
                    }
                    break;
                case GAIN:
                    if (difference > 0) {
                        result.put(itemID, difference);
                    }
                    break;
            }
        }

        return result;
    }

    // Subtracts quantities from source, adds them to destination.
    // Positive quantities represent movement from source to destination, and negative vice-versa.
    private static void performTransfer(
            Map<Integer, Integer> source, Map<Integer, Integer> destination, Map<Integer, Integer> transferQuantities) {
        for (final Map.Entry<Integer, Integer> entry : transferQuantities.entrySet()) {
            final int itemID = entry.getKey();
            final int quantity = entry.getValue();

            if (quantity == 0) {
                continue;
            }
            source.merge(itemID, -quantity, Integer::sum);
            destination.merge(itemID, quantity, Integer::sum);
        }
    }

    public void onXQuerySubmitted(int quantity, int tickCount) {
        for (int opIdx = itemOpQueue.size() - 1; opIdx >= 0; opIdx--) {
            TrackedMenuOptionClicked op = itemOpQueue.get(opIdx);

            if (!op.isQueryXAction()) continue;

            if (op.getQueryXQuantity() == null) {
                op.setQueryXQuantity(quantity, tickCount);
            }

            break;
        }
    }

    public void cullQueryXActions() {
        itemOpQueue.removeIf(op -> {
            if (!op.isQueryXAction()) {
                return false;
            }

            final Integer quantity = op.getQueryXQuantity();
            return quantity == null || quantity <= 0;
        });
    }

    public void onMenuOptionClicked(Client client, MenuOptionClicked event) {
        final TrackedMenuOptionClicked op = new TrackedMenuOptionClicked(event, client.getTickCount());

        final boolean isOpTracked;
        // TODO: worn items (in deposit box, equipment, and bank). This is unlikely to matter unless the player does a
        // bunch of weird actions.
        if (op.param1 == InterfaceID.Bankside.ITEMS) {
            final boolean isDeposit = op.identifier >= 2 && op.identifier <= 8;
            final boolean isUseFillEmpty = op.identifier == 9;
            isOpTracked = isDeposit || isUseFillEmpty;
        } else if (op.param1 == InterfaceID.BankDepositbox.INVENTORY) {
            final boolean isDeposit = op.identifier >= 1 && op.identifier <= 6;
            final boolean isUseFillEmpty = op.identifier == 9;
            isOpTracked = isDeposit || isUseFillEmpty;
        } else if (op.param1 == InterfaceID.Bankmain.ITEMS) {
            isOpTracked = op.identifier >= 1 && op.identifier <= 8;
        } else if (op.param1 == InterfaceID.Inventory.ITEMS) {
            final int FILL = IDENTIFIER_FILL_BY_ITEM_ID.get(op.itemId);
            final int EMPTY = IDENTIFIER_EMPTY_BY_ITEM_ID.get(op.itemId);
            isOpTracked = op.identifier == FILL || op.identifier == EMPTY;
        } else
            isOpTracked = op.param1 == InterfaceID.BankDepositbox.DEPOSIT_INV
                    || op.param1 == InterfaceID.BankDepositbox.DEPOSIT_WORN
                    || op.param1 == InterfaceID.Bankmain.DEPOSITINV
                    || op.param1 == InterfaceID.Bankmain.DEPOSITWORN;

        if (!isOpTracked) {
            return;
        }

        itemOpQueue.add(op);
    }
}
