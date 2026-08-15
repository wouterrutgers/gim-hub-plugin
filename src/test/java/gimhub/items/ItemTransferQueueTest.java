package gimhub.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;

public class ItemTransferQueueTest {
    private Client client;
    private ItemTransferQueue.BankSettings bankSettings;
    private ItemTransferQueue itemTransferQueue;

    @Before
    public void setUp() {
        client = mock(Client.class);
        when(client.getVarpValue(VarPlayerID.DEPOSITBOX_REQUESTEDQUANTITY)).thenReturn(4);
        when(client.getVarbitValue(VarbitID.BANK_QUANTITY_TYPE)).thenReturn(0);
        when(client.getVarbitValue(VarbitID.DEPOSITBOX_MODE)).thenReturn(0);
        bankSettings = new ItemTransferQueue.BankSettings(client);
        itemTransferQueue = new ItemTransferQueue();
    }

    @Test
    public void infersAConfirmedBankDeposit() {
        initialize(state(Map.of(100, 10), Map.of(), Map.of(), Map.of()), true, false);
        queue(InterfaceID.Bankside.ITEMS, 3, 100, 1);

        ItemTransferQueue.ContainersToUpdate result = itemTransferQueue.onGameTick(
                state(Map.of(100, 9), Map.of(), Map.of(100, 1), Map.of()), bankSettings, 2, true, false);

        assertEquals(Map.of(100, 1), result.bank);
    }

    @Test
    public void infersAConfirmedBankWithdrawal() {
        initialize(state(Map.of(), Map.of(), Map.of(100, 10), Map.of()), true, false);
        queue(InterfaceID.Bankmain.ITEMS, 2, 100, 1);

        ItemTransferQueue.ContainersToUpdate result = itemTransferQueue.onGameTick(
                state(Map.of(100, 1), Map.of(), Map.of(100, 9), Map.of()), bankSettings, 2, true, false);

        assertEquals(Map.of(100, 9), result.bank);
    }

    @Test
    public void ignoresAnActionWhenTheExpectedTransferDidNotOccur() {
        ItemTransferQueue.TrackedContainers unchanged = state(Map.of(100, 10), Map.of(), Map.of(), Map.of());
        initialize(unchanged, true, false);
        queue(InterfaceID.Bankside.ITEMS, 3, 100, 1);

        ItemTransferQueue.ContainersToUpdate result =
                itemTransferQueue.onGameTick(unchanged, bankSettings, 2, true, false);

        assertNull(result.bank);
        assertNull(result.tackleBox);
    }

    @Test
    public void appliesAConfirmedQueryQuantity() {
        initialize(state(Map.of(100, 10), Map.of(), Map.of(), Map.of()), true, false);
        queue(InterfaceID.Bankside.ITEMS, 7, 100, 1);
        itemTransferQueue.onXQuerySubmitted(4, 2);

        ItemTransferQueue.ContainersToUpdate result = itemTransferQueue.onGameTick(
                state(Map.of(100, 6), Map.of(), Map.of(100, 4), Map.of()), bankSettings, 2, true, false);

        assertEquals(Map.of(100, 4), result.bank);
    }

    @Test
    public void infersDepositAllInventory() {
        initialize(state(Map.of(100, 2, 200, 3), Map.of(), Map.of(), Map.of()), true, false);
        queue(InterfaceID.Bankmain.DEPOSITINV, 0, -1, 1);

        ItemTransferQueue.ContainersToUpdate result = itemTransferQueue.onGameTick(
                state(Map.of(), Map.of(), Map.of(100, 2, 200, 3), Map.of()), bankSettings, 2, true, false);

        assertEquals(Map.of(100, 2, 200, 3), result.bank);
    }

    @Test
    public void infersTackleBoxFillAndEmptyActions() {
        int itemIdentifier = ItemID.FEATHER;
        initialize(state(Map.of(itemIdentifier, 10), Map.of(), Map.of(), Map.of()), false, true);
        queue(InterfaceID.Inventory.ITEMS, 3, ItemID.TACKLE_BOX, 1);

        ItemTransferQueue.ContainersToUpdate filled = itemTransferQueue.onGameTick(
                state(Map.of(), Map.of(), Map.of(), Map.of(itemIdentifier, 10)), bankSettings, 2, false, true);
        assertEquals(Map.of(itemIdentifier, 10), filled.tackleBox);

        queue(InterfaceID.Inventory.ITEMS, 4, ItemID.TACKLE_BOX, 2);
        ItemTransferQueue.ContainersToUpdate emptied = itemTransferQueue.onGameTick(
                state(Map.of(itemIdentifier, 10), Map.of(), Map.of(), Map.of()), bankSettings, 3, false, true);
        assertEquals(Map.of(), emptied.tackleBox);
    }

    private void initialize(ItemTransferQueue.TrackedContainers state, boolean bankOpen, boolean tackleBoxOpen) {
        itemTransferQueue.onGameTick(state, bankSettings, 1, bankOpen, tackleBoxOpen);
    }

    private void queue(int parameterOne, int identifier, int itemIdentifier, int tickCount) {
        MenuOptionClicked event = mock(MenuOptionClicked.class);
        when(event.getParam1()).thenReturn(parameterOne);
        when(event.getId()).thenReturn(identifier);
        when(event.getItemId()).thenReturn(itemIdentifier);
        when(client.getTickCount()).thenReturn(tickCount);
        itemTransferQueue.onMenuOptionClicked(client, event);
    }

    private static ItemTransferQueue.TrackedContainers state(
            Map<Integer, Integer> inventory,
            Map<Integer, Integer> equipment,
            Map<Integer, Integer> bank,
            Map<Integer, Integer> tackleBox) {
        return new ItemTransferQueue.TrackedContainers(
                new HashMap<>(inventory), new HashMap<>(equipment), new HashMap<>(bank), new HashMap<>(tackleBox));
    }
}
