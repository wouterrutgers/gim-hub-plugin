package gimhub;

import com.google.inject.Provides;
import gimhub.items.ItemsUtilities;
import java.time.temporal.ChronoUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
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
    private CollectionLogWidgetSubscriber collectionLogWidgetSubscriber;

    private static final int SECONDS_BETWEEN_UPLOADS = 1;
    private static final int SECONDS_BETWEEN_INFREQUENT_DATA_CHANGES = 60;

    private static final int WIDGET_DEPOSIT_ITEM_BUTTON = 12582935;
    private static final int WIDGET_DEPOSIT_INVENTORY_BUTTON = 12582941;
    private static final int WIDGET_DEPOSIT_EQUIPMENT_BUTTON = 12582942;
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
        if (doNotUseThisData()) return;

        String playerName = client.getLocalPlayer().getName();
        dataManager.submitToApi(playerName);
    }

    @Schedule(period = SECONDS_BETWEEN_UPLOADS, unit = ChronoUnit.SECONDS)
    public void updateThingsThatDoChangeOften() {
        if (doNotUseThisData()) return;
        Player player = client.getLocalPlayer();
        String playerName = player.getName();
        StateRepository states = dataManager.getStateRepository();

        states.getResources().update(new ResourcesState(playerName, client));

        final int worldViewID = player.getWorldView().getId();
        final boolean isOnBoat = worldViewID != -1;
        WorldPoint location = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
        if (isOnBoat) {
            WorldEntity worldEntity =
                    client.getTopLevelWorldView().worldEntities().byIndex(worldViewID);
            location = WorldPoint.fromLocalInstance(client, worldEntity.getLocalLocation());
        }

        states.getPosition().update(new LocationState(playerName, location, isOnBoat));

        dataManager.getItemRepository().updateRunepouch(client);
        dataManager.getItemRepository().updateQuiver(client);
    }

    @Schedule(period = SECONDS_BETWEEN_INFREQUENT_DATA_CHANGES, unit = ChronoUnit.SECONDS)
    public void updateThingsThatDoNotChangeOften() {
        if (doNotUseThisData()) return;

        dataManager.getAchievementRepository().update(client);
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (doNotUseThisData()) return;

        final int varpId = event.getVarpId();
        final int varbitId = event.getVarbitId();

        if (ItemsUtilities.isQuiver(varpId)) {
            dataManager.getItemRepository().updateQuiver(client);
        }
        if (ItemsUtilities.isRunePouch(varbitId)) {
            dataManager.getItemRepository().updateRunepouch(client);
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        if (doNotUseThisData()) return;
        String playerName = client.getLocalPlayer().getName();

        updateInteracting();

        Widget groupStorageLoaderText =
                client.getWidget(WIDGET_GROUP_STORAGE_LOADER_PARENT, WIDGET_GROUP_STORAGE_LOADER_TEXT_CHILD);
        if (groupStorageLoaderText != null && groupStorageLoaderText.getText().equalsIgnoreCase("saving...")) {
            dataManager.getItemRepository().commitSharedBank(playerName);
        }

        dataManager.getItemRepository().onGameTick();
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
        ItemContainer container = event.getItemContainer();

        dataManager.getItemRepository().onItemContainerChanged(playerName, container);
    }

    @Subscribe
    private void onScriptPostFired(ScriptPostFired event) {
        if (doNotUseThisData()) return;
        String playerName = client.getLocalPlayer().getName();

        final boolean enteredChatbox = event.getScriptId() == SCRIPT_CHATBOX_ENTERED;
        final boolean depositBoxWidgetIsOpen = client.getWidget(InterfaceID.BankDepositbox.INVENTORY) != null;
        if (enteredChatbox && depositBoxWidgetIsOpen) {
            dataManager.getItemRepository().itemsMayHaveBeenDeposited(playerName);
        }
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        if (doNotUseThisData()) return;
        String playerName = client.getLocalPlayer().getName();

        final int param1 = event.getParam1();
        final MenuAction menuAction = event.getMenuAction();
        final boolean depositButtonWasClicked = param1 == WIDGET_DEPOSIT_ITEM_BUTTON
                || param1 == WIDGET_DEPOSIT_INVENTORY_BUTTON
                || param1 == WIDGET_DEPOSIT_EQUIPMENT_BUTTON;
        if (menuAction == MenuAction.CC_OP && depositButtonWasClicked) {
            dataManager.getItemRepository().itemsMayHaveBeenDeposited(playerName);
        }
    }

    @Subscribe
    private void onInteractingChanged(InteractingChanged event) {
        if (doNotUseThisData()) return;

        if (event.getSource() != client.getLocalPlayer()) return;
        updateInteracting();
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

    /**
     * A guard blocking client callbacks that may write invalid state, such as when the player is not logged in to a
     * main-game profile.
     */
    private boolean doNotUseThisData() {
        boolean isStandardProfile = RuneScapeProfileType.getCurrent(client) == RuneScapeProfileType.STANDARD;

        return client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null || !isStandardProfile;
    }

    @Provides
    GimHubConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GimHubConfig.class);
    }
}
