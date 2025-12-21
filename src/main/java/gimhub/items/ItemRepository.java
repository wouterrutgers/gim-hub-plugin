package gimhub.items;

import gimhub.APISerializable;
import gimhub.items.containers.*;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

public class ItemRepository {
    private final TrackedItemContainer[] containers;

    public ItemRepository() {
        final InventoryItems inventory = new InventoryItems();
        final EquipmentItems equipment = new EquipmentItems();

        containers = new TrackedItemContainer[] {
            inventory,
            equipment,
            new SharedBankItems(),
            new BankItems(),
            new SeedVaultItems(),
            new PohCostumeRoomItems(),
            new RunePouchItems(),
            new QuiverItems(),
            new DepositedItems(inventory, equipment),
        };
    }

    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        for (TrackedItemContainer tracked : containers) {
            tracked.onItemContainerChanged(container, itemManager);
        }
    }

    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        for (TrackedItemContainer tracked : containers) {
            tracked.onVarbitChanged(client, varpId, varbitId, itemManager);
        }
    }

    public void onUpdateOften(Client client, ItemManager itemManager) {
        for (TrackedItemContainer tracked : containers) {
            tracked.onUpdateOften(client, itemManager);
        }
    }

    public void onGameTick(Client client) {
        for (TrackedItemContainer tracked : containers) {
            tracked.onGameTick(client);
        }
    }

    public void onDepositTriggered() {
        for (TrackedItemContainer tracked : containers) {
            tracked.onDepositTriggered();
        }
    }

    public void flatten(Map<String, APISerializable> flat) {
        for (TrackedItemContainer tracked : containers) {
            flat.put(tracked.key(), tracked.get());
        }
    }
}
