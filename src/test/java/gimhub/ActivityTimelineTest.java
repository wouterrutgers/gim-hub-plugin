package gimhub;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;

public class ActivityTimelineTest {
    private ActivityUpdates ready(ActivityTimeline timeline) {
        Map<String, APISerializable> fields = new HashMap<>();
        timeline.flatten(fields);
        return (ActivityUpdates) fields.get("activity_updates");
    }

    @Test
    public void trainingStartsQuietlyGroupsSkillsAndKeepsNewerGainsAfterAcknowledgement() {
        ActivityTimeline timeline = new ActivityTimeline();
        Client client = mock(Client.class);
        when(client.getRealSkillLevel(Skill.ATTACK)).thenReturn(10);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(10);
        when(client.getSkillExperience(Skill.ATTACK)).thenReturn(1000);
        when(client.getSkillExperience(Skill.HITPOINTS)).thenReturn(1000);
        timeline.initialize(client);
        assertNull(ready(timeline));
        timeline.trackExperience(Skill.ATTACK, 1100, 10, 2000);
        ActivityUpdates first = ready(timeline);
        timeline.trackExperience(Skill.HITPOINTS, 1030, 10, 3000);
        timeline.acknowledge(first);
        ActivityUpdates newer = ready(timeline);
        assertEquals(1, newer.updates.size());
        assertEquals(first.updates.get(0).get("id"), newer.updates.get(0).get("id"));
        assertEquals(
                Map.of("skills", Map.of("Attack", 100L, "Hitpoints", 30L)),
                newer.updates.get(0).get("data"));
        timeline.acknowledge(newer);
        assertNull(ready(timeline));
        timeline.trackExperience(Skill.ATTACK, 1200, 10, 3000 + 15 * 60 * 1000);
        assertNotEquals(
                first.updates.get(0).get("id"), ready(timeline).updates.get(0).get("id"));
    }

    @Test
    public void trainingKeepsItsLastTimestampWhenTheClockMovesBackwards() {
        ActivityTimeline timeline = new ActivityTimeline();
        timeline.trackExperience(Skill.ATTACK, 1000, 10, 1000);
        timeline.trackExperience(Skill.ATTACK, 1100, 10, 2000);
        timeline.trackExperience(Skill.ATTACK, 1200, 10, 3000);
        timeline.acknowledge(ready(timeline));
        timeline.trackExperience(Skill.ATTACK, 1300, 10, 2900);
        Map<String, Object> update = ready(timeline).updates.get(0);
        assertEquals(3000L, update.get("occurred_at"));
        assertEquals(3, update.get("revision"));
        assertEquals(Map.of("skills", Map.of("Attack", 300L)), update.get("data"));
        timeline.trackExperience(Skill.ATTACK, 1400, 10, 3100);
        assertEquals(3100L, ready(timeline).updates.get(0).get("occurred_at"));
    }

    @Test
    public void bossCountsIgnoreRepeatsAndCreateANewStretchAfterAPause() {
        ActivityTimeline timeline = new ActivityTimeline();
        timeline.trackBoss("Vorkath", "kill", 100, 1000);
        timeline.trackBoss("Vorkath", "kill", 100, 2000);
        timeline.trackBoss("Vorkath", "kill", 102, 3000);
        ActivityUpdates first = ready(timeline);
        assertEquals(1, first.updates.size());
        assertEquals(
                Map.of("boss", "Vorkath", "count_type", "kill", "count", 3, "total_count", 102),
                first.updates.get(0).get("data"));
        timeline.acknowledge(first);
        timeline.trackBoss("Vorkath", "kill", 103, 3000 + 30 * 60 * 1000);
        assertNotEquals(
                first.updates.get(0).get("id"), ready(timeline).updates.get(0).get("id"));
    }

    @Test
    public void completedRaidChatCreatesBossActivity() {
        ActivityTimeline timeline = new ActivityTimeline();
        timeline.onChatMessage(new ChatMessage(
                null,
                ChatMessageType.GAMEMESSAGE,
                "",
                "Your completed Chambers of Xeric count is: <col=ff0000>1,234</col>.",
                "",
                0));
        ActivityUpdates updates = ready(timeline);
        assertNotNull(updates);
        assertEquals(1, updates.updates.size());
        assertEquals("boss", updates.updates.get(0).get("type"));
        assertEquals(
                Map.of("boss", "Chambers of Xeric", "count_type", "completion", "count", 1, "total_count", 1234),
                updates.updates.get(0).get("data"));
    }

    @Test
    public void aCollectionUnlockAndItsLootBecomeOnePricedAnnouncement() {
        ActivityTimeline timeline = new ActivityTimeline();
        Client client = mock(Client.class);
        ItemManager items = mock(ItemManager.class);
        when(client.getTickCount()).thenReturn(100);
        when(items.canonicalize(4151)).thenReturn(4151);
        when(items.getItemPrice(4151)).thenReturn(2000000L);
        timeline.onLootReceived(
                client,
                new LootReceived("Abyssal Sire", 0, LootRecordType.NPC, List.of(new ItemStack(4151, 1)), 1, null),
                items);
        timeline.markCollectionItem(4151);
        assertNull(ready(timeline));
        timeline.onGameTick(110);
        ActivityUpdates batch = ready(timeline);
        assertEquals(1, batch.updates.size());
        assertEquals(
                Map.of(
                        "item_id",
                        4151,
                        "quantity",
                        1,
                        "value",
                        2000000L,
                        "source",
                        "Abyssal Sire",
                        "new_collection_item",
                        true),
                batch.updates.get(0).get("data"));
    }

    @Test
    public void collectionScansDoNotAnnounceHistoricalItems() {
        CollectionLogManager manager = new CollectionLogManager();
        manager.storeCollectionLogItem(4151, 10);
        Map<String, APISerializable> fields = new HashMap<>();
        manager.flatten(fields);
        assertNotNull(fields.get("collection_log_updates"));
        assertNull(ready(manager.activityTimeline));
    }

    @Test
    public void logoutResetsBaselinesWithoutLosingUnacknowledgedEvents() {
        ActivityTimeline timeline = new ActivityTimeline();
        timeline.trackBoss("Vorkath", "kill", 100, 1000);
        ActivityUpdates pending = ready(timeline);
        timeline.resetBaselines();
        timeline.trackExperience(Skill.ATTACK, 200000000, 99, 2000);
        assertEquals(pending.updates, ready(timeline).updates);
    }
}
