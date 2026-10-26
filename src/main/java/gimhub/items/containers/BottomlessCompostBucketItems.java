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
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

public class BottomlessCompostBucketItems implements TrackedItemContainer {
    protected static final int[] COMPOST = {
        ItemID.BUCKET_COMPOST, ItemID.BUCKET_SUPERCOMPOST, ItemID.BUCKET_ULTRACOMPOST
    };
    protected static final Pattern USES = Pattern.compile("([\\d,]+|one|single) uses?");
    protected ItemsUnordered items;
    protected boolean firstTick = true;

    @Override
    public String key() {
        return "bottomless_compost_bucket";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    protected void read(String message, ItemManager itemManager) {
        String text = Text.removeTags(message.replace("<br>", " "));
        if (!text.contains("compost bucket")) {
            return;
        }
        if (text.contains("currently empty") || text.contains("run out") || text.startsWith("You discard")) {
            items = new ItemsUnordered();
            return;
        }
        Matcher uses = USES.matcher(text);
        if (!uses.find()) {
            return;
        }
        int quantity = uses.group(1).equals("one") || uses.group(1).equals("single")
                ? 1
                : Integer.parseInt(uses.group(1).replace(",", ""));
        int type = text.contains("ultracompost") ? 2 : text.contains("supercompost") ? 1 : 0;
        items = new ItemsUnordered(Map.of(COMPOST[type], quantity), itemManager);
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM) {
            read(event.getMessage(), itemManager);
        }
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        if (varbitId == VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE
                || varbitId == VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY) {
            readStoredBucket(client, itemManager);
        }
    }

    protected void readStoredBucket(Client client, ItemManager itemManager) {
        int type = client.getVarbitValue(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE);
        if (type > 0) {
            items = type == 1
                    ? new ItemsUnordered()
                    : new ItemsUnordered(
                            Map.of(
                                    COMPOST[type - 2],
                                    client.getVarbitValue(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY)),
                            itemManager);
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (firstTick) {
            firstTick = false;
            readStoredBucket(client, itemManager);
        }
        Widget widget = client.getWidget(InterfaceID.Objectbox.TEXT);
        if (widget != null) {
            read(widget.getText(), itemManager);
        }
    }
}
