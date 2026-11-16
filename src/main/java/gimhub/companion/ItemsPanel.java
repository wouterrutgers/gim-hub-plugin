package gimhub.companion;

import com.google.gson.JsonObject;
import gimhub.companion.CompanionData.Holding;
import gimhub.companion.CompanionData.StockItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

public class ItemsPanel extends JPanel {
    private static final Color ACCENT = CompanionStyle.ACCENT;
    private static final Color MUTED = CompanionStyle.MUTED;
    private static final int TILE_HEIGHT = 82;
    private final CompanionClient client;
    private final BiFunction<Integer, Long, ImageIcon> itemIcons;
    private final JTextField search = new JTextField();
    private final JComboBox<String> player = new JComboBox<>();
    private final JComboBox<String> storage = new JComboBox<>();
    private final JLabel count = label("");
    private final Map<String, ImageIcon> ownerIcons = new HashMap<>();
    private final JList<StockItem> grid = new JList<StockItem>() {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = locationToIndex(event.getPoint());
            Rectangle bounds = index >= 0 ? getCellBounds(index, index) : null;
            if (bounds == null || !bounds.contains(event.getPoint())) {
                return null;
            }
            StockItem item = getModel().getElementAt(index);
            return starBounds(bounds).contains(event.getPoint())
                    ? pinTooltip(item)
                    : item.getName() + " (" + number(item.total()) + " total)";
        }
    };
    private final JScrollPane scroller = new JScrollPane(grid);
    private final JPanel details = new JPanel(new BorderLayout(0, 6));
    private final JTextArea itemName = text("");
    private final JLabel itemTotal = label("");
    private final JPanel owners = stack();
    private final JLabel empty = label("No matching items");
    private CompanionData data;
    private List<StockItem> displayed = new ArrayList<>();
    private Integer detailsIdentifier;
    private Integer hoveredStar;
    private final Set<Integer> pinningIdentifiers = new HashSet<>();
    private boolean choosing;
    private boolean resetScroll;
    private long renderVersion;

    public ItemsPanel(CompanionClient client, BiFunction<Integer, Long, ImageIcon> itemIcons) {
        this.client = client;
        this.itemIcons = itemIcons;
        setLayout(new BorderLayout(0, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel filters = stack();
        CompanionStyle.input(search);
        CompanionStyle.choice(player);
        CompanionStyle.choice(storage);
        search.putClientProperty("JTextField.placeholderText", "Search group items...");
        search.getAccessibleContext().setAccessibleName("Search group items");
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, search.getPreferredSize().height));
        filters.add(search);
        filters.add(Box.createVerticalStrut(CompanionStyle.SMALL_GAP));
        JPanel choices = new JPanel(new GridLayout(1, 2, 4, 0));
        choices.setOpaque(false);
        player.setMinimumSize(new Dimension(0, player.getPreferredSize().height));
        storage.setMinimumSize(new Dimension(0, storage.getPreferredSize().height));
        choices.add(player);
        choices.add(storage);
        storage.addItem("All storage");
        CompanionData.containers().forEach(storage::addItem);
        storage.setMaximumRowCount(12);
        filters.add(choices);
        JPanel options = new JPanel(new BorderLayout());
        options.setOpaque(false);
        options.add(count, BorderLayout.EAST);
        count.setForeground(MUTED);
        filters.add(Box.createVerticalStrut(CompanionStyle.SMALL_GAP));
        filters.add(options);
        add(filters, BorderLayout.NORTH);

        grid.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        grid.setVisibleRowCount(-1);
        grid.setFixedCellWidth(68);
        grid.setFixedCellHeight(TILE_HEIGHT);
        grid.setCellRenderer(new TileRenderer());
        CompanionStyle.hover(grid, item -> true);
        grid.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
        grid.setToolTipText("");
        grid.getAccessibleContext().setAccessibleName("Group item bank");
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroller.getVerticalScrollBar().setUnitIncrement(TILE_HEIGHT);
        scroller.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                if (scroller.getViewport().getWidth() <= 0) {
                    return;
                }
                int width = Math.max(1, scroller.getViewport().getWidth() / 3);
                if (grid.getFixedCellWidth() != width) {
                    grid.setFixedCellWidth(width);
                }
            }
        });
        JPanel bank = new JPanel(new BorderLayout());
        bank.setOpaque(false);
        empty.setForeground(MUTED);
        bank.add(empty, BorderLayout.NORTH);
        bank.add(scroller, BorderLayout.CENTER);
        add(bank, BorderLayout.CENTER);

        details.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        details.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CompanionStyle.BORDER), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        JPanel heading = new JPanel(new BorderLayout(4, 0));
        heading.setOpaque(false);
        itemName.setForeground(CompanionStyle.TEXT);
        itemName.setOpaque(false);
        itemTotal.setForeground(MUTED);
        JPanel title = stack();
        title.add(itemName);
        title.add(Box.createVerticalStrut(CompanionStyle.SMALL_GAP));
        title.add(itemTotal);
        heading.add(title, BorderLayout.CENTER);
        JButton close = new JButton("×");
        CompanionStyle.button(close);
        close.setMargin(new java.awt.Insets(0, 0, 0, 0));
        close.setForeground(MUTED);
        close.setToolTipText("Close item details");
        close.getAccessibleContext().setAccessibleName("Close item details");
        close.setPreferredSize(new Dimension(24, 24));
        close.addActionListener(event -> {
            detailsIdentifier = null;
            showDetails();
            grid.repaint();
        });
        heading.add(close, BorderLayout.EAST);
        details.add(heading, BorderLayout.NORTH);
        JScrollPane ownerScroll = new JScrollPane(owners);
        ownerScroll.setBorder(BorderFactory.createEmptyBorder());
        ownerScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ownerScroll.getVerticalScrollBar().setUnitIncrement(16);
        details.add(ownerScroll, BorderLayout.CENTER);
        details.setVisible(false);
        add(details, BorderLayout.SOUTH);

        player.addActionListener(event -> filtersChanged());
        storage.addActionListener(event -> filtersChanged());
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                filtersChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                filtersChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                filtersChanged();
            }
        });
        grid.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                int index = grid.locationToIndex(event.getPoint());
                Rectangle bounds = index >= 0 ? grid.getCellBounds(index, index) : null;
                if (bounds == null || !bounds.contains(event.getPoint())) {
                    return;
                }
                StockItem item = grid.getModel().getElementAt(index);
                if (starBounds(bounds).contains(event.getPoint())) {
                    togglePin(item);
                } else {
                    select(item);
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoveredStar = null;
                grid.repaint();
            }
        });
        grid.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int index = grid.locationToIndex(event.getPoint());
                Rectangle bounds = index >= 0 ? grid.getCellBounds(index, index) : null;
                Integer next = bounds != null && starBounds(bounds).contains(event.getPoint())
                        ? grid.getModel().getElementAt(index).getIdentifier()
                        : null;
                if (!Objects.equals(hoveredStar, next)) {
                    hoveredStar = next;
                    grid.repaint();
                }
            }
        });
    }

    public void update() {
        CompanionData next = client.getData();
        if (data != next) {
            detailsIdentifier = null;
            pinningIdentifiers.clear();
            displayed = new ArrayList<>();
            resetScroll = true;
            data = next;
        }
        ownerIcons.clear();
        for (JsonObject member : data.players()) {
            int hue = member.has("color_hue_degrees")
                    ? member.get("color_hue_degrees").getAsInt()
                    : 0;
            ownerIcons.put(CompanionData.displayName(member), new ImageIcon(MemberPanel.playerIcon(hue)));
        }
        ownerIcons.put("Group storage", new ImageIcon(MemberPanel.artwork("group-storage.png")));
        List<String> names = new ArrayList<>();
        names.add("All players");
        names.add("Group storage");
        data.players().forEach(member -> names.add(CompanionData.displayName(member)));
        choosing = true;
        choices(player, names);
        choosing = false;
        refreshItems();
    }

    private void filtersChanged() {
        if (choosing) {
            return;
        }
        resetScroll = true;
        refreshItems();
    }

    private void refreshItems() {
        if (data == null || player.getSelectedItem() == null) {
            return;
        }
        player.setToolTipText(player.getSelectedItem().toString());
        storage.setToolTipText(storage.getSelectedItem().toString());
        List<StockItem> results = data.hasMetadata()
                ? data.items((String) player.getSelectedItem(), (String) storage.getSelectedItem(), search.getText())
                : new ArrayList<>();
        count.setText(number(results.size()) + (results.size() == 1 ? " item" : " items"));
        empty.setText("No matching items.");
        empty.setVisible(data.hasMetadata() && results.isEmpty());
        if (!displayed.equals(results) || resetScroll) {
            Point position =
                    resetScroll ? new Point(0, 0) : scroller.getViewport().getViewPosition();
            int firstVisible = resetScroll ? -1 : grid.getFirstVisibleIndex();
            int lastVisible = grid.getLastVisibleIndex();
            while (firstVisible >= 0
                    && firstVisible <= lastVisible
                    && pinningIdentifiers.contains(displayed.get(firstVisible).getIdentifier())) {
                firstVisible++;
            }
            if (firstVisible > lastVisible) {
                firstVisible = -1;
            }
            Integer anchor = firstVisible >= 0 ? displayed.get(firstVisible).getIdentifier() : null;
            int anchorOffset = firstVisible >= 0 ? grid.getCellBounds(firstVisible, firstVisible).y - position.y : 0;
            resetScroll = false;
            Integer selected = grid.getSelectedValue() == null
                    ? null
                    : grid.getSelectedValue().getIdentifier();
            displayed = results;
            grid.setListData(results.toArray(new StockItem[0]));
            for (int index = 0; index < displayed.size(); index++) {
                if (Objects.equals(displayed.get(index).getIdentifier(), selected)) {
                    grid.setSelectedIndex(index);
                    break;
                }
            }
            long version = ++renderVersion;
            SwingUtilities.invokeLater(() -> {
                if (version == renderVersion) {
                    for (int index = 0; index < displayed.size(); index++) {
                        if (Objects.equals(displayed.get(index).getIdentifier(), anchor)) {
                            position.y = grid.getCellBounds(index, index).y - anchorOffset;
                            break;
                        }
                    }
                    position.y = Math.max(
                            0,
                            Math.min(
                                    position.y,
                                    grid.getPreferredSize().height
                                            - scroller.getViewport().getExtentSize().height));
                    scroller.getViewport().setViewPosition(position);
                }
            });
        }
        pinningIdentifiers.removeIf(identifier -> !client.isPinPending(identifier));
        showDetails();
        grid.repaint();
    }

    private void togglePin(StockItem item) {
        if (client.canPin()) {
            pinningIdentifiers.add(item.getIdentifier());
            client.togglePin(item.getIdentifier());
        }
    }

    private void select(StockItem item) {
        detailsIdentifier = Objects.equals(detailsIdentifier, item.getIdentifier()) ? null : item.getIdentifier();
        showDetails();
        grid.repaint();
    }

    private void showDetails() {
        StockItem selected = displayed.stream()
                .filter(item -> Objects.equals(item.getIdentifier(), detailsIdentifier))
                .findFirst()
                .orElse(null);
        details.setVisible(selected != null);
        if (selected == null) {
            detailsIdentifier = null;
            revalidate();
            return;
        }
        itemName.setText(selected.getName());
        itemTotal.setText(number(selected.total()) + " total");
        Map<String, Map<String, Long>> breakdown = new LinkedHashMap<>();
        for (Holding holding : selected.getHoldings()) {
            breakdown
                    .computeIfAbsent(holding.getOwner(), ignored -> new LinkedHashMap<>())
                    .merge(holding.getContainer(), holding.getQuantity(), Long::sum);
        }
        owners.removeAll();
        breakdown.forEach((owner, containers) -> {
            JPanel row = new JPanel(new BorderLayout(4, 2));
            row.setOpaque(false);
            JLabel name = label(owner);
            ImageIcon image = ownerIcons.get(owner);
            if (image != null) {
                name.setIcon(new ImageIcon(ImageUtil.resizeImage(
                        (java.awt.image.BufferedImage) image.getImage(),
                        Math.round(15f * image.getIconWidth() / image.getIconHeight()),
                        15)));
            }
            row.add(name, BorderLayout.CENTER);
            JLabel quantity = label(number(
                    containers.values().stream().mapToLong(Long::longValue).sum()));
            quantity.setForeground(MUTED);
            row.add(quantity, BorderLayout.EAST);
            JTextArea locations = text(containers.entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + number(entry.getValue()))
                    .collect(Collectors.joining(", ")));
            locations.setForeground(MUTED);
            row.add(locations, BorderLayout.SOUTH);
            row.setBorder(BorderFactory.createEmptyBorder(4, 0, 5, 0));
            owners.add(row);
        });
        details.setPreferredSize(new Dimension(0, Math.min(190, 64 + breakdown.size() * 48)));
        owners.revalidate();
        revalidate();
    }

    private String pinTooltip(StockItem item) {
        String name = client.getPinPlayerName();
        if (name == null) {
            return "Log in as a group member to sync pinned items";
        }
        if (!client.canPin() || client.isPinPending(item.getIdentifier())) {
            return "Syncing pinned items for " + name;
        }
        return (client.isPinned(item.getIdentifier()) ? "Unpin for " : "Pin for ") + name;
    }

    private static Rectangle starBounds(Rectangle cell) {
        return new Rectangle(cell.x + cell.width - 23, cell.y + 2, 21, 20);
    }

    private class TileRenderer extends JPanel implements ListCellRenderer<StockItem> {
        private StockItem item;
        private List<String> itemOwners;
        private boolean selected;
        private boolean hovered;
        private boolean pinned;
        private boolean pending;

        @Override
        public Component getListCellRendererComponent(
                JList<? extends StockItem> list, StockItem item, int index, boolean selected, boolean focused) {
            this.item = item;
            this.selected = Objects.equals(detailsIdentifier, item.getIdentifier());
            this.hovered = CompanionStyle.isHovered(list, index);
            pinned = client.isPinned(item.getIdentifier());
            pending = client.isPinPending(item.getIdentifier());
            itemOwners = item.getHoldings().stream()
                    .map(Holding::getOwner)
                    .distinct()
                    .collect(Collectors.toList());
            getAccessibleContext().setAccessibleName(item.getName());
            getAccessibleContext()
                    .setAccessibleDescription(number(item.total()) + " total. "
                            + (pinned ? "Pinned. " : "Unpinned. ")
                            + (pending ? "Syncing. " : "")
                            + "Owned by " + String.join(", ", itemOwners));
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            graphics.setColor(ColorScheme.DARK_GRAY_COLOR);
            graphics.fillRect(0, 0, getWidth(), getHeight());
            graphics.setColor(CompanionStyle.rowBackground(selected, hovered));
            graphics.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 5, 5);
            if (selected) {
                graphics.setColor(ACCENT);
                graphics.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 5, 5);
            }
            ImageIcon image = itemIcons.apply(item.getIdentifier(), item.total());
            if (image != null) {
                image.paintIcon(this, graphics, (getWidth() - image.getIconWidth()) / 2, 25);
            }
            graphics.setColor(
                    item.total() >= 10_000_000
                            ? new Color(80, 198, 109)
                            : item.total() >= 100_000 ? Color.WHITE : Color.YELLOW);
            graphics.drawString(shortQuantity(item.total()), 6, 16);
            graphics.setColor(
                    pending
                            ? MUTED
                            : pinned
                                    ? ACCENT
                                    : Objects.equals(hoveredStar, item.getIdentifier()) && client.canPin()
                                            ? Color.WHITE
                                            : MUTED);
            Polygon star = star(getWidth() - 13, 12);
            if (pinned) {
                graphics.fillPolygon(star);
            } else {
                graphics.drawPolygon(star);
            }
            int width = itemOwners.size() * 10;
            for (int index = 0; index < itemOwners.size(); index++) {
                ImageIcon owner = ownerIcons.get(itemOwners.get(index));
                if (owner != null) {
                    Graphics2D scaled = (Graphics2D) graphics.create();
                    scaled.translate((getWidth() - width) / 2 + index * 10, getHeight() - 17);
                    scaled.scale(9.0 / owner.getIconWidth(), 12.0 / owner.getIconHeight());
                    owner.paintIcon(this, scaled, 0, 0);
                    scaled.dispose();
                }
            }
        }
    }

    private static Polygon star(int x, int y) {
        Polygon star = new Polygon();
        for (int index = 0; index < 10; index++) {
            double angle = -Math.PI / 2 + index * Math.PI / 5;
            int radius = index % 2 == 0 ? 7 : 3;
            star.addPoint(
                    x + (int) Math.round(Math.cos(angle) * radius), y + (int) Math.round(Math.sin(angle) * radius));
        }
        return star;
    }

    private static void choices(JComboBox<String> combo, List<String> values) {
        List<String> current = new ArrayList<>();
        for (int index = 0; index < combo.getItemCount(); index++) {
            current.add(combo.getItemAt(index));
        }
        if (current.equals(values)) {
            return;
        }
        Object selected = combo.getSelectedItem();
        combo.removeAllItems();
        values.forEach(combo::addItem);
        if (values.contains(selected)) {
            combo.setSelectedItem(selected);
        }
    }

    private static JPanel stack() {
        return CompanionStyle.stack();
    }

    private static JLabel label(String value) {
        JLabel label = new JLabel(value);
        label.putClientProperty("html.disable", true);
        label.setForeground(CompanionStyle.TEXT);
        return label;
    }

    private static JTextArea text(String value) {
        JTextArea area = new JTextArea(value);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setForeground(CompanionStyle.TEXT);
        area.setOpaque(false);
        return area;
    }

    private static String shortQuantity(long quantity) {
        return quantity >= 1_000_000_000
                ? quantity / 1_000_000_000 + "B"
                : quantity >= 1_000_000
                        ? quantity / 1_000_000 + "M"
                        : quantity >= 1_000 ? quantity / 1_000 + "K" : Long.toString(quantity);
    }

    private static String number(long value) {
        return String.format("%,d", value);
    }
}
