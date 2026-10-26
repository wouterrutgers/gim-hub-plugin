package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

public class CargoHoldItems implements TrackedItemContainer {
    protected static final int[] INVENTORIES = {
        InventoryID.SAILING_BOAT_1_CARGOHOLD, InventoryID.SAILING_BOAT_2_CARGOHOLD,
        InventoryID.SAILING_BOAT_3_CARGOHOLD, InventoryID.SAILING_BOAT_4_CARGOHOLD,
        InventoryID.SAILING_BOAT_5_CARGOHOLD
    };
    protected static final int[] OWNED = {
        VarbitID.SAILING_BOAT_1_OWNED, VarbitID.SAILING_BOAT_2_OWNED,
        VarbitID.SAILING_BOAT_3_OWNED, VarbitID.SAILING_BOAT_4_OWNED,
        VarbitID.SAILING_BOAT_5_OWNED
    };
    protected static final int[][] NAMES = {
        {VarbitID.SAILING_BOAT_1_NAME_1, VarbitID.SAILING_BOAT_1_NAME_2, VarbitID.SAILING_BOAT_1_NAME_3},
        {VarbitID.SAILING_BOAT_2_NAME_1, VarbitID.SAILING_BOAT_2_NAME_2, VarbitID.SAILING_BOAT_2_NAME_3},
        {VarbitID.SAILING_BOAT_3_NAME_1, VarbitID.SAILING_BOAT_3_NAME_2, VarbitID.SAILING_BOAT_3_NAME_3},
        {VarbitID.SAILING_BOAT_4_NAME_1, VarbitID.SAILING_BOAT_4_NAME_2, VarbitID.SAILING_BOAT_4_NAME_3},
        {VarbitID.SAILING_BOAT_5_NAME_1, VarbitID.SAILING_BOAT_5_NAME_2, VarbitID.SAILING_BOAT_5_NAME_3}
    };
    protected static final int[] NAME_ROWS = {
        DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_PREFIX_OPTIONS,
        DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_DESCRIPTOR_OPTIONS,
        DBTableID.SailingBoatNameOptions.Row.SAILING_BOAT_NAME_NOUN_OPTIONS
    };
    protected final Map<Integer, ItemsUnordered> contents = new LinkedHashMap<>();
    protected Snapshot snapshot;
    protected boolean pending = true;

    @Value
    protected static class Hold {
        int id;
        String name;
        Object items;
    }

    @Value
    protected static class Snapshot implements APISerializable {
        List<Hold> holds;

        @Override
        public Object serialize() {
            return holds;
        }
    }

    @Override
    public String key() {
        return "cargo_holds";
    }

    @Override
    public APISerializable get() {
        return snapshot;
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        for (int index = 0; index < INVENTORIES.length; index++) {
            if ((container.getId() & 0x7fff) == INVENTORIES[index]) {
                if (container.getId() == INVENTORIES[index]) {
                    contents.put(index + 1, new ItemsUnordered(container, itemManager));
                }
                pending = true;
                return;
            }
        }
    }

    @Override
    public void onVarbitChanged(Client client, int varpId, int varbitId, ItemManager itemManager) {
        for (int index = 0; index < OWNED.length; index++) {
            if (varbitId == OWNED[index]) {
                pending = true;
                return;
            }
            for (int name : NAMES[index]) {
                if (varbitId == name) {
                    pending = true;
                    return;
                }
            }
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (!pending) {
            return;
        }
        pending = false;
        List<Hold> holds = new ArrayList<>();
        for (int index = 0; index < INVENTORIES.length; index++) {
            int identifier = index + 1;
            boolean owned = client.getVarbitValue(OWNED[index]) == 1;
            if (owned
                    && client.getVarbitValue(VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT) == 1
                    && client.getVarbitValue(VarbitID.SAILING_BOAT_SPAWNED) == identifier) {
                ItemContainer container = client.getItemContainer(INVENTORIES[index] | 0x8000);
                if (container != null) {
                    contents.put(identifier, new ItemsUnordered(container, itemManager));
                }
            }
            if (!owned) {
                contents.put(identifier, new ItemsUnordered());
            }
            if (contents.containsKey(identifier)) {
                holds.add(new Hold(
                        identifier,
                        owned ? boatName(client, index) : "Boat " + identifier,
                        contents.get(identifier).serialize()));
            }
        }
        snapshot = new Snapshot(holds);
    }

    protected String boatName(Client client, int boat) {
        StringJoiner name = new StringJoiner(" ");
        for (int index = 0; index < NAME_ROWS.length; index++) {
            int option = client.getVarbitValue(NAMES[boat][index]);
            int column = option == 0
                    ? DBTableID.SailingBoatNameOptions.COL_DEFAULT
                    : DBTableID.SailingBoatNameOptions.COL_OPTION;
            String part = (String) client.getDBTableField(NAME_ROWS[index], column, 0)[Math.max(0, option - 1)];
            if (!part.isEmpty()) {
                name.add(part);
            }
        }
        return name.toString();
    }
}
