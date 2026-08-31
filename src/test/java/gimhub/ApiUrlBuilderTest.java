package gimhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;

public class ApiUrlBuilderTest {
    private GimHubConfig configuration;
    private HttpRequestService httpRequestService;
    private ApiUrlBuilder apiUrlBuilder;

    @Before
    public void setUp() throws ReflectiveOperationException {
        configuration = mock(GimHubConfig.class);
        httpRequestService = mock(HttpRequestService.class);
        apiUrlBuilder = new ApiUrlBuilder();
        setField(apiUrlBuilder, "config", configuration);
        setField(apiUrlBuilder, "httpRequestService", httpRequestService);
        when(httpRequestService.getBaseUrl()).thenReturn("https://gim-hub.test");
    }

    @Test
    public void buildsUpdateUrlFromTrimmedGroupName() {
        when(configuration.groupName()).thenReturn("  iron friends  ");

        assertEquals(
                "https://gim-hub.test/api/group/iron friends/update-group-member",
                apiUrlBuilder.getUpdateGroupMemberUrl());
    }

    @Test
    public void buildsMembershipUrlWithPlayerName() {
        when(configuration.groupName()).thenReturn("iron-friends");

        assertEquals(
                "https://gim-hub.test/api/group/iron-friends/am-i-in-group?member_name=Player One",
                apiUrlBuilder.getMembershipCheckUrl("Player One"));
    }

    @Test
    public void buildsRelayChatUrl() {
        when(configuration.groupName()).thenReturn("iron-friends");

        assertEquals(
                "https://gim-hub.test/api/group/iron-friends/relay-chat", apiUrlBuilder.getRelayChatUrl());
    }

    @Test
    public void returnsNullWithoutGroupOrBaseUrl() {
        when(configuration.groupName()).thenReturn("  ");
        assertNull(apiUrlBuilder.getUpdateGroupMemberUrl());

        when(configuration.groupName()).thenReturn("iron-friends");
        when(httpRequestService.getBaseUrl()).thenReturn(null);
        assertNull(apiUrlBuilder.getUpdateGroupMemberUrl());
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
