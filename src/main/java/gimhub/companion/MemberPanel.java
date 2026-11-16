package gimhub.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gimhub.companion.CompanionData.DiaryRegion;
import gimhub.companion.CompanionData.DiaryTask;
import gimhub.companion.CompanionData.DiaryTier;
import gimhub.companion.CompanionData.QuestProgress;
import gimhub.companion.CompanionData.SkillProgress;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

public class MemberPanel extends JPanel {
    private static final int INTERACTION_LIFETIME_SECONDS = 30;
    private static final Color ACCENT = CompanionStyle.ACCENT;
    private static final Color COMPLETE = new Color(104, 190, 126);
    private static final Color INCOMPLETE = new Color(220, 139, 139);
    private static final Color IN_PROGRESS = new Color(225, 202, 115);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault());
    private static final Map<String, BufferedImage> ARTWORK = new HashMap<>();
    private static final List<String> SKILL_ORDER = Arrays.asList(
            "Attack",
            "Hitpoints",
            "Mining",
            "Strength",
            "Agility",
            "Smithing",
            "Defence",
            "Herblore",
            "Fishing",
            "Ranged",
            "Thieving",
            "Cooking",
            "Prayer",
            "Crafting",
            "Firemaking",
            "Magic",
            "Fletching",
            "Woodcutting",
            "Runecraft",
            "Slayer",
            "Farming",
            "Construction",
            "Hunter",
            "Sailing");
    private static final EquipmentInventorySlot[] EQUIPMENT = {
        EquipmentInventorySlot.HEAD, EquipmentInventorySlot.CAPE, EquipmentInventorySlot.AMULET,
        EquipmentInventorySlot.AMMO, EquipmentInventorySlot.WEAPON, EquipmentInventorySlot.BODY,
        EquipmentInventorySlot.SHIELD, EquipmentInventorySlot.LEGS, EquipmentInventorySlot.GLOVES,
        EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.RING
    };
    private static final int[][] EQUIPMENT_POSITIONS = {
        {70, 0},
        {29, 39},
        {70, 39},
        {111, 39},
        {14, 78},
        {70, 78},
        {126, 78},
        {70, 118},
        {14, 158},
        {70, 158},
        {126, 158}
    };
    private static final int[] EMPTY_EQUIPMENT_ICONS = {156, 157, 158, 166, 159, 161, 162, 163, 164, 165, 160};

    public enum View {
        INVENTORY("Inventory", "inventory.png", "inventory"),
        EQUIPMENT("Equipment", "equipment.png", "equipment"),
        STATS("Stats", "stats.png", "skills"),
        QUESTS("Quests", "quests.png", "quests"),
        DIARIES("Achievement diaries", "diaries.png", "diary_vars");

        private final String label;
        private final String icon;
        private final String property;

        View(String label, String icon, String property) {
            this.label = label;
            this.icon = icon;
            this.property = property;
        }
    }

    private final BiFunction<Integer, Long, ImageIcon> itemIcons;
    private final SkillIconManager skillIcons;
    private final JLabel playerIcon = new JLabel();
    private final JLabel name = label("");
    private final JLabel localTime = label("");
    private final JLabel presence = label("");
    private final JPanel resources = stack();
    private final ResourceBar health = resourceBar(18);
    private final ResourceBar prayer = resourceBar(18);
    private final ResourceBar energy = resourceBar(3);
    private final ResourceBar special = resourceBar(3);
    private final JPanel body = new JPanel(new BorderLayout());
    private final Map<View, JToggleButton> buttons = new EnumMap<>(View.class);
    private final JTextField questSearch = new JTextField();
    private final JLabel questPoints = label("");
    private final JList<Object> questList = resultList();
    private final JPanel questView = new JPanel(new BorderLayout(0, CompanionStyle.GAP));
    private final JPanel diaryView = new JPanel(new BorderLayout(0, CompanionStyle.GAP));
    private final JPanel diaryRegions = stack();
    private final JScrollPane diaryRegionScroll = scroll(diaryRegions);
    private final JComboBox<String> diaryTier = new JComboBox<>();
    private final JList<DiaryTask> diaryTasks = resultList();
    private final JScrollPane diaryTaskScroll = scroll(diaryTasks);
    private JsonObject member;
    private CompanionData data;
    private View selectedView;
    private String selectedDiary;
    private String displayedDiary;
    private String displayedDiaryTier;
    private boolean updating;
    private boolean metadataReady;

    public MemberPanel(BiFunction<Integer, Long, ImageIcon> itemIcons, SkillIconManager skillIcons) {
        this.itemIcons = itemIcons;
        this.skillIcons = skillIcons;
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(0, 0));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(BorderFactory.createLineBorder(CompanionStyle.BORDER));

        localTime.setForeground(CompanionStyle.MUTED);
        JPanel heading = new JPanel(new BorderLayout(CompanionStyle.GAP, CompanionStyle.SMALL_GAP));
        heading.setOpaque(false);
        heading.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        JPanel identity = new JPanel(new BorderLayout(4, 0));
        identity.setOpaque(false);
        identity.add(playerIcon, BorderLayout.WEST);
        identity.add(name, BorderLayout.CENTER);
        heading.add(identity, BorderLayout.CENTER);
        heading.add(localTime, BorderLayout.EAST);
        heading.add(presence, BorderLayout.SOUTH);
        JPanel top = stack();
        top.add(heading);
        resources.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        resources.add(health);
        resources.add(Box.createVerticalStrut(4));
        resources.add(prayer);
        resources.add(Box.createVerticalStrut(CompanionStyle.SMALL_GAP));
        resources.add(energy);
        resources.add(Box.createVerticalStrut(CompanionStyle.SMALL_GAP));
        resources.add(special);
        top.add(resources);

        JPanel toolbar = new JPanel(new GridLayout(1, View.values().length, CompanionStyle.SMALL_GAP, 0));
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, CompanionStyle.BORDER),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        for (View view : View.values()) {
            BufferedImage image = artwork(view.icon);
            JToggleButton button = new JToggleButton(new ImageIcon(
                    ImageUtil.resizeImage(image, Math.round(24f * image.getWidth() / image.getHeight()), 24)));
            button.setToolTipText(view.label);
            button.getAccessibleContext().setAccessibleName(view.label);
            CompanionStyle.iconButton(button);
            button.addActionListener(event -> selectView(selectedView == view ? null : view));
            buttons.put(view, button);
            toolbar.add(button);
        }
        top.add(toolbar);
        add(top, BorderLayout.NORTH);
        body.setOpaque(false);
        body.setVisible(false);
        add(body, BorderLayout.CENTER);
        prepareQuests();
        prepareDiaries();
    }

    public void update(JsonObject member, CompanionData data) {
        boolean redraw = this.member == null
                || metadataReady != data.hasMetadata()
                || selectedView != null
                        && !Objects.equals(this.member.get(selectedView.property), member.get(selectedView.property));
        this.member = member;
        this.data = data;
        metadataReady = data.hasMetadata();
        name.setText(CompanionData.displayName(member));
        name.setToolTipText(CompanionData.displayName(member));
        playerIcon.setIcon(new ImageIcon(playerIcon(
                member.has("color_hue_degrees")
                        ? member.get("color_hue_degrees").getAsFloat()
                        : 0)));
        localTime.setText(
                member.has("timezone")
                        ? DateTimeFormatter.ofPattern("HH:mm")
                                .format(Instant.now()
                                        .atZone(ZoneId.of(member.get("timezone").getAsString())))
                        : "");
        boolean online = member.has("last_online_at")
                && Instant.parse(member.get("last_online_at").getAsString())
                        .isAfter(Instant.now().minusSeconds(300));
        JsonArray stats = member.getAsJsonArray("stats");
        presence.setText(
                online
                        ? "Online" + (stats == null ? "" : " W" + stats.get(6).getAsInt())
                        : member.has("last_online_at")
                                ? "Last online "
                                        + DATE.format(Instant.parse(
                                                member.get("last_online_at").getAsString()))
                                : "Offline");
        presence.setForeground(online ? COMPLETE : CompanionStyle.MUTED);
        resources.setVisible(stats != null);
        if (stats != null) {
            updateBar(
                    health,
                    stats.get(0).getAsInt(),
                    stats.get(1).getAsInt(),
                    online ? new Color(0x157145) : Color.GRAY,
                    online ? new Color(0x073823) : new Color(0x383838),
                    "Hitpoints",
                    true);
            updateBar(
                    prayer,
                    stats.get(2).getAsInt(),
                    stats.get(3).getAsInt(),
                    online ? new Color(0x336699) : Color.GRAY,
                    online ? new Color(0x112233) : new Color(0x383838),
                    "Prayer",
                    true);
            updateBar(
                    energy,
                    stats.get(4).getAsInt(),
                    10000,
                    online ? new Color(0xA9A9A9) : Color.GRAY,
                    new Color(0x383838),
                    "Run energy",
                    false);
            updateBar(
                    special,
                    stats.get(7).getAsInt(),
                    100,
                    online ? new Color(0x397D3B) : Color.GRAY,
                    new Color(0x383838),
                    "Special attack",
                    false);
        }
        JsonObject interacting = member.getAsJsonObject("interacting");
        health.contextText = online
                        && interacting != null
                        && Instant.parse(interacting.get("last_updated").getAsString())
                                .isAfter(Instant.now().minusSeconds(INTERACTION_LIFETIME_SECONDS))
                ? interacting.get("name").getAsString()
                : null;
        if (health.contextText != null) {
            int scale = interacting.get("scale").getAsInt();
            int ratio = interacting.get("ratio").getAsInt();
            health.contextRatio = scale > 0 && ratio >= 0 ? (float) ratio / scale : 1;
            health.contextColor = scale > 0 && ratio >= 0 ? new Color(0xA41623) : new Color(0x333355);
            health.setToolTipText(health.getToolTipText() + " (" + health.contextText + ")");
        }
        if (redraw) {
            refreshView();
        }
    }

    public void selectView(View view) {
        selectedView = view;
        buttons.forEach((candidate, button) -> button.setSelected(candidate == view));
        refreshView();
    }

    private void refreshView() {
        body.removeAll();
        body.setVisible(selectedView != null);
        if (selectedView == null || member == null) {
            revalidate();
            repaint();
            return;
        }
        body.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        if (!data.hasMetadata()) {
            body.add(note("Loading game data..."));
        } else if (selectedView == View.INVENTORY || selectedView == View.EQUIPMENT) {
            JPanel centered = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            centered.setOpaque(false);
            centered.add(new ItemGrid(selectedView == View.INVENTORY));
            body.add(centered);
        } else if (selectedView == View.STATS) {
            JPanel centered = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            centered.setOpaque(false);
            centered.add(new SkillGrid());
            body.add(centered);
        } else if (selectedView == View.QUESTS) {
            refreshQuests();
            body.add(questView);
        } else {
            refreshDiaries();
            body.add(diaryView);
        }
        revalidate();
        repaint();
    }

    private void prepareQuests() {
        JPanel filters = stack();
        CompanionStyle.input(questSearch);
        questSearch.putClientProperty("JTextField.placeholderText", "Search quests...");
        questSearch.getAccessibleContext().setAccessibleName("Search quests");
        questPoints.setForeground(CompanionStyle.MUTED);
        filters.add(questSearch);
        filters.add(Box.createVerticalStrut(CompanionStyle.SMALL_GAP));
        filters.add(questPoints);
        questView.setOpaque(false);
        questView.add(filters, BorderLayout.NORTH);
        questView.add(scroll(questList), BorderLayout.CENTER);
        questView.setPreferredSize(new Dimension(0, 300));
        questList.setFixedCellHeight(questSearch.getPreferredSize().height);
        CompanionStyle.hover(questList, value -> value instanceof QuestProgress);
        questList.setCellRenderer((list, value, index, selected, focused) -> {
            JLabel row = label(value instanceof QuestProgress ? ((QuestProgress) value).getName() : value.toString());
            row.setOpaque(true);
            row.setBackground(CompanionStyle.rowBackground(
                    selected && value instanceof QuestProgress, CompanionStyle.isHovered(list, index)));
            row.setBorder(CompanionStyle.rowBorder(selected && value instanceof QuestProgress));
            row.setForeground(value instanceof QuestProgress ? questColor(((QuestProgress) value).getState()) : ACCENT);
            if (value instanceof QuestProgress) {
                QuestProgress quest = (QuestProgress) value;
                row.setToolTipText(quest.getName() + " (" + questStatus(quest.getState()) + ")");
            }
            return row;
        });
        questList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int index = questList.locationToIndex(event.getPoint());
                if (SwingUtilities.isLeftMouseButton(event)
                        && event.getClickCount() == 1
                        && index >= 0
                        && questList.getCellBounds(index, index).contains(event.getPoint())
                        && questList.getModel().getElementAt(index) instanceof QuestProgress) {
                    questList.setSelectedIndex(index);
                    openSelectedQuest();
                }
            }
        });
        questSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshQuests();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshQuests();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshQuests();
            }
        });
    }

    private void openSelectedQuest() {
        Object selected = questList.getSelectedValue();
        if (selected instanceof QuestProgress) {
            QuestProgress quest = (QuestProgress) selected;
            LinkBrowser.browse("https://oldschool.runescape.wiki/w/"
                    + URLEncoder.encode(quest.getName().replace(' ', '_'), StandardCharsets.UTF_8)
                    + "/Quick_guide");
        }
    }

    private void refreshQuests() {
        if (member == null || !data.hasMetadata()) {
            return;
        }
        List<QuestProgress> quests = data.quests(CompanionData.displayName(member));
        questPoints.setText("Quest points: "
                + quests.stream()
                        .filter(QuestProgress::isComplete)
                        .mapToInt(QuestProgress::getPoints)
                        .sum() + " / "
                + quests.stream().mapToInt(QuestProgress::getPoints).sum());
        List<Object> rows = new ArrayList<>();
        String search = questSearch.getText().toLowerCase(Locale.ROOT);
        for (String section : Arrays.asList("Free quests", "Members' quests", "Miniquests")) {
            List<QuestProgress> entries = quests.stream()
                    .filter(quest -> quest.getSection().equals(section)
                            && quest.getName().toLowerCase(Locale.ROOT).contains(search))
                    .collect(Collectors.toList());
            if (!entries.isEmpty()) {
                rows.add(section);
                rows.addAll(entries);
            }
        }
        Point position = ((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, questList))
                .getViewport()
                .getViewPosition();
        questList.setListData(rows.toArray());
        ((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, questList))
                .getViewport()
                .setViewPosition(position);
    }

    private void prepareDiaries() {
        diaryView.setOpaque(false);
        diaryView.setPreferredSize(new Dimension(0, 320));
        CompanionStyle.choice(diaryTier);
        CompanionStyle.hover(diaryTasks, task -> true);
        JTextArea taskRow = note("");
        diaryTasks.setCellRenderer((list, task, index, selected, focused) -> {
            taskRow.setText(task.getName());
            taskRow.setBackground(CompanionStyle.rowBackground(selected, CompanionStyle.isHovered(list, index)));
            taskRow.setBorder(CompanionStyle.rowBorder(selected));
            taskRow.setForeground(
                    task.getComplete() == null ? Color.LIGHT_GRAY : task.getComplete() ? COMPLETE : INCOMPLETE);
            taskRow.setToolTipText(task.getName() + " (Open on OSRS Wiki)");
            taskRow.setSize(list.getFixedCellWidth(), Short.MAX_VALUE);
            return taskRow;
        });
        diaryTaskScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                int width = diaryTaskScroll.getViewport().getExtentSize().width;
                if (width <= 0 || width == diaryTasks.getFixedCellWidth()) {
                    return;
                }
                Point position = diaryTaskScroll.getViewport().getViewPosition();
                int index = diaryTasks.locationToIndex(position);
                Rectangle previous = index < 0 ? null : diaryTasks.getCellBounds(index, index);
                diaryTasks.setFixedCellWidth(width);
                if (previous != null) {
                    Rectangle current = diaryTasks.getCellBounds(index, index);
                    diaryTaskScroll
                            .getViewport()
                            .setViewPosition(new Point(
                                    position.x, current.y + Math.min(position.y - previous.y, current.height - 1)));
                }
            }
        });
        diaryTasks.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int index = diaryTasks.locationToIndex(event.getPoint());
                if (SwingUtilities.isLeftMouseButton(event)
                        && event.getClickCount() == 1
                        && index >= 0
                        && diaryTasks.getCellBounds(index, index).contains(event.getPoint())) {
                    diaryTasks.setSelectedIndex(index);
                    openSelectedDiaryTask();
                }
            }
        });
        diaryTier.addActionListener(event -> {
            if (!updating && selectedDiary != null) {
                refreshDiaryTasks();
            }
        });
    }

    private void openSelectedDiaryTask() {
        if (selectedDiary != null && diaryTasks.getSelectedValue() != null) {
            LinkBrowser.browse("https://oldschool.runescape.wiki/w/"
                    + URLEncoder.encode(selectedDiary.replace(' ', '_') + "_Diary", StandardCharsets.UTF_8)
                    + "#" + diaryTier.getSelectedItem());
        }
    }

    private void refreshDiaries() {
        diaryView.removeAll();
        List<DiaryRegion> regions = data.diaries(CompanionData.displayName(member));
        if (selectedDiary == null) {
            displayedDiary = null;
            displayedDiaryTier = null;
            JLabel title = label("Achievement diaries");
            title.setForeground(ACCENT);
            diaryView.add(title, BorderLayout.NORTH);
            Point position = diaryRegionScroll.getViewport().getViewPosition();
            diaryRegions.removeAll();
            for (DiaryRegion region : regions) {
                JButton button =
                        new JButton(region.getName() + " (" + region.completed() + "/" + region.total() + ")") {
                            @Override
                            protected void paintComponent(Graphics graphics) {
                                super.paintComponent(graphics);
                                int width = (getWidth() - 8) / region.getTiers().size();
                                for (int index = 0; index < region.getTiers().size(); index++) {
                                    DiaryTier tier = region.getTiers().get(index);
                                    float ratio = (float) tier.completed()
                                            / tier.getTasks().size();
                                    graphics.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
                                    graphics.fillRect(4 + index * width, getHeight() - 9, width - 1, 5);
                                    graphics.setColor(Color.getHSBColor(107f / 360 * ratio, 1, .82f));
                                    graphics.fillRect(
                                            4 + index * width, getHeight() - 9, (int) ((width - 1) * ratio), 5);
                                }
                            }
                        };
                CompanionStyle.button(button);
                button.setMargin(new java.awt.Insets(4, 8, 12, 8));
                int height = button.getPreferredSize().height + CompanionStyle.GAP;
                button.setPreferredSize(new Dimension(button.getPreferredSize().width, height));
                button.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
                button.setHorizontalAlignment(JButton.LEFT);
                button.setToolTipText(region.getTiers().stream()
                        .map(tier -> tier.getName() + ": " + tier.completed() + "/"
                                + tier.getTasks().size())
                        .collect(Collectors.joining(", ")));
                button.addActionListener(event -> {
                    selectedDiary = region.getName();
                    refreshDiaries();
                    diaryView.revalidate();
                    diaryView.repaint();
                });
                diaryRegions.add(button);
                diaryRegions.add(Box.createVerticalStrut(4));
            }
            diaryView.add(diaryRegionScroll, BorderLayout.CENTER);
            diaryRegionScroll.getViewport().setViewPosition(position);
        } else {
            JPanel controls = stack();
            JButton back = new JButton("All diaries");
            CompanionStyle.button(back);
            back.addActionListener(event -> {
                selectedDiary = null;
                refreshDiaries();
                diaryView.revalidate();
                diaryView.repaint();
            });
            controls.add(back);
            controls.add(Box.createVerticalStrut(CompanionStyle.GAP));
            JLabel title = label(selectedDiary);
            title.setForeground(ACCENT);
            controls.add(title);
            controls.add(Box.createVerticalStrut(CompanionStyle.SMALL_GAP));
            String previous = (String) diaryTier.getSelectedItem();
            updating = true;
            diaryTier.removeAllItems();
            for (DiaryTier tier : selectedRegion().getTiers()) {
                diaryTier.addItem(tier.getName());
            }
            if (previous != null) {
                diaryTier.setSelectedItem(previous);
            }
            updating = false;
            controls.add(diaryTier);
            diaryView.add(controls, BorderLayout.NORTH);
            diaryView.add(diaryTaskScroll, BorderLayout.CENTER);
            refreshDiaryTasks();
        }
    }

    private DiaryRegion selectedRegion() {
        return data.diaries(CompanionData.displayName(member)).stream()
                .filter(region -> region.getName().equals(selectedDiary))
                .findFirst()
                .orElseThrow();
    }

    private void refreshDiaryTasks() {
        String tierName = (String) diaryTier.getSelectedItem();
        boolean sameContext =
                Objects.equals(selectedDiary, displayedDiary) && Objects.equals(tierName, displayedDiaryTier);
        DiaryTask selectedTask = sameContext ? diaryTasks.getSelectedValue() : null;
        Point taskPosition = sameContext ? diaryTaskScroll.getViewport().getViewPosition() : new Point();
        DiaryTier tier = selectedRegion().getTiers().stream()
                .filter(candidate -> candidate.getName().equals(tierName))
                .findFirst()
                .orElseThrow();
        diaryTasks.setListData(tier.getTasks().toArray(new DiaryTask[0]));
        if (selectedTask != null) {
            for (int index = 0; index < tier.getTasks().size(); index++) {
                if (tier.getTasks().get(index).getName().equals(selectedTask.getName())) {
                    diaryTasks.setSelectedIndex(index);
                    break;
                }
            }
        }
        diaryTaskScroll.getViewport().setViewPosition(taskPosition);
        displayedDiary = selectedDiary;
        displayedDiaryTier = tierName;
    }

    private class ItemGrid extends JPanel {
        private final boolean inventory;

        private ItemGrid(boolean inventory) {
            this.inventory = inventory;
            setOpaque(false);
            setPreferredSize(new Dimension(inventory ? 190 : 175, inventory ? 254 : 200));
            setToolTipText("");
            getAccessibleContext().setAccessibleName(inventory ? "Inventory" : "Equipment");
            List<String> contents = new ArrayList<>();
            if (items() != null) {
                for (int index = 0; index < (inventory ? 28 : EQUIPMENT.length); index++) {
                    int slot = inventory ? index : EQUIPMENT[index].getSlotIdx();
                    int identifier = identifier(slot);
                    if (identifier > 0 && items().get(2 * slot + 1).getAsLong() > 0) {
                        contents.add(data.itemName(identifier) + ", quantity "
                                + number(items().get(2 * slot + 1).getAsLong()));
                    }
                }
            }
            getAccessibleContext()
                    .setAccessibleDescription(
                            items() == null
                                    ? "Not synced yet"
                                    : contents.isEmpty() ? "Empty" : String.join("; ", contents));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    int slot = slotAt(event.getX(), event.getY());
                    int identifier = identifier(slot);
                    if (SwingUtilities.isLeftMouseButton(event) && identifier > 0) {
                        LinkBrowser.browse(
                                "https://oldschool.runescape.wiki/w/Special:Lookup?type=item&id=" + identifier);
                    }
                }
            });
        }

        private JsonArray items() {
            return member.getAsJsonArray(inventory ? "inventory" : "equipment");
        }

        private int identifier(int slot) {
            JsonArray items = items();
            return slot < 0 || items == null || 2 * slot + 1 >= items.size()
                    ? 0
                    : items.get(2 * slot).getAsInt();
        }

        private int slotAt(int x, int y) {
            if (inventory) {
                int column = (x - 8) / 43;
                int row = (y - 8) / 34;
                return x < 8 || y < 8 || column >= 4 || row >= 7 ? -1 : row * 4 + column;
            }
            for (int index = 0; index < EQUIPMENT.length; index++) {
                int[] position = EQUIPMENT_POSITIONS[index];
                if (x >= position[0] && x < position[0] + 36 && y >= position[1] && y < position[1] + 36) {
                    return EQUIPMENT[index].getSlotIdx();
                }
            }
            return -1;
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int slot = slotAt(event.getX(), event.getY());
            int identifier = identifier(slot);
            return identifier > 0
                    ? data.itemName(identifier) + " x "
                            + number(items().get(2 * slot + 1).getAsLong())
                    : null;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (inventory) {
                graphics.drawImage(artwork("inventory-background.png"), 0, 0, 190, 254, null);
            }
            if (items() == null) {
                graphics.setColor(Color.LIGHT_GRAY);
                graphics.drawString("Not synced yet", 20, 30);
                return;
            }
            for (int index = 0; index < (inventory ? 28 : EQUIPMENT.length); index++) {
                int slot = inventory ? index : EQUIPMENT[index].getSlotIdx();
                int x = inventory ? 8 + index % 4 * 43 : EQUIPMENT_POSITIONS[index][0];
                int y = inventory ? 8 + index / 4 * 34 : EQUIPMENT_POSITIONS[index][1];
                int identifier = identifier(slot);
                if (!inventory) {
                    graphics.drawImage(artwork("equipment-slot.png"), x, y, null);
                }
                if (identifier > 0 && items().get(2 * slot + 1).getAsLong() > 0) {
                    long quantity = items().get(2 * slot + 1).getAsLong();
                    ImageIcon image = itemIcons.apply(identifier, quantity);
                    if (image != null) {
                        image.paintIcon(this, graphics, x, y + 2);
                    }
                    if (quantity > 1) {
                        String label = shortQuantity(quantity);
                        graphics.setColor(Color.BLACK);
                        graphics.drawString(label, x + 2, y + 12);
                        graphics.setColor(
                                quantity >= 10_000_000 ? COMPLETE : quantity >= 100_000 ? Color.WHITE : Color.YELLOW);
                        graphics.drawString(label, x + 1, y + 11);
                    }
                } else if (!inventory) {
                    BufferedImage empty = artwork("slot-" + EMPTY_EQUIPMENT_ICONS[index] + ".png");
                    graphics.drawImage(empty, x + (36 - empty.getWidth()) / 2, y + (36 - empty.getHeight()) / 2, null);
                }
            }
        }
    }

    private class SkillGrid extends JPanel {
        private SkillGrid() {
            setOpaque(false);
            setPreferredSize(new Dimension(186, 275));
            setToolTipText("");
            getAccessibleContext().setAccessibleName("Stats");
            Map<String, SkillProgress> progress = data.playerSkills(CompanionData.displayName(member));
            getAccessibleContext()
                    .setAccessibleDescription(
                            progress.isEmpty()
                                    ? "Stats not synced yet"
                                    : progress.values().stream()
                                            .map(skill -> skill.getName() + ", level " + skill.getLevel() + ", "
                                                    + number(skill.getExperience()) + " XP")
                                            .collect(Collectors.joining("; ")));
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = event.getY() / 32 * 3 + event.getX() / 62;
            if (index >= SKILL_ORDER.size()) {
                return "Total XP: "
                        + number(data.playerSkills(CompanionData.displayName(member)).values().stream()
                                .mapToLong(SkillProgress::getExperience)
                                .sum());
            }
            SkillProgress skill =
                    data.playerSkills(CompanionData.displayName(member)).get(SKILL_ORDER.get(index));
            return skill == null
                    ? "Not synced yet"
                    : skill.getName() + ": " + number(skill.getExperience()) + " XP (" + number(skill.getRemaining())
                            + " to next level)";
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Map<String, SkillProgress> progress = data.playerSkills(CompanionData.displayName(member));
            if (progress.isEmpty()) {
                graphics.setColor(Color.LIGHT_GRAY);
                graphics.drawString("Stats not synced yet", 10, 20);
                return;
            }
            for (int index = 0; index < SKILL_ORDER.size(); index++) {
                String name = SKILL_ORDER.get(index);
                SkillProgress skill = progress.get(name);
                int x = index % 3 * 62;
                int y = index / 3 * 32;
                graphics.drawImage(artwork("skill-background.png"), x, y, null);
                Skill runeliteSkill = Skill.valueOf(name.toUpperCase(Locale.ROOT));
                BufferedImage icon = skillIcons.getSkillImage(runeliteSkill, true);
                graphics.drawImage(icon, x + (28 - icon.getWidth()) / 2, y + (30 - icon.getHeight()) / 2, null);
                graphics.setColor(Color.YELLOW);
                graphics.drawString(Integer.toString(skill.getLevel()), x + 30, y + 14);
                graphics.drawString(Integer.toString(skill.getLevel()), x + 43, y + 28);
                graphics.setColor(Color.getHSBColor(skill.getPercent() / 100f * 100 / 360, 1, .9f));
                graphics.fillRect(x + 4, y + 30, 54 * skill.getPercent() / 100, 2);
            }
            graphics.drawImage(artwork("total-level.png"), 0, 256, null);
            String total = "Total level: "
                    + progress.values().stream()
                            .mapToInt(SkillProgress::getLevel)
                            .sum();
            graphics.setColor(Color.YELLOW);
            graphics.drawString(total, (186 - graphics.getFontMetrics().stringWidth(total)) / 2, 270);
        }
    }

    static BufferedImage artwork(String name) {
        return ARTWORK.computeIfAbsent(name, file -> ImageUtil.loadImageResource(MemberPanel.class, file));
    }

    /** Applies the same sRGB colour matrix as the website's CSS hue-rotate filter. */
    static BufferedImage playerIcon(float hue) {
        BufferedImage source = artwork("player-icon.png");
        BufferedImage icon = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        double cosine = Math.cos(Math.toRadians(hue));
        double sine = Math.sin(Math.toRadians(hue));
        double[][] rotation = {
            {.213 + .787 * cosine - .213 * sine, .715 - .715 * cosine - .715 * sine, .072 - .072 * cosine + .928 * sine
            },
            {.213 - .213 * cosine + .143 * sine, .715 + .285 * cosine + .140 * sine, .072 - .072 * cosine - .283 * sine
            },
            {.213 - .213 * cosine - .787 * sine, .715 - .715 * cosine + .715 * sine, .072 + .928 * cosine + .072 * sine}
        };
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int original = source.getRGB(x, y);
                int red = original >> 16 & 255;
                int green = original >> 8 & 255;
                int blue = original & 255;
                int color = original & 0xff000000;
                for (int channel = 0; channel < rotation.length; channel++) {
                    double value =
                            rotation[channel][0] * red + rotation[channel][1] * green + rotation[channel][2] * blue;
                    color |= (int) Math.round(Math.max(0, Math.min(255, value))) << (16 - 8 * channel);
                }
                icon.setRGB(x, y, color);
            }
        }
        return icon;
    }

    private static class ResourceBar extends JProgressBar {
        private String valueText;
        private String contextText;
        private float contextRatio;
        private Color contextColor;

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (valueText != null) {
                int x = 6;
                int y = (getHeight()
                                + graphics.getFontMetrics().getAscent()
                                - graphics.getFontMetrics().getDescent())
                        / 2;
                graphics.setColor(Color.WHITE);
                graphics.drawString(valueText, x, y);
                if (contextText != null) {
                    int width = getWidth() * 42 / 100;
                    Graphics context = graphics.create((getWidth() - width) / 2, 1, width, getHeight() - 2);
                    context.setColor(new Color(0x383838));
                    context.fillRect(0, 0, width, getHeight() - 2);
                    context.setColor(contextColor);
                    context.fillRect(0, 0, Math.round(width * contextRatio), getHeight() - 2);
                    context.setColor(Color.WHITE);
                    context.drawString(
                            contextText, (width - context.getFontMetrics().stringWidth(contextText)) / 2, y - 1);
                    context.dispose();
                }
            }
        }
    }

    private static ResourceBar resourceBar(int height) {
        ResourceBar bar = new ResourceBar();
        bar.setBorderPainted(false);
        bar.setPreferredSize(new Dimension(190, height));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return bar;
    }

    private static void updateBar(
            ResourceBar bar, int current, int maximum, Color color, Color background, String name, boolean numbers) {
        bar.setMaximum(maximum);
        bar.setValue(current);
        bar.setForeground(color);
        bar.setBackground(background);
        bar.valueText = numbers ? current + " / " + maximum : null;
        bar.setToolTipText(
                name + ": " + (numbers ? current + " / " + maximum : Math.round(current * 100f / maximum) + "%"));
    }

    private static JLabel label(String value) {
        JLabel label = new JLabel(value);
        label.putClientProperty("html.disable", true);
        label.setForeground(CompanionStyle.TEXT);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JTextArea note(String value) {
        JTextArea text = new JTextArea(value);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setForeground(CompanionStyle.TEXT);
        text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        return text;
    }

    private static JPanel stack() {
        return CompanionStyle.stack();
    }

    private static JScrollPane scroll(java.awt.Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static <T> JList<T> resultList() {
        JList<T> list = new JList<T>() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        list.setFixedCellWidth(PluginPanel.PANEL_WIDTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        return list;
    }

    private static Color questColor(int state) {
        return state == 2 ? COMPLETE : state == 0 ? IN_PROGRESS : state == 1 ? INCOMPLETE : Color.LIGHT_GRAY;
    }

    private static String questStatus(int state) {
        return state == 2 ? "Complete" : state == 0 ? "In progress" : state == 1 ? "Not started" : "Not synced";
    }

    private static String shortQuantity(long quantity) {
        return quantity >= 10_000_000
                ? quantity / 1_000_000 + "M"
                : quantity >= 100_000 ? quantity / 1_000 + "K" : Long.toString(quantity);
    }

    private static String number(long value) {
        return String.format("%,d", value);
    }
}
