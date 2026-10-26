package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import gimhub.items.ItemsUtilities;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

public class SandstormItems implements TrackedItemContainer {
    protected static final Pattern CHECK = Pattern.compile(
            "I have (\\d+) of your buckets and you've ground enough sandstone for (\\d+) buckets of sand\\.");
    protected static final Pattern SAND =
            Pattern.compile("sandstone (?:equivalent to|for) (\\d+|one) buckets? of sand");
    protected static final Pattern BUCKETS = Pattern.compile("holding onto (\\d+|one) buckets? for ya");
    protected ItemsUnordered items;
    protected String previousText = "";
    protected int previousInventorySand;

    @Override
    public String key() {
        return "sandstorm";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        int inventorySand = ItemsUtilities.convertToSafeMap(client.getItemContainer(InventoryID.INV), itemManager)
                .getOrDefault(ItemID.BUCKET_SAND, 0);
        Widget widget = client.getWidget(InterfaceID.ChatLeft.TEXT);
        String text = widget == null
                ? ""
                : Text.removeTags(widget.getText().replace("<br>", " ")).replace(",", "");
        Matcher check = CHECK.matcher(text);
        if (check.find()) {
            items = new ItemsUnordered(
                    Map.of(
                            ItemID.BUCKET_EMPTY,
                            Integer.parseInt(check.group(1)),
                            ItemID.HANDSAND_SAND,
                            Integer.parseInt(check.group(2))),
                    itemManager);
        } else if (items != null && !text.equals(previousText)) {
            Map<Integer, Integer> quantities = new HashMap<>(items.getItemsQuantityByID());
            Matcher sand = SAND.matcher(text);
            Matcher buckets = BUCKETS.matcher(text);
            if (sand.find()) {
                quantities.put(ItemID.HANDSAND_SAND, sand.group(1).equals("one") ? 1 : Integer.parseInt(sand.group(1)));
            }
            if (buckets.find()) {
                quantities.put(
                        ItemID.BUCKET_EMPTY, buckets.group(1).equals("one") ? 1 : Integer.parseInt(buckets.group(1)));
            }
            if (text.startsWith("If ya need any more sand")) {
                int withdrawn = Math.max(0, inventorySand - previousInventorySand);
                quantities.replaceAll((item, quantity) -> Math.max(0, quantity - withdrawn));
            }
            items = new ItemsUnordered(quantities, itemManager);
        }
        previousText = text;
        previousInventorySand = inventorySand;
    }
}
