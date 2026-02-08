package gimhub;

import gimhub.achievement.AchievementRepository;
import gimhub.activity.ActivityRepository;
import gimhub.items.ItemRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.RuneScapeProfileType;

@Slf4j
@Singleton
public class DataManager {
    // Managed by the Client thread

    private PlayerState state = null;
    private FlatState flatMostRecent = null;

    // Shared by both threads

    private final AtomicReference<FlatState> flatRef = new AtomicReference<>();

    // Managed by the request thread

    private FlatState flatFromFailedRequest = null;

    @Inject
    private HttpRequestService httpRequestService;

    @Inject
    private CollectionLogManager collectionLogManager;

    @Inject
    private GimHubConfig config;

    @Inject
    private ApiUrlBuilder apiUrlBuilder;

    private boolean isMemberInGroup = false;
    private int skipNextNAttempts = 0;

    /** Our tracked state of the player: their stats, items, location, etc. */
    public static class PlayerState {
        public final String ownedPlayer;
        public final ActivityRepository activityRepository;
        public final ItemRepository itemRepository;
        public final AchievementRepository achievementRepository;

        private FlatState flatten() {
            Map<String, APISerializable> flat = new HashMap<>();

            activityRepository.flatten(flat);
            itemRepository.flatten(flat);
            achievementRepository.flatten(flat);

            flat.entrySet().removeIf(e -> e.getValue() == null);

            return new FlatState(ownedPlayer, flat);
        }

        PlayerState(String ownedPlayer) {
            this.ownedPlayer = ownedPlayer;
            this.activityRepository = new ActivityRepository();
            this.itemRepository = new ItemRepository();
            this.achievementRepository = new AchievementRepository();
        }
    }

    /** PlayerState flattened but not yet serialized, in a form that allows easier diffing by key-value-pairs. */
    private static class FlatState {
        @Getter
        private final String ownedPlayer;

        private final Map<String, APISerializable> fields;

        public static FlatState combineWithPriority(FlatState priority, FlatState defaults) {
            final boolean samePlayer = priority.ownedPlayer.equals(defaults.ownedPlayer);
            if (!samePlayer) {
                return new FlatState(priority.ownedPlayer, new HashMap<>(priority.fields));
            }

            Map<String, APISerializable> mergedFields = new HashMap<>(defaults.fields);
            mergedFields.putAll(priority.fields);

            return new FlatState(priority.ownedPlayer, mergedFields);
        }

        public static FlatState diffKeepChangedFields(FlatState newer, FlatState older) {
            final boolean samePlayer = newer.ownedPlayer.equals(older.ownedPlayer);
            if (!samePlayer) {
                return new FlatState(newer.ownedPlayer, new HashMap<>(newer.fields));
            }

            Map<String, APISerializable> fieldsThatChanged = new HashMap<>(newer.fields);

            for (Entry<String, APISerializable> entry : older.fields.entrySet()) {
                APISerializable newerValue = fieldsThatChanged.get(entry.getKey());
                APISerializable olderValue = entry.getValue();

                if (newerValue != null && newerValue.equals(olderValue)) {
                    fieldsThatChanged.remove(entry.getKey());
                } else if (entry.getKey().endsWith("_partial")) {
                    // Send incremental updates to the server when we don't know the full state.
                    fieldsThatChanged.put(entry.getKey(), entry.getValue().diff(newer.fields.get(entry.getKey())));
                }
            }

            return new FlatState(newer.ownedPlayer, fieldsThatChanged);
        }

        public Map<String, Object> serialize() {
            Map<String, Object> serialized = fields.entrySet().stream()
                    .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().serialize()));
            serialized.put("name", ownedPlayer);
            return serialized;
        }

        FlatState(String player, Map<String, APISerializable> fields) {
            this.ownedPlayer = player;
            this.fields = fields;
        }
    }

    /**
     * Call from Client thread. Get the PlayerState to then write updates to. Checks the passed client to see if the
     * player changes and is valid (logged in, not seasonal, etc.). If the player name changes, then the state is reset
     * before being returned.
     *
     * @return The current valid PlayerState, or null if the Player is invalid.
     */
    @Nullable public PlayerState getMaybeResetState(Client client) {
        final boolean isStandardProfile = RuneScapeProfileType.getCurrent(client) == RuneScapeProfileType.STANDARD;
        final boolean isValidPlayerLoggedIn = client.getGameState() == GameState.LOGGED_IN
                && client.getLocalPlayer() != null
                && client.getLocalPlayer().getName() != null;

        if (!isValidPlayerLoggedIn || !isStandardProfile) {
            return null;
        }

        final String player = client.getLocalPlayer().getName();
        if (state == null || !state.ownedPlayer.equals(player)) {
            state = new PlayerState(player);
        }

        return state;
    }

    /** Call from the Client thread. Releases state updates to the request thread. */
    public void stageForSubmitToAPI() {
        FlatState full = state.flatten();
        FlatState trimmed = full;

        if (flatMostRecent != null && trimmed.ownedPlayer.equals(flatMostRecent.ownedPlayer)) {
            trimmed = FlatState.diffKeepChangedFields(trimmed, flatMostRecent);
        }
        flatMostRecent = full;

        // Carry forward the updates that the submission thread has not yet consumed.

        FlatState notYetSubmitted = flatRef.get();
        FlatState combined = trimmed;
        if (notYetSubmitted != null) {
            combined = FlatState.combineWithPriority(trimmed, notYetSubmitted);
        }

        final boolean raceOccurred = !flatRef.compareAndSet(notYetSubmitted, combined);
        if (!raceOccurred) {
            return;
        }

        if (!flatRef.compareAndSet(null, trimmed)) {
            log.error("Another thread wrote to flatRef.");
        }
    }

    /**
     * Call from the request thread. Performs all our network requests and authentication for posting player data to the
     * configured server. If playerName does not match the state we are given, this method aborts.
     *
     * @param playerName The player name to expect updates for.
     */
    public void submitToApi(String playerName) {
        if (skipNextNAttempts-- > 0) return;

        String groupToken = config.authorizationToken().trim();

        if (groupToken.isEmpty()) return;

        if (!isMemberInGroup) {
            isMemberInGroup = fetchIsMember(groupToken, playerName);
        }

        if (!isMemberInGroup) {
            log.debug("Skip POST: not a member. Backing off.");
            skipNextNAttempts = 10;
            return;
        }

        String url = apiUrlBuilder.getUpdateGroupMemberUrl();
        if (url == null) {
            log.debug("Skip POST: Update Group Member URL is null (check base URL and group name).");
            return;
        }

        FlatState flat = flatRef.get();
        if (flat == null || !flat.ownedPlayer.equals(playerName)) {
            // The Client thread changed players.
            // We exit and wait for next time since we don't know how out-of-date flat is.
            log.debug("Skip POST: Player changed. Backing off.");
            return;
        }
        flatRef.compareAndSet(flat, null);

        if (flatFromFailedRequest != null && playerName.equals(flatFromFailedRequest.ownedPlayer)) {
            flat = FlatState.combineWithPriority(flat, flatFromFailedRequest);
        }
        flatFromFailedRequest = null;

        Map<String, Object> updates = flat.serialize();
        collectionLogManager.consumeState(updates);

        // We require greater than 1 since name field is automatically included
        if (updates.size() <= 1) {
            log.debug("Skip POST: no changes to send (fields={})", updates.size());
            return;
        }

        HttpRequestService.HttpResponse response = httpRequestService.post(url, groupToken, updates);

        if (!response.isSuccessful()) {
            skipNextNAttempts = 10;
            if (response.getCode() == 422) {
                isMemberInGroup = false;
            }
            flatFromFailedRequest = flat;
            return;
        }

        collectionLogManager.clearClogItems();
    }

    private boolean fetchIsMember(String groupToken, String playerName) {
        String url = apiUrlBuilder.getMembershipCheckUrl(playerName);
        if (url == null) {
            log.debug("Skip POST: Membership Check URL is null (check base URL and group name).");
            return false;
        }

        return httpRequestService.get(url, groupToken).isSuccessful();
    }
}
