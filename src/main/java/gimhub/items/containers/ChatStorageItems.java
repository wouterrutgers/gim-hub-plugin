package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import gimhub.items.ItemsUtilities;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

abstract class ChatStorageItems implements TrackedItemContainer {
    protected ItemsUnordered items;
    protected final Set<Integer> containerIds;
    protected final Set<Integer> contentIds;
    protected Map<Integer, Integer> beforeTransfer;
    protected boolean filling;
    protected int transferDeadline;
    protected Map<Integer, Integer> bankBeforeTransfer;
    protected boolean depositing;

    protected ChatStorageItems(Set<Integer> containerIds, Set<Integer> contentIds) {
        this.containerIds = containerIds;
        this.contentIds = contentIds;
    }

    @Override
    public APISerializable get() {
        return items;
    }

    protected void setItems(Map<Integer, Integer> contents, ItemManager itemManager) {
        items = new ItemsUnordered(contents, itemManager);
        beforeTransfer = null;
    }

    protected void addItem(int itemId, int quantity, ItemManager itemManager) {
        if (items == null) {
            return;
        }
        Map<Integer, Integer> contents = new HashMap<>(items.getItemsQuantityByID());
        contents.merge(itemId, quantity, Integer::sum);
        items = new ItemsUnordered(contents, itemManager);
    }

    @Override
    public void onMenuOptionClicked(Client client, MenuOptionClicked event, ItemManager itemManager) {
        if (items == null) {
            return;
        }
        Widget selected = client.getSelectedWidget();
        boolean usingItem = selected != null
                && ((containerIds.contains(event.getItemId()) && contentIds.contains(selected.getItemId()))
                        || (contentIds.contains(event.getItemId()) && containerIds.contains(selected.getItemId())));
        if ((containerIds.contains(event.getItemId())
                        && (event.getMenuOption().equals("Fill")
                                || event.getMenuOption().equals("Empty")))
                || usingItem) {
            ItemContainer inventory = client.getItemContainer(InventoryID.INV);
            if (inventory != null) {
                beforeTransfer = ItemsUtilities.convertToSafeMap(inventory, itemManager);
                transferDeadline = client.getTickCount() + 3;
                ItemContainer bank = client.getItemContainer(InventoryID.BANK);
                bankBeforeTransfer = bank == null ? null : ItemsUtilities.convertToSafeMap(bank, itemManager);
                filling = usingItem || event.getMenuOption().equals("Fill");
            }
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (beforeTransfer == null) {
            return;
        }
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) {
            beforeTransfer = null;
            return;
        }
        Map<Integer, Integer> current = ItemsUtilities.convertToSafeMap(inventory, itemManager);
        boolean changed = false;
        ItemContainer bank = client.getItemContainer(InventoryID.BANK);
        Map<Integer, Integer> currentBank = bank == null ? null : ItemsUtilities.convertToSafeMap(bank, itemManager);
        for (int itemId : contentIds) {
            int difference = beforeTransfer.getOrDefault(itemId, 0) - current.getOrDefault(itemId, 0);
            if (!filling && difference == 0 && bankBeforeTransfer != null && currentBank != null) {
                difference = bankBeforeTransfer.getOrDefault(itemId, 0) - currentBank.getOrDefault(itemId, 0);
            }
            if ((filling && difference > 0) || (!filling && difference < 0)) {
                int previous = items.getItemsQuantityByID().getOrDefault(itemId, 0);
                addItem(itemId, Math.max(-previous, difference), itemManager);
                changed = true;
            }
        }
        if (changed || client.getTickCount() >= transferDeadline) {
            beforeTransfer = null;
        }
    }

    @Override
    public Map<Integer, Integer> onDepositContainers(
            Client client, ItemManager itemManager, Set<Integer> inventoryIds) {
        depositing = inventoryIds.stream().anyMatch(containerIds::contains);
        return depositing && items != null ? Map.copyOf(items.getItemsQuantityByID()) : Map.of();
    }

    protected boolean confirmDeposit(String message, ItemManager itemManager) {
        if (depositing && message.equals("You empty all of your containers into the bank.")) {
            depositing = false;
            setItems(Map.of(), itemManager);
            return true;
        }
        return false;
    }
}
