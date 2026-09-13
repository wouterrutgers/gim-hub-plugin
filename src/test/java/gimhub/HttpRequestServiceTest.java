package gimhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import net.runelite.client.RuneLiteProperties;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpRequestServiceTest {
    private MockWebServer server;
    private GimHubConfig configuration;
    private HttpRequestService httpRequestService;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        configuration = mock(GimHubConfig.class);
        when(configuration.baseUrlOverride()).thenReturn(server.url("/").toString());

        httpRequestService = new HttpRequestService();
        setField(httpRequestService, "okHttpClient", new OkHttpClient());
        setField(httpRequestService, "config", configuration);
        setField(httpRequestService, "gson", new Gson());
        httpRequestService.initialize();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void internalGetIncludesAuthorizationAndAcceptHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        HttpRequestService.HttpResponse response =
                httpRequestService.get(server.url("/membership").toString(), "group-token");
        RecordedRequest request = server.takeRequest();

        assertTrue(response.isSuccessful());
        assertEquals(200, response.getCode());
        assertEquals("ok", response.getBody());
        assertEquals("group-token", request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Accept"));
        Properties pluginProperties = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get("runelite-plugin.properties"))) {
            pluginProperties.load(input);
        }
        assertEquals(
                "GIM-hub/" + pluginProperties.getProperty("version") + " RuneLite/" + RuneLiteProperties.getVersion(),
                request.getHeader("User-Agent"));
    }

    @Test
    public void externalRequestDoesNotLeakInternalHeaders() throws Exception {
        MockWebServer externalServer = new MockWebServer();
        externalServer.start();
        try {
            externalServer.enqueue(new MockResponse().setResponseCode(200));

            httpRequestService.get(externalServer.url("/cache").toString(), "group-token");
            RecordedRequest request = externalServer.takeRequest();

            assertNull(request.getHeader("Authorization"));
            assertNull(request.getHeader("Accept"));
        } finally {
            externalServer.shutdown();
        }
    }

    @Test
    public void usesTrimmedOverrideOrPublicBaseUrl() {
        when(configuration.baseUrlOverride()).thenReturn("  https://self-hosted.test  ");
        assertEquals("https://self-hosted.test", httpRequestService.getBaseUrl());

        when(configuration.baseUrlOverride()).thenReturn("  ");
        assertEquals("https://gim-hub.com", httpRequestService.getBaseUrl());
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
