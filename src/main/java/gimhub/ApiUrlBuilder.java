package gimhub;

import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.HttpUrl;

@Singleton
public class ApiUrlBuilder {
    @Inject
    private GimHubConfig config;

    @Inject
    private HttpRequestService httpRequestService;

    public String getUpdateGroupMemberUrl() {
        HttpUrl url = getGroupUrl("update-group-member");
        return url == null ? null : url.toString();
    }

    public String getMembershipCheckUrl(String playerName) {
        HttpUrl url = getGroupUrl("am-i-in-group");
        return url == null
                ? null
                : url.newBuilder()
                        .addQueryParameter("member_name", playerName)
                        .build()
                        .toString();
    }

    public HttpUrl getGroupUrl(String path) {
        String baseUrl = httpRequestService.getBaseUrl();
        String groupName = getGroupName();
        if (baseUrl == null || groupName == null) {
            return null;
        }
        return HttpUrl.get(baseUrl)
                .newBuilder()
                .addPathSegment("api")
                .addPathSegment("group")
                .addPathSegment(groupName)
                .addPathSegments(path)
                .build();
    }

    private String getGroupName() {
        String groupName = config.groupName().trim();

        return groupName.isEmpty() ? null : groupName;
    }
}
