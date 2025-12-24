package gimhub.items.containers;

import gimhub.APISerializable;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

public interface TrackedItemContainer {
    String key();

    APISerializable get();

    default void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {}

    default void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {}

    default void onGameTick(Client client, ItemManager itemManager) {}

    default void onDepositTriggered() {}
}
