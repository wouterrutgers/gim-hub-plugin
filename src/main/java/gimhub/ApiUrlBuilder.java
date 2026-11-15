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
        String baseUrl = httpRequestService.getBaseUrl();
        String groupName = getGroupName();

        if (baseUrl == null || groupName == null) return null;

        return String.format("%s/api/group/%s/update-group-member", baseUrl, groupName);
    }

    public String getMembershipCheckUrl(String playerName) {
        String baseUrl = httpRequestService.getBaseUrl();
        String groupName = getGroupName();

        if (baseUrl == null || groupName == null) return null;

        return String.format("%s/api/group/%s/am-i-in-group?member_name=%s", baseUrl, groupName, playerName);
    }

    public HttpUrl getGroupUrl(String path) {
        return HttpUrl.parse(httpRequestService.getBaseUrl())
                .newBuilder()
                .addPathSegment("api")
                .addPathSegment("group")
                .addPathSegment(getGroupName())
                .addPathSegments(path)
                .build();
    }

    private String getGroupName() {
        String groupName = config.groupName().trim();

        return groupName.isEmpty() ? null : groupName;
    }
}
