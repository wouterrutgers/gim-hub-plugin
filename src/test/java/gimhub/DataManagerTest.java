package gimhub;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class DataManagerTest {
    private Client client;
    private Player player;
    private HttpRequestService httpRequestService;
    private DataManager dataManager;

    @Before
    public void setUp() throws ReflectiveOperationException {
        client = mock(Client.class);
        player = mock(Player.class);
        GimHubConfig configuration = mock(GimHubConfig.class);
        ApiUrlBuilder apiUrlBuilder = mock(ApiUrlBuilder.class);
        httpRequestService = mock(HttpRequestService.class);
        dataManager = new DataManager();
        setField(dataManager, "httpRequestService", httpRequestService);
        setField(dataManager, "config", configuration);
        setField(dataManager, "apiUrlBuilder", apiUrlBuilder);

        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("Player one");
        when(configuration.authorizationToken()).thenReturn(" group-token ");
        when(apiUrlBuilder.getMembershipCheckUrl("Player one")).thenReturn("https://gim-hub.test/membership");
        when(apiUrlBuilder.getUpdateGroupMemberUrl()).thenReturn("https://gim-hub.test/update");
        when(httpRequestService.get("https://gim-hub.test/membership", "group-token"))
                .thenReturn(response(true, 200));
        when(httpRequestService.post(eq("https://gim-hub.test/update"), eq("group-token"), anyMap()))
                .thenReturn(response(true, 200));
    }

    @Test
    public void returnsNullForInvalidPlayersAndReusesValidState() {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        assertNull(dataManager.getMaybeResetState(client));

        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        assertSame(state, dataManager.getMaybeResetState(client));

        when(player.getName()).thenReturn("Player two");
        assertNotSame(state, dataManager.getMaybeResetState(client));
    }

    @Test
    public void baselineOnlyStateDoesNotPost() {
        dataManager.getMaybeResetState(client);
        dataManager.stageForSubmitToAPI();

        dataManager.submitToApi("Player one");

        verify(httpRequestService).get("https://gim-hub.test/membership", "group-token");
        verify(httpRequestService, never()).post(eq("https://gim-hub.test/update"), eq("group-token"), anyMap());
    }

    @Test
    public void postsChangedFieldsAndSuppressesAnUnchangedSecondSnapshot() {
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        state.activityRepository.updateResources(client);
        dataManager.stageForSubmitToAPI();

        dataManager.submitToApi("Player one");
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");

        ArgumentCaptor<Map<String, Object>> updates = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService, times(1))
                .post(eq("https://gim-hub.test/update"), eq("group-token"), updates.capture());
        assertTrue(updates.getValue().containsKey("name"));
        assertTrue(updates.getValue().containsKey("league_mode"));
        assertTrue(updates.getValue().containsKey("timezone"));
        assertTrue(updates.getValue().containsKey("stats"));
    }

    @Test
    public void mergesSnapshotsStagedBeforeTheRequestThreadConsumesThem() {
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        state.activityRepository.updateResources(client);
        dataManager.stageForSubmitToAPI();
        state.activityRepository.updateSkills(client);
        dataManager.stageForSubmitToAPI();

        dataManager.submitToApi("Player one");

        ArgumentCaptor<Map<String, Object>> updates = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService).post(eq("https://gim-hub.test/update"), eq("group-token"), updates.capture());
        assertTrue(updates.getValue().containsKey("stats"));
        assertTrue(updates.getValue().containsKey("skills"));
    }

    @Test
    public void retriesFailedFieldsTogetherWithLaterChanges() {
        when(httpRequestService.post(eq("https://gim-hub.test/update"), eq("group-token"), anyMap()))
                .thenReturn(response(false, 500), response(true, 200));
        DataManager.PlayerState state = dataManager.getMaybeResetState(client);
        state.activityRepository.updateResources(client);
        dataManager.stageForSubmitToAPI();
        dataManager.submitToApi("Player one");

        state.activityRepository.updateSkills(client);
        dataManager.stageForSubmitToAPI();
        for (int attempt = 0; attempt < 11; attempt++) {
            dataManager.submitToApi("Player one");
        }

        ArgumentCaptor<Map<String, Object>> updates = ArgumentCaptor.forClass(Map.class);
        verify(httpRequestService, times(2))
                .post(eq("https://gim-hub.test/update"), eq("group-token"), updates.capture());
        Map<String, Object> retriedUpdates = updates.getAllValues().get(1);
        assertTrue(retriedUpdates.containsKey("stats"));
        assertTrue(retriedUpdates.containsKey("skills"));
    }

    private static HttpRequestService.HttpResponse response(boolean successful, int code) {
        return new HttpRequestService.HttpResponse(successful, code, "");
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
