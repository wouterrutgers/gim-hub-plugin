package gimhub.companion;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

public class ActivityReadStateTest {
    private final Gson gson = new Gson();
    private final Map<String, String> saved = new HashMap<>();
    private ConfigManager config;

    @Before
    public void setUp() {
        config = mock(ConfigManager.class);
        when(config.getConfiguration(anyString(), anyString())).thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> {
                    saved.put(call.getArgument(1), call.getArgument(2));
                    return null;
                })
                .when(config)
                .setConfiguration(anyString(), anyString(), anyString());
    }

    @Test
    public void remembersViewedRevisionsAcrossRestartsWithoutAcknowledgingBackgroundChanges() {
        ActivityReadState state = new ActivityReadState(config, gson, "group_alice");
        state.receive(entries("[{id:1,revision:1}]"), "2026-11-15T10:00:00Z");
        state.setViewing(true);
        state.setViewing(false);
        String previousVisit = saved.get("group_alice");

        state.receive(entries("[{id:1,revision:1}]"), "2026-11-15T10:00:05Z");
        assertEquals(0, state.getUnreadCount());
        state.receive(entries("[{id:1,revision:2},{id:2,revision:1}]"), "2026-11-15T10:00:10Z");
        assertEquals(2, state.getUnreadCount());
        assertEquals(previousVisit, saved.get("group_alice"));

        ActivityReadState restarted = new ActivityReadState(config, gson, "group_alice");
        restarted.receive(entries("[{id:1,revision:2},{id:2,revision:1}]"), "2026-11-15T10:00:15Z");
        assertEquals(2, restarted.getUnreadCount());
        restarted.setViewing(true);
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L)), restarted.getHighlights());
        assertEquals(0, restarted.getUnreadCount());

        ActivityReadState nextVisit = new ActivityReadState(config, gson, "group_alice");
        nextVisit.receive(entries("[{id:1,revision:2},{id:2,revision:1}]"), "2026-11-15T10:00:20Z");
        assertEquals(0, nextVisit.getUnreadCount());
        ActivityReadState otherPlayer = new ActivityReadState(config, gson, "group_bob");
        assertNull(otherPlayer.getCursor());
    }

    @Test
    public void firstLoadIsAcknowledgedAfterItsContentsAreShown() {
        ActivityReadState state = new ActivityReadState(config, gson, "group_alice");
        state.setViewing(true);
        state.receive(entries("[{id:1,revision:1}]"), "2026-11-15T10:00:00Z");
        assertFalse(saved.containsKey("group_alice"));

        state.viewed();

        assertEquals("2026-11-15T10:00:00Z", new ActivityReadState(config, gson, "group_alice").getCursor());
    }

    private JsonArray entries(String json) {
        return gson.fromJson(json, JsonArray.class);
    }
}
