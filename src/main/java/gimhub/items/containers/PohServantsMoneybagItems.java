package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

public class PohServantsMoneybagItems implements TrackedItemContainer {
    private ItemsUnordered items;

    @Override
    public String key() {
        return "poh_servants_moneybag";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        Widget widget = client.getWidget(InterfaceID.Objectbox.TEXT);
        if (widget == null || widget.isHidden()) {
            return;
        }

        String message = Text.removeTags(widget.getText());
        if (!message.startsWith("The money bag ")) {
            return;
        }

        String coins = message.replaceAll("\\D", "");
        items = new ItemsUnordered(Map.of(ItemID.COINS, coins.isEmpty() ? 0 : Integer.parseInt(coins)), itemManager);
    }
}
