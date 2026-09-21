package gimhub.items.containers;

import gimhub.APISerializable;
import gimhub.items.ItemsUnordered;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

public class PohSpiceRackItems implements TrackedItemContainer {
    private static final Pattern CHECK = Pattern.compile(
            "(\\d+) x Red Spice\\.<br>(\\d+) x Brown Spice\\.<br>(\\d+) x Yellow Spice\\.<br>(\\d+) x Orange Spice\\.");
    private static final int[][] SPICES = {
        {
            ItemID.HUNDRED_DAVE_SPICE_RED_1,
            ItemID.HUNDRED_DAVE_SPICE_RED_2,
            ItemID.HUNDRED_DAVE_SPICE_RED_3,
            ItemID.HUNDRED_DAVE_SPICE_RED_4
        },
        {
            ItemID.HUNDRED_DAVE_SPICE_BROWN_1,
            ItemID.HUNDRED_DAVE_SPICE_BROWN_2,
            ItemID.HUNDRED_DAVE_SPICE_BROWN_3,
            ItemID.HUNDRED_DAVE_SPICE_BROWN_4
        },
        {
            ItemID.HUNDRED_DAVE_SPICE_YELLOW_1,
            ItemID.HUNDRED_DAVE_SPICE_YELLOW_2,
            ItemID.HUNDRED_DAVE_SPICE_YELLOW_3,
            ItemID.HUNDRED_DAVE_SPICE_YELLOW_4
        },
        {
            ItemID.HUNDRED_DAVE_SPICE_ORANGE_1,
            ItemID.HUNDRED_DAVE_SPICE_ORANGE_2,
            ItemID.HUNDRED_DAVE_SPICE_ORANGE_3,
            ItemID.HUNDRED_DAVE_SPICE_ORANGE_4
        },
    };

    private final int[] doses = new int[4];
    private ItemsUnordered items;
    private int[] previousInventory;
    private int[] beforeTransfer;
    private boolean withdrawing;
    private boolean depositMessageVisible;
    private boolean depositing;
    private int transferTicks;

    @Override
    public String key() {
        return "poh_spice_rack";
    }

    @Override
    public APISerializable get() {
        return items;
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) {
            return;
        }

        int[] currentInventory = inventoryDoses(inventory);
        Widget message = client.getWidget(InterfaceID.Messagebox.TEXT);
        String text = message == null || message.isHidden() ? "" : message.getText();
        Matcher check = CHECK.matcher(text);
        if (check.matches()) {
            for (int colour = 0; colour < SPICES.length; colour++) {
                doses[colour] = Integer.parseInt(check.group(colour + 1));
            }
            publish(itemManager);
            beforeTransfer = null;
            withdrawing = false;
            transferTicks = 0;
        }

        boolean depositMessage = text.equals("Your spices have been stored.");
        if (items != null) {
            Widget prompt = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
            boolean withdrawalPrompt = prompt != null
                    && !prompt.isHidden()
                    && prompt.getText().equals("How much spice would you like to take?:");
            if (withdrawalPrompt && !withdrawing) {
                beforeTransfer = currentInventory;
                depositing = false;
                transferTicks = 0;
            } else if (!withdrawalPrompt && withdrawing) {
                // The inventory update can arrive on either tick after the input closes.
                transferTicks = 2;
            }
            withdrawing = withdrawalPrompt;

            if (depositMessage && !depositMessageVisible) {
                beforeTransfer = previousInventory;
                depositing = true;
                transferTicks = 2;
            }

            if (beforeTransfer != null && transferTicks > 0) {
                boolean changed = false;
                for (int colour = 0; colour < SPICES.length; colour++) {
                    int difference = beforeTransfer[colour] - currentInventory[colour];
                    if ((depositing && difference > 0) || (!depositing && difference < 0)) {
                        doses[colour] += difference;
                        changed = true;
                    }
                }
                beforeTransfer = currentInventory;
                if (changed) {
                    publish(itemManager);
                }
                if (--transferTicks == 0) {
                    beforeTransfer = null;
                }
            }
        }

        depositMessageVisible = depositMessage;
        previousInventory = currentInventory;
    }

    private static int[] inventoryDoses(ItemContainer inventory) {
        int[] quantities = new int[SPICES.length];
        for (Item item : inventory.getItems()) {
            for (int colour = 0; colour < SPICES.length; colour++) {
                for (int dose = 1; dose <= 4; dose++) {
                    if (item.getId() == SPICES[colour][dose - 1]) {
                        quantities[colour] += item.getQuantity() * dose;
                    }
                }
            }
        }
        return quantities;
    }

    private void publish(ItemManager itemManager) {
        Map<Integer, Integer> contents = new HashMap<>();
        for (int colour = 0; colour < SPICES.length; colour++) {
            if (doses[colour] >= 4) {
                contents.put(SPICES[colour][3], doses[colour] / 4);
            }
            if (doses[colour] % 4 > 0) {
                contents.put(SPICES[colour][doses[colour] % 4 - 1], 1);
            }
        }
        items = new ItemsUnordered(contents, itemManager);
    }
}
