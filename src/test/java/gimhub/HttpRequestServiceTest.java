package gimhub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
        assertTrue(request.getHeader("User-Agent").startsWith("GIM-hub/RuneLite/"));
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
    public void postSerializesJsonAndReturnsUnsuccessfulResponse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(422).setBody("invalid"));

        HttpRequestService.HttpResponse response = httpRequestService.post(
                server.url("/update").toString(), "group-token", Map.of("name", "Player", "world", 420));
        RecordedRequest request = server.takeRequest();

        assertFalse(response.isSuccessful());
        assertEquals(422, response.getCode());
        assertEquals("invalid", response.getBody());
        assertEquals("POST", request.getMethod());
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"));
        assertEquals(
                Map.of("name", "Player", "world", 420.0),
                new Gson().fromJson(request.getBody().readUtf8(), Map.class));
    }

    @Test
    public void asyncPostSendsJsonAndDeliversResponseToCallback() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("created"));

        CompletableFuture<HttpRequestService.HttpResponse> future = new CompletableFuture<>();
        httpRequestService.asyncPost(
                server.url("/relay-chat").toString(),
                "group-token",
                Map.of("name", "Player", "message", "hello"),
                future::complete);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        HttpRequestService.HttpResponse response = future.get(5, TimeUnit.SECONDS);

        assertTrue(response.isSuccessful());
        assertEquals(201, response.getCode());
        assertEquals("created", response.getBody());
        assertEquals("POST", request.getMethod());
        assertEquals("group-token", request.getHeader("Authorization"));
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"));
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
