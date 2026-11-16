package gimhub.companion;

import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

public class CompanionPanel extends PluginPanel {
    private static final Color ACCENT = CompanionStyle.ACCENT;
    private final CompanionClient companion;
    private final ItemManager itemManager;
    private final SkillIconManager skillIconManager;
    private final Map<List<Integer>, ImageIcon> itemIcons = CacheBuilder.newBuilder()
            .maximumSize(128)
            .<List<Integer>, ImageIcon>build()
            .asMap();
    private int activityUnread;
    private final JTabbedPane tabs = new JTabbedPane() {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (activityUnread > 0) {
                Rectangle bounds = getBoundsAt(1);
                graphics.setColor(ACCENT);
                graphics.fillOval(bounds.x + bounds.width - 7, bounds.y + 5, 4, 4);
            }
        }
    };
    private final JPanel members = vertical();
    private final Map<Integer, MemberPanel> memberPanels = new HashMap<>();
    private CompanionData displayedData;
    private List<Integer> memberOrder = new ArrayList<>();
    private final JTextArea status = text("");
    private final ItemsPanel itemsPanel;
    private final ActivityPanel activityPanel;
    private boolean active;

    public CompanionPanel(CompanionClient companion, ItemManager itemManager, SkillIconManager skillIconManager) {
        super(false);
        this.companion = companion;
        this.itemManager = itemManager;
        this.skillIconManager = skillIconManager;
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        tabs.putClientProperty(
                "FlatLaf.style", "underlineColor: #e5ab4d; hoverColor: #2d2d2d; contentAreaColor: #373737");
        add(status, BorderLayout.NORTH);

        activityPanel = new ActivityPanel(companion, skillIconManager, this::itemIcon);
        itemsPanel = new ItemsPanel(companion, this::itemIcon);
        tabs.addTab("Members", scroll(members));
        tabs.addTab("Activity", activityPanel);
        tabs.addTab("Items", itemsPanel);
        add(tabs, BorderLayout.CENTER);
        tabs.addChangeListener(event -> update());
        update();
    }

    public void update() {
        status.setText(companion.getStatus());
        status.setVisible(!companion.getStatus().isEmpty());
        status.setForeground(ACCENT);
        refreshSelectedPanel();
        updateActivityTab();
        revalidate();
        repaint();
    }

    private void refreshSelectedPanel() {
        int selected = tabs.getSelectedIndex();
        activityPanel.setViewing(active && selected == 1);
        switch (selected) {
            case 0:
                renderMembers();
                break;
            case 1:
                activityPanel.update();
                break;
            case 2:
                itemsPanel.update();
                break;
        }
    }

    private void renderMembers() {
        CompanionData data = companion.getData();
        List<JsonObject> group = data.players();
        List<Integer> order =
                group.stream().map(member -> member.get("id").getAsInt()).collect(Collectors.toList());
        boolean rebuild = displayedData != data || !memberOrder.equals(order);
        if (displayedData != data) {
            memberPanels.clear();
            displayedData = data;
        }
        if (rebuild) {
            members.removeAll();
            memberOrder = order;
        }
        for (JsonObject member : group) {
            int identifier = member.get("id").getAsInt();
            MemberPanel panel = memberPanels.computeIfAbsent(
                    identifier, ignored -> new MemberPanel(this::itemIcon, skillIconManager));
            panel.update(member, data);
            if (rebuild) {
                members.add(panel);
                members.add(Box.createVerticalStrut(8));
            }
        }
        memberPanels.keySet().retainAll(order);
        if (rebuild && group.isEmpty()) {
            members.add(text("Your group members will appear here."));
        }
        members.revalidate();
        members.repaint();
    }

    private ImageIcon itemIcon(int identifier, long quantity) {
        int amount = (int) Math.min(quantity, Integer.MAX_VALUE);
        return itemIcons.computeIfAbsent(List.of(identifier, amount), key -> {
            AsyncBufferedImage image = itemManager.getImage(identifier, amount, false);
            if (image == null) {
                return null;
            }
            image.onLoaded(this::repaint);
            return new ImageIcon(image);
        });
    }

    private void updateActivityTab() {
        activityUnread = companion.getUnreadCount();
        String description = activityUnread > 0 ? "Activity (" + activityUnread + " unread)" : "Activity";
        tabs.setToolTipTextAt(1, description);
        tabs.getAccessibleContext().getAccessibleChild(1).getAccessibleContext().setAccessibleDescription(description);
    }

    @Override
    public void onActivate() {
        active = true;
        companion.setVisible(true);
        update();
    }

    @Override
    public void onDeactivate() {
        active = false;
        activityPanel.setViewing(false);
        companion.setVisible(false);
    }

    private static JPanel vertical() {
        JPanel panel = new JPanel() {
            @Override
            protected void addImpl(Component component, Object constraints, int index) {
                if (component instanceof JComponent) {
                    ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
                }
                super.addImpl(component, constraints, index);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return panel;
    }

    private static JScrollPane scroll(Component content) {
        if (content instanceof JPanel) {
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
            wrapper.add(content, BorderLayout.NORTH);
            content = wrapper;
        }
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static JTextArea text(String value) {
        JTextArea area = new JTextArea(value);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setForeground(CompanionStyle.TEXT);
        area.setBackground(ColorScheme.DARK_GRAY_COLOR);
        area.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        return area;
    }
}
