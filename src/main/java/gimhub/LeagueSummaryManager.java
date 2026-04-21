package gimhub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

public class LeagueSummaryManager {
    private LeagueSummary summary = null;

    public void update(Client client) {
        LeagueSummary captured = LeagueSummary.capture(client);

        if (captured != null) {
            summary = captured;
        }
    }

    public void flatten(Map<String, APISerializable> flat) {
        flat.put("league_summary", summary);
    }

    private static final class LeagueSummary implements APISerializable {
        private final LeagueSummarySection stats;
        private final LeagueSummarySection mainStats;
        private final LeagueSummarySection areas;
        private final LeagueSummarySection relics;
        private final LeagueSummarySection masteries;

        private LeagueSummary(
                LeagueSummarySection stats,
                LeagueSummarySection mainStats,
                LeagueSummarySection areas,
                LeagueSummarySection relics,
                LeagueSummarySection masteries) {
            this.stats = stats;
            this.mainStats = mainStats;
            this.areas = areas;
            this.relics = relics;
            this.masteries = masteries;
        }

        private static LeagueSummary capture(Client client) {
            Widget root = client.getWidget(InterfaceID.LeagueSummary.UNIVERSE);

            if (root == null) {
                return null;
            }

            LeagueSummarySection stats = LeagueSummarySection.capture(
                    true,
                    client.getWidget(InterfaceID.LeagueSummary.STATS),
                    client.getWidget(InterfaceID.LeagueSummary.STATS_CONTENT),
                    client.getWidget(InterfaceID.LeagueSummary.STATS_SCROLL));
            LeagueSummarySection mainStats = LeagueSummarySection.capture(
                    true,
                    client.getWidget(InterfaceID.LeagueSummary.MAIN_STATS),
                    client.getWidget(InterfaceID.LeagueSummary.MAIN_STATS_CONTENT));
            LeagueSummarySection areas = LeagueSummarySection.capture(
                    false,
                    client.getWidget(InterfaceID.LeagueSummary.AREAS),
                    client.getWidget(InterfaceID.LeagueSummary.AREAS_CONTENT));
            LeagueSummarySection relics = LeagueSummarySection.capture(
                    false,
                    client.getWidget(InterfaceID.LeagueSummary.RELICS),
                    client.getWidget(InterfaceID.LeagueSummary.RELICS_CONTENT));
            LeagueSummarySection masteries = LeagueSummarySection.capture(
                    true,
                    client.getWidget(InterfaceID.LeagueSummary.MASTERIES),
                    client.getWidget(InterfaceID.LeagueSummary.MASTERIES_CONTENT));

            return new LeagueSummary(stats, mainStats, areas, relics, masteries);
        }

        @Override
        public Object serialize() {
            return this;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof LeagueSummary)) {
                return false;
            }

            LeagueSummary other = (LeagueSummary) object;

            return Objects.equals(stats, other.stats)
                    && Objects.equals(mainStats, other.mainStats)
                    && Objects.equals(areas, other.areas)
                    && Objects.equals(relics, other.relics)
                    && Objects.equals(masteries, other.masteries);
        }
    }

    private static final class LeagueSummarySection {
        private final List<String> texts;
        private final List<LeagueSummaryRow> rows;
        private final List<LeagueSummaryIcon> icons;

        private LeagueSummarySection(List<String> texts, List<LeagueSummaryRow> rows, List<LeagueSummaryIcon> icons) {
            this.texts = texts;
            this.rows = rows;
            this.icons = icons;
        }

        private static LeagueSummarySection capture(boolean useLayoutRows, Widget... widgets) {
            Set<String> texts = new LinkedHashSet<>();
            List<LeagueSummaryIcon> icons = new ArrayList<>();
            Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());

            collect(widgets, texts, icons, visited);

            List<LeagueSummaryRow> rows =
                    useLayoutRows && widgets.length > 1 ? captureRows(widgets[1]) : Collections.emptyList();

            return new LeagueSummarySection(new ArrayList<>(texts), rows, icons);
        }

        private static void collect(
                Widget widget, Set<String> texts, List<LeagueSummaryIcon> icons, Set<Widget> visited) {
            if (widget == null || !visited.add(widget)) {
                return;
            }

            String text = sanitizeText(widget.getText());
            if (text != null) {
                texts.add(text);
            }

            String name = sanitizeText(widget.getName());
            if (name != null) {
                texts.add(name);
            }

            int spriteId = widget.getSpriteId();
            if (spriteId > 0) {
                icons.add(new LeagueSummaryIcon(spriteId));
            }

            collect(widget.getDynamicChildren(), texts, icons, visited);
            collect(widget.getStaticChildren(), texts, icons, visited);
            collect(widget.getNestedChildren(), texts, icons, visited);
        }

        private static void collect(
                Widget[] widgets, Set<String> texts, List<LeagueSummaryIcon> icons, Set<Widget> visited) {
            if (widgets == null) {
                return;
            }

            for (Widget child : widgets) {
                collect(child, texts, icons, visited);
            }
        }

        private static List<LeagueSummaryRow> captureRows(Widget contentWidget) {
            if (contentWidget == null || contentWidget.isHidden()) {
                return Collections.emptyList();
            }

            Widget[] children = contentWidget.getDynamicChildren();
            if (children == null) {
                return Collections.emptyList();
            }

            List<String> allTexts = new ArrayList<>();
            for (Widget child : children) {
                if (child != null && !child.isHidden()) {
                    collectTexts(child, allTexts);
                }
            }

            Set<String> seen = new HashSet<>();
            List<LeagueSummaryRow> rows = new ArrayList<>();
            for (int i = 0; i + 1 < allTexts.size(); i += 2) {
                String label = allTexts.get(i);
                String value = allTexts.get(i + 1);
                if (seen.add(label + ":" + value)) {
                    rows.add(new LeagueSummaryRow(label, value));
                }
            }

            return rows;
        }

        private static void collectTexts(Widget widget, List<String> texts) {
            if (widget == null || widget.isHidden()) {
                return;
            }

            String text = sanitizeText(widget.getText());
            if (text != null) {
                texts.add(text);
            }

            collectTexts(widget.getDynamicChildren(), texts);
            collectTexts(widget.getStaticChildren(), texts);
            collectTexts(widget.getNestedChildren(), texts);
        }

        private static void collectTexts(Widget[] widgets, List<String> texts) {
            if (widgets != null) {
                for (Widget child : widgets) {
                    collectTexts(child, texts);
                }
            }
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof LeagueSummarySection)) {
                return false;
            }

            LeagueSummarySection other = (LeagueSummarySection) object;

            return Objects.equals(texts, other.texts)
                    && Objects.equals(rows, other.rows)
                    && Objects.equals(icons, other.icons);
        }
    }

    private static final class LeagueSummaryIcon {
        private final int spriteId;

        private LeagueSummaryIcon(int spriteId) {
            this.spriteId = spriteId;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof LeagueSummaryIcon)) {
                return false;
            }

            LeagueSummaryIcon other = (LeagueSummaryIcon) object;

            return spriteId == other.spriteId;
        }
    }

    private static final class LeagueSummaryRow {
        private final String label;
        private final String value;

        private LeagueSummaryRow(String label, String value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof LeagueSummaryRow)) {
                return false;
            }

            LeagueSummaryRow other = (LeagueSummaryRow) object;

            return Objects.equals(label, other.label) && Objects.equals(value, other.value);
        }
    }

    private static String sanitizeText(String text) {
        if (text == null) {
            return null;
        }

        String sanitized = text.replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();

        return sanitized.isEmpty() ? null : sanitized;
    }
}
