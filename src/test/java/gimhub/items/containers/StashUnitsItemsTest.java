package gimhub.items.containers;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gimhub.APISerializable;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

public class StashUnitsItemsTest {
    private Client client;
    private ItemManager itemManager;
    private StashUnitsItems tracker;
    private Map<Integer, EnumComposition> enums;
    private Set<Integer> filled;
    private final int[] stack = new int[1000];

    @Before
    public void setUp() {
        client = mock(Client.class);
        itemManager = mock(ItemManager.class);
        tracker = new StashUnitsItems();
        enums = new HashMap<>();
        filled = Set.of(28958, 28959, 29019, 58602);
        JsonObject fixture = new Gson()
                .fromJson(
                        new InputStreamReader(
                                getClass().getResourceAsStream("/gimhub/stash-overview.json"), StandardCharsets.UTF_8),
                        JsonObject.class);
        for (Map.Entry<String, JsonElement> entry : fixture.entrySet()) {
            EnumComposition composition = mock(EnumComposition.class);
            JsonObject values = entry.getValue().getAsJsonObject();
            when(composition.getIntValue(anyInt())).thenAnswer(invocation -> {
                JsonElement value = values.get(Integer.toString(invocation.getArgument(0)));
                return value == null ? -1 : value.getAsInt();
            });
            when(composition.getStringValue(anyInt())).thenAnswer(invocation -> {
                JsonElement value = values.get(Integer.toString(invocation.getArgument(0)));
                return value == null ? "" : value.getAsString();
            });
            if (!entry.getKey().equals("1531")) {
                when(composition.getIntVals())
                        .thenReturn(values.entrySet().stream()
                                .mapToInt(value -> value.getValue().getAsInt())
                                .toArray());
            }
            enums.put(Integer.parseInt(entry.getKey()), composition);
        }
        when(client.getEnum(anyInt())).thenAnswer(invocation -> enums.get(invocation.getArgument(0)));
        when(client.getWidget(InterfaceID.HideyHoles.UNIVERSE)).thenReturn(mock(Widget.class));
        doReturn(PortableStorageItemsTest.container(InventoryID.HH_INV))
                .when(client)
                .getItemContainer(InventoryID.HH_INV);
        when(client.getVarps()).thenReturn(new int[5000]);
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(2);
        doAnswer(invocation -> {
                    int identifier = invocation.getArgument(1);
                    assertEquals(7, invocation.<Integer>getArgument(2).intValue());
                    assertEquals(3, invocation.<Integer>getArgument(3).intValue());
                    assertEquals(1024, invocation.<Integer>getArgument(4).intValue());
                    stack[0] = identifier == 34736 ? 0 : 1;
                    stack[1] = filled.contains(identifier) ? 1 : 0;
                    return null;
                })
                .when(client)
                .runScript(eq(ScriptID.WATSON_STASH_UNIT_CHECK), anyInt(), anyInt(), anyInt(), anyInt());
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getPlaceholderTemplateId()).thenReturn(-1);
        when(itemManager.getItemComposition(anyInt())).thenReturn(composition);
        when(itemManager.canonicalize(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void overviewDiscoversPreExistingUnitsAcrossEverySectionWithoutVisitingThem() {
        open();
        assertEquals(119, snapshot().getUnits().size());
        assertEquals("filled", unit(28958).getState());
        assertEquals("filled", unit(28959).getState());
        assertEquals("unbuilt", unit(34736).getState());
        assertEquals("empty", unit(28960).getState());
        // All six section enums are read, including the final master entry beyond every visible row.
        int lastMaster = enums.get(1530).getIntVals()[24];
        verify(client).runScript(ScriptID.WATSON_STASH_UNIT_CHECK, lastMaster, 7, 3, 1024);
        verify(client, times(119))
                .runScript(eq(ScriptID.WATSON_STASH_UNIT_CHECK), anyInt(), anyInt(), anyInt(), anyInt());
        assertFalse(unit(28958).getItems().isEmpty());
    }

    @Test
    public void reopeningReconcilesFilledUnitsToEmptyAndDoesNotAppendDuplicates() {
        open();
        APISerializable first = tracker.get();
        open();
        assertEquals(first, tracker.get());
        filled = Set.of(28959);
        open();
        assertEquals(119, snapshot().getUnits().size());
        assertEquals("empty", unit(28958).getState());
        assertTrue(unit(28958).getItems().isEmpty());
        assertEquals("filled", unit(28959).getState());
    }

    @Test
    public void missingContainerOrAnUnloadedSectionDoesNotPublishAPartialOverview() {
        open();
        APISerializable known = tracker.get();
        filled = Set.of();
        when(client.getItemContainer(InventoryID.HH_INV)).thenReturn(null);
        open();
        assertSame(known, tracker.get());
        doReturn(PortableStorageItemsTest.container(InventoryID.HH_INV))
                .when(client)
                .getItemContainer(InventoryID.HH_INV);
        EnumComposition master = enums.remove(1530);
        tracker.onGameTick(client, itemManager);
        assertSame(known, tracker.get());
        enums.put(1530, master);
        tracker.onGameTick(client, itemManager);
        assertEquals("empty", unit(28958).getState());
        assertEquals(119, snapshot().getUnits().size());
    }

    @Test
    public void initializationMustCompleteBeforeCommittingTheSnapshot() {
        open();
        APISerializable known = tracker.get();
        filled = Set.of();
        pre(new Object[] {1475, 7, 3, 1024});
        tracker.onGameTick(client, itemManager);
        assertSame(known, tracker.get());
        tracker.onScriptPostFired(client, new ScriptPostFired(1475));
        tracker.onGameTick(client, itemManager);
        assertEquals("empty", unit(28958).getState());
        pre(new Object[] {1475, 7});
        tracker.onScriptPostFired(client, new ScriptPostFired(1475));
        tracker.onGameTick(client, itemManager);
        assertEquals(119, snapshot().getUnits().size());
    }

    @Test
    public void ambiguousRequirementsDoNotInventVariantsOrCountAllAlternatives() {
        open();
        assertTrue(unit(29019).getItems().isEmpty());
        assertEquals(
                List.of("Any stole", "Any heraldic rune shield"), unit(29019).getAlternatives());
        Item[] items = new Item[11];
        items[9] = new Item(ItemID.TRAIL_ZAMORAK_SCARF, 1);
        items[10] = new Item(ItemID.RUNE_HERALDIC_KITESHIELD3, 1);
        doReturn(PortableStorageItemsTest.container(InventoryID.HH_INV, items))
                .when(client)
                .getItemContainer(InventoryID.HH_INV);
        open();
        assertEquals(4, unit(29019).getItems().size());
        assertTrue(unit(29019).getItems().contains(ItemID.TRAIL_ZAMORAK_SCARF));
        assertFalse(unit(29019).getItems().contains(ItemID.TRAIL_GUTHIX_SCARF));
        assertTrue(unit(29019).getAlternatives().isEmpty());
    }

    @Test
    public void aNewAccountStartsUnknownInsteadOfReusingThePreviousOverview() {
        open();
        StashUnitsItems otherAccount = new StashUnitsItems();
        when(client.getWidget(InterfaceID.HideyHoles.UNIVERSE)).thenReturn(null);
        otherAccount.onGameTick(client, itemManager);
        assertNull(otherAccount.get());
        assertNotNull(tracker.get());
    }

    @Test
    public void enablingWithAnAlreadyOpenOverviewReadsItsCompleteListenerState() {
        when(client.getWidget(InterfaceID.HideyHoles.UNIVERSE).getOnVarTransmitListener())
                .thenReturn(new Object[] {1476, 7, 3, 1024});
        tracker.onGameTick(client, itemManager);
        assertEquals(119, snapshot().getUnits().size());
        assertEquals("filled", unit(28958).getState());
        assertEquals("filled", unit(28959).getState());
    }

    @Test
    public void wellOfVoyagePublishesAllUnitsWithItsReservedSlotEmpty() {
        for (int staff : new int[] {ItemID.IBANSTAFF, ItemID.IBANSTAFF_UPGRADED}) {
            open();
            APISerializable known = tracker.get();
            filled = Set.of(29043);
            Item[] items = new Item[51];
            items[47] = new Item(staff, 1);
            items[48] = new Item(ItemID.MYSTIC_ROBE_TOP_DARK, 1);
            doReturn(PortableStorageItemsTest.container(InventoryID.HH_INV, items))
                    .when(client)
                    .getItemContainer(InventoryID.HH_INV);
            open();
            assertSame(known, tracker.get());

            items[49] = new Item(ItemID.MYSTIC_ROBE_BOTTOM_DARK, 1);
            items[50] = new Item(-1, 0);
            tracker.onGameTick(client, itemManager);
            assertEquals(119, snapshot().getUnits().size());
            assertEquals("filled", unit(29043).getState());
            assertEquals(6, unit(29043).getItems().size());
            assertTrue(unit(29043)
                    .getItems()
                    .containsAll(List.of(staff, ItemID.MYSTIC_ROBE_TOP_DARK, ItemID.MYSTIC_ROBE_BOTTOM_DARK)));
            assertTrue(unit(29043).getAlternatives().isEmpty());
            assertEquals("empty", unit(28958).getState());
        }
    }

    @Test
    public void partiallyTransmittedExactContentsPreserveTheWholeKnownOverview() {
        open();
        APISerializable known = tracker.get();
        Item[] items = new Item[11];
        items[9] = new Item(ItemID.TRAIL_ZAMORAK_SCARF, 1);
        doReturn(PortableStorageItemsTest.container(InventoryID.HH_INV, items))
                .when(client)
                .getItemContainer(InventoryID.HH_INV);
        open();
        assertSame(known, tracker.get());
        items[10] = new Item(ItemID.RUNE_HERALDIC_KITESHIELD3, 1);
        tracker.onGameTick(client, itemManager);
        assertEquals(4, unit(29019).getItems().size());
    }

    private void open() {
        pre(new Object[] {1475, 7, 3, 1024});
        tracker.onScriptPostFired(client, new ScriptPostFired(1475));
        tracker.onGameTick(client, itemManager);
    }

    private void pre(Object[] arguments) {
        ScriptEvent event = mock(ScriptEvent.class);
        when(event.getArguments()).thenReturn(arguments);
        ScriptPreFired pre = new ScriptPreFired(1475);
        pre.setScriptEvent(event);
        tracker.onScriptPreFired(client, pre);
    }

    private StashUnitsItems.Snapshot snapshot() {
        return (StashUnitsItems.Snapshot) tracker.get();
    }

    private StashUnitsItems.Unit unit(int identifier) {
        return snapshot().getUnits().stream()
                .filter(unit -> unit.getId() == identifier)
                .findFirst()
                .orElseThrow();
    }
}
