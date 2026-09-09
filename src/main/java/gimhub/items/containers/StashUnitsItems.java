package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;

public class StashUnitsItems implements TrackedItemContainer {
    protected static final int BUILD = 1475;
    protected static final int BUILD_LISTS = 1476;
    protected static final int[] SECTION_ENUMS = {2317, 1526, 1527, 1528, 1529, 1530};
    protected static final String[] TIERS = {"Beginner", "Easy", "Medium", "Hard", "Elite", "Master"};
    protected static final int NAMES_ENUM = 1531;
    protected static final int INVENTORY_SLOTS_ENUM = 1525;
    protected static final int CONSTRUCTION_ENUM = 1368;
    protected Snapshot snapshot;
    protected int[] flags;
    protected boolean building;
    protected boolean pending;
    protected boolean firstTick = true;
    protected final Map<Integer, Boolean> interactions = new LinkedHashMap<>();

    @Value
    protected static class Unit {
        int id;
        String name;
        String tier;
        String state;
        List<Integer> items;
        List<String> alternatives;
    }

    @Value
    protected static class Snapshot implements APISerializable {
        List<Unit> units;

        @Override
        public Object serialize() {
            return units;
        }
    }

    @Override
    public String key() {
        return "stash_units";
    }

    @Override
    public APISerializable get() {
        return snapshot;
    }

    @Override
    public void onScriptPreFired(Client client, ScriptPreFired event) {
        if (event.getScriptId() != BUILD && event.getScriptId() != BUILD_LISTS) {
            return;
        }
        pending = false;
        building = false;
        flags = null;
        if (event.getScriptEvent() == null) {
            return;
        }
        Object[] arguments = event.getScriptEvent().getArguments();
        if (arguments.length != 4
                || !(arguments[1] instanceof Integer)
                || !(arguments[2] instanceof Integer)
                || !(arguments[3] instanceof Integer)) {
            return;
        }
        flags = new int[] {(Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3]};
        building = true;
    }

    @Override
    public void onScriptPostFired(Client client, ScriptPostFired event) {
        if ((event.getScriptId() == BUILD || event.getScriptId() == BUILD_LISTS) && building) {
            building = false;
            pending = true;
        }
    }

    @Override
    public void onItemContainerChanged(ItemContainer container, ItemManager itemManager) {
        if (container != null && container.getId() == InventoryID.HH_INV && flags != null && !building) {
            pending = true;
        }
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        if (firstTick) {
            firstTick = false;
            Widget overview = client.getWidget(InterfaceID.HideyHoles.UNIVERSE);
            if (overview != null && flags == null) {
                Object[] listener = overview.getOnVarTransmitListener();
                if (listener != null
                        && listener.length == 4
                        && listener[0].equals(BUILD_LISTS)
                        && listener[1] instanceof Integer
                        && listener[2] instanceof Integer
                        && listener[3] instanceof Integer) {
                    flags = new int[] {(Integer) listener[1], (Integer) listener[2], (Integer) listener[3]};
                    pending = true;
                }
            }
        }
        if (pending && !building && client.getWidget(InterfaceID.HideyHoles.UNIVERSE) != null) {
            Snapshot complete = readOverview(client, itemManager);
            if (complete != null) {
                snapshot = complete;
                pending = false;
            }
        }
        if (client.getWidget(InterfaceID.HideyHoles.UNIVERSE) == null) {
            flags = null;
            pending = false;
        }
        if (!interactions.isEmpty()) {
            Map<Integer, Unit> units = new LinkedHashMap<>();
            if (snapshot != null) {
                for (Unit unit : snapshot.units) {
                    units.put(unit.id, unit);
                }
            }
            for (Map.Entry<Integer, Boolean> interaction : interactions.entrySet()) {
                Unit unit = readInteractedUnit(client, itemManager, interaction.getKey(), interaction.getValue());
                if (unit != null) {
                    units.put(unit.id, unit);
                }
            }
            if (!units.isEmpty()) {
                snapshot = new Snapshot(List.copyOf(units.values()));
            }
            interactions.clear();
        }
    }

    protected Snapshot readOverview(Client client, ItemManager itemManager) {
        if (flags == null
                || client.getItemContainer(InventoryID.HH_INV) == null
                || client.getVarps() == null
                || client.getVarps().length <= 2202) {
            return null;
        }
        EnumComposition construction = client.getEnum(CONSTRUCTION_ENUM);
        EnumComposition names = client.getEnum(NAMES_ENUM);
        EnumComposition slots = client.getEnum(INVENTORY_SLOTS_ENUM);
        if (construction == null || names == null || slots == null) {
            return null;
        }
        List<Unit> units = new ArrayList<>();
        for (int section = 0; section < SECTION_ENUMS.length; section++) {
            EnumComposition members = client.getEnum(SECTION_ENUMS[section]);
            if (members == null || members.getIntVals() == null || members.getIntVals().length == 0) {
                return null;
            }
            for (int id : members.getIntVals()) {
                if (!StashDefinitions.UNITS.containsKey(id)
                        || construction.getIntValue(id) < 0
                        || names.getStringValue(id) == null
                        || names.getStringValue(id).isEmpty()) {
                    return null;
                }
                client.runScript(ScriptID.WATSON_STASH_UNIT_CHECK, id, flags[0], flags[1], flags[2]);
                if (client.getIntStackSize() != 2) {
                    return null;
                }
                int built = client.getIntStack()[0];
                int filled = client.getIntStack()[1];
                if ((built != 0 && built != 1) || (filled != 0 && filled != 1) || (built == 0 && filled == 1)) {
                    return null;
                }
                Unit unit = readUnit(
                        client,
                        itemManager,
                        id,
                        names.getStringValue(id),
                        TIERS[section],
                        built == 0 ? "unbuilt" : filled == 0 ? "empty" : "filled",
                        slots);
                if (unit == null) {
                    return null;
                }
                units.add(unit);
            }
        }
        return new Snapshot(List.copyOf(units));
    }

    protected Unit readUnit(
            Client client,
            ItemManager itemManager,
            int id,
            String name,
            String tier,
            String state,
            EnumComposition slots) {
        if (!state.equals("filled")) {
            return new Unit(id, name, tier, state, List.of(), List.of());
        }
        StashDefinitions.Definition definition = StashDefinitions.UNITS.get(id);
        int coordinates = slots.getIntValue(id);
        ItemContainer container = client.getItemContainer(InventoryID.HH_INV);
        if (coordinates >= 0 && container != null) {
            Map<Integer, Integer> contents = new HashMap<>();
            int first = (coordinates >> 14) & 16383;
            int last = coordinates & 16383;
            int loaded = 0;
            for (int slot = first; slot <= last; slot++) {
                Item item = container.getItem(slot);
                if (item != null && item.getId() > 0 && item.getQuantity() > 0) {
                    contents.merge(item.getId(), item.getQuantity(), Integer::sum);
                    loaded++;
                }
            }
            // The Well of Voyage reserves four slots but stores only the staff, top and bottom.
            int requiredItems = id == STASHUnit.WELL_OF_VOYAGE.getObjectId() ? 3 : last - first + 1;
            if (loaded > 0 && loaded < requiredItems) {
                return null;
            }
            if (!contents.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Integer> items = (List<Integer>) new ItemsUnordered(contents, itemManager).serialize();
                return new Unit(id, name, tier, state, items, List.of());
            }
        }
        return new Unit(id, name, tier, state, definition.getItems(), definition.getAlternatives());
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        boolean filled = event.getMessage().equals("You deposit your items into the STASH unit.");
        if (!filled && !event.getMessage().equals("You withdraw your items from the STASH unit.")) {
            return;
        }
        Integer nearest = null;
        for (STASHUnit unit : STASHUnit.values()) {
            for (WorldPoint location : unit.getWorldPoints()) {
                if (location.distanceTo(client.getLocalPlayer().getWorldLocation()) <= 3) {
                    if (nearest != null && nearest != unit.getObjectId()) {
                        return;
                    }
                    nearest = unit.getObjectId();
                }
            }
        }
        if (nearest != null) {
            interactions.put(nearest, filled);
        }
    }

    protected Unit readInteractedUnit(Client client, ItemManager itemManager, int id, boolean filled) {
        EnumComposition names = client.getEnum(NAMES_ENUM);
        EnumComposition slots = client.getEnum(INVENTORY_SLOTS_ENUM);
        if (names == null || slots == null || !StashDefinitions.UNITS.containsKey(id)) {
            return null;
        }
        for (int section = 0; section < SECTION_ENUMS.length; section++) {
            EnumComposition members = client.getEnum(SECTION_ENUMS[section]);
            if (members == null || members.getIntVals() == null) {
                return null;
            }
            for (int member : members.getIntVals()) {
                if (member == id) {
                    return readUnit(
                            client,
                            itemManager,
                            id,
                            names.getStringValue(id),
                            TIERS[section],
                            filled ? "filled" : "empty",
                            slots);
                }
            }
        }
        return null;
    }
}
