package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsOrdered;
import java.util.ArrayList;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

@Slf4j
public class MasterScrollBookItems implements TrackedItemContainer {
    private ItemsOrdered items = null;

    private static final Map<Integer, Integer> SCROLL_VARBIT_ID_TO_ITEM_ID = Map.ofEntries(
            Map.entry(VarbitID.BOOKOFSCROLLS_NARDAH, ItemID.TELEPORTSCROLL_NARDAH),
            Map.entry(VarbitID.BOOKOFSCROLLS_DIGSITE, ItemID.TELEPORTSCROLL_DIGSITE),
            Map.entry(VarbitID.BOOKOFSCROLLS_FELDIP, ItemID.TELEPORTSCROLL_FELDIP),
            Map.entry(VarbitID.BOOKOFSCROLLS_LUNARISLE, ItemID.TELEPORTSCROLL_LUNARISLE),
            Map.entry(VarbitID.BOOKOFSCROLLS_MORTTON, ItemID.TELEPORTSCROLL_MORTTON),
            Map.entry(VarbitID.BOOKOFSCROLLS_PESTCONTROL, ItemID.TELEPORTSCROLL_PESTCONTROL),
            Map.entry(VarbitID.BOOKOFSCROLLS_PISCATORIS, ItemID.TELEPORTSCROLL_PISCATORIS),
            Map.entry(VarbitID.BOOKOFSCROLLS_TAIBWO, ItemID.TELEPORTSCROLL_TAIBWO),
            Map.entry(VarbitID.BOOKOFSCROLLS_ELF, ItemID.TELEPORTSCROLL_ELF),
            Map.entry(VarbitID.BOOKOFSCROLLS_MOSLES, ItemID.TELEPORTSCROLL_MOSLES),
            Map.entry(VarbitID.BOOKOFSCROLLS_LUMBERYARD, ItemID.TELEPORTSCROLL_LUMBERYARD),
            Map.entry(VarbitID.BOOKOFSCROLLS_ZULANDRA, ItemID.TELEPORTSCROLL_ZULANDRA),
            Map.entry(VarbitID.BOOKOFSCROLLS_CERBERUS, ItemID.TELEPORTSCROLL_CERBERUS),
            Map.entry(VarbitID.BOOKOFSCROLLS_REVENANTS, ItemID.TELEPORTSCROLL_REVENANTS),
            Map.entry(VarbitID.BOOKOFSCROLLS_WATSON_HIGHBITS, ItemID.TELEPORTSCROLL_WATSON),
            Map.entry(VarbitID.BOOKOFSCROLLS_GUTHIXIAN_TEMPLE, ItemID.TELEPORTSCROLL_GUTHIXIAN_TEMPLE),
            Map.entry(VarbitID.BOOKOFSCROLLS_SPIDERCAVE, ItemID.TELEPORTSCROLL_SPIDERCAVE),
            Map.entry(VarbitID.BOOKOFSCROLLS_COLOSSAL_WYRM, ItemID.TELEPORTSCROLL_COLOSSAL_WYRM),
            Map.entry(VarbitID.BOOKOFSCROLLS_CHASMOFFIRE, ItemID.TELEPORTSCROLL_CHASMOFFIRE));

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
