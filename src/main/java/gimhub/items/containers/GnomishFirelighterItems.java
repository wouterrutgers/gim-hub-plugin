package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

public class GnomishFirelighterItems implements TrackedItemContainer {
    protected static final Pattern CHARGES =
            Pattern.compile("([\\d,]+) (red|green|blue|purple|white) firelighter charges?");
    protected static final Map<String, Integer> COLOURS = Map.of(
            "red",
            ItemID.GNOMISH_FIRELIGHTER_RED,
            "green",
            ItemID.GNOMISH_FIRELIGHTER_GREEN,
            "blue",
            ItemID.GNOMISH_FIRELIGHTER_BLUE,
            "purple",
            ItemID.TRAIL_GNOMISH_FIRELIGHTER_PURPLE,
            "white",
            ItemID.TRAIL_GNOMISH_FIRELIGHTER_WHITE);
    protected ItemsUnordered items;

    @Override
    public String key() {
        return "gnomish_firelighter";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        if ((event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)
                && Text.removeTags(event.getMessage()).startsWith("You uncharge the gnomish firelighter")) {
            items = new ItemsUnordered();
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        Widget widget = client.getWidget(InterfaceID.Objectbox.TEXT);
        if (widget == null) {
            return;
        }
        String text = Text.removeTags(widget.getText().replace("<br>", " "));
        if (!text.contains("gnomish firelighter")) {
            return;
        }
        if (text.contains("is empty")) {
            items = new ItemsUnordered();
            return;
        }
        Matcher charges = CHARGES.matcher(text);
        if (charges.find()) {
            items = new ItemsUnordered(
                    Map.of(
                            COLOURS.get(charges.group(2)),
                            Integer.parseInt(charges.group(1).replace(",", ""))),
                    itemManager);
        }
    }
}
