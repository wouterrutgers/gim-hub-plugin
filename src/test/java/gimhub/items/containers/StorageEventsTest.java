package gimhub.items.containers;

import static gimhub.items.containers.PortableStorageItemsTest.container;
import static gimhub.items.containers.PortableStorageItemsTest.pairs;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import gimhub.APISerializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

public class StorageEventsTest {
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
    public void incompleteHerbChecksPreserveThePreviousSnapshot() {
        HerbSackItems herbs = new HerbSackItems();
        chat(herbs, "You look in your herb sack and see:");
        chat(herbs, "2 x Grimy huasca");
        herbs.onGameTick(client, itemManager);
        APISerializable known = herbs.get();
        chat(herbs, "You look in your herb sack and see:");
        herbs.onGameTick(client, itemManager);
        assertSame(known, herbs.get());
        chat(herbs, "You look in your herb sack and see:");
        chat(herbs, "1 x Grimy ranarr weed");
        chat(herbs, "2 x Grimy unknown herb");
        herbs.onGameTick(client, itemManager);
        assertSame(known, herbs.get());
    }

    @Test
    public void allHerbSackVariantsUpdateTheSameKnownInventoryAfterConfirmedTransfers() {
        for (int variant : new int[] {
            ItemID.SLAYER_HERB_SACK,
            ItemID.SLAYER_HERB_SACK_OPEN,
            ItemID.SLAYER_HERB_SACK_SILK,
            ItemID.SLAYER_HERB_SACK_SILK_OPEN
        }) {
            HerbSackItems herbs = new HerbSackItems();
            chat(herbs, "The herb sack is empty.");
            doReturn(container(InventoryID.INV, new Item(variant, 1), new Item(ItemID.UNIDENTIFIED_RANARR, 4)))
                    .when(client)
                    .getItemContainer(InventoryID.INV);
            MenuEntry entry = mock(MenuEntry.class);
            when(entry.getItemId()).thenReturn(variant);
            when(entry.getOption()).thenReturn("Fill");
            MenuOptionClicked fill = new MenuOptionClicked(entry);
            herbs.onMenuOptionClicked(client, fill, itemManager);
            herbs.onGameTick(client, itemManager);
            assertEquals(Map.of(), pairs(herbs.get()));
            doReturn(container(InventoryID.INV, new Item(variant, 1)))
                    .when(client)
                    .getItemContainer(InventoryID.INV);
            herbs.onGameTick(client, itemManager);
            assertEquals(Map.of(ItemID.UNIDENTIFIED_RANARR, 4), pairs(herbs.get()));
            assertEquals(
                    Map.of(ItemID.UNIDENTIFIED_RANARR, 4),
                    herbs.onDepositContainers(client, itemManager, Set.of(variant)));
            assertEquals(Map.of(ItemID.UNIDENTIFIED_RANARR, 4), pairs(herbs.get()));
            chat(herbs, "You empty all of your containers into the bank.");
            assertEquals(Map.of(), pairs(herbs.get()));
        }
    }

    @Test
    public void seedMessagesUpdateClosedBoxesWithoutDoubleCountingContainerTransmissions() {
        SeedBoxItems seeds = new SeedBoxItems();
        seeds.onItemContainerChanged(container(InventoryID.SEED_BOX, new Item(ItemID.RANARR_SEED, 10)), itemManager);
        seeds.onGameTick(client, itemManager);
        chat(seeds, "You put 3 x Ranarr seed straight into your open seed box.");
        seeds.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.RANARR_SEED, 13), pairs(seeds.get()));
        chat(seeds, "Stored 2 x Ranarr seed in your seed box.");
        seeds.onItemContainerChanged(container(InventoryID.SEED_BOX, new Item(ItemID.RANARR_SEED, 15)), itemManager);
        seeds.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.RANARR_SEED, 15), pairs(seeds.get()));
        chat(seeds, "Emptied 15 x Ranarr seed to your inventory.");
        seeds.onGameTick(client, itemManager);
        assertEquals(Map.of(), pairs(seeds.get()));
    }

    @Test
    public void initializedEmptyInterfacesClearWithoutAnItemContainer() {
        LootingBagItems bag = new LootingBagItems();
        bag.onItemContainerChanged(container(InventoryID.LOOTING_BAG, new Item(ItemID.COINS, 10)), itemManager);
        Widget items = mock(Widget.class);
        Widget empty = mock(Widget.class);
        when(empty.getText()).thenReturn("The bag is empty.");
        when(items.getChild(28)).thenReturn(empty);
        when(client.getWidget(InterfaceID.WildernessLootingbag.ITEMS)).thenReturn(items);
        bag.onGameTick(client, itemManager);
        assertEquals(List.of(), bag.get().serialize());

        SeedBoxItems seeds = new SeedBoxItems();
        seeds.onItemContainerChanged(container(InventoryID.SEED_BOX, new Item(ItemID.RANARR_SEED, 10)), itemManager);
        Widget slot = mock(Widget.class);
        when(slot.getItemId()).thenReturn(-1);
        when(items.getChildren()).thenReturn(new Widget[] {slot, slot, slot, slot, slot, slot});
        when(client.getWidget(InterfaceID.HosidiusSeedbox.SEED_LAYER)).thenReturn(items);
        seeds.onGameTick(client, itemManager);
        assertEquals(List.of(), seeds.get().serialize());
    }

    @Test
    public void chuggingDosesAreReconciledWithoutDoublingChatAndContainerUpdates() {
        ChuggingBarrelItems barrel = new ChuggingBarrelItems();
        EnumComposition potions = mock(EnumComposition.class);
        EnumComposition potion = mock(EnumComposition.class);
        when(potions.getIntVals()).thenReturn(new int[] {500});
        when(potion.getIntValue(1)).thenReturn(ItemID._1DOSE2RESTORE);
        when(client.getEnum(EnumID.POTIONSTORE_POTIONS)).thenReturn(potions);
        when(client.getEnum(500)).thenReturn(potion);
        when(itemManager.getItemComposition(ItemID._1DOSE2RESTORE).getName()).thenReturn("Super restore(1)");
        barrel.onItemContainerChanged(
                container(InventoryID.PREPOT_DEVICE_INV, new Item(ItemID._1DOSE2RESTORE, 10)), itemManager);
        barrel.onGameTick(client, itemManager);
        chat(barrel, "Your chugging barrel has been filled with 5 doses of Super restore(1).");
        barrel.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID._1DOSE2RESTORE, 15), pairs(barrel.get()));
        chat(barrel, "Your chugging barrel has been filled with 5 doses of Super restore(1).");
        barrel.onItemContainerChanged(
                container(InventoryID.PREPOT_DEVICE_INV, new Item(ItemID._1DOSE2RESTORE, 20)), itemManager);
        barrel.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID._1DOSE2RESTORE, 20), pairs(barrel.get()));
        chat(barrel, "You have 9 doses of Super restore(1) left in your barrel.");
        barrel.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID._1DOSE2RESTORE, 9), pairs(barrel.get()));
        chat(barrel, "You have finished all doses of Super restore(1) in your barrel.");
        barrel.onGameTick(client, itemManager);
        assertEquals(Map.of(), pairs(barrel.get()));
    }

    @Test
    public void lootingBagUseTransfersAreConfirmedByInventoryChangesAndNotDoubled() {
        LootingBagItems bag = new LootingBagItems();
        bag.onItemContainerChanged(container(InventoryID.LOOTING_BAG, new Item(ItemID.COINS, 5)), itemManager);
        bag.onGameTick(client, itemManager);
        doReturn(container(InventoryID.INV, new Item(ItemID.COINS, 10)))
                .when(client)
                .getItemContainer(InventoryID.INV);
        Widget selected = mock(Widget.class);
        when(selected.getItemId()).thenReturn(ItemID.COINS);
        when(client.getSelectedWidget()).thenReturn(selected);
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getItemId()).thenReturn(ItemID.LOOTING_BAG_OPEN);
        when(entry.getOption()).thenReturn("Use");
        bag.onMenuOptionClicked(client, new MenuOptionClicked(entry), itemManager);
        bag.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.COINS, 5), pairs(bag.get()));
        doReturn(container(InventoryID.INV, new Item(ItemID.COINS, 6)))
                .when(client)
                .getItemContainer(InventoryID.INV);
        bag.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.COINS, 9), pairs(bag.get()));
        doReturn(container(InventoryID.INV)).when(client).getItemContainer(InventoryID.INV);
        bag.onItemContainerChanged(container(InventoryID.LOOTING_BAG, new Item(ItemID.COINS, 15)), itemManager);
        bag.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID.COINS, 15), pairs(bag.get()));
    }

    @Test
    public void nativeContainersClearOnlyAfterSuccessfulBankDeposits() {
        for (ContainerItems tracker : List.of(new LootingBagItems(), new SeedBoxItems())) {
            int itemId = tracker instanceof SeedBoxItems ? ItemID.RANARR_SEED : ItemID.COINS;
            int containerId = tracker instanceof SeedBoxItems ? ItemID.SEED_BOX_OPEN : ItemID.LOOTING_BAG_OPEN;
            tracker.onItemContainerChanged(container(tracker.inventoryId, new Item(itemId, 5)), itemManager);
            tracker.onGameTick(client, itemManager);
            assertEquals(Map.of(itemId, 5), tracker.onDepositContainers(client, itemManager, Set.of(containerId)));
            assertEquals(Map.of(itemId, 5), pairs(tracker.get()));
            chat(tracker, "You empty all of your containers into the bank.");
            tracker.onGameTick(client, itemManager);
            assertEquals(Map.of(), pairs(tracker.get()));
        }
    }

    @Test
    public void barrelDrinksUseKnownPotionIdentitiesAndDoNotGuessBetweenBrews() {
        ChuggingBarrelItems barrel = new ChuggingBarrelItems();
        barrel.onItemContainerChanged(
                container(InventoryID.PREPOT_DEVICE_INV, new Item(ItemID._1DOSE2RESTORE, 10)), itemManager);
        barrel.onGameTick(client, itemManager);
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getItemId()).thenReturn(ItemID.MM_PREPOT_DEVICE);
        when(entry.getOption()).thenReturn("Drink");
        barrel.onMenuOptionClicked(client, new MenuOptionClicked(entry), itemManager);
        chat(barrel, "You drink some of your super restore potion.");
        barrel.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID._1DOSE2RESTORE, 9), pairs(barrel.get()));
        barrel.onMenuOptionClicked(client, new MenuOptionClicked(entry), itemManager);
        chat(barrel, "You drink some of your super restore potion.");
        barrel.onItemContainerChanged(
                container(InventoryID.PREPOT_DEVICE_INV, new Item(ItemID._1DOSE2RESTORE, 8)), itemManager);
        barrel.onGameTick(client, itemManager);
        assertEquals(Map.of(ItemID._1DOSE2RESTORE, 8), pairs(barrel.get()));
        barrel.onItemContainerChanged(
                container(
                        InventoryID.PREPOT_DEVICE_INV,
                        new Item(ItemID._1DOSEANCIENTBREW, 10),
                        new Item(ItemID._1DOSEPOTIONOFSARADOMIN, 10)),
                itemManager);
        barrel.onGameTick(client, itemManager);
        barrel.onMenuOptionClicked(client, new MenuOptionClicked(entry), itemManager);
        chat(barrel, "You drink some of your foul liquid.");
        barrel.onGameTick(client, itemManager);
        assertEquals(10, pairs(barrel.get()).get(ItemID._1DOSEANCIENTBREW).intValue());
        assertEquals(10, pairs(barrel.get()).get(ItemID._1DOSEPOTIONOFSARADOMIN).intValue());
        chat(barrel, "Your chugging barrel has been filled with 2 doses of Ancient mix(1).");
        barrel.onGameTick(client, itemManager);
        assertEquals(2, pairs(barrel.get()).get(ItemID.BRUTAL_1DOSEANCIENTBREW).intValue());
        WidgetLoaded loaded = new WidgetLoaded();
        loaded.setGroupId(InterfaceID.OBJECTBOX_DOUBLE);
        barrel.onWidgetLoaded(loaded);
        Widget text = mock(Widget.class);
        when(text.getText()).thenReturn("You disassemble the Chugging barrel.");
        when(client.getWidget(InterfaceID.ObjectboxDouble.TEXT)).thenReturn(text);
        barrel.onGameTick(client, itemManager);
        assertEquals(Map.of(), pairs(barrel.get()));
    }

    @Test
    public void miningOnlyAddsSupportedGemsToKnownOpenBagsWithRoom() {
        GemBagItems gems = new GemBagItems();
        chat(gems, "Sapphires: 59 / Emeralds: 0 / Rubies: 0 / Diamonds: 0 / Dragonstones: 0");
        ItemContainer inventory = container(InventoryID.INV, new Item(ItemID.GEM_BAG_OPEN, 1));
        when(inventory.contains(ItemID.GEM_BAG_OPEN)).thenReturn(true);
        when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
        chat(gems, "You just mined a sapphire!");
        assertEquals(60, pairs(gems.get()).get(ItemID.UNCUT_SAPPHIRE).intValue());
        chat(gems, "You just found a sapphire!");
        chat(gems, "You just mined an opal!");
        assertEquals(Map.of(ItemID.UNCUT_SAPPHIRE, 60), pairs(gems.get()));
        when(inventory.contains(ItemID.GEM_BAG_OPEN)).thenReturn(false);
        chat(gems, "You just mined an emerald!");
        assertEquals(Map.of(ItemID.UNCUT_SAPPHIRE, 60), pairs(gems.get()));
    }

    @Test
    public void openingNativeStorageWaitsForContentsInsteadOfPublishingEmptyData() {
        for (ContainerItems tracker : List.of(new LootingBagItems(), new SeedBoxItems(), new ChuggingBarrelItems())) {
            tracker.onGameTick(client, itemManager);
            WidgetLoaded loaded = new WidgetLoaded();
            loaded.setGroupId(tracker.interfaceId);
            tracker.onWidgetLoaded(loaded);
            when(client.getWidget(tracker.interfaceId, 0)).thenReturn(mock(Widget.class));
            tracker.onGameTick(client, itemManager);
            assertNull(tracker.get());
            doReturn(container(tracker.inventoryId, new Item(ItemID.COINS, 10)))
                    .when(client)
                    .getItemContainer(tracker.inventoryId);
            tracker.onGameTick(client, itemManager);
            assertEquals(Map.of(ItemID.COINS, 10), pairs(tracker.get()));
            when(client.getWidget(tracker.interfaceId, 0)).thenReturn(null);
            when(client.getItemContainer(tracker.inventoryId)).thenReturn(null);
            tracker.onGameTick(client, itemManager);
            assertEquals(Map.of(ItemID.COINS, 10), pairs(tracker.get()));
        }
    }

    private void chat(TrackedItemContainer tracker, String message) {
        tracker.onChatMessage(
                client, new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", message, "", 0), itemManager);
    }
}
