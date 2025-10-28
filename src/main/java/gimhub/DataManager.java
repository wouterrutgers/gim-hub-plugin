package gimhub;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DataManager {
    @Inject
    private GimHubConfig config;

    @Inject
    private HttpRequestService httpRequestService;

    @Inject
    private CollectionLogManager collectionLogManager;

    @Getter
    @Inject
    private StateRepository stateRepository;

    @Inject
    private ApiUrlBuilder apiUrlBuilder;

    private boolean isMemberInGroup = false;
    private int skipNextNAttempts = 0;

    public void submitToApi(String playerName) {
        if (skipNextNAttempts-- > 0) return;

        String groupToken = config.authorizationToken().trim();

        if (groupToken.isEmpty()) return;

        if(!isMemberInGroup) {
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

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", playerName);
        stateRepository.consumeAllStates(updates);
        collectionLogManager.consumeState(updates);

        // We require greater than 1 since name field is automatically included
        if(updates.size() <= 1) {
            log.debug("Skip POST: no changes to send (fields={})", updates.size());
            return;
        }

        HttpRequestService.HttpResponse response = httpRequestService.post(url, groupToken, updates);

        if (!response.isSuccessful()) {
            skipNextNAttempts = 10;
            if (response.getCode() == 422) {
                isMemberInGroup = false;
            }
            stateRepository.restoreAllStates();
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
