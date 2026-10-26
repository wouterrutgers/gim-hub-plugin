package gimhub.items.containers;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

public class CheckedStorageItems extends ChatStorageItems {
    protected static final Pattern ENTRY = Pattern.compile("([\\d,]+)(?:\\s*[x×]\\s*|\\s+)([^<,\\n]+)");
    protected final String key;
    protected final String label;
    protected Map<Integer, Integer> checking;
    protected int checkingDeadline = -1;
    protected boolean invalidCheck;

    public CheckedStorageItems(String key, String label, Set<Integer> containers, Set<Integer> contents) {
        super(containers, contents);
        this.key = key;
        this.label = label;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public void onMenuOptionClicked(Client client, MenuOptionClicked event, ItemManager itemManager) {
        if (containerIds.contains(event.getItemId()) && event.getMenuOption().equals("Check")) {
            checking = new HashMap<>();
            invalidCheck = false;
            checkingDeadline = client.getTickCount() + 3;
        }
        super.onMenuOptionClicked(client, event, itemManager);
    }

    protected void readCheck(String message, ItemManager itemManager) {
        if (checking == null) {
            return;
        }
        String text = Text.removeTags(message.replace("<br>", "\n")).trim().replace(" and ", "\n");
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" is empty.") || (lower.contains(label) && lower.contains(" contains no "))) {
            setItems(Map.of(), itemManager);
            checking = null;
            return;
        }
        Matcher entry = ENTRY.matcher(text);
        while (entry.find()) {
            String name = entry.group(2).trim().replaceFirst("[.!]$", "");
            Integer identifier = contentIds.stream()
                    .filter(item ->
                            itemManager.getItemComposition(item).getName().equalsIgnoreCase(name)
                                    || (itemManager.getItemComposition(item).getName() + "s").equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (identifier == null) {
                invalidCheck = true;
                return;
            }
            checking.put(identifier, Integer.parseInt(entry.group(1).replace(",", "")));
        }
    }

    @Override
    public void onChatMessage(Client client, ChatMessage event, ItemManager itemManager) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }
        if (confirmDeposit(Text.removeTags(event.getMessage()), itemManager)) {
            checking = null;
            return;
        }
        readCheck(event.getMessage(), itemManager);
    }

    @Override
    public void onGameTick(Client client, ItemManager itemManager) {
        Widget widget = client.getWidget(InterfaceID.Objectbox.TEXT);
        if (widget != null) {
            String text =
                    Text.removeTags(widget.getText().replace("<br>", "\n")).trim();
            if (checking == null && text.startsWith("The " + label + " contains:")) {
                checking = new HashMap<>();
                invalidCheck = false;
                checkingDeadline = client.getTickCount() + 3;
            }
            readCheck(widget.getText(), itemManager);
        }
        if (checking != null) {
            if (!checking.isEmpty() && !invalidCheck) {
                setItems(checking, itemManager);
                checking = null;
            }
            if (client.getTickCount() >= checkingDeadline) {
                checking = null;
            }
        }
        super.onGameTick(client, itemManager);
    }

    public static CheckedStorageItems meatPouch() {
        return new CheckedStorageItems(
                "meat_pouch",
                "meat pouch",
                Set.of(
                        ItemID.HG_MEATPOUCH_SMALL,
                        ItemID.HG_MEATPOUCH_LARGE,
                        ItemID.HG_MEATPOUCH_SMALL_OPEN,
                        ItemID.HG_MEATPOUCH_LARGE_OPEN),
                Set.of(
                        ItemID.SPIT_RAW_BIRD_MEAT,
                        ItemID.SPIT_RAW_BEAST_MEAT,
                        ItemID.HUNTINGBEAST_BARBED_MEAT,
                        ItemID.HUNTINGBEAST_WILD_MEAT,
                        ItemID.HUNTINGBEAST_SPEEDY2_MEAT,
                        ItemID.HUNTING_FENNECFOX_MEAT,
                        ItemID.HUNTING_ANTELOPEMOON_MEAT,
                        ItemID.HUNTING_ANTELOPESUN_MEAT,
                        ItemID.HUNTING_GRAAHK_MEAT,
                        ItemID.HUNTING_LARUPIA_MEAT,
                        ItemID.HUNTING_KYATT_MEAT));
    }

    public static CheckedStorageItems furPouch() {
        return new CheckedStorageItems(
                "fur_pouch",
                "fur pouch",
                Set.of(
                        ItemID.HG_FURPOUCH_SMALL,
                        ItemID.HG_FURPOUCH_MED,
                        ItemID.HG_FURPOUCH_LARGE,
                        ItemID.HG_FURPOUCH_SMALL_OPEN,
                        ItemID.HG_FURPOUCH_MED_OPEN,
                        ItemID.HG_FURPOUCH_LARGE_OPEN),
                Set.of(
                        ItemID.HUNTING_FUR_JAGUAR_PERFECT,
                        ItemID.HUNTING_FUR_LEOPARD_PERFECT,
                        ItemID.HUNTING_FUR_TIGER_PERFECT,
                        ItemID.HUNTINGBEAST_SILENT_FUR,
                        ItemID.HUNTINGBEAST_POLAR_FUR,
                        ItemID.HUNTINGBEAST_JUNGLE_FUR,
                        ItemID.HUNTINGBEAST_WOODLAND_FUR,
                        ItemID.HUNTINGBEAST_DESERT_FUR,
                        ItemID.HUNTINGBEAST_SPEEDY_FUR,
                        ItemID.HUNTINGBEAST_SPEEDY2_FUR,
                        ItemID.HUNTING_FENNECFOX_FUR,
                        ItemID.HUNTING_ANTELOPEMOON_FUR,
                        ItemID.HUNTING_ANTELOPESUN_FUR));
    }

    public static CheckedStorageItems logBasket() {
        return new LogBasketItems();
    }

    public static CheckedStorageItems reagentPouch() {
        return new CheckedStorageItems(
                "reagent_pouch",
                "reagent pouch",
                Set.of(ItemID.MM_SECONDARY_POUCH, ItemID.MM_SECONDARY_POUCH_OPEN),
                Set.of(
                        ItemID.EYE_OF_NEWT,
                        ItemID.LIMPWURT_ROOT,
                        ItemID.UNICORN_HORN_DUST,
                        ItemID.RED_SPIDERS_EGGS,
                        ItemID.CHOCOLATE_DUST,
                        ItemID.WHITE_BERRIES,
                        ItemID.TOADS_LEGS,
                        ItemID.SNAPE_GRASS,
                        ItemID.MORTMYREMUSHROOM,
                        ItemID.CRUSHED_BIRD_NEST,
                        ItemID.CACTUS_POTATO,
                        ItemID.WINE_OF_ZAMORAK,
                        ItemID.JANGERBERRIES,
                        ItemID.ASHES,
                        ItemID.FAIRYTALE2_GROUND_GORAK_CLAWS,
                        ItemID.HUNTINGBEAST_SABRETEETH_DUST,
                        ItemID.LILY_OF_THE_SANDS,
                        ItemID.NAIL_BEAST_NAIL,
                        ItemID.POISONIVY_BERRIES,
                        ItemID.SILVER_DUST,
                        ItemID.BRUT_ROE,
                        ItemID.BRUT_CAVIAR,
                        ItemID.SWAMP_TAR,
                        ItemID.GROUND_DESERT_GOAT_HORN,
                        ItemID.DRAGON_SCALE_DUST,
                        ItemID.CRUSHED_DRAGON_BONES,
                        ItemID.LAVA_SHARD,
                        ItemID.FOSSIL_VOLCANIC_ASH,
                        ItemID.AMYLASE,
                        ItemID.SNAKEBOSS_SCALE,
                        ItemID.SUNFIRESPLINTER,
                        ItemID.ALDARIUM,
                        ItemID.VIAL_COCONUT_MILK,
                        ItemID.SOTE_CRYSTAL_DUST,
                        ItemID.NIHIL_DUST));
    }

    public static CheckedStorageItems flamtaerBag() {
        return new CheckedStorageItems(
                "flamtaer_bag",
                "bag",
                Set.of(ItemID.FLAMTAER_BAG),
                Set.of(ItemID.TIMBERBEAM, ItemID.LIMESTONEBRICK, ItemID.SWAMPPASTE));
    }
}
