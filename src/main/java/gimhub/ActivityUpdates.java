package gimhub;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ActivityUpdates implements APISerializable {
    final List<Map<String, Object>> updates;

    public ActivityUpdates(List<Map<String, Object>> updates) {
        this.updates = List.copyOf(updates);
    }

    public static APISerializable combine(APISerializable older, APISerializable newer) {
        Map<String, Map<String, Object>> combined = new LinkedHashMap<>();
        for (ActivityUpdates batch : List.of((ActivityUpdates) older, (ActivityUpdates) newer)) {
            for (Map<String, Object> update : batch.updates) {
                String identifier = (String) update.get("id");
                Map<String, Object> existing = combined.get(identifier);
                if (existing == null || (int) existing.get("revision") < (int) update.get("revision")) {
                    combined.put(identifier, update);
                }
            }
        }
        return new ActivityUpdates(new ArrayList<>(combined.values()).subList(0, Math.min(100, combined.size())));
    }

    @Override
    public Object serialize() {
        return updates;
    }
}
