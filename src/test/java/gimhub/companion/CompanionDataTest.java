package gimhub.companion;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.junit.Test;

public class CompanionDataTest {
    @Test
    public void weeklySummariesReplaceArchivedActivityEvenWhenAnOlderPageArrivesLater() {
        CompanionData data = new CompanionData();
        JsonArray daily = new JsonArray();
        daily.add(activity(1, 1, "2026-10-04T23:55:00Z", "2026-10-05T00:05:00Z", false));
        daily.add(activity(2, 1, "2026-10-11T23:00:00Z", "2026-10-11T23:30:00Z", false));
        daily.add(activity(3, 2, "2026-10-05T12:00:00Z", "2026-10-05T12:05:00Z", false));
        daily.add(activity(4, 1, "2026-10-04T12:00:00Z", "2026-10-04T12:05:00Z", false));
        data.mergeActivity(daily);

        JsonArray summaries = new JsonArray();
        summaries.add(activity(5, 1, "2026-10-05T00:00:00Z", "2026-10-11T23:30:00Z", true));
        data.mergeActivity(summaries);

        assertEquals(
                new HashSet<>(Arrays.asList(3L, 4L, 5L)),
                data.activities().stream()
                        .map(entry -> entry.get("id").getAsLong())
                        .collect(Collectors.toSet()));

        data.mergeActivity(daily);

        assertEquals(
                new HashSet<>(Arrays.asList(3L, 4L, 5L)),
                data.activities().stream()
                        .map(entry -> entry.get("id").getAsLong())
                        .collect(Collectors.toSet()));
    }

    private JsonObject activity(long identifier, int member, String startedAt, String occurredAt, boolean weekly) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", identifier);
        entry.addProperty("member_id", member);
        entry.addProperty("revision", 1);
        entry.addProperty("type", "xp");
        entry.addProperty("started_at", Instant.parse(startedAt).toEpochMilli());
        entry.addProperty("occurred_at", Instant.parse(occurredAt).toEpochMilli());
        entry.addProperty("is_weekly", weekly);
        return entry;
    }
}
