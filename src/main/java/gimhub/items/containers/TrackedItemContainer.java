package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import javax.annotation.Nullable;
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

    @Nullable default ItemContainerInterface itemContainerInterface() {
        return null;
    }

    // Quantities are added. Will change as much as possible, and return the overage quantity per item that could not be
    // used.
    // Sign of overage is preserved from the input sign.
    // Positive signs indicate increasing items, negative indicate removing items.
    // Overage occurs when containers become empty, don't fit an item, etc.
    @Nullable default ItemsUnordered modify(ItemsUnordered itemsToDeposit) {
        return null;
    }
}
