package gimhub;

import com.google.inject.Provides;
import java.time.temporal.ChronoUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;

@Slf4j
@PluginDescriptor(name = "GIM hub")
public class GimHubPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private DataManager dataManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private CollectionLogManager collectionLogManager;

    @Inject
    private CollectionLogWidgetSubscriber collectionLogWidgetSubscriber;

    private int itemsDeposited = 0;

    private static final int SECONDS_BETWEEN_UPLOADS = 1;
    private static final int SECONDS_BETWEEN_INFREQUENT_DATA_CHANGES = 60;
    private static final int GAME_TICKS_FOR_DEPOSIT_DETECTION = 2;

    private static final int WIDGET_DEPOSIT_ITEM_BUTTON = 12582914;
    private static final int WIDGET_DEPOSIT_INVENTORY_BUTTON = 12582916;
    private static final int WIDGET_DEPOSIT_EQUIPMENT_BUTTON = 12582918;
    private static final int SCRIPT_CHATBOX_ENTERED = 681;
    private static final int WIDGET_GROUP_STORAGE_LOADER_PARENT = 293;
    private static final int WIDGET_GROUP_STORAGE_LOADER_TEXT_CHILD = 1;

    @Override
    protected void startUp() throws Exception {
        collectionLogWidgetSubscriber.startUp();
        log.info("GIM hub started!");
    }

    @Override
    protected void shutDown() throws Exception {
        collectionLogWidgetSubscriber.shutDown();
        log.info("GIM hub stopped!");
    }

    @Schedule(period = SECONDS_BETWEEN_UPLOADS, unit = ChronoUnit.SECONDS, asynchronous = true)
    public void submitToApi() {
        dataManager.submitToApi();
    }

    @Schedule(period = SECONDS_BETWEEN_UPLOADS, unit = ChronoUnit.SECONDS)
    public void updateThingsThatDoChangeOften() {
        if (doNotUseThisData()) return;
        Player player = client.getLocalPlayer();
        String playerName = player.getName();
        StateRepository states = dataManager.getStateRepository();

        states.getResources().update(new ResourcesState(playerName, client));

        LocalPoint localPoint = player.getLocalLocation();
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);
        states.getPosition().update(new LocationState(playerName, worldPoint));

        states.getRunePouch().update(new RunePouchState(playerName, client));
        states.getQuiver().update(new QuiverState(playerName, client, itemManager));
    }

    @Schedule(period = SECONDS_BETWEEN_INFREQUENT_DATA_CHANGES, unit = ChronoUnit.SECONDS)
    public void updateThingsThatDoNotChangeOften() {
        if (doNotUseThisData()) return;
        String playerName = client.getLocalPlayer().getName();
        StateRepository states = dataManager.getStateRepository();
        states.getQuests().update(new QuestState(playerName, client));
        states.getAchievementDiary().update(new AchievementDiaryState(playerName, client));
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (doNotUseThisData()) return;

        final int varpId = event.getVarpId();
        if (varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO || varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT) {
            String playerName = client.getLocalPlayer().getName();
            dataManager.getStateRepository().getQuiver().update(new QuiverState(playerName, client, itemManager));
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        --itemsDeposited;
        updateInteracting();

        Widget groupStorageLoaderText =
                client.getWidget(WIDGET_GROUP_STORAGE_LOADER_PARENT, WIDGET_GROUP_STORAGE_LOADER_TEXT_CHILD);
        if (groupStorageLoaderText != null && groupStorageLoaderText.getText().equalsIgnoreCase("saving...")) {
            dataManager.getStateRepository().getSharedBank().commitTransaction();
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        if (doNotUseThisData()) return;
        String playerName = client.getLocalPlayer().getName();
        dataManager.getStateRepository().getSkills().update(new SkillState(playerName, client));
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (doNotUseThisData()) return;
        String playerName = client.getLocalPlayer().getName();
        StateRepository states = dataManager.getStateRepository();
        final int id = event.getContainerId();
        ItemContainer container = event.getItemContainer();

        if (id == InventoryID.BANK) {
            states.getDeposited().reset();
            states.getBank().update(new ItemContainerState(playerName, container, itemManager));
        } else if (id == InventoryID.SEED_VAULT) {
            states.getSeedVault().update(new ItemContainerState(playerName, container, itemManager));
        } else if (id == InventoryID.INV) {
            ItemContainerState newInventoryState = new ItemContainerState(playerName, container, itemManager, 28);
            if (itemsDeposited > 0) {
                updateDeposited(newInventoryState, (ItemContainerState)
                        states.getInventory().mostRecentState());
            }
            states.getInventory().update(newInventoryState);
        } else if (id == InventoryID.WORN) {
            ItemContainerState newEquipmentState = new ItemContainerState(playerName, container, itemManager, 14);
            if (itemsDeposited > 0) {
                updateDeposited(newEquipmentState, (ItemContainerState)
                        states.getEquipment().mostRecentState());
            }
            states.getEquipment().update(newEquipmentState);
        } else if (id == InventoryID.INV_GROUP_TEMP) {
            states.getSharedBank().update(new ItemContainerState(playerName, container, itemManager));
        }
    }

    @Subscribe
    private void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == SCRIPT_CHATBOX_ENTERED
                && client.getWidget(InterfaceID.BankDepositbox.INVENTORY) != null) {
            itemsMayHaveBeenDeposited();
        }
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        final int param1 = event.getParam1();
        final MenuAction menuAction = event.getMenuAction();
        if (menuAction == MenuAction.CC_OP
                && (param1 == WIDGET_DEPOSIT_ITEM_BUTTON
                        || param1 == WIDGET_DEPOSIT_INVENTORY_BUTTON
                        || param1 == WIDGET_DEPOSIT_EQUIPMENT_BUTTON)) {
            itemsMayHaveBeenDeposited();
        }
    }

    @Subscribe
    private void onInteractingChanged(InteractingChanged event) {
        if (event.getSource() != client.getLocalPlayer()) return;
        updateInteracting();
    }

    private void itemsMayHaveBeenDeposited() {
        itemsDeposited = GAME_TICKS_FOR_DEPOSIT_DETECTION;
    }

    private void updateInteracting() {
        Player player = client.getLocalPlayer();

        if (player != null) {
            Actor actor = player.getInteracting();

            if (actor != null) {
                String playerName = player.getName();
                dataManager
                        .getStateRepository()
                        .getInteracting()
                        .update(new InteractingState(playerName, actor, client));
            }
        }
    }

    private void updateDeposited(ItemContainerState newState, ItemContainerState previousState) {
        ItemContainerState deposited = newState.whatGotRemoved(previousState);
        dataManager.getStateRepository().getDeposited().update(deposited);
    }

    private boolean doNotUseThisData() {
        return client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null;
    }

    @Provides
    GimHubConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GimHubConfig.class);
    }
}
