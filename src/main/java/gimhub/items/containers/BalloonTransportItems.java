package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.HashMap;
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

public class BalloonTransportItems implements TrackedItemContainer {
    protected static final int[] LOGS = {
        ItemID.LOGS, ItemID.OAK_LOGS, ItemID.WILLOW_LOGS, ItemID.YEW_LOGS, ItemID.MAGIC_LOGS
    };
    protected static final Pattern CHECK = Pattern.compile(
            "This crate currently contains (\\d+) logs, (\\d+) oak logs, (\\d+) willow logs, (\\d+) yew logs and (\\d+) magic logs\\.");
    protected static final Pattern DEPOSIT =
            Pattern.compile("You put the (.+) in the crate\\. You now have (\\d+) stored\\.");
    protected static final Pattern TRAVEL = Pattern.compile("You have (\\d+|one) sets? of (.+) left in storage\\.");
    protected static final Pattern EXHAUSTED = Pattern.compile("You used the last of your (.+)\\.");
    protected final Map<Integer, Integer> known = new HashMap<>();
    protected ItemsUnordered items;

    @Override
    public String key() {
        return "balloon_transport";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    protected void read(String message, ItemManager itemManager) {
        String text = Text.removeTags(message.replace("<br>", " ")).replaceAll("(?<=\\d),(?=\\d)", "");
        Matcher check = CHECK.matcher(text);
        if (check.find()) {
            for (int index = 0; index < LOGS.length; index++) {
                known.put(LOGS[index], Integer.parseInt(check.group(index + 1)));
            }
            items = new ItemsUnordered(known, itemManager);
            return;
        }
        Matcher deposit = DEPOSIT.matcher(text);
        Matcher travel = TRAVEL.matcher(text);
        Matcher exhausted = EXHAUSTED.matcher(text);
        if (deposit.find()) {
            update(deposit.group(1), Integer.parseInt(deposit.group(2)), itemManager);
        } else if (travel.find()) {
            update(travel.group(2), travel.group(1).equals("one") ? 1 : Integer.parseInt(travel.group(1)), itemManager);
        } else if (exhausted.find()) {
            update(exhausted.group(1), 0, itemManager);
        }
    }

    protected void update(String name, int quantity, ItemManager itemManager) {
        for (int identifier : LOGS) {
            String logName = itemManager.getItemComposition(identifier).getName();
            if (logName.equalsIgnoreCase(name)
                    || logName.equalsIgnoreCase(name + " logs")
                    || (identifier == ItemID.LOGS
                            && (name.equalsIgnoreCase("normal") || name.equalsIgnoreCase("regular")))) {
                known.put(identifier, quantity);
                if (known.size() == LOGS.length) {
                    items = new ItemsUnordered(known, itemManager);
                }
                return;
            }
        }
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM) {
            read(event.getMessage(), itemManager);
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        for (int component : new int[] {InterfaceID.Messagebox.TEXT, InterfaceID.Objectbox.TEXT}) {
            Widget widget = client.getWidget(component);
            if (widget != null) {
                read(widget.getText(), itemManager);
            }
        }
    }
}
