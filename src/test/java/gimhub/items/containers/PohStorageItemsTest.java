package gimhub.items.containers;

import static gimhub.items.containers.PortableStorageItemsTest.container;
import static gimhub.items.containers.PortableStorageItemsTest.pairs;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

public class PohStorageItemsTest {
    private Client client;
    private ItemManager itemManager;

    @Before
    public void setUp() {
        client = mock(Client.class);
        itemManager = mock(ItemManager.class);
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getPlaceholderTemplateId()).thenReturn(-1);
        when(itemManager.getItemComposition(anyInt())).thenReturn(composition);
        when(itemManager.canonicalize(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void spiceCheckPublishesFourDoseItemsAndRemaindersWithoutInventingUnknownContents() {
        PohSpiceRackItems rack = new PohSpiceRackItems();
        inventory(new Item(ItemID.HUNDRED_DAVE_SPICE_RED_4, 1));
        rack.onGameTick(client, itemManager);
        inventory();
        message(InterfaceID.Messagebox.TEXT, "Your spices have been stored.");
        rack.onGameTick(client, itemManager);
        assertNull(rack.get());

        message(
                InterfaceID.Messagebox.TEXT,
                "3 x Red Spice.<br>8 x Brown Spice.<br>0 x Yellow Spice.<br>11 x Orange Spice.");
        rack.onGameTick(client, itemManager);
        assertEquals(
                Map.of(
                        ItemID.HUNDRED_DAVE_SPICE_RED_3, 1,
                        ItemID.HUNDRED_DAVE_SPICE_BROWN_4, 2,
                        ItemID.HUNDRED_DAVE_SPICE_ORANGE_4, 2,
                        ItemID.HUNDRED_DAVE_SPICE_ORANGE_3, 1),
                pairs(rack.get()));

        message(
                InterfaceID.Messagebox.TEXT,
                "0 x Red Spice.<br>0 x Brown Spice.<br>0 x Yellow Spice.<br>0 x Orange Spice.");
        rack.onGameTick(client, itemManager);
        assertEquals(Map.of(), pairs(rack.get()));
    }

    @Test
    public void spiceTransfersCountMixedDosesOnceAndIgnoreCancelledWithdrawals() {
        PohSpiceRackItems rack = new PohSpiceRackItems();
        inventory(new Item(ItemID.HUNDRED_DAVE_SPICE_RED_4, 1), new Item(ItemID.HUNDRED_DAVE_SPICE_RED_2, 1));
        message(
                InterfaceID.Messagebox.TEXT,
                "3 x Red Spice.<br>0 x Brown Spice.<br>0 x Yellow Spice.<br>0 x Orange Spice.");
        rack.onGameTick(client, itemManager);

        inventory();
        message(InterfaceID.Messagebox.TEXT, "Your spices have been stored.");
        rack.onGameTick(client, itemManager);
        rack.onGameTick(client, itemManager);
        rack.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.HUNDRED_DAVE_SPICE_RED_4, 2, ItemID.HUNDRED_DAVE_SPICE_RED_1, 1), pairs(rack.get()));

        when(client.getWidget(InterfaceID.Messagebox.TEXT)).thenReturn(null);
        message(InterfaceID.Chatbox.MES_TEXT, "How much spice would you like to take?:");
        rack.onGameTick(client, itemManager);
        when(client.getWidget(InterfaceID.Chatbox.MES_TEXT)).thenReturn(null);
        rack.onGameTick(client, itemManager);
        inventory(new Item(ItemID.HUNDRED_DAVE_SPICE_RED_4, 1), new Item(ItemID.HUNDRED_DAVE_SPICE_RED_1, 1));
        rack.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.HUNDRED_DAVE_SPICE_RED_4, 1), pairs(rack.get()));

        message(InterfaceID.Chatbox.MES_TEXT, "How much spice would you like to take?:");
        rack.onGameTick(client, itemManager);
        when(client.getWidget(InterfaceID.Chatbox.MES_TEXT)).thenReturn(null);
        rack.onGameTick(client, itemManager);
        rack.onGameTick(client, itemManager);
        inventory(new Item(ItemID.HUNDRED_DAVE_SPICE_RED_4, 2));
        rack.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.HUNDRED_DAVE_SPICE_RED_4, 1), pairs(rack.get()));
    }

    @Test
    public void petSnapshotsCombineOrdinaryPetsAndAllThreeBitfieldsWithoutCountingOverviewFlags() {
        PohPetHouseItems house = new PohPetHouseItems();
        EnumComposition pets = mock(EnumComposition.class);
        when(client.getEnum(985)).thenReturn(pets);
        when(pets.getKeys()).thenReturn(new int[] {0, 31, 32, 62, 63, 93, 94});
        when(pets.getIntValue(0)).thenReturn(12650);
        when(pets.getIntValue(31)).thenReturn(12651);
        when(pets.getIntValue(32)).thenReturn(12652);
        when(pets.getIntValue(62)).thenReturn(12653);
        when(pets.getIntValue(63)).thenReturn(12654);
        when(pets.getIntValue(93)).thenReturn(12655);
        when(pets.getIntValue(94)).thenReturn(12656);
        when(client.getVarpValue(VarPlayerID.PRAYER20)).thenReturn(0x80000001);
        when(client.getVarpValue(VarPlayerID.MENAGERIE_CONTENTS2)).thenReturn(0xc0000001);
        when(client.getVarpValue(VarPlayerID.MENAGERIE_CONTENTS3)).thenReturn(0xc0000001);

        house.onGameTick(client, itemManager);
        assertNull(house.get());
        house.onItemContainerChanged(
                container(InventoryID.POH_MENAGERIE_PETS, new Item(1555, 1), new Item(1555, 1), new Item(-1, 0)),
                itemManager);
        house.onGameTick(client, itemManager);
        assertEquals(Map.of(1555, 2, 12650, 1, 12651, 1, 12652, 1, 12653, 1, 12654, 1, 12655, 1), pairs(house.get()));

        when(client.getVarpValue(VarPlayerID.PRAYER20)).thenReturn(0);
        when(client.getVarpValue(VarPlayerID.MENAGERIE_CONTENTS2)).thenReturn(0x80000000);
        when(client.getVarpValue(VarPlayerID.MENAGERIE_CONTENTS3)).thenReturn(0x80000000);
        house.onVarbitChanged(client, VarPlayerID.PRAYER20, -1, itemManager);
        house.onGameTick(client, itemManager);
        assertEquals(Map.of(1555, 2), pairs(house.get()));
        house.onItemContainerChanged(container(InventoryID.POH_MENAGERIE_PETS), itemManager);
        house.onGameTick(client, itemManager);
        assertEquals(Map.of(), pairs(house.get()));
    }

    @Test
    public void petBitfieldUpdatesPublishWithoutOpeningTheHouseOrReceivingOrdinaryPets() {
        PohPetHouseItems house = new PohPetHouseItems();
        EnumComposition pets = mock(EnumComposition.class);
        when(client.getEnum(985)).thenReturn(pets);
        when(pets.getKeys()).thenReturn(new int[] {63});
        when(pets.getIntValue(63)).thenReturn(12652);

        when(client.getVarpValue(VarPlayerID.MENAGERIE_CONTENTS3)).thenReturn(1);
        house.onVarbitChanged(client, VarPlayerID.MENAGERIE_CONTENTS3, -1, itemManager);
        house.onGameTick(client, itemManager);
        assertEquals(Map.of(12652, 1), pairs(house.get()));

        when(client.getVarpValue(VarPlayerID.MENAGERIE_CONTENTS3)).thenReturn(0);
        house.onVarbitChanged(client, VarPlayerID.MENAGERIE_CONTENTS3, -1, itemManager);
        house.onGameTick(client, itemManager);
        assertEquals(Map.of(), pairs(house.get()));
    }

    @Test
    public void openingPetHousePublishesBossPetsWithoutAnOrdinaryPetContainerUpdate() {
        PohPetHouseItems house = new PohPetHouseItems();
        EnumComposition pets = mock(EnumComposition.class);
        when(client.getEnum(985)).thenReturn(pets);
        when(pets.getKeys()).thenReturn(new int[] {0});
        when(pets.getIntValue(0)).thenReturn(12650);
        when(client.getVarpValue(VarPlayerID.PRAYER20)).thenReturn(1);

        when(client.getWidget(InterfaceID.PohMenagerie.UNIVERSE)).thenReturn(mock(Widget.class));
        house.onGameTick(client, itemManager);
        assertEquals(Map.of(12650, 1), pairs(house.get()));
    }

    @Test
    public void moneybagKeepsItsLastCheckedBalanceUntilAnotherBalanceMessage() {
        PohServantsMoneybagItems moneybag = new PohServantsMoneybagItems();
        moneybag.onGameTick(client, itemManager);
        assertNull(moneybag.get());

        message(InterfaceID.Objectbox.TEXT, "The money bag contains <col=000080>1,234,567</col> coins.");
        moneybag.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.COINS, 1234567), pairs(moneybag.get()));
        when(client.getWidget(InterfaceID.Objectbox.TEXT)).thenReturn(null);
        moneybag.onGameTick(client, itemManager);
        message(InterfaceID.Objectbox.TEXT, "The barrel contains: 2 x trout");
        moneybag.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.COINS, 1234567), pairs(moneybag.get()));

        message(InterfaceID.Objectbox.TEXT, "The money bag is empty.");
        moneybag.onGameTick(client, itemManager);
        assertEquals(Map.of(), pairs(moneybag.get()));
    }

    private void inventory(Item... items) {
        doReturn(container(InventoryID.INV, items)).when(client).getItemContainer(InventoryID.INV);
    }

    private void message(int component, String text) {
        Widget widget = mock(Widget.class);
        when(widget.getText()).thenReturn(text);
        when(client.getWidget(component)).thenReturn(widget);
    }
}
