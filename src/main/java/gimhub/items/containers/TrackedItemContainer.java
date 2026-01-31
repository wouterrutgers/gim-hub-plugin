package gimhub.items.containers;

import gimhub.APISerializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.client.game.ItemManager;

public interface TrackedItemContainer {
    String key();

    APISerializable get();

    default void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {}

    default void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {}

    default void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {}

    default void onMenuOptionClicked(Client client, MenuOptionClicked event, ItemManager itemManager) {}

    default void onGameTick(Client client, ItemManager itemManager) {}

    default void onStatChanged(StatChanged event, ItemManager itemManager) {}

    default Map<Integer, Integer> onDepositContainers(
            Client client, ItemManager itemManager, Set<Integer> inventoryIDs) {
        return new HashMap<>();
    }
}
