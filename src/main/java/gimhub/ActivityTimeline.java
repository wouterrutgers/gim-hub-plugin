package gimhub;

import gimhub.achievement.DiariesProgress;
import gimhub.achievement.QuestsProgress;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.StatChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

public class ActivityTimeline {
    private static final long TRAINING_PAUSE = 15 * 60 * 1000;
    private static final long BOSS_PAUSE = 30 * 60 * 1000;
    private static final Pattern KILL_COUNT = Pattern.compile(
            "Your (?:(.+?) (kill|chest|completion)|completed (.+?)) count is:? ([0-9,]+)", Pattern.CASE_INSENSITIVE);
    private final Map<Skill, Integer> experience = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
    private final Map<String, Entry> pending = new LinkedHashMap<>();
    private final Map<String, Entry> bosses = new HashMap<>();
    private final Map<String, Integer> killCounts = new HashMap<>();
    private final Map<Integer, Entry> recentLoot = new HashMap<>();
    private final Map<Integer, Integer> recentLootTicks = new HashMap<>();
    private Map<Integer, QuestState> quests;
    private List<Integer> diaryValues;
    private Entry training;
    private Entry diaries;
    private int currentTick;

    private static class Entry {
        final String identifier = UUID.randomUUID().toString();
        final String type;
        final long startedAt;
        long occurredAt;
        int revision;
        int readyTick;
        Map<String, Object> data;

        Entry(String type, long timestamp, Map<String, Object> data) {
            this.type = type;
            this.startedAt = timestamp;
            this.occurredAt = timestamp;
            this.data = data;
        }

        Map<String, Object> snapshot() {
            return Map.of(
                    "id",
                    identifier,
                    "type",
                    type,
                    "revision",
                    revision,
                    "started_at",
                    startedAt,
                    "occurred_at",
                    occurredAt,
                    "data",
                    data);
        }
    }

    private void changed(Entry entry, long timestamp) {
        entry.occurredAt = timestamp;
        entry.revision++;
        pending.put(entry.identifier, entry);
    }

    public synchronized void initialize(Client client) {
        for (Skill skill : Skill.values()) {
            if (experience.containsKey(skill)) continue;
            int level = client.getRealSkillLevel(skill);
            if (level < 1) continue;
            experience.put(skill, client.getSkillExperience(skill));
            levels.put(skill, level);
        }
    }

    public synchronized void onStatChanged(Client client, StatChanged event) {
        Skill skill = event.getSkill();
        if (skill == null) return;
        trackExperience(skill, event.getXp(), client.getRealSkillLevel(skill), System.currentTimeMillis());
    }

    synchronized void trackExperience(Skill skill, int currentExperience, int currentLevel, long timestamp) {
        if (currentLevel < 1) return;
        Integer previousExperience = experience.put(skill, currentExperience);
        Integer previousLevel = levels.put(skill, currentLevel);
        if (previousExperience == null || currentExperience <= previousExperience) return;
        if (training == null || timestamp - training.occurredAt >= TRAINING_PAUSE) {
            training = new Entry("xp", timestamp, Map.of("skills", Map.of()));
        }
        Map<String, Long> gains = new LinkedHashMap<>((Map<String, Long>) training.data.get("skills"));
        gains.merge(skill.getName(), (long) currentExperience - previousExperience, Long::sum);
        training.data = Map.of("skills", gains);
        changed(training, timestamp);
        if (previousLevel != null && previousLevel > 0 && currentLevel > previousLevel) {
            changed(
                    new Entry(
                            "level",
                            timestamp,
                            Map.of("skill", skill.getName(), "before", previousLevel, "after", currentLevel)),
                    timestamp);
        }
    }

    public synchronized void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;
        Matcher matcher = KILL_COUNT.matcher(Text.removeTags(event.getMessage()));
        if (!matcher.find()) return;
        trackBoss(
                matcher.group(1) == null ? matcher.group(3) : matcher.group(1),
                matcher.group(2) == null ? "completion" : matcher.group(2).toLowerCase(java.util.Locale.ROOT),
                Integer.parseInt(matcher.group(4).replace(",", "")),
                System.currentTimeMillis());
    }

    synchronized void trackBoss(String boss, String countType, int total, long timestamp) {
        Integer previous = killCounts.put(boss, total);
        if (previous != null && total <= previous) return;
        Entry entry = bosses.get(boss);
        if (entry == null || timestamp - entry.occurredAt >= BOSS_PAUSE) {
            entry = new Entry(
                    "boss", timestamp, Map.of("boss", boss, "count_type", countType, "count", 0, "total_count", total));
            bosses.put(boss, entry);
        }
        int gained = previous == null ? 1 : total - previous;
        entry.data = Map.of(
                "boss",
                boss,
                "count_type",
                countType,
                "count",
                (int) entry.data.get("count") + gained,
                "total_count",
                total);
        changed(entry, timestamp);
    }

    public synchronized void achievements(QuestsProgress currentQuests, DiariesProgress currentDiaries) {
        long timestamp = System.currentTimeMillis();
        Map<Integer, QuestState> values = currentQuests.getQuestStateMap();
        if (quests != null) {
            for (Quest quest : Quest.values()) {
                if (values.get(quest.getId()) == QuestState.FINISHED
                        && quests.get(quest.getId()) != QuestState.FINISHED) {
                    changed(
                            new Entry(
                                    "quest",
                                    timestamp,
                                    Map.of("quest_id", quest.getId(), "quest_name", quest.getName())),
                            timestamp);
                }
            }
        }
        quests = new HashMap<>(values);
        List<Integer> currentValues = currentDiaries.getDiaryVarValues();
        if (diaryValues != null && !diaryValues.equals(currentValues)) {
            if (diaries == null || timestamp - diaries.occurredAt >= TRAINING_PAUSE) {
                diaries = new Entry(
                        "diary",
                        timestamp,
                        Map.of("before", List.copyOf(diaryValues), "after", List.copyOf(currentValues)));
            } else {
                diaries.data = Map.of("before", diaries.data.get("before"), "after", List.copyOf(currentValues));
            }
            changed(diaries, timestamp);
        }
        diaryValues = List.copyOf(currentValues);
    }

    public synchronized void onLootReceived(Client client, LootReceived event, ItemManager itemManager) {
        if (event.getType() != LootRecordType.NPC
                && event.getType() != LootRecordType.EVENT
                && event.getType() != LootRecordType.PICKPOCKET) return;
        if (event.getName().equals("Loot Chest")) return;
        currentTick = client.getTickCount();
        Map<Integer, Integer> items = new LinkedHashMap<>();
        for (ItemStack item : event.getItems()) {
            items.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
        }
        long timestamp = System.currentTimeMillis();
        for (Map.Entry<Integer, Integer> item : items.entrySet()) {
            Entry entry = new Entry(
                    "loot",
                    timestamp,
                    Map.of(
                            "item_id",
                            item.getKey(),
                            "quantity",
                            item.getValue(),
                            "value",
                            itemManager.getItemPrice(item.getKey()) * item.getValue(),
                            "source",
                            event.getName(),
                            "new_collection_item",
                            false));
            entry.readyTick = currentTick + CollectionLogManager.UNLOCK_MATCH_TICKS;
            recentLoot.put(item.getKey(), entry);
            recentLootTicks.put(item.getKey(), currentTick);
            changed(entry, timestamp);
        }
    }

    public synchronized boolean markCollectionItem(int identifier) {
        Entry entry = recentLoot.get(identifier);
        if (entry == null) return false;
        entry.data = new LinkedHashMap<>(entry.data);
        entry.data.put("new_collection_item", true);
        changed(entry, entry.occurredAt);
        return true;
    }

    public synchronized void collectionItem(int identifier) {
        if (markCollectionItem(identifier)) return;
        long timestamp = System.currentTimeMillis();
        changed(
                new Entry(
                        "loot",
                        timestamp,
                        Map.of(
                                "item_id",
                                identifier,
                                "quantity",
                                1,
                                "value",
                                0L,
                                "source",
                                "Collection log",
                                "new_collection_item",
                                true)),
                timestamp);
    }

    public synchronized void onGameTick(int tick) {
        currentTick = tick;
        recentLootTicks.entrySet().removeIf(item -> {
            if (tick - item.getValue() <= CollectionLogManager.UNLOCK_MATCH_TICKS) return false;
            recentLoot.remove(item.getKey());
            return true;
        });
    }

    public synchronized void flatten(Map<String, APISerializable> fields) {
        List<Map<String, Object>> ready = new ArrayList<>();
        for (Entry entry : pending.values()) {
            if (entry.readyTick <= currentTick) ready.add(entry.snapshot());
            if (ready.size() == 100) break;
        }
        if (!ready.isEmpty()) fields.put("activity_updates", new ActivityUpdates(ready));
    }

    public synchronized void acknowledge(ActivityUpdates sent) {
        for (Map<String, Object> update : sent.updates) {
            Entry entry = pending.get(update.get("id"));
            if (entry != null && entry.revision <= (int) update.get("revision")) pending.remove(entry.identifier);
        }
    }

    public synchronized void resetBaselines() {
        for (Entry entry : pending.values()) entry.readyTick = 0;
        currentTick = 0;
        experience.clear();
        levels.clear();
        quests = null;
        diaryValues = null;
        diaries = null;
        killCounts.clear();
        recentLoot.clear();
        recentLootTicks.clear();
    }
}
