package gimhub.items;

import gimhub.APISerializable;
import gimhub.items.containers.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
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
        itemTransferQueue.onMenuOptionClicked(client, event, itemManager);

        for (TrackedItemContainer tracked : containers) {
            tracked.onMenuOptionClicked(client, event, itemManager);
        }
    }

    public void flatten(Map<String, APISerializable> flat) {
        for (TrackedItemContainer tracked : containers) {
            flat.put(tracked.key(), tracked.get());
        }
    }
}
