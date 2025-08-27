package men.groupiron;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static java.lang.Math.round;

@Slf4j
@Singleton
public class ManualUpdateButtonManager {
    private static final int DRAW_BURGER_MENU = 7812;
    private static final int FONT_COLOR = 0xFF981F;
    private static final int FONT_COLOR_ACTIVE = 0xFFFFFF;
    private static final String BUTTON_TEXT = "GIM";

    private final Client client;
    private final EventBus eventBus;
    private final CollectionLogWidgetSubscriber clogWidgetSubscriber;

    private int baseMenuHeight = -1;
    private int lastAttemptedUpdate = -1;

    @Inject
    public ManualUpdateButtonManager(Client client, EventBus eventBus, CollectionLogWidgetSubscriber clogWidgetSubscriber) {
        this.client = client;
        this.eventBus = eventBus;
        this.clogWidgetSubscriber = clogWidgetSubscriber;
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event) {
        if (event.getScriptId() != DRAW_BURGER_MENU) return;

        // args: [var0, var1, var2, menuId, ...]
        Object[] args = event.getScriptEvent().getArguments();
        if (args == null || args.length < 4) return;
        int menuId = (int) args[3];

        try {
            addButton(menuId, this::onButtonClick);
        } catch (Exception e) {
            log.debug("Failed adding GIM button: {}", e.getMessage());
        }
    }

    private void onButtonClick() {
        // Debounce frequent clicks
        if (lastAttemptedUpdate != -1 && lastAttemptedUpdate + 50 > client.getTickCount()) {
            int secsLeft = (int) round((lastAttemptedUpdate + 50 - client.getTickCount()) * 0.6);
            client.addChatMessage(ChatMessageType.CONSOLE, "GIM", "Last update within 30 seconds. You can update again in " + secsLeft + " seconds.", "GIM");
            return;
        }
        lastAttemptedUpdate = client.getTickCount();

        int collectionCount = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
        if (collectionCount == 0) {
            client.addChatMessage(ChatMessageType.CONSOLE, "GIM", "Syncing your collection log...", "GIM");
            return;
        }

        client.menuAction(-1, 40697932, MenuAction.CC_OP, 1, -1, "Search", null);
        client.runScript(2240);
        client.addChatMessage(ChatMessageType.CONSOLE, "GIM", "Syncing your collection log...", "GIM");
    }

    private void addButton(int menuId, Runnable onClick) throws NullPointerException, NoSuchElementException {
        boolean pohAdventureLog = client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
        if (pohAdventureLog) return;

        Widget menu = Objects.requireNonNull(client.getWidget(menuId));
        Widget[] children = Objects.requireNonNull(menu.getChildren());
        if (baseMenuHeight == -1) baseMenuHeight = menu.getOriginalHeight();

        List<Widget> reversed = new ArrayList<>(Arrays.asList(children));
        Collections.reverse(reversed);
        Widget lastRectangle = reversed.stream()
                .filter(w -> w.getType() == WidgetType.RECTANGLE)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No RECTANGLE widget found in menu"));

        Widget lastText = reversed.stream()
                .filter(w -> w.getType() == WidgetType.TEXT)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No TEXT widget found in menu"));

        final int buttonHeight = lastRectangle.getHeight();
        final int buttonY = lastRectangle.getOriginalY() + buttonHeight;

        boolean exists = Arrays.stream(children).anyMatch(w -> BUTTON_TEXT.equals(w.getText()));
        if (!exists) {
            Widget background = menu.createChild(WidgetType.RECTANGLE)
                    .setOriginalWidth(lastRectangle.getOriginalWidth())
                    .setOriginalHeight(lastRectangle.getOriginalHeight())
                    .setOriginalX(lastRectangle.getOriginalX())
                    .setOriginalY(buttonY)
                    .setOpacity(lastRectangle.getOpacity())
                    .setFilled(lastRectangle.isFilled());
            background.revalidate();

            Widget text = menu.createChild(WidgetType.TEXT)
                    .setText(BUTTON_TEXT)
                    .setTextColor(FONT_COLOR)
                    .setFontId(lastText.getFontId())
                    .setTextShadowed(lastText.getTextShadowed())
                    .setOriginalWidth(lastText.getOriginalWidth())
                    .setOriginalHeight(lastText.getOriginalHeight())
                    .setOriginalX(lastText.getOriginalX())
                    .setOriginalY(buttonY)
                    .setXTextAlignment(lastText.getXTextAlignment())
                    .setYTextAlignment(lastText.getYTextAlignment());
            text.setHasListener(true);
            text.setOnMouseOverListener((JavaScriptCallback) ev -> text.setTextColor(FONT_COLOR_ACTIVE));
            text.setOnMouseLeaveListener((JavaScriptCallback) ev -> text.setTextColor(FONT_COLOR));
            text.setAction(0, "Sync GIM");
            text.setOnOpListener((JavaScriptCallback) ev -> onClick.run());
            text.revalidate();
        }

        if (menu.getOriginalHeight() <= baseMenuHeight) {
            menu.setOriginalHeight(menu.getOriginalHeight() + buttonHeight);
        }

        menu.revalidate();
        for (Widget child : children) {
            child.revalidate();
        }
    }
}
