package gimhub.items;

import gimhub.APISerializable;
import gimhub.items.containers.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.*;
import net.runelite.api.gameval.*;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ItemRepository {
    private final List<TrackedItemContainer> allContainers;

    private final BankItems bank = new BankItems();
    private final TackleBoxItems tackleBox = new TackleBoxItems();

    private final ItemTransferQueue itemTransferQueue = new ItemTransferQueue();

    private final TrackedItemContainer[] depositButtonContainers;
    private Map<Integer, Integer> depositButtonItemsStaged = null;
    private static final String DEPOSIT_CONTAINERS_MESSAGE = "You empty all of your containers into the bank.";

    public ItemRepository() {
        FishBarrelItems fishBarrel = new FishBarrelItems();
        fishBarrel.setBank(bank);

        depositButtonContainers = new TrackedItemContainer[] {
            // We are missing Herb sacks, Meat pouches, Log baskets, Reagent pouches, Fur pouches and Looting bags here
            new EssencePouchesItems(), new CoalBagItems(), fishBarrel, new PlankSackItems(),
        };
        final TrackedItemContainer[] otherContainers = new TrackedItemContainer[] {
            bank,
            new InventoryItems(),
            new EquipmentItems(),
            new SharedBankItems(),
            new PotionStorageItems(),
            new SeedVaultItems(),
            new PohCostumeRoomItems(),
            new RunePouchItems(),
            new QuiverItems(),
            new MasterScrollBookItems(),
            tackleBox,
            fishBarrel,
        };

        allContainers = Stream.of(Stream.of(otherContainers), Stream.of(depositButtonContainers))
                .flatMap(s -> s)
                .collect(Collectors.toList());
    }

    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        for (TrackedItemContainer tracked : allContainers) {
            tracked.onItemContainerChanged(container, itemManager);
        }
    }

    public void onStatChanged(StatChanged event, ItemManager itemManager) {
        for (TrackedItemContainer tracked : allContainers) {
            tracked.onStatChanged(event, itemManager);
        }
    }

    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        for (TrackedItemContainer tracked : allContainers) {
            tracked.onVarbitChanged(client, varpId, varbitId, itemManager);
        }
    }

    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        final ChatMessageType type = event.getType();
        final String msg = event.getMessage();

        if (type == ChatMessageType.GAMEMESSAGE
                && msg != null
                && msg.contains(DEPOSIT_CONTAINERS_MESSAGE)
                && depositButtonItemsStaged != null) {
            // The message event comes AFTER all the varbits change, so that's why we staged the items already.

            // TODO: do we need to credit these items to ItemTransferQueue to help bookkeeping?
            bank.addItems(depositButtonItemsStaged, itemManager);

            depositButtonItemsStaged = null;
        }

        for (TrackedItemContainer tracked : allContainers) {
            tracked.onChatMessage(client, event, itemManager);
        }
    }

    public void onGameTick(Client client, ItemManager itemManager) {
        final boolean bankIsOpen = client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null;
        final boolean tackleBoxIsOpen = client.getWidget(InterfaceID.TackleBoxMain.UNIVERSE) != null;

        final ItemTransferQueue.TrackedContainers knownState = new ItemTransferQueue.TrackedContainers(
                ItemsUtilities.convertToSafeMap(client.getItemContainer(InventoryID.INV), itemManager),
                ItemsUtilities.convertToSafeMap(client.getItemContainer(InventoryID.WORN), itemManager),
                bank.getBankItems(),
                tackleBox.getTackleBoxItems());

        ItemTransferQueue.ContainersToUpdate updates = itemTransferQueue.onGameTick(
                knownState,
                new ItemTransferQueue.BankSettings(client),
                client.getTickCount(),
                bankIsOpen,
                tackleBoxIsOpen);

        if (!bankIsOpen && updates.bank != null) {
            bank.setItems(updates.bank, itemManager);
        }
        if (!tackleBoxIsOpen && updates.tackleBox != null) {
            tackleBox.setItems(updates.tackleBox, itemManager);
        }

        for (TrackedItemContainer tracked : allContainers) {
            tracked.onGameTick(client, itemManager);
        }
    }

    public void onMenuOptionClicked(Client client, MenuOptionClicked event, ItemManager itemManager) {
        final boolean clickedDepositContainersButton =
                event.getParam1() == InterfaceID.BankDepositbox.DEPOSIT_LOOTINGBAG
                        || event.getParam1() == InterfaceID.Bankmain.DEPOSITCONTAINERS;
        if (clickedDepositContainersButton && depositButtonItemsStaged == null) {
            final ItemContainer inventory = client.getItemContainer(InventoryID.INV);
            if (inventory != null) {
                final Set<Integer> inventoryIDs = Arrays.stream(inventory.getItems())
                        .map(Item::getId)
                        .filter(itemID -> itemID >= 0)
                        .collect(Collectors.toSet());

                final Map<Integer, Integer> depositedItems = new HashMap<>();
                for (TrackedItemContainer container : depositButtonContainers) {
                    final Map<Integer, Integer> containerItems =
                            container.onDepositContainers(client, itemManager, inventoryIDs);
                    for (final Map.Entry<Integer, Integer> entry : containerItems.entrySet()) {
                        depositedItems.merge(entry.getKey(), entry.getValue(), Integer::sum);
                    }
                }
                depositButtonItemsStaged = depositedItems;
            }
            return;
        }

        itemTransferQueue.onMenuOptionClicked(client, event);

        for (TrackedItemContainer tracked : allContainers) {
            tracked.onMenuOptionClicked(client, event, itemManager);
        }
    }

    private static final int MESLAYERMODE_XQUERY = 7;

    public void onScriptPostFired(Client client, ScriptPostFired event) {
        // This script converts strings with "K" "M" and "B" to a format without them
        final int PROCESS_STRING_SCRIPT = 212;
        final boolean isXQueryOpen = client.getVarcIntValue(VarClientID.MESLAYERMODE) == MESLAYERMODE_XQUERY;
        final Object scriptReturnValue = client.getObjectStack()[0];

        if (event.getScriptId() == PROCESS_STRING_SCRIPT && isXQueryOpen && scriptReturnValue instanceof String) {
            final int xQueryQuantity = Integer.parseInt((String) scriptReturnValue);
            itemTransferQueue.onXQuerySubmitted(xQueryQuantity, client.getTickCount());
        }
    }

    private int mesLayerMode = 0;

    public void onVarClientIntChanged(Client client, VarClientIntChanged event) {
        if (event.getIndex() != VarClientID.MESLAYERMODE) return;

        final int oldMode = mesLayerMode;
        final int newMode = client.getVarcIntValue(VarClientID.MESLAYERMODE);

        mesLayerMode = newMode;

        if (oldMode == MESLAYERMODE_XQUERY && newMode != MESLAYERMODE_XQUERY) {
            itemTransferQueue.cullQueryXActions();
        }
    }

    public void flatten(Map<String, APISerializable> flat) {
        for (TrackedItemContainer tracked : allContainers) {
            flat.put(tracked.key(), tracked.get());
        }
    }
}
