package gimhub.companion;

import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class ActivityPanel extends JPanel {
    private static final Color ACCENT = CompanionStyle.ACCENT;
    private static final Color MUTED = CompanionStyle.MUTED;
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final List<String> FILTERS =
            Arrays.asList("All activity", "XP", "Levels", "Boss kills", "Quests", "Diaries", "Drops");
    private static final List<String> TYPES = Arrays.asList("", "xp", "level", "boss", "quest", "diary", "loot");
    private final CompanionClient client;
    private final SkillIconManager skillIcons;
    private final BiFunction<Integer, Long, ImageIcon> itemIcons;
    private final JComboBox<String> filter = new JComboBox<>(FILTERS.toArray(new String[0]));
    private final JCheckBox newOnly = new JCheckBox("New only");
    private final JCheckBox filterDrops = new JCheckBox("Filter drops", true);
    private final JLabel sinceLastVisit = label("");
    private final JList<Row> feed = new JList<Row>() {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };
    private final JScrollPane scroller = new JScrollPane(feed);
    private final Set<Long> expanded = new HashSet<>();
    private final Map<Integer, ImageIcon> avatars = new HashMap<>();
    private CompanionData data;
    private Set<Long> highlights = new HashSet<>();
    private List<Row> rows = new ArrayList<>();
    private List<String> signatures = new ArrayList<>();
    private boolean viewing;
    private boolean restoringScroll;
    private boolean resetScroll;
    private long renderVersion;

    public ActivityPanel(
            CompanionClient client, SkillIconManager skillIcons, BiFunction<Integer, Long, ImageIcon> itemIcons) {
        this.client = client;
        this.skillIcons = skillIcons;
        this.itemIcons = itemIcons;
        setLayout(new BorderLayout(0, CompanionStyle.GAP));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel filters = new JPanel();
        filters.setLayout(new BoxLayout(filters, BoxLayout.Y_AXIS));
        filters.setOpaque(false);
        CompanionStyle.choice(filter);
        filter.getAccessibleContext().setAccessibleName("Filter activity by type");
        CompanionStyle.checkbox(newOnly);
        CompanionStyle.checkbox(filterDrops);
        newOnly.setToolTipText("Only show new or updated activity since you last viewed this tab.");
        newOnly.getAccessibleContext().setAccessibleName("Only new or updated activity");
        filterDrops.setToolTipText(
                "Only show drops worth at least 1m GP or new collection log unlocks. Other activity is always shown.");
        filter.setAlignmentX(LEFT_ALIGNMENT);
        JPanel options = new JPanel(new BorderLayout(CompanionStyle.SMALL_GAP, 0));
        options.setOpaque(false);
        options.setAlignmentX(LEFT_ALIGNMENT);
        options.add(newOnly, BorderLayout.WEST);
        options.add(filterDrops, BorderLayout.EAST);
        sinceLastVisit.setAlignmentX(LEFT_ALIGNMENT);
        sinceLastVisit.setForeground(MUTED);
        filters.add(filter);
        filters.add(javax.swing.Box.createVerticalStrut(CompanionStyle.SMALL_GAP));
        filters.add(options);
        filters.add(javax.swing.Box.createVerticalStrut(CompanionStyle.SMALL_GAP));
        filters.add(sinceLastVisit);
        add(filters, BorderLayout.NORTH);

        feed.setCellRenderer(new TimelineRenderer());
        CompanionStyle.hover(
                feed,
                row -> row.entry != null && !row.entry.get("type").getAsString().equals("quest"));
        feed.setFixedCellWidth(PluginPanel.PANEL_WIDTH);
        feed.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        feed.setBackground(ColorScheme.DARK_GRAY_COLOR);
        feed.getAccessibleContext().setAccessibleName("Group activity timeline");
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroller.getVerticalScrollBar().setUnitIncrement(20);
        scroller.getVerticalScrollBar().addAdjustmentListener(event -> loadNearEnd());
        scroller.getViewport().addComponentListener(new ComponentAdapter() {
            private int viewportWidth;

            @Override
            public void componentResized(ComponentEvent event) {
                int width = scroller.getViewport().getWidth();
                if (width == viewportWidth) {
                    loadNearEnd();
                    return;
                }
                viewportWidth = width;
                signatures = new ArrayList<>();
                update();
            }
        });
        add(scroller, BorderLayout.CENTER);
        filter.addActionListener(event -> resetFilter());
        newOnly.addActionListener(event -> resetFilter());
        filterDrops.addActionListener(event -> resetFilter());
        feed.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                int index = feed.locationToIndex(event.getPoint());
                if (index >= 0 && feed.getCellBounds(index, index).contains(event.getPoint())) {
                    toggle(rows.get(index));
                }
            }
        });
    }

    public void setViewing(boolean viewing) {
        this.viewing = viewing;
        client.setActivityViewing(viewing);
    }

    private void resetFilter() {
        resetScroll = true;
        update();
    }

    public void update() {
        CompanionData next = client.getData();
        if (data != next) {
            expanded.clear();
            signatures = new ArrayList<>();
            resetScroll = true;
            data = next;
        }
        if (viewing && data.hasMetadata()) {
            client.activityViewed();
        }
        highlights = client.getActivityHighlights();
        sinceLastVisit.setText(highlights.size() + " new since last viewed");
        sinceLastVisit.setVisible(!highlights.isEmpty());
        String selectedType = TYPES.get(filter.getSelectedIndex());
        List<JsonObject> entries = data.hasMetadata()
                ? data.activities().stream()
                        .filter(entry -> !filterDrops.isSelected()
                                || !entry.get("type").getAsString().equals("collection"))
                        .filter(entry -> selectedType.isEmpty()
                                || entry.get("type").getAsString().equals(selectedType)
                                || selectedType.equals("loot")
                                        && entry.get("type").getAsString().equals("collection"))
                        .filter(entry -> !newOnly.isSelected()
                                || highlights.contains(entry.get("id").getAsLong()))
                        .collect(Collectors.toList())
                : new ArrayList<>();
        List<Row> nextRows = new ArrayList<>();
        String previousDay = null;
        for (JsonObject entry : entries) {
            String day = dayKey(entry);
            if (!day.equals(previousDay)) {
                nextRows.add(new Row("date:" + day, dateLabel(entry), null));
                previousDay = day;
            }
            nextRows.add(new Row("entry:" + entry.get("id").getAsLong(), null, entry));
        }
        if (client.isLoadingOlder()) {
            nextRows.add(new Row("loading", "Loading older activity...", null));
        } else if (entries.isEmpty()) {
            nextRows.add(new Row(
                    "empty",
                    newOnly.isSelected() ? "No new activity since your last visit." : "No matching activity.",
                    null));
        }
        List<String> nextSignatures = nextRows.stream()
                .map(row -> row.entry == null
                        ? row.key + row.label
                        : row.key + ":" + row.entry.get("revision").getAsInt() + ":"
                                + expanded.contains(row.entry.get("id").getAsLong()) + ":"
                                + highlights.contains(row.entry.get("id").getAsLong()))
                .collect(Collectors.toList());
        if (signatures.equals(nextSignatures) && !resetScroll) {
            feed.repaint();
            SwingUtilities.invokeLater(this::loadNearEnd);
            return;
        }
        int first = feed.getFirstVisibleIndex();
        String anchor = first >= 0 && first < rows.size() ? rows.get(first).key : null;
        Rectangle bounds = first >= 0 ? feed.getCellBounds(first, first) : null;
        int scrollTop = scroller.getViewport().getViewPosition().y;
        int offset = bounds == null ? 0 : scrollTop - bounds.y;
        boolean atTop = resetScroll || scrollTop <= 24;
        resetScroll = false;
        restoringScroll = true;
        String selected = feed.getSelectedValue() == null ? null : feed.getSelectedValue().key;
        rows = nextRows;
        signatures = nextSignatures;
        feed.setListData(rows.toArray(new Row[0]));
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).key.equals(selected)) {
                feed.setSelectedIndex(index);
                break;
            }
        }
        long version = ++renderVersion;
        SwingUtilities.invokeLater(() -> {
            if (version != renderVersion) {
                return;
            }
            int target = atTop ? 0 : scrollTop;
            if (!atTop && anchor != null) {
                for (int index = 0; index < rows.size(); index++) {
                    if (rows.get(index).key.equals(anchor)) {
                        target = feed.getCellBounds(index, index).y + offset;
                        break;
                    }
                }
            }
            scroller.getViewport().setViewPosition(new Point(0, Math.max(0, target)));
            restoringScroll = false;
            loadNearEnd();
        });
    }

    private void loadNearEnd() {
        if (!viewing
                || restoringScroll
                || newOnly.isSelected()
                || !client.getStatus().isEmpty()
                || !client.canLoadOlder()
                || scroller.getViewport().getExtentSize().height <= 0) {
            return;
        }
        int remaining = feed.getHeight()
                - scroller.getViewport().getViewPosition().y
                - scroller.getViewport().getExtentSize().height;
        if (remaining <= 200) {
            client.loadOlder();
        }
    }

    private void toggle(Row row) {
        if (row.entry == null || row.entry.get("type").getAsString().equals("quest")) {
            return;
        }
        long identifier = row.entry.get("id").getAsLong();
        if (!expanded.remove(identifier)) {
            expanded.add(identifier);
        }
        update();
    }

    private static String dayKey(JsonObject entry) {
        return (weekly(entry) ? "week:" : "day:")
                + started(entry)
                        .atZone(weekly(entry) ? ZoneOffset.UTC : ZoneId.systemDefault())
                        .toLocalDate();
    }

    private static String dateLabel(JsonObject entry) {
        LocalDate date = started(entry)
                .atZone(weekly(entry) ? ZoneOffset.UTC : ZoneId.systemDefault())
                .toLocalDate();
        if (weekly(entry)) {
            return "Week of " + date.format(DATE);
        }
        LocalDate today = LocalDate.now();
        return date.equals(today) ? "Today" : date.equals(today.minusDays(1)) ? "Yesterday" : date.format(DATE);
    }

    private static Instant started(JsonObject entry) {
        return Instant.ofEpochMilli(entry.get("started_at").getAsLong());
    }

    private static boolean weekly(JsonObject entry) {
        return entry.get("is_weekly").getAsBoolean();
    }

    private static boolean live(JsonObject entry) {
        String type = entry.get("type").getAsString();
        return !weekly(entry)
                && Arrays.asList("xp", "boss", "diary", "collection").contains(type)
                && entry.get("occurred_at").getAsLong()
                        > System.currentTimeMillis() - (type.equals("boss") ? 30 : 15) * 60_000;
    }

    private static boolean milestone(JsonObject entry) {
        JsonObject details = entry.getAsJsonObject("data");
        switch (entry.get("type").getAsString()) {
            case "quest":
                return true;
            case "level":
                return details.get("after").getAsInt() == 99;
            case "diary":
                return details.get("complete").getAsBoolean();
            case "loot":
            case "collection":
                return details.get("new_collection_item").getAsBoolean();
            default:
                return false;
        }
    }

    private Icon eventIcon(JsonObject entry) {
        JsonObject details = entry.getAsJsonObject("data");
        switch (entry.get("type").getAsString()) {
            case "loot":
            case "collection":
                return itemIcons.apply(
                        details.get("item_id").getAsInt(),
                        details.get("quantity").getAsLong());
            case "level":
                return new ImageIcon(skillIcons.getSkillImage(
                        Skill.valueOf(details.get("skill").getAsString().toUpperCase(java.util.Locale.ROOT)), true));
            case "boss":
                return new ImageIcon(skillIcons.getSkillImage(Skill.SLAYER, true));
            case "quest":
                return new ImageIcon(MemberPanel.artwork("quests.png"));
            case "diary":
                return new ImageIcon(MemberPanel.artwork("diaries.png"));
            default:
                return new ImageIcon(MemberPanel.artwork("stats.png"));
        }
    }

    private String details(JsonObject entry) {
        String range;
        if (weekly(entry)) {
            LocalDate start = started(entry).atZone(ZoneOffset.UTC).toLocalDate();
            range = start.format(DATE) + " to " + start.plusDays(6).format(DATE) + " (UTC)";
        } else {
            range = CLOCK.format(started(entry));
            long occurred = entry.get("occurred_at").getAsLong();
            if (occurred != entry.get("started_at").getAsLong() || live(entry)) {
                range += " to " + (live(entry) ? "now" : CLOCK.format(Instant.ofEpochMilli(occurred)));
            }
        }
        return range + "\n" + data.activityDetails(entry);
    }

    private class TimelineRenderer extends JPanel implements ListCellRenderer<Row> {
        private final JLabel heading = label("");
        private final JLabel avatar = label("");
        private final JLabel member = label("");
        private final JLabel badge = label("");
        private final JLabel clock = label("");
        private final JPanel header = new JPanel(new BorderLayout(4, 0));
        private final JPanel identity = new JPanel();
        private final JPanel center = new JPanel(new BorderLayout(0, CompanionStyle.GAP));
        private final JPanel description = new JPanel(new BorderLayout(CompanionStyle.GAP, 0));
        private final LazyIcon icon = new LazyIcon();
        private final JTextArea summary = text();
        private final JLabel chevron = label("");
        private final JTextArea detail = text();

        private TimelineRenderer() {
            setLayout(new BorderLayout(0, CompanionStyle.GAP));
            identity.setLayout(new BorderLayout(4, 0));
            identity.setOpaque(false);
            identity.add(avatar, BorderLayout.WEST);
            identity.add(member, BorderLayout.CENTER);
            identity.add(badge, BorderLayout.EAST);
            header.setOpaque(false);
            header.add(identity, BorderLayout.CENTER);
            header.add(clock, BorderLayout.EAST);
            clock.setForeground(MUTED);
            badge.setForeground(MUTED);
            description.setOpaque(false);
            description.add(icon, BorderLayout.WEST);
            description.add(summary, BorderLayout.CENTER);
            description.add(chevron, BorderLayout.EAST);
            chevron.setForeground(MUTED);
            center.setOpaque(false);
            center.add(description, BorderLayout.NORTH);
            center.add(detail, BorderLayout.CENTER);
            detail.setForeground(MUTED);
            detail.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 1, 0, 0, CompanionStyle.BORDER),
                    BorderFactory.createEmptyBorder(4, 8, 4, 0)));
            add(header, BorderLayout.NORTH);
            add(center, BorderLayout.CENTER);
            heading.setOpaque(true);
            heading.setForeground(MUTED);
            heading.setBackground(ColorScheme.DARK_GRAY_COLOR);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends Row> list, Row row, int index, boolean selected, boolean focused) {
            int width = list.getWidth() > 0 ? list.getWidth() : PluginPanel.PANEL_WIDTH - 12;
            if (row.entry == null) {
                heading.setText(row.label);
                heading.setBorder(
                        row.key.startsWith("date:")
                                ? BorderFactory.createCompoundBorder(
                                        BorderFactory.createMatteBorder(0, 0, 1, 0, CompanionStyle.BORDER),
                                        BorderFactory.createEmptyBorder(8, 8, 4, 8))
                                : BorderFactory.createEmptyBorder(8, 8, 8, 8));
                heading.setPreferredSize(new Dimension(width, 34));
                return heading;
            }
            JsonObject entry = row.entry;
            long identifier = entry.get("id").getAsLong();
            boolean changed = highlights.contains(identifier);
            boolean open = expanded.contains(identifier);
            boolean hasDetails = !entry.get("type").getAsString().equals("quest");
            Color background = CompanionStyle.rowBackground(open, CompanionStyle.isHovered(list, index));
            setBackground(background);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 4, 0, ColorScheme.DARK_GRAY_COLOR),
                    CompanionStyle.rowBorder(changed || open)));
            int memberIdentifier = entry.get("member_id").getAsInt();
            int hue = data.players().stream()
                    .filter(player ->
                            player.get("id").getAsInt() == memberIdentifier && player.has("color_hue_degrees"))
                    .map(player -> player.get("color_hue_degrees").getAsInt())
                    .findFirst()
                    .orElse(0);
            avatar.setIcon(avatars.computeIfAbsent(hue, color -> new ImageIcon(MemberPanel.playerIcon(color))));
            member.setText(data.memberName(memberIdentifier));
            clock.setText(weekly(entry) ? "Week" : CLOCK.format(started(entry)));
            badge.setText(changed ? "New" : live(entry) ? "Live" : "");
            badge.setForeground(changed ? ACCENT : MUTED);
            summary.setText(data.activityText(entry));
            summary.setForeground(milestone(entry) ? ACCENT : CompanionStyle.TEXT);
            summary.setBackground(background);
            icon.supplier = () -> eventIcon(entry);
            chevron.setText(hasDetails ? open ? "v" : ">" : "");
            detail.setVisible(open);
            detail.setBackground(background);
            detail.setText(open ? details(entry) : "");
            int innerWidth = width - getInsets().left - getInsets().right;
            int summaryWidth = innerWidth
                    - icon.getPreferredSize().width
                    - chevron.getPreferredSize().width
                    - 2 * CompanionStyle.GAP;
            summary.setSize(Math.max(1, summaryWidth), Short.MAX_VALUE);
            int descriptionHeight = Math.max(24, summary.getPreferredSize().height);
            description.setPreferredSize(new Dimension(innerWidth, descriptionHeight));
            detail.setSize(Math.max(1, innerWidth), Short.MAX_VALUE);
            int height = getInsets().top
                    + getInsets().bottom
                    + header.getPreferredSize().height
                    + CompanionStyle.GAP
                    + descriptionHeight
                    + (open ? CompanionStyle.GAP + detail.getPreferredSize().height : 0);
            setPreferredSize(new Dimension(width, height));
            setSize(width, height);
            doLayout();
            header.doLayout();
            identity.doLayout();
            center.doLayout();
            description.doLayout();
            return this;
        }
    }

    private static class LazyIcon extends JComponent {
        private Supplier<Icon> supplier;

        private LazyIcon() {
            setPreferredSize(new Dimension(22, 24));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Icon icon = supplier.get();
            if (icon == null) {
                return;
            }
            Graphics2D scaled = (Graphics2D) graphics.create();
            double scale = Math.min(20.0 / icon.getIconWidth(), 20.0 / icon.getIconHeight());
            scaled.translate(
                    (getWidth() - icon.getIconWidth() * scale) / 2, (getHeight() - icon.getIconHeight() * scale) / 2);
            scaled.scale(scale, scale);
            icon.paintIcon(this, scaled, 0, 0);
            scaled.dispose();
        }
    }

    private static class Row {
        private final String key;
        private final String label;
        private final JsonObject entry;

        private Row(String key, String label, JsonObject entry) {
            this.key = key;
            this.label = label;
            this.entry = entry;
        }
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        label.setForeground(CompanionStyle.TEXT);
        return label;
    }

    private static JTextArea text() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setForeground(CompanionStyle.TEXT);
        return area;
    }
}
