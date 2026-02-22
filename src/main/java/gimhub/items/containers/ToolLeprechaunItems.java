package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

@Slf4j
public class ToolLeprechaunItems implements TrackedItemContainer {
    private ItemsUnordered items = null;

    @Override
    public String key() {
        return "tool_leprechaun";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    private static boolean isRelevantVarbit(int varbitId) {
        return varbitId == VarbitID.FARMING_TOOLS_RAKE
                || varbitId == VarbitID.FARMING_TOOLS_EXTRARAKES
                || varbitId == VarbitID.FARMING_TOOLS_DIBBER
                || varbitId == VarbitID.FARMING_TOOLS_EXTRADIBBERS
                || varbitId == VarbitID.FARMING_TOOLS_SPADE
                || varbitId == VarbitID.FARMING_TOOLS_EXTRASPADES
                || varbitId == VarbitID.FARMING_TOOLS_TROWEL
                || varbitId == VarbitID.FARMING_TOOLS_EXTRATROWELS
                || varbitId == VarbitID.FARMING_TOOLS_FAIRYSECATEURS
                || varbitId == VarbitID.FARMING_TOOLS_SECATEURS
                || varbitId == VarbitID.FARMING_TOOLS_EXTRASECATEURS
                || varbitId == VarbitID.FARMING_TOOLS_WATERINGCAN
                || varbitId == VarbitID.FARMING_TOOLS_BUCKETS
                || varbitId == VarbitID.FARMING_TOOLS_EXTRABUCKETS
                || varbitId == VarbitID.FARMING_TOOLS_EXTRA2BUCKETS
                || varbitId == VarbitID.FARMING_TOOLS_COMPOST
                || varbitId == VarbitID.FARMING_TOOLS_EXTRACOMPOST
                || varbitId == VarbitID.FARMING_TOOLS_SUPERCOMPOST
                || varbitId == VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST
                || varbitId == VarbitID.FARMING_TOOLS_ULTRACOMPOST;
    }

    static final int ENUM_WATERING_CANS = 136;

    private void update(Client client, ItemManager itemManager) {
        // Refer to client script 1063 for how the varbits should be combined

        Map<Integer, Integer> quantities = new HashMap<>();
        quantities.put(
                ItemID.RAKE,
                2 * client.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRARAKES)
                        + client.getVarbitValue(VarbitID.FARMING_TOOLS_RAKE));
        quantities.put(
                ItemID.DIBBER,
                2 * client.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRADIBBERS)
                        + client.getVarbitValue(VarbitID.FARMING_TOOLS_DIBBER));
        quantities.put(
                ItemID.SPADE,
                2 * client.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRASPADES)
                        + client.getVarbitValue(VarbitID.FARMING_TOOLS_SPADE));
        quantities.put(
                ItemID.TROWEL,
                2 * client.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRATROWELS)
                        + client.getVarbitValue(VarbitID.FARMING_TOOLS_TROWEL));

        final boolean isFairy = client.getVarbitValue(VarbitID.FARMING_TOOLS_FAIRYSECATEURS) == 1;
        quantities.put(
                isFairy ? ItemID.FAIRY_ENCHANTED_SECATEURS : ItemID.SECATEURS,
                2 * client.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRASECATEURS)
                        + client.getVarbitValue(VarbitID.FARMING_TOOLS_SECATEURS));

        quantities.put(
                ItemID.BUCKET_EMPTY,
                (1 << 8) * client.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRA2BUCKETS)
                        + (1 << 5) * client.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRABUCKETS)
                        + client.getVarbitValue(VarbitID.FARMING_TOOLS_BUCKETS));

        quantities.put(
                ItemID.BUCKET_COMPOST,
                (1 << 8) * client.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRACOMPOST)
                        + client.getVarbitValue(VarbitID.FARMING_TOOLS_COMPOST));

        quantities.put(
                ItemID.BUCKET_SUPERCOMPOST,
                (1 << 8) * client.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST)
                        + client.getVarbitValue(VarbitID.FARMING_TOOLS_SUPERCOMPOST));

        quantities.put(ItemID.BUCKET_ULTRACOMPOST, client.getVarbitValue(VarbitID.FARMING_TOOLS_ULTRACOMPOST));

        final int bottomlessBucketType = client.getVarbitValue(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE);
        if (bottomlessBucketType == 1) {
            quantities.put(ItemID.BOTTOMLESS_COMPOST_BUCKET, 1);
        } else if (bottomlessBucketType > 1) {
            quantities.put(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 1);
        }

        final int wateringCanEnumKey = client.getVarbitValue(VarbitID.FARMING_TOOLS_WATERINGCAN);
        if (wateringCanEnumKey >= 0) {
            final int wateringCanID = client.getEnum(ENUM_WATERING_CANS).getIntValue(wateringCanEnumKey);
            quantities.put(wateringCanID, 1);
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
