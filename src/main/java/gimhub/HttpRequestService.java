package gimhub;

import com.google.gson.Gson;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import okhttp3.*;

@Slf4j
@Singleton
public class HttpRequestService {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String USER_AGENT = "GIM-hub/RuneLite/" + RuneLiteProperties.getVersion();
    private static final String PUBLIC_BASE_URL = "https://gim-hub.com";

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private GimHubConfig config;

    @Inject
    private Gson gson;

    public HttpResponse get(String url, String authToken) {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Authorization", authToken)
                .header("User-Agent", USER_AGENT)
                .get();

        if (url.startsWith(getBaseUrl())) {
            requestBuilder.header("Accept", "application/json");
        }

        Request request = requestBuilder.build();

        return executeRequest(request, "GET", url, null);
    }

    public HttpResponse post(String url, String authToken, Object requestBody) {
        String requestJson = gson.toJson(requestBody);
        RequestBody body = RequestBody.create(JSON, requestJson);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Authorization", authToken)
                .header("User-Agent", USER_AGENT)
                .post(body);

        if (url.startsWith(getBaseUrl())) {
            requestBuilder.header("Accept", "application/json");
        }

        Request request = requestBuilder.build();

        return executeRequest(request, "POST", url, requestJson);
    }

    private HttpResponse executeRequest(Request request, String method, String url, String requestBody) {
        Call call = okHttpClient.newCall(request);

        try (Response response = call.execute()) {
            String responseBody = readBodySafe(response);

            if (config.httpDebugLogging()) {
                logRequest(method, url, requestBody, response, responseBody);
            }

            return new HttpResponse(response.isSuccessful(), response.code(), responseBody);

        } catch (IOException ex) {
            if (config.httpDebugLogging()) {
                log.warn("{} {} failed: {}", method, url, ex.toString());
            }

            return new HttpResponse(false, -1, ex.getMessage());
        }
    }

    private void logRequest(String method, String url, String requestBody, Response response, String responseBody) {
        if ("GET".equals(method)) {
            log.info("GET {} -> {}\nresp: {}", url, response.code(), truncate(responseBody, 2000));
        } else if ("POST".equals(method)) {
            log.info(
                    "POST {}\nreq: {}\nresp({}): {}",
                    url,
                    truncate(requestBody, 2000),
                    response.code(),
                    truncate(responseBody, 2000));
        }
    }

    private static String readBodySafe(Response response) {
        try {
            ResponseBody rb = response.body();

            return rb != null ? rb.string() : "<no body>";
        } catch (Exception e) {
            return "<unavailable: " + e.getMessage() + ">";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;

        return s.substring(0, max) + "...(" + s.length() + " chars)";
    }

    public String getBaseUrl() {
        String baseUrlOverride = config.baseUrlOverride().trim();
        if (!baseUrlOverride.isEmpty()) {
            return baseUrlOverride;
        }

        return PUBLIC_BASE_URL;
    }

    public static class HttpResponse {
        private final boolean successful;
        private final int code;
        private final String body;

        public HttpResponse(boolean successful, int code, String body) {
            this.successful = successful;
            this.code = code;
            this.body = body;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public int getCode() {
            return code;
        }

        public String getBody() {
            return body;
        }
    }
}
