package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.game.ItemManager;

abstract class ContainerItems implements TrackedItemContainer {
    protected ItemsUnordered items;
    protected final int inventoryId;
    protected final int interfaceId;
    protected final Set<Integer> containerIds;
    protected boolean depositing;
    protected boolean pending;
    protected boolean authoritativeUpdate;
    protected boolean firstTick = true;

    protected ContainerItems(int inventoryId, int interfaceId, Integer... containerIds) {
        this.inventoryId = inventoryId;
        this.interfaceId = interfaceId;
        this.containerIds = Set.of(containerIds);
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container != null && container.getId() == inventoryId) {
            items = new ItemsUnordered(container, itemManager);
            authoritativeUpdate = true;
        }
    }

    @Override
    public Map<Integer, Integer> onDepositContainers(
            Client client, ItemManager itemManager, Set<Integer> inventoryIds) {
        depositing = inventoryIds.stream().anyMatch(containerIds::contains);
        return depositing && items != null ? Map.copyOf(items.getItemsQuantityByID()) : Map.of();
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        if (depositing
                && event.getType() == ChatMessageType.GAMEMESSAGE
                && event.getMessage().equals("You empty all of your containers into the bank.")) {
            items = new ItemsUnordered();
            authoritativeUpdate = true;
            depositing = false;
        }
    }

    @Override
    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == interfaceId) {
            pending = true;
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (firstTick) {
            firstTick = false;
            pending = client.getWidget(interfaceId, 0) != null;
        }
        if (!pending) {
            return;
        }
        if (client.getWidget(interfaceId, 0) == null) {
            pending = false;
            return;
        }
        ItemContainer container = client.getItemContainer(inventoryId);
        if (container != null) {
            items = new ItemsUnordered(container, itemManager);
            authoritativeUpdate = true;
            pending = false;
        }
    }
}
