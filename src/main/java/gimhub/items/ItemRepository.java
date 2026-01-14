package gimhub.items;

import gimhub.APISerializable;
import gimhub.items.containers.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ItemRepository {
    private final TrackedItemContainer[] containers;
    // Mapping of ItemID -> the container that is controlled by that item, e.g. the Tackle Box
    private final Map<Integer, TrackedItemContainer> containersControlledByItem;
    private final List<TrackedItemOperation> itemOpQueue;

    // TODO: reencapsulate deposited items?
    private static final int GAME_TICKS_FOR_DEPOSIT_DETECTION = 2;
    private final BankItems bank;
    private final InventoryItems inventory;
    private final EquipmentItems equipment;

    private ItemsUnordered equipmentOneTickAgo = null;
    private ItemsUnordered inventoryOneTickAgo = null;

    public ItemRepository() {
        bank = new BankItems();
        inventory = new InventoryItems();
        equipment = new EquipmentItems();

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
            new TackleBoxItems(),
        };

        containersControlledByItem = new HashMap<>();
        for (final TrackedItemContainer container : containers) {
            final ItemContainerInterface item = container.itemContainerInterface();
            if (item == null) continue;

            if (containersControlledByItem.containsKey(item.itemID)) {
                log.error("Found duplicate container itemID: {}", item.itemID);
                continue;
            }

            if (Objects.equals(item.fillItemOp, item.emptyItemOp)
                    || Objects.equals(item.fillItemOp, item.viewItemOp)
                    || Objects.equals(item.emptyItemOp, item.viewItemOp)) {
                log.error("Colliding ops for container of itemID: {}", item.itemID);
                continue;
            }

            containersControlledByItem.put(item.itemID, container);
        }

        itemOpQueue = new ArrayList<>();
    }

    private enum ItemOp {
        FILL,
        EMPTY,
    }

    /**
     * Represents an interaction the player did with an item container. For example, clicking the deposit button, or
     * clicking "Fill" on an item.
     */
    private static final class TrackedItemOperation {
        public final int serverTick;
        public final ItemOp itemOp;
        public final TrackedItemContainer container;

        public TrackedItemOperation(int serverTick, ItemOp itemOp, TrackedItemContainer container) {
            this.serverTick = serverTick;
            this.itemOp = itemOp;
            this.container = container;
        }
    }

    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        for (TrackedItemContainer tracked : containers) {
            tracked.onItemContainerChanged(container, itemManager);
        }
    }

    /**
     * Indicates that an item menu option was clicked. This flags that we need to watch for items moving to/from a
     * container.
     */
    public void onInventoryItemClicked(int serverTick, int itemID, int itemOp) {
        final TrackedItemContainer container = containersControlledByItem.get(itemID);
        if (container == null) return;

        final ItemContainerInterface itemContainerInterface = container.itemContainerInterface();
        if (itemContainerInterface == null) return;

        if (itemOp == itemContainerInterface.fillItemOp) {
            itemOpQueue.add(new TrackedItemOperation(serverTick, ItemOp.FILL, container));
        } else if (itemOp == itemContainerInterface.emptyItemOp) {
            itemOpQueue.add(new TrackedItemOperation(serverTick, ItemOp.EMPTY, container));
        }
    }

    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        for (TrackedItemContainer tracked : containers) {
            tracked.onVarbitChanged(client, varpId, varbitId, itemManager);
        }
    }

    public void onGameTick(Client client, ItemManager itemManager) {
        final int serverTick = client.getTickCount();
        // Delete operations more than 2 ticks old, at which point we assume clicking the item resulted in no inventory
        // changes.
        final Set<TrackedItemOperation> outdatedOps = itemOpQueue.stream()
                .filter(op -> op.serverTick < serverTick - GAME_TICKS_FOR_DEPOSIT_DETECTION)
                .collect(Collectors.toSet());
        itemOpQueue.removeAll(outdatedOps);

        ItemsUnordered equipmentThisTick = new ItemsUnordered(equipment.getItems());
        ItemsUnordered inventoryThisTick = new ItemsUnordered(inventory.getItems());

        // We subtract old - new, since this reflects the changes we will see in the containers.
        // For example, if you're filling a plank sack, it will gain +N planks, while the inventory gets -N planks.
        ItemsUnordered inventoryDifference = ItemsUnordered.subtract(inventoryOneTickAgo, inventoryThisTick);
        ItemsUnordered equipmentDifference = ItemsUnordered.subtract(equipmentOneTickAgo, equipmentThisTick);
        if (!equipmentDifference.isEmpty() || !inventoryDifference.isEmpty()) {
            // TODO: Consider whether we need to consider if items were transferred within one game tick or during lag

            final Set<TrackedItemOperation> consumedOperations = new HashSet<>();

            for (int idx = 0; idx < itemOpQueue.size(); idx++) {
                final TrackedItemOperation containerOperation = itemOpQueue.get(idx);

                final boolean isBankDeposit = containerOperation.itemOp == null;

                final ItemsUnordered attributableInventoryDifference =
                        ItemsUnordered.filter(inventoryDifference, (itemID, quantity) -> {
                            if (isBankDeposit) {
                                return Math.max(quantity, 0);
                            }

                            switch (containerOperation.itemOp) {
                                case FILL:
                                    // We are filling a container, so only positive changes are possible
                                    return Math.max(quantity, 0);
                                case EMPTY:
                                    // We are emptying a container, so only negative changes are possible.
                                    return Math.min(quantity, 0);
                            }
                            return 0;
                        });
                final ItemsUnordered attributableEquipmentDifference =
                        ItemsUnordered.filter(equipmentDifference, (itemID, quantity) -> {
                            if (isBankDeposit) {
                                return Math.max(quantity, 0);
                            }

                            return 0;
                        });
                if (attributableEquipmentDifference.isEmpty() && attributableInventoryDifference.isEmpty()) continue;
                inventoryDifference = ItemsUnordered.subtract(inventoryDifference, attributableInventoryDifference);
                equipmentDifference = ItemsUnordered.subtract(equipmentDifference, attributableEquipmentDifference);

                {
                    final ItemsUnordered overage = containerOperation.container.modify(attributableInventoryDifference);
                    inventoryDifference = ItemsUnordered.add(inventoryDifference, overage);
                }
                {
                    final ItemsUnordered overage = containerOperation.container.modify(attributableEquipmentDifference);
                    equipmentDifference = ItemsUnordered.add(equipmentDifference, overage);
                }

                final Set<TrackedItemOperation> staleOperations = IntStream.range(0, idx)
                        .mapToObj(itemOpQueue::get)
                        .filter(op -> Objects.equals(op.container, containerOperation.container))
                        .collect(Collectors.toSet());
                consumedOperations.add(containerOperation);
                consumedOperations.addAll(staleOperations);
            }

            itemOpQueue.removeAll(consumedOperations);
        }

        if (!inventoryDifference.isEmpty()) {
            log.debug(
                    "Item amount desync. Either a series of item container changes occurred that were too complicated for us to follow, or the client interacted with an container-like item we don't track.");
        }

        equipmentOneTickAgo = new ItemsUnordered(equipmentThisTick);
        inventoryOneTickAgo = new ItemsUnordered(inventoryThisTick);

        for (TrackedItemContainer tracked : containers) {
            tracked.onGameTick(client, itemManager);
        }
    }

    public void onDepositTriggered(int serverTick) {
        itemOpQueue.add(new TrackedItemOperation(serverTick, ItemOp.FILL, bank));
    }

    public void flatten(Map<String, APISerializable> flat) {
        for (TrackedItemContainer tracked : containers) {
            flat.put(tracked.key(), tracked.get());
        }
    }
}
