package gimhub;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CollectionLogUpdates implements APISerializable {
    private final List<Update> updates;

    public static class Update {
        private final String type;
        private final Map<Integer, Integer> items;

        public Update(String type, Map<Integer, Integer> items) {
            this.type = type;
            this.items = new LinkedHashMap<>(items);
        }

        private Map<String, Object> serialize() {
            return Map.of(
                    "type",
                    type,
                    "items",
                    items.entrySet().stream()
                            .map(item -> Map.of("item_id", item.getKey(), "quantity", item.getValue()))
                            .collect(Collectors.toList()));
        }
    }

    public CollectionLogUpdates(List<Update> updates) {
        this.updates = new ArrayList<>(updates);
    }

    public static APISerializable combine(APISerializable older, APISerializable newer) {
        List<Update> combined = new ArrayList<>(((CollectionLogUpdates) older).updates);
        combined.addAll(((CollectionLogUpdates) newer).updates);
        return new CollectionLogUpdates(combined);
    }

    @Override
    public Object serialize() {
        return updates.stream().map(Update::serialize).collect(Collectors.toList());
    }
}
