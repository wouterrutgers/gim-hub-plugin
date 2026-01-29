package gimhub.items;

import gimhub.APISerializable;
import gimhub.items.containers.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.*;
import net.runelite.api.gameval.*;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ItemRepository {
    private final TrackedItemContainer[] containers;

    private final BankItems bank = new BankItems();
    private final TackleBoxItems tackleBox = new TackleBoxItems();

    private final ItemTransferQueue itemTransferQueue = new ItemTransferQueue();

    public ItemRepository() {
        FishBarrelItems fishBarrel = new FishBarrelItems();
        fishBarrel.setBank(bank);

        containers = new TrackedItemContainer[] {
            bank,
            new InventoryItems(),
            new EquipmentItems(),
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

        for (TrackedItemContainer tracked : containers) {
            tracked.onGameTick(client, itemManager);
        }
    }

    public void onMenuOptionClicked(Client client, MenuOptionClicked event, ItemManager itemManager) {
        itemTransferQueue.onMenuOptionClicked(client, event);

        for (TrackedItemContainer tracked : containers) {
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
        for (TrackedItemContainer tracked : containers) {
            flat.put(tracked.key(), tracked.get());
        }
    }
}
