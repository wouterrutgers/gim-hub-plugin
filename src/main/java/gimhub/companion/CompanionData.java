package gimhub.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;
import java.util.stream.Collectors;
import lombok.Value;
import net.runelite.api.Experience;
import net.runelite.client.util.QuantityFormatter;

public class CompanionData {
    public static final List<String> SKILLS = Arrays.asList(
            "Agility",
            "Attack",
            "Construction",
            "Cooking",
            "Crafting",
            "Defence",
            "Farming",
            "Firemaking",
            "Fishing",
            "Fletching",
            "Herblore",
            "Hitpoints",
            "Hunter",
            "Magic",
            "Mining",
            "Prayer",
            "Ranged",
            "Runecraft",
            "Slayer",
            "Smithing",
            "Strength",
            "Thieving",
            "Woodcutting",
            "Sailing");
    private static final Map<String, String> CONTAINERS = new LinkedHashMap<>();

    static {
        for (String key : Arrays.asList(
                "bank",
                "equipment",
                "inventory",
                "quiver",
                "rune_pouch",
                "seed_vault",
                "potion_storage",
                "poh_costume_room",
                "poh_spice_rack",
                "poh_pet_house",
                "poh_servants_moneybag",
                "blast_furnace_coffer",
                "nightmare_zone_coffer",
                "scar_essence_mine_coffer",
                "plank_sack",
                "master_scroll_book",
                "essence_pouches",
                "tackle_box",
                "tool_leprechaun",
                "elnock_inquisitor",
                "coal_bag",
                "fish_barrel",
                "herb_sack",
                "looting_bag",
                "seed_box",
                "gem_bag",
                "chugging_barrel",
                "meat_pouch",
                "log_basket",
                "reagent_pouch",
                "fur_pouch",
                "bolt_pouch",
                "forestry_kit",
                "huntsmans_kit",
                "bow_string_spool",
                "steel_key_ring",
                "flamtaer_bag",
                "bottomless_compost_bucket",
                "gnomish_firelighter",
                "blast_furnace",
                "fossil_storage",
                "balloon_transport",
                "sandstorm",
                "hallowed_sepulchre")) {
            String label = key.replace("poh_", "POH ").replace('_', ' ');
            CONTAINERS.put(key, Character.toUpperCase(label.charAt(0)) + label.substring(1));
        }
        CONTAINERS.put("blast_furnace_coffer", "Blast Furnace coffer");
        CONTAINERS.put("nightmare_zone_coffer", "Nightmare Zone coffer");
        CONTAINERS.put("scar_essence_mine_coffer", "Scar Essence Mine coffer");
        CONTAINERS.put("elnock_inquisitor", "Elnock Inquisitor");
        CONTAINERS.put("blast_furnace", "Blast Furnace");
        CONTAINERS.put("hallowed_sepulchre", "Hallowed Sepulchre");
    }

    private final Map<Integer, JsonObject> members = new LinkedHashMap<>();
    private final Map<Long, JsonObject> activities = new LinkedHashMap<>();
    private JsonObject metadata = new JsonObject();
    private Map<Integer, Long> itemPrices = new HashMap<>();
    private final Map<Integer, String> itemNames = new HashMap<>();
    private Set<Integer> pinnedItems = new HashSet<>();

    public synchronized void mergeMembers(JsonArray updates) {
        Set<Integer> present = new HashSet<>();
        for (JsonElement element : updates) {
            JsonObject update = element.getAsJsonObject();
            int identifier = update.get("id").getAsInt();
            present.add(identifier);
            JsonObject previous = members.get(identifier);
            JsonObject member = previous == null ? new JsonObject() : previous.deepCopy();
            update.entrySet().stream()
                    .filter(entry -> !entry.getValue().isJsonNull())
                    .forEach(entry -> member.add(entry.getKey(), entry.getValue()));
            members.put(identifier, member);
        }
        members.keySet().retainAll(present);
    }

    public synchronized String groupCursor() {
        return members.values().stream()
                .filter(member -> member.has("last_updated"))
                .map(member -> Instant.parse(member.get("last_updated").getAsString()))
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH)
                .toString();
    }

    public synchronized void mergeActivity(JsonArray entries) {
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            long identifier = entry.get("id").getAsLong();
            JsonObject previous = activities.get(identifier);
            if (previous == null
                    || entry.get("revision").getAsInt()
                            >= previous.get("revision").getAsInt()) {
                activities.put(identifier, entry);
            }
        }
        Set<String> archivedWeeks = activities.values().stream()
                .filter(entry -> entry.get("is_weekly").getAsBoolean())
                .map(entry -> entry.get("member_id").getAsInt() + ":"
                        + entry.get("started_at").getAsLong())
                .collect(Collectors.toSet());
        activities
                .values()
                .removeIf(entry -> !entry.get("is_weekly").getAsBoolean()
                        && archivedWeeks.contains(entry.get("member_id").getAsInt() + ":"
                                + Instant.ofEpochMilli(entry.get("occurred_at").getAsLong())
                                        .atZone(ZoneOffset.UTC)
                                        .toLocalDate()
                                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                        .atStartOfDay(ZoneOffset.UTC)
                                        .toInstant()
                                        .toEpochMilli()));
    }

    public synchronized List<JsonObject> activities() {
        return activities.values().stream()
                .sorted(Comparator.comparingLong(
                                (JsonObject entry) -> entry.get("started_at").getAsLong())
                        .reversed()
                        .thenComparing(Comparator.comparingLong(
                                        (JsonObject entry) -> entry.get("id").getAsLong())
                                .reversed()))
                .collect(Collectors.toList());
    }

    public synchronized List<JsonObject> players() {
        return members.values().stream()
                .filter(member -> !member.get("name").getAsString().equals("@SHARED"))
                .collect(Collectors.toList());
    }

    public synchronized JsonObject player(String name) {
        return players().stream()
                .filter(player -> player.get("name").getAsString().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public synchronized String memberName(int identifier) {
        JsonObject member = members.get(identifier);
        return member == null ? "Former member" : displayName(member);
    }

    public static String displayName(JsonObject member) {
        String name = member.get("name").getAsString();
        return name.equals("@SHARED") ? "Group storage" : name;
    }

    public synchronized void setMetadata(JsonObject metadata) {
        this.metadata = metadata;
    }

    public synchronized void updateItemData(IntToLongFunction priceLookup, IntFunction<String> nameLookup) {
        Map<Integer, Long> prices = new HashMap<>();
        Set<Integer> heldItems = holdings("All players", "All storage").keySet();
        updateItemNames(heldItems, nameLookup);
        heldItems.forEach(identifier -> prices.put(identifier, priceLookup.applyAsLong(identifier)));
        itemPrices = prices;
    }

    synchronized void updateItemNames(Set<Integer> identifiers, IntFunction<String> nameLookup) {
        for (int identifier : identifiers) {
            itemNames.computeIfAbsent(identifier, nameLookup::apply);
        }
    }

    public synchronized boolean hasMetadata() {
        return metadata.has("quests");
    }

    public synchronized void setPreferences(JsonObject response) {
        pinnedItems = new HashSet<>();
        JsonElement preferences = response.get("preferences");
        if (!preferences.isJsonNull() && preferences.getAsJsonObject().has("pinnedItemIds")) {
            for (JsonElement identifier : preferences.getAsJsonObject().getAsJsonArray("pinnedItemIds")) {
                pinnedItems.add(identifier.getAsInt());
            }
        }
    }

    public synchronized Set<Integer> pinnedItems() {
        return new HashSet<>(pinnedItems);
    }

    public synchronized boolean isPinned(int identifier) {
        return pinnedItems.contains(identifier);
    }

    public synchronized String itemName(int identifier) {
        return itemNames.getOrDefault(identifier, "Item " + identifier);
    }

    public synchronized List<StockItem> items(String memberFilter, String containerFilter, String query) {
        String search = query.trim().toLowerCase(Locale.ROOT);
        return holdings(memberFilter, containerFilter).entrySet().stream()
                .map(entry -> new StockItem(entry.getKey(), itemName(entry.getKey()), entry.getValue()))
                .filter(item -> Arrays.stream(search.split("\\s+"))
                        .allMatch(word -> item.name.toLowerCase(Locale.ROOT).contains(word)))
                .sorted(Comparator.comparing((StockItem item) -> !pinnedItems.contains(item.identifier))
                        .thenComparing(Comparator.comparingDouble((StockItem item) ->
                                        itemPrices.getOrDefault(item.identifier, 0L) * (double) item.total())
                                .reversed())
                        .thenComparing(StockItem::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private Map<Integer, List<Holding>> holdings(String memberFilter, String containerFilter) {
        Map<Integer, List<Holding>> holdings = new LinkedHashMap<>();
        for (JsonObject member : members.values()) {
            String owner = displayName(member);
            if (!memberFilter.equals("All players") && !owner.equals(memberFilter)) {
                continue;
            }
            CONTAINERS.forEach((key, label) -> {
                if (member.has(key) && (containerFilter.equals("All storage") || containerFilter.equals(label))) {
                    addHoldings(holdings, member.getAsJsonArray(key), owner, label);
                }
            });
            for (String key : Arrays.asList("stash_units", "cargo_holds")) {
                String label = key.equals("stash_units") ? "STASH units" : "Cargo holds";
                if (!member.has(key) || (!containerFilter.equals("All storage") && !containerFilter.equals(label))) {
                    continue;
                }
                for (JsonElement element : member.getAsJsonArray(key)) {
                    JsonObject container = element.getAsJsonObject();
                    if (key.equals("stash_units")
                            && !container.get("state").getAsString().equals("filled")) {
                        continue;
                    }
                    addHoldings(
                            holdings,
                            container.getAsJsonArray("items"),
                            owner,
                            label + " (" + container.get("name").getAsString() + ")");
                }
            }
        }
        return holdings;
    }

    private static void addHoldings(
            Map<Integer, List<Holding>> holdings, JsonArray items, String owner, String container) {
        for (int index = 0; index < items.size(); index += 2) {
            int identifier = items.get(index).getAsInt();
            long quantity = items.get(index + 1).getAsLong();
            if (identifier > 0 && quantity > 0) {
                holdings.computeIfAbsent(identifier, ignored -> new ArrayList<>())
                        .add(new Holding(owner, container, quantity));
            }
        }
    }

    public static List<String> containers() {
        List<String> labels = new ArrayList<>(CONTAINERS.values());
        labels.add("STASH units");
        labels.add("Cargo holds");
        return labels;
    }

    public synchronized Map<String, SkillProgress> playerSkills(String name) {
        Map<String, SkillProgress> result = new LinkedHashMap<>();
        JsonObject member = player(name);
        if (member == null || !member.has("skills")) {
            return result;
        }
        JsonArray skills = member.getAsJsonArray("skills");
        for (int index = 0; index < SKILLS.size(); index++) {
            int experience = skills.get(index).getAsInt();
            int level = Math.min(99, Experience.getLevelForXp(experience));
            int minimum = Experience.getXpForLevel(level);
            int next = Experience.getXpForLevel(Math.min(99, level + 1));
            result.put(
                    SKILLS.get(index),
                    new SkillProgress(
                            SKILLS.get(index),
                            level,
                            experience,
                            Math.max(0, next - experience),
                            level == 99 ? 100 : (int) (100L * (experience - minimum) / (next - minimum))));
        }
        return result;
    }

    public synchronized List<QuestProgress> quests(String name) {
        List<QuestProgress> result = new ArrayList<>();
        JsonObject member = player(name);
        if (!hasMetadata() || member == null) {
            return result;
        }
        List<Map.Entry<String, JsonElement>> quests =
                new ArrayList<>(metadata.getAsJsonObject("quests").entrySet());
        quests.sort(Comparator.comparingInt(entry -> Integer.parseInt(entry.getKey())));
        JsonArray progress = member.getAsJsonArray("quests");
        for (int index = 0; index < quests.size(); index++) {
            JsonObject quest = quests.get(index).getValue().getAsJsonObject();
            if (quest.has("hidden") && quest.get("hidden").getAsBoolean()) {
                continue;
            }
            String section = quest.has("miniquest") && quest.get("miniquest").getAsBoolean()
                    ? "Miniquests"
                    : quest.get("member").getAsBoolean() ? "Members' quests" : "Free quests";
            result.add(new QuestProgress(
                    Integer.parseInt(quests.get(index).getKey()),
                    quest.get("name").getAsString(),
                    section,
                    quest.get("points").getAsInt(),
                    progress == null || index >= progress.size()
                            ? -1
                            : progress.get(index).getAsInt()));
        }
        return result;
    }

    public synchronized List<DiaryRegion> diaries(String name) {
        List<DiaryRegion> regions = new ArrayList<>();
        JsonObject member = player(name);
        if (!hasMetadata() || member == null) {
            return regions;
        }
        JsonArray variables = member.getAsJsonArray("diary_vars");
        metadata.getAsJsonObject("diaries").entrySet().forEach(region -> {
            List<DiaryTier> tiers = new ArrayList<>();
            region.getValue().getAsJsonObject().entrySet().forEach(tier -> {
                JsonArray definitions = metadata.getAsJsonObject("diary_tasks")
                        .getAsJsonObject(region.getKey())
                        .getAsJsonArray(tier.getKey());
                JsonArray descriptions = tier.getValue().getAsJsonArray();
                List<DiaryTask> tasks = new ArrayList<>();
                for (int index = 0; index < descriptions.size(); index++) {
                    JsonObject task = descriptions.get(index).getAsJsonObject();
                    tasks.add(new DiaryTask(
                            task.get("task").getAsString(),
                            variables == null
                                    ? null
                                    : diaryComplete(
                                            variables, definitions.get(index).getAsJsonObject())));
                }
                tiers.add(new DiaryTier(tier.getKey(), tasks));
            });
            regions.add(new DiaryRegion(region.getKey(), tiers));
        });
        return regions;
    }

    private static boolean diaryComplete(JsonArray variables, JsonObject definition) {
        if (definition.has("value")) {
            JsonArray value = definition.getAsJsonArray("value");
            return variables.get(value.get(0).getAsInt()).getAsInt()
                    == value.get(1).getAsInt();
        }
        for (JsonElement element : definition.getAsJsonArray("bits")) {
            JsonArray bit = element.getAsJsonArray();
            if ((variables.get(bit.get(0).getAsInt()).getAsInt()
                            & (1 << bit.get(1).getAsInt()))
                    != 0) {
                return true;
            }
        }
        return false;
    }

    public synchronized String activityText(JsonObject entry) {
        JsonObject data = entry.getAsJsonObject("data");
        switch (entry.get("type").getAsString()) {
            case "xp":
                long total = data.getAsJsonObject("skills").entrySet().stream()
                        .mapToLong(skill -> skill.getValue().getAsLong())
                        .sum();
                return "Gained " + QuantityFormatter.formatNumber(total) + " XP";
            case "level":
                return "Reached level " + data.get("after").getAsInt() + " "
                        + data.get("skill").getAsString();
            case "boss":
                return data.get("boss").getAsString() + ": " + data.get("count").getAsInt() + " "
                        + data.get("count_type").getAsString()
                        + (data.get("count").getAsInt() == 1 ? "" : "s");
            case "quest":
                return "Completed " + data.get("quest_name").getAsString();
            case "diary":
                String tier = data.get("tier").getAsString().toLowerCase(Locale.ROOT);
                String region = data.get("region").getAsString();
                int count = data.getAsJsonArray("tasks").size();
                return data.get("complete").getAsBoolean()
                        ? "Completed the " + tier + " " + region + " diary"
                        : region + " " + tier + " diary: " + count + (count == 1 ? " task" : " tasks");
            default:
                long quantity = data.get("quantity").getAsLong();
                return (data.get("new_collection_item").getAsBoolean()
                                        && data.get("source").getAsString().equals("Collection log")
                                ? "Unlocked "
                                : "Received ")
                        + (quantity > 1 ? QuantityFormatter.formatNumber(quantity) + " x " : "")
                        + itemName(data.get("item_id").getAsInt());
        }
    }

    public synchronized String activityDetails(JsonObject entry) {
        JsonObject data = entry.getAsJsonObject("data");
        switch (entry.get("type").getAsString()) {
            case "xp":
                return data.getAsJsonObject("skills").entrySet().stream()
                        .map(skill -> skill.getKey() + ": +"
                                + QuantityFormatter.formatNumber(
                                        skill.getValue().getAsLong()) + " XP")
                        .collect(Collectors.joining("\n"));
            case "boss":
                return "Total kill count: " + data.get("total_count").getAsLong();
            case "level":
                return "Level " + data.get("before").getAsInt() + " to "
                        + data.get("after").getAsInt();
            case "diary":
                JsonArray tasks = metadata.getAsJsonObject("diaries")
                        .getAsJsonObject(data.get("region").getAsString())
                        .getAsJsonArray(data.get("tier").getAsString());
                List<String> descriptions = new ArrayList<>();
                for (JsonElement task : data.getAsJsonArray("tasks")) {
                    descriptions.add(tasks.get(task.getAsInt())
                            .getAsJsonObject()
                            .get("task")
                            .getAsString());
                }
                return String.join("\n", descriptions);
            case "loot":
            case "collection":
                return "From " + data.get("source").getAsString()
                        + (data.has("value")
                                ? "\nValue: "
                                        + QuantityFormatter.formatNumber(
                                                data.get("value").getAsLong()) + " GP"
                                : "");
            default:
                return activityText(entry);
        }
    }

    @Value
    public static class Holding {
        String owner;
        String container;
        long quantity;
    }

    @Value
    public static class StockItem {
        int identifier;
        String name;
        List<Holding> holdings;

        public long total() {
            return holdings.stream().mapToLong(Holding::getQuantity).sum();
        }
    }

    @Value
    public static class SkillProgress {
        String name;
        int level;
        int experience;
        int remaining;
        int percent;
    }

    @Value
    public static class QuestProgress {
        int identifier;
        String name;
        String section;
        int points;
        int state;

        public boolean isComplete() {
            return state == 2;
        }
    }

    @Value
    public static class DiaryTask {
        String name;
        Boolean complete;
    }

    @Value
    public static class DiaryTier {
        String name;
        List<DiaryTask> tasks;

        public long completed() {
            return tasks.stream()
                    .filter(task -> Boolean.TRUE.equals(task.complete))
                    .count();
        }
    }

    @Value
    public static class DiaryRegion {
        String name;
        List<DiaryTier> tiers;

        public long completed() {
            return tiers.stream().mapToLong(DiaryTier::completed).sum();
        }

        public int total() {
            return tiers.stream().mapToInt(tier -> tier.tasks.size()).sum();
        }
    }
}
