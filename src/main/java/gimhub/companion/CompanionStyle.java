package gimhub.companion;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

final class CompanionStyle {
    static final Color ACCENT = new Color(229, 171, 77);
    static final Color TEXT = new Color(226, 226, 226);
    static final Color MUTED = new Color(160, 160, 160);
    static final Color BORDER = new Color(55, 55, 55);
    static final Color HOVER = new Color(45, 45, 45);
    static final Color SELECTED = new Color(55, 49, 39);
    static final int GAP = 8;
    static final int SMALL_GAP = 4;
    private static final String HOVER_INDEX = "gimHub.hoveredIndex";
    private static final String CONTROL_STYLE = "arc: 4; borderColor: #373737";
    private static final Border[] ROW_BORDERS = {new RowBorder(false), new RowBorder(true)};

    private CompanionStyle() {}

    static void input(JTextField field) {
        field.putClientProperty("FlatLaf.style", CONTROL_STYLE + "; placeholderForeground: #a0a0a0; minimumWidth: 0");
        field.setMargin(new Insets(4, 8, 4, 8));
        control(field);
    }

    static void choice(JComboBox<?> choice) {
        choice.putClientProperty(
                "FlatLaf.style",
                CONTROL_STYLE
                        + "; padding: 4,8,4,8; minimumWidth: 0; buttonStyle: none; "
                        + "buttonArrowColor: #a0a0a0; buttonHoverArrowColor: #e5ab4d");
        control(choice);
    }

    static void button(AbstractButton button) {
        button.putClientProperty(
                "FlatLaf.style",
                CONTROL_STYLE
                        + "; minimumWidth: 0; hoverBorderColor: #6a6a6a; "
                        + "hoverBackground: #2d2d2d; pressedBackground: #373127; "
                        + "selectedBackground: #373127; selectedForeground: #e5ab4d");
        button.setMargin(new Insets(4, 8, 4, 8));
        button.setRolloverEnabled(true);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        control(button);
    }

    static void iconButton(AbstractButton button) {
        button.putClientProperty(
                "FlatLaf.style",
                "buttonType: tab; minimumWidth: 0; "
                        + "tab.underlineColor: #e5ab4d; tab.underlineHeight: 2; "
                        + "tab.selectedBackground: #373127; tab.hoverBackground: #2d2d2d");
        button.setMargin(new Insets(4, 4, 4, 4));
        button.setContentAreaFilled(true);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(32, 32));
        button.setMinimumSize(new Dimension(0, 32));
    }

    static void checkbox(JCheckBox checkbox) {
        checkbox.putClientProperty(
                "FlatLaf.style",
                "icon.arc: 4; icon.borderColor: #6a6a6a; "
                        + "icon.selectedBorderColor: #e5ab4d; icon.selectedBackground: #e5ab4d; "
                        + "icon.checkmarkColor: #1e1e1e; icon.hoverBorderColor: #e5ab4d");
        checkbox.setOpaque(false);
        checkbox.setFocusPainted(false);
        checkbox.setForeground(MUTED);
        checkbox.setIconTextGap(8);
        checkbox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void control(JComponent component) {
        int height = Math.max(28, component.getFontMetrics(component.getFont()).getHeight() + 12);
        component.setForeground(TEXT);
        component.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setPreferredSize(new Dimension(component.getPreferredSize().width, height));
        component.setMinimumSize(new Dimension(0, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    static JPanel stack() {
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
        panel.setOpaque(false);
        return panel;
    }

    static Color rowBackground(boolean selected, boolean hovered) {
        return selected ? SELECTED : hovered ? HOVER : ColorScheme.DARKER_GRAY_COLOR;
    }

    static Border rowBorder(boolean selected) {
        return ROW_BORDERS[selected ? 1 : 0];
    }

    static boolean isHovered(JList<?> list, int index) {
        return Objects.equals(list.getClientProperty(HOVER_INDEX), index);
    }

    static <T> void hover(JList<T> list, Predicate<T> interactive) {
        MouseAdapter hover = new MouseAdapter() {
            private void update(int index) {
                Object previous = list.getClientProperty(HOVER_INDEX);
                list.setCursor(Cursor.getPredefinedCursor(index >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                if (!Objects.equals(previous, index)) {
                    list.putClientProperty(HOVER_INDEX, index);
                    if (previous instanceof Integer) {
                        repaintRow(list, (Integer) previous);
                    }
                    repaintRow(list, index);
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                Rectangle bounds = index >= 0 ? list.getCellBounds(index, index) : null;
                update(
                        bounds != null
                                        && bounds.contains(event.getPoint())
                                        && interactive.test(list.getModel().getElementAt(index))
                                ? index
                                : -1);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                update(-1);
            }
        };
        list.addMouseListener(hover);
        list.addMouseMotionListener(hover);
        list.addPropertyChangeListener("model", event -> {
            list.putClientProperty(HOVER_INDEX, -1);
            list.setCursor(Cursor.getDefaultCursor());
        });
    }

    private static void repaintRow(JList<?> list, int index) {
        if (index >= 0 && index < list.getModel().getSize()) {
            Rectangle bounds = list.getCellBounds(index, index);
            if (bounds != null) {
                list.repaint(bounds);
            }
        }
    }

    private static final class RowBorder extends EmptyBorder {
        private final boolean selected;

        private RowBorder(boolean selected) {
            super(6, 8, 6, 8);
            this.selected = selected;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            if (selected) {
                graphics.setColor(ACCENT);
                graphics.fillRect(x + 1, y + 5, 2, height - 10);
            }
        }
    }
}
