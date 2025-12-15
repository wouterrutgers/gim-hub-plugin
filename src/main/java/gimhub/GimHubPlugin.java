package gimhub;

import com.google.inject.Provides;
import gimhub.DataManager.PlayerState;
import gimhub.items.ItemsUtilities;
import java.time.temporal.ChronoUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
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
        PlayerState state = dataManager.getMaybeResetState(client);
        if (state == null) return;

        String playerName = client.getLocalPlayer().getName();
        dataManager.submitToApi(playerName);
    }

    @Schedule(period = SECONDS_BETWEEN_UPLOADS, unit = ChronoUnit.SECONDS)
    public void updateThingsThatDoChangeOften() {
        PlayerState state = dataManager.getMaybeResetState(client);
        if (state == null) return;

        state.activityRepository.updateResources(client);
        state.activityRepository.updateLocation(client);

        state.itemRepository.updateRunepouch(client, itemManager);
        state.itemRepository.updateQuiver(client, itemManager);
    }

    @Schedule(period = SECONDS_BETWEEN_INFREQUENT_DATA_CHANGES, unit = ChronoUnit.SECONDS)
    public void updateThingsThatDoNotChangeOften() {
        PlayerState state = dataManager.getMaybeResetState(client);
        if (state == null) return;

        state.achievementRepository.update(client);
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        PlayerState state = dataManager.getMaybeResetState(client);
        if (state == null) return;

        final int varpId = event.getVarpId();
        final int varbitId = event.getVarbitId();

        if (ItemsUtilities.isQuiver(varpId)) {
            state.itemRepository.updateQuiver(client, itemManager);
        }
        if (ItemsUtilities.isRunePouch(varbitId)) {
            state.itemRepository.updateRunepouch(client, itemManager);
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        PlayerState state = dataManager.getMaybeResetState(client);
        if (state == null) return;

        state.activityRepository.updateInteracting(client);

        Widget groupStorageLoaderText =
                client.getWidget(WIDGET_GROUP_STORAGE_LOADER_PARENT, WIDGET_GROUP_STORAGE_LOADER_TEXT_CHILD);
        if (groupStorageLoaderText != null && groupStorageLoaderText.getText().equalsIgnoreCase("saving...")) {
            state.itemRepository.commitSharedBank();
        }

        state.itemRepository.onGameTick();

        // It seems onGameTick runs after all other subscribed callbacks, so this is a good spot to stage all the state
        // changes.
        dataManager.stageForSubmitToAPI();
    }

    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        PlayerState state = dataManager.getMaybeResetState(client);
        if (state == null) return;

        state.activityRepository.updateSkills(client);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        PlayerState state = dataManager.getMaybeResetState(client);
        if (state == null) return;

        ItemContainer container = event.getItemContainer();
        state.itemRepository.onItemContainerChanged(container, itemManager);
    }

    @Subscribe
    private void onScriptPostFired(ScriptPostFired event) {
        PlayerState state = dataManager.getMaybeResetState(client);
        if (state == null) return;

        final boolean enteredChatbox = event.getScriptId() == SCRIPT_CHATBOX_ENTERED;
        final boolean depositBoxWidgetIsOpen = client.getWidget(InterfaceID.BankDepositbox.INVENTORY) != null;
        if (enteredChatbox && depositBoxWidgetIsOpen) {
            state.itemRepository.itemsMayHaveBeenDeposited();
        }
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        PlayerState state = dataManager.getMaybeResetState(client);
        if (state == null) return;

        final int param1 = event.getParam1();
        final MenuAction menuAction = event.getMenuAction();
        final boolean depositButtonWasClicked = menuAction == MenuAction.CC_OP
                && (param1 == WIDGET_DEPOSIT_ITEM_BUTTON
                        || param1 == WIDGET_DEPOSIT_INVENTORY_BUTTON
                        || param1 == WIDGET_DEPOSIT_EQUIPMENT_BUTTON);
        if (depositButtonWasClicked) {
            state.itemRepository.itemsMayHaveBeenDeposited();
        }
    }

    @Subscribe
    private void onInteractingChanged(InteractingChanged event) {
        PlayerState state = dataManager.getMaybeResetState(client);
        if (state == null || event.getSource() != client.getLocalPlayer()) return;

        state.activityRepository.updateInteracting(client);
    }

    @Provides
    GimHubConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GimHubConfig.class);
    }
}
