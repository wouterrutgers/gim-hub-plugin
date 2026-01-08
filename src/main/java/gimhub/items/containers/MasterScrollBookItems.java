package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import java.util.ArrayList;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

@Slf4j
public class MasterScrollBookItems implements TrackedItemContainer {
    private ItemsOrdered items = null;

    private static final Map<Integer, Integer> SCROLL_VARBIT_ID_TO_ITEM_ID = Map.ofEntries(
            Map.entry(VarbitID.BOOKOFSCROLLS_NARDAH, 12402),
            Map.entry(VarbitID.BOOKOFSCROLLS_DIGSITE, 12403),
            Map.entry(VarbitID.BOOKOFSCROLLS_FELDIP, 12404),
            Map.entry(VarbitID.BOOKOFSCROLLS_LUNARISLE, 12405),
            Map.entry(VarbitID.BOOKOFSCROLLS_MORTTON, 12406),
            Map.entry(VarbitID.BOOKOFSCROLLS_PESTCONTROL, 12407),
            Map.entry(VarbitID.BOOKOFSCROLLS_PISCATORIS, 12408),
            Map.entry(VarbitID.BOOKOFSCROLLS_TAIBWO, 12409),
            Map.entry(VarbitID.BOOKOFSCROLLS_ELF, 12410),
            Map.entry(VarbitID.BOOKOFSCROLLS_MOSLES, 12411),
            Map.entry(VarbitID.BOOKOFSCROLLS_LUMBERYARD, 12642),
            Map.entry(VarbitID.BOOKOFSCROLLS_ZULANDRA, 12938),
            Map.entry(VarbitID.BOOKOFSCROLLS_CERBERUS, 13249),
            Map.entry(VarbitID.BOOKOFSCROLLS_REVENANTS, 21802),
            Map.entry(VarbitID.BOOKOFSCROLLS_WATSON_HIGHBITS, 23387),
            Map.entry(VarbitID.BOOKOFSCROLLS_GUTHIXIAN_TEMPLE, 29684),
            Map.entry(VarbitID.BOOKOFSCROLLS_SPIDERCAVE, 29782),
            Map.entry(VarbitID.BOOKOFSCROLLS_COLOSSAL_WYRM, 30040),
            Map.entry(VarbitID.BOOKOFSCROLLS_CHASMOFFIRE, 30775));

    @Override
    public String key() {
        return "master_scroll_book";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private int getQuantity(Client client, int varbitId) {
        if (varbitId == VarbitID.BOOKOFSCROLLS_WATSON_HIGHBITS || varbitId == VarbitID.BOOKOFSCROLLS_WATSON_LOWBITS) {
            final int hi = client.getVarbitValue(VarbitID.BOOKOFSCROLLS_WATSON_HIGHBITS);
            final int lo = client.getVarbitValue(VarbitID.BOOKOFSCROLLS_WATSON_LOWBITS);
            return hi * 256 + lo;
        }

        return client.getVarbitValue(varbitId);
    }

    private void update(Client client, ItemManager itemManager) {
        final ArrayList<Item> sackItems = new ArrayList<>();
        for (final Map.Entry<Integer, Integer> kvp : SCROLL_VARBIT_ID_TO_ITEM_ID.entrySet()) {
            final int varbitID = kvp.getKey();
            final int itemID = kvp.getValue();

            final int quantity = getQuantity(client, varbitID);

            if (quantity > 0) {
                sackItems.add(new Item(itemID, quantity));
            }
        }
        items = new ItemsOrdered(sackItems, itemManager);
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        if (varbitId != VarbitID.BOOKOFSCROLLS_WATSON_LOWBITS && !SCROLL_VARBIT_ID_TO_ITEM_ID.containsKey(varbitId)) {
            return;
        }

        update(client, itemManager);
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        update(client, itemManager);
    }
}
