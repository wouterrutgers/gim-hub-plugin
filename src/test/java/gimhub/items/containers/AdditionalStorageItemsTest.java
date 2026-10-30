package gimhub.items.containers;

import static gimhub.items.containers.PortableStorageItemsTest.container;
import static gimhub.items.containers.PortableStorageItemsTest.pairs;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.util.Map;
import java.util.Set;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

public class AdditionalStorageItemsTest {
    protected Client client;
    protected ItemManager itemManager;

    @Before
    public void setUp() {
        client = mock(Client.class);
        itemManager = mock(ItemManager.class);
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getPlaceholderTemplateId()).thenReturn(-1);
        when(composition.getName()).thenReturn("");
        when(itemManager.getItemComposition(anyInt())).thenReturn(composition);
        when(itemManager.canonicalize(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void flamtaerBagChecksReadLabelledQuantitiesFromChatAndDialogue() {
        CheckedStorageItems bag = CheckedStorageItems.flamtaerBag();
        name(ItemID.TIMBERBEAM, "Timber beam");
        name(ItemID.LIMESTONEBRICK, "Limestone brick");
        name(ItemID.SWAMPPASTE, "Swamp paste");
        check(bag, ItemID.FLAMTAER_BAG);
        chat(bag, "Timber beams: 60<br>Limestone bricks: 60<br>Swamp paste: 500");
        bag.onGameTick(client, itemManager);
        assertEquals(
                Map.of(ItemID.TIMBERBEAM, 60, ItemID.LIMESTONEBRICK, 60, ItemID.SWAMPPASTE, 500), pairs(bag.get()));

        check(bag, ItemID.FLAMTAER_BAG);
        Widget text = mock(Widget.class);
        when(client.getWidget(InterfaceID.Objectbox.TEXT)).thenReturn(text);
        when(text.getText()).thenReturn("Timber beams: 34<br>Limestone bricks: 60<br>Swamp paste: 500");
        bag.onGameTick(client, itemManager);
        assertEquals(
                Map.of(ItemID.TIMBERBEAM, 34, ItemID.LIMESTONEBRICK, 60, ItemID.SWAMPPASTE, 500), pairs(bag.get()));
    }

    @Test
    public void basketChecksPreserveKnownContentsUntilEveryListedItemCanBeRead() {
        CheckedStorageItems basket = CheckedStorageItems.logBasket();
        name(ItemID.OAK_LOGS, "Oak logs");
        name(ItemID.YEW_LOGS, "Yew logs");
        Widget text = mock(Widget.class);
        when(client.getWidget(InterfaceID.Objectbox.TEXT)).thenReturn(text);
        when(text.getText()).thenReturn("The basket contains:<br>12 x Oak logs<br>4×Yew logs");
        basket.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.OAK_LOGS, 12, ItemID.YEW_LOGS, 4), pairs(basket.get()));

        check(basket, ItemID.LOG_BASKET_OPEN);
        when(text.getText()).thenReturn("The basket contains:<br>2 x Oak logs<br>3 x Unknown logs");
        basket.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.OAK_LOGS, 12, ItemID.YEW_LOGS, 4), pairs(basket.get()));

        check(basket, ItemID.LOG_BASKET_CLOSED);
        when(text.getText()).thenReturn("The basket is empty.");
        basket.onGameTick(client, itemManager);
        assertEquals(Map.of(), pairs(basket.get()));
    }

    @Test
    public void openBasketHarvestsKeepTheCorrectContentsForBulkDeposits() {
        CheckedStorageItems basket = CheckedStorageItems.logBasket();
        name(ItemID.OAK_LOGS, "Oak logs");
        Widget text = mock(Widget.class);
        when(client.getWidget(InterfaceID.Objectbox.TEXT)).thenReturn(text);
        when(text.getText()).thenReturn("The basket contains:<br>12 x Oak logs");
        doReturn(container(
                        InventoryID.WORN, new Item(ItemID.FORESTRY_BASKET_OPEN, 1), new Item(ItemID.INFERNAL_AXE, 1)))
                .when(client)
                .getItemContainer(InventoryID.WORN);
        basket.onGameTick(client, itemManager);
        when(client.getWidget(InterfaceID.Objectbox.TEXT)).thenReturn(null);

        chat(basket, ChatMessageType.SPAM, "You get some oak logs.");
        chat(basket, ChatMessageType.SPAM, "Your Kandarin headgear provides you with an additional log.");
        chat(basket, ChatMessageType.SPAM, "The nature offerings enabled you to chop an extra log.");
        basket.onStatChanged(new StatChanged(Skill.FIREMAKING, 1000, 10, 10), itemManager);
        basket.onGameTick(client, itemManager);
        assertEquals(
                Map.of(ItemID.OAK_LOGS, 14),
                basket.onDepositContainers(client, itemManager, Set.of(ItemID.FORESTRY_BASKET_OPEN)));

        for (int index = 0; index < 20; index++) {
            chat(basket, ChatMessageType.SPAM, "You get some oak logs.");
            basket.onGameTick(client, itemManager);
        }
        assertEquals(Map.of(ItemID.OAK_LOGS, 28), pairs(basket.get()));
    }

    @Test
    public void cargoSnapshotsSeparateOwnedBoatsAndClearSoldBoatsWithoutGuessingUnseenCargo() {
        CargoHoldItems cargo = new CargoHoldItems();
        for (int varbit : CargoHoldItems.OWNED) {
            when(client.getVarbitValue(varbit)).thenReturn(1);
        }
        for (int row : CargoHoldItems.NAME_ROWS) {
            when(client.getDBTableField(row, DBTableID.SailingBoatNameOptions.COL_DEFAULT, 0))
                    .thenReturn(new Object[] {""});
        }
        when(client.getDBTableField(CargoHoldItems.NAME_ROWS[2], DBTableID.SailingBoatNameOptions.COL_DEFAULT, 0))
                .thenReturn(new Object[] {"Albatross"});
        cargo.onGameTick(client, itemManager);
        assertEquals(0, json(cargo).size());

        cargo.onItemContainerChanged(
                container(InventoryID.SAILING_BOAT_1_CARGOHOLD, new Item(ItemID.COINS, 100)), itemManager);
        cargo.onItemContainerChanged(
                container(InventoryID.SAILING_BOAT_2_CARGOHOLD, new Item(ItemID.COINS, 50)), itemManager);
        cargo.onGameTick(client, itemManager);
        JsonArray holds = json(cargo);
        assertEquals(2, holds.size());
        assertEquals(1, holds.get(0).getAsJsonObject().get("id").getAsInt());
        assertEquals("Albatross", holds.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(
                100,
                holds.get(0).getAsJsonObject().getAsJsonArray("items").get(1).getAsInt());
        assertEquals(2, holds.get(1).getAsJsonObject().get("id").getAsInt());

        when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_OWNED)).thenReturn(0);
        cargo.onVarbitChanged(client, -1, VarbitID.SAILING_BOAT_1_OWNED, itemManager);
        cargo.onGameTick(client, itemManager);
        assertEquals(
                0, json(cargo).get(0).getAsJsonObject().getAsJsonArray("items").size());
        assertEquals(
                50,
                json(cargo)
                        .get(1)
                        .getAsJsonObject()
                        .getAsJsonArray("items")
                        .get(1)
                        .getAsInt());
    }

    @Test
    public void flaggedCargoUpdatesOnlyReadThePlayersOwnBoardedBoat() {
        CargoHoldItems cargo = new CargoHoldItems();
        when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_OWNED)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.SAILING_BOAT_SPAWNED)).thenReturn(1);
        for (int row : CargoHoldItems.NAME_ROWS) {
            when(client.getDBTableField(row, DBTableID.SailingBoatNameOptions.COL_DEFAULT, 0))
                    .thenReturn(new Object[] {"Albatross"});
        }
        int flaggedInventory = InventoryID.SAILING_BOAT_1_CARGOHOLD | 0x8000;
        ItemContainer ownedCargo = container(flaggedInventory, new Item(ItemID.COINS, 100));
        doReturn(ownedCargo).when(client).getItemContainer(flaggedInventory);
        cargo.onItemContainerChanged(ownedCargo, itemManager);
        cargo.onGameTick(client, itemManager);
        assertEquals(
                100,
                json(cargo)
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("items")
                        .get(1)
                        .getAsInt());

        when(client.getVarbitValue(VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT)).thenReturn(0);
        ItemContainer otherPlayersCargo = container(flaggedInventory, new Item(ItemID.COINS, 999));
        doReturn(otherPlayersCargo).when(client).getItemContainer(flaggedInventory);
        cargo.onItemContainerChanged(otherPlayersCargo, itemManager);
        cargo.onGameTick(client, itemManager);
        assertEquals(
                100,
                json(cargo)
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("items")
                        .get(1)
                        .getAsInt());
    }

    @Test
    public void keyRingPublishesOnlyAfterAllKeysHaveBeenObserved() {
        SteelKeyRingItems ring = new SteelKeyRingItems();
        keyPage(ring, 983, -1, 984, 4);
        ring.onGameTick(client, itemManager);
        assertNull(ring.get());
        keyPage(ring, 985, 984, 986, 4);
        ring.onGameTick(client, itemManager);
        assertEquals(Map.of(983, 1, 984, 1, 985, 1, 986, 1), pairs(ring.get()));
        keyPage(ring, -1, -1, -1, 0);
        ring.onGameTick(client, itemManager);
        assertEquals(Map.of(), pairs(ring.get()));
    }

    @Test
    public void compostChecksAndChargeMessagesReplaceUsesAndClearExhaustedBuckets() {
        BottomlessCompostBucketItems bucket = new BottomlessCompostBucketItems();
        bucket.onGameTick(client, itemManager);
        assertNull(bucket.get());
        Widget text = mock(Widget.class);
        when(client.getWidget(InterfaceID.Objectbox.TEXT)).thenReturn(text);
        when(text.getText())
                .thenReturn("Your bottomless compost bucket is currently holding 1,234 uses of ultracompost.");
        bucket.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.BUCKET_ULTRACOMPOST, 1234), pairs(bucket.get()));
        when(client.getWidget(InterfaceID.Objectbox.TEXT)).thenReturn(null);
        chat(bucket, "Your bottomless compost bucket has a single use of supercompost left.");
        assertEquals(Map.of(ItemID.BUCKET_SUPERCOMPOST, 1), pairs(bucket.get()));
        chat(bucket, "Your bottomless compost bucket has run out of compost.");
        bucket.onGameTick(client, itemManager);
        assertEquals(Map.of(), pairs(bucket.get()));
    }

    @Test
    public void balloonTravelUpdatesPluralSingularAndExhaustedSupplies() {
        BalloonTransportItems balloon = new BalloonTransportItems();
        name(ItemID.LOGS, "Logs");
        name(ItemID.OAK_LOGS, "Oak logs");
        chat(balloon, "You have 4 sets of Oak logs left in storage.");
        assertNull(balloon.get());
        Widget text = mock(Widget.class);
        when(client.getWidget(InterfaceID.Messagebox.TEXT)).thenReturn(text);
        when(text.getText())
                .thenReturn(
                        "This crate currently contains 10 logs, 20 oak logs, 30 willow logs, 40 yew logs and 50 magic logs.");
        balloon.onGameTick(client, itemManager);
        when(client.getWidget(InterfaceID.Messagebox.TEXT)).thenReturn(null);
        chat(balloon, "You have 9 sets of Logs left in storage.");
        assertEquals(
                Map.of(
                        ItemID.LOGS,
                        9,
                        ItemID.OAK_LOGS,
                        20,
                        ItemID.WILLOW_LOGS,
                        30,
                        ItemID.YEW_LOGS,
                        40,
                        ItemID.MAGIC_LOGS,
                        50),
                pairs(balloon.get()));
        chat(balloon, "You have one set of Logs left in storage.");
        assertEquals(Integer.valueOf(1), pairs(balloon.get()).get(ItemID.LOGS));
        chat(balloon, "You used the last of your Logs.");
        assertFalse(pairs(balloon.get()).containsKey(ItemID.LOGS));
    }

    @Test
    public void sandstormWithdrawalsDeductBucketsAndSandOnlyOnce() {
        SandstormItems sandstorm = new SandstormItems();
        Widget text = mock(Widget.class);
        when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(text);
        when(text.getText())
                .thenReturn("I have 10 of your buckets and you've ground enough sandstone for 20 buckets of sand.");
        sandstorm.onGameTick(client, itemManager);
        when(text.getText()).thenReturn("If ya need any more sand, let me know.");
        doReturn(container(InventoryID.INV, new Item(ItemID.BUCKET_SAND, 7)))
                .when(client)
                .getItemContainer(InventoryID.INV);
        sandstorm.onGameTick(client, itemManager);
        sandstorm.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.BUCKET_EMPTY, 3, ItemID.HANDSAND_SAND, 13), pairs(sandstorm.get()));
    }

    @Test
    public void firelighterChecksReplaceThePreviousColourAndUnchargingClearsIt() {
        GnomishFirelighterItems lighter = new GnomishFirelighterItems();
        Widget text = mock(Widget.class);
        when(client.getWidget(InterfaceID.Objectbox.TEXT)).thenReturn(text);
        when(text.getText()).thenReturn("Your gnomish firelighter contains 15 red firelighter charges.");
        lighter.onGameTick(client, itemManager);
        when(text.getText()).thenReturn("Your gnomish firelighter contains 2 blue firelighter charges.");
        lighter.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.GNOMISH_FIRELIGHTER_BLUE, 2), pairs(lighter.get()));
        chat(lighter, "You uncharge the gnomish firelighter.");
        assertEquals(Map.of(), pairs(lighter.get()));
    }

    protected void check(TrackedItemContainer tracker, int identifier) {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getItemId()).thenReturn(identifier);
        when(entry.getOption()).thenReturn("Check");
        tracker.onMenuOptionClicked(client, new MenuOptionClicked(entry), itemManager);
    }

    protected void name(int identifier, String name) {
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getPlaceholderTemplateId()).thenReturn(-1);
        when(composition.getName()).thenReturn(name);
        when(itemManager.getItemComposition(identifier)).thenReturn(composition);
    }

    protected void keyPage(SteelKeyRingItems ring, int current, int previous, int next, int total) {
        ScriptEvent script = mock(ScriptEvent.class);
        when(script.getArguments()).thenReturn(new Object[] {861, current, previous, next, total});
        ScriptPreFired event = new ScriptPreFired(861);
        event.setScriptEvent(script);
        ring.onScriptPreFired(client, event);
    }

    protected void chat(TrackedItemContainer tracker, String message) {
        chat(tracker, ChatMessageType.GAMEMESSAGE, message);
    }

    protected void chat(TrackedItemContainer tracker, ChatMessageType type, String message) {
        tracker.onChatMessage(client, new ChatMessage(null, type, "", message, "", 0), itemManager);
    }

    protected JsonArray json(TrackedItemContainer tracker) {
        return new Gson().toJsonTree(tracker.get().serialize()).getAsJsonArray();
    }
}
