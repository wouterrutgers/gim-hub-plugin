package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ElnockInquisitorItems implements TrackedItemContainer {
    private ItemsUnordered items = null;

    @Override
    public String key() {
        return "elnock_inquisitor";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private static boolean isRelevantVarbit(int varbitId) {
        return varbitId == VarbitID.II_STORED_REPELLENT
                || varbitId == VarbitID.II_STORED_IMPLING_JARS
                || varbitId == VarbitID.II_STORED_NET;
    }

    private void update(Client client, ItemManager itemManager) {
        // Refer to client script 2259

        Map<Integer, Integer> quantities = new HashMap<>();
        quantities.put(ItemID.II_IMP_REPELLENT, client.getVarbitValue(VarbitID.II_STORED_REPELLENT));
        quantities.put(ItemID.II_IMPLING_JAR, client.getVarbitValue(VarbitID.II_STORED_IMPLING_JARS));

        final int storedNet = client.getVarbitValue(VarbitID.II_STORED_NET);
        switch (storedNet) {
            case 1:
                quantities.put(ItemID.HUNTING_BUTTERFLY_NET, 1);
                break;
            case 2:
                quantities.put(ItemID.II_MAGIC_BUTTERFLY_NET, 1);
                break;
        }

        items = new ItemsUnordered(quantities, itemManager);
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        if (isRelevantVarbit(varbitId)) {
            update(client, itemManager);
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        update(client, itemManager);
    }
}
