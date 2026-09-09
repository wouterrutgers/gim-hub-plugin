package gimhub.items.containers;

import gimhub.items.ItemsUnordered;
import gimhub.items.ItemsUtilities;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

public class LootingBagItems extends ContainerItems {
    protected Map<Integer, Integer> beforeTransfer;
    protected Integer usedItem;
    protected int transferDeadline;

    public LootingBagItems() {
        super(InventoryID.LOOTING_BAG, InterfaceID.WILDERNESS_LOOTINGBAG, ItemID.LOOTING_BAG, ItemID.LOOTING_BAG_OPEN);
    }

    @Override
    public String key() {
        return "looting_bag";
    }

    @Override
    public void onMenuOptionClicked(Client client, MenuOptionClicked event, ItemManager itemManager) {
        if (items == null) {
            return;
        }
        Widget selected = client.getSelectedWidget();
        if (selected != null
                && (containerIds.contains(selected.getItemId()) || containerIds.contains(event.getItemId()))) {
            ItemContainer inventory = client.getItemContainer(InventoryID.INV);
            if (inventory != null) {
                usedItem = containerIds.contains(event.getItemId()) ? selected.getItemId() : event.getItemId();
                beforeTransfer = ItemsUtilities.convertToSafeMap(inventory, itemManager);
                transferDeadline = client.getTickCount() + 3;
            }
        } else if (!event.getMenuOption().equals("Use")
                && !event.getMenuOption().equals("Examine")) {
            usedItem = null;
            beforeTransfer = null;
        }
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        super.onChatMessage(client, event, itemManager);
        if (event.getMessage().startsWith("You can't put items in the looting bag")) {
            usedItem = null;
            beforeTransfer = null;
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        boolean inspect = pending || firstTick;
        super.onGameTick(client, itemManager);
        Widget title = client.getWidget(InterfaceID.WildernessLootingbag.TITLE);
        boolean adding = title != null && title.getText().equals("Add to bag");
        if (items != null && (adding || beforeTransfer != null)) {
            ItemContainer inventory = client.getItemContainer(InventoryID.INV);
            if (inventory != null) {
                Map<Integer, Integer> current = ItemsUtilities.convertToSafeMap(inventory, itemManager);
                if (beforeTransfer != null && !authoritativeUpdate) {
                    Map<Integer, Integer> contents = new HashMap<>(items.getItemsQuantityByID());
                    for (Map.Entry<Integer, Integer> previous : beforeTransfer.entrySet()) {
                        int quantity = previous.getValue() - current.getOrDefault(previous.getKey(), 0);
                        if (quantity > 0
                                && !containerIds.contains(previous.getKey())
                                && (adding || previous.getKey().equals(usedItem))) {
                            contents.merge(previous.getKey(), quantity, Integer::sum);
                        }
                    }
                    items = new ItemsUnordered(contents, itemManager);
                }
                beforeTransfer = current;
            }
            Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
            boolean choosingQuantity = options != null
                    && options.getChild(0) != null
                    && options.getChild(0).getText().equals("How many do you want to deposit?");
            if (choosingQuantity) {
                transferDeadline = client.getTickCount() + 3;
            }
            if (!adding && client.getTickCount() >= transferDeadline) {
                beforeTransfer = null;
                usedItem = null;
            }
        }
        authoritativeUpdate = false;
        if (inspect) {
            Widget widget = client.getWidget(InterfaceID.WildernessLootingbag.ITEMS);
            if (widget != null
                    && widget.getChild(28) != null
                    && "The bag is empty.".equals(widget.getChild(28).getText())) {
                items = new ItemsUnordered();
                pending = false;
            }
        }
    }
}
