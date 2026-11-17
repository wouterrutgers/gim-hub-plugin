package gimhub;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import okhttp3.*;
import okio.BufferedSink;

@Slf4j
@Singleton
public class HttpRequestService {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String USER_AGENT =
            "GIM hub/" + GimHubPlugin.VERSION + " RuneLite/" + RuneLiteProperties.getVersion();
    private static final String PUBLIC_BASE_URL = "https://gim-hub.com";

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private GimHubConfig config;

    @Inject
    private Gson gson;

    @Inject
    protected void initialize() {
        okHttpClient = okHttpClient.newBuilder().retryOnConnectionFailure(false).build();
    }

    public HttpResponse get(String url, String authToken) {
        Request request = buildRequest(url, authToken).get().build();

        return executeRequest(request, "GET", url, null);
    }

    public CompletableFuture<HttpResponse> getAsync(String url, String authToken) {
        return executeAsync(buildRequest(url, authToken).get().build(), "GET", url, null);
    }

    public CompletableFuture<HttpResponse> patchAsync(String url, String authToken, Object changes) {
        String requestJson = gson.toJson(changes);
        return executeAsync(
                buildRequest(url, authToken)
                        .patch(RequestBody.create(JSON, requestJson))
                        .build(),
                "PATCH",
                url,
                requestJson);
    }

    private CompletableFuture<HttpResponse> executeAsync(
            Request request, String method, String url, String requestBody) {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        Call call = okHttpClient.newCall(request);
        future.whenComplete((response, error) -> {
            if (future.isCancelled()) {
                call.cancel();
            }
        });
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                if (!future.isCancelled()) {
                    log.warn("{} {} failed: {}", method, url, error.toString());
                }
                future.completeExceptionally(error);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response ignored = response) {
                    String responseBody = readBodySafe(response);
                    logRequest(method, url, requestBody, response, responseBody);
                    future.complete(new HttpResponse(response.isSuccessful(), response.code(), responseBody));
                }
            }
        });
        return future;
    }

    public HttpResponse post(String url, String authToken, Object requestBody) {
        String requestJson = gson.toJson(requestBody);
        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return JSON;
            }

            @Override
            public long contentLength() {
                return requestJson.getBytes(StandardCharsets.UTF_8).length;
            }

            @Override
            public boolean isOneShot() {
                return true;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.writeUtf8(requestJson);
            }
        };

        Request request = buildRequest(url, authToken).post(body).build();

        return executeRequest(request, "POST", url, requestJson);
    }

    private Request.Builder buildRequest(String url, String authToken) {
        HttpUrl requestUrl = HttpUrl.get(url);
        Request.Builder requestBuilder = new Request.Builder().url(requestUrl).header("User-Agent", USER_AGENT);

        if (isInternalUrl(requestUrl)) {
            if (authToken != null && !authToken.trim().isEmpty()) {
                requestBuilder.header("Authorization", authToken);
            }
            requestBuilder.header("Accept", "application/json");
        }

        return requestBuilder;
    }

    private boolean isInternalUrl(HttpUrl url) {
        HttpUrl baseUrl = HttpUrl.parse(getBaseUrl());
        if (baseUrl == null
                || !url.scheme().equals(baseUrl.scheme())
                || !url.host().equals(baseUrl.host())
                || url.port() != baseUrl.port()) {
            return false;
        }
        String path = baseUrl.encodedPath();
        return url.encodedPath().equals(path) || url.encodedPath().startsWith(path.endsWith("/") ? path : path + "/");
    }

    private HttpResponse executeRequest(Request request, String method, String url, String requestBody) {
        Call call = okHttpClient.newCall(request);

        try (Response response = call.execute()) {
            String responseBody = readBodySafe(response);
            logRequest(method, url, requestBody, response, responseBody);

            return new HttpResponse(response.isSuccessful(), response.code(), responseBody);
        } catch (IOException ex) {
            log.warn("{} {} failed: {}", method, url, ex.toString());

            return new HttpResponse(false, -1, ex.getMessage());
        }
    }

    private void logRequest(String method, String url, String requestBody, Response response, String responseBody) {
        if (!log.isDebugEnabled()) {
            return;
        }

        switch (method) {
            case "GET":
                log.debug("GET {} -> {}\nResponse: {}", url, response.code(), responseBody);
                break;
            case "POST":
            case "PATCH":
                log.debug(
                        "{} {}\nRequest: {}\nResponse({}): {}",
                        method,
                        url,
                        requestBody,
                        response.code(),
                        responseBody);
                break;
            default:
                log.debug("{} {} -> {}\nResponse: {}", method, url, response.code(), responseBody);
        }
    }

    private static String readBodySafe(Response response) {
        try {
            ResponseBody responseBody = response.body();

            return responseBody != null ? responseBody.string() : "<no body>";
        } catch (Exception e) {
            return "<unavailable: " + e.getMessage() + ">";
        }
    }

    public String getBaseUrl() {
        String baseUrlOverride = config.baseUrlOverride().trim();
        if (!baseUrlOverride.isEmpty()) {
            return baseUrlOverride;
        }
        return PUBLIC_BASE_URL;
    }

    @Getter
    public static class HttpResponse {
        private final boolean successful;
        private final int code;
        private final String body;

        public HttpResponse(boolean successful, int code, String body) {
            this.successful = successful;
            this.code = code;
            this.body = body;
        }
    }
}
