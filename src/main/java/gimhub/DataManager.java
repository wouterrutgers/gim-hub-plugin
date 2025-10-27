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

        if (!ensureMembershipChecked(groupToken, playerName)) {
            return;
        }

        String url = apiUrlBuilder.getUpdateGroupMemberUrl();
        if (url == null) {
            log.debug("Skip POST: URL is null (check base URL and group name).");
            return;
        }

        Map<String, Object> updates = buildUpdates(playerName);

        if (updates.size() > 1) {
            sendUpdates(url, groupToken, updates);
        } else {
            log.debug("Skip POST: no changes to send (fields={})", updates.size());
        }
    }

    private boolean ensureMembershipChecked(String groupToken, String playerName) {
        if (isMemberInGroup) return true;

        boolean isMember = checkIfPlayerIsInGroup(groupToken, playerName);
        if (!isMember) {
            log.debug("Skip POST: not a member (422/unprocessable entity). Backing off.");
            skipNextNAttempts = 10;

            return false;
        }

        isMemberInGroup = true;

        return true;
    }

    private Map<String, Object> buildUpdates(String playerName) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", playerName);
        stateRepository.consumeAllStates(updates);
        collectionLogManager.consumeState(updates);

        return updates;
    }

    private void sendUpdates(String url, String groupToken, Map<String, Object> updates) {
        HttpRequestService.HttpResponse response = httpRequestService.post(url, groupToken, updates);

        if (!response.isSuccessful()) {
            handleFailedSubmission(response);
        } else {
            collectionLogManager.clearClogItems();
        }
    }

    private void handleFailedSubmission(HttpRequestService.HttpResponse response) {
        skipNextNAttempts = 10;
        if (response.getCode() == 422) {
            isMemberInGroup = false;
        }
        stateRepository.restoreAllStates();
    }

    private boolean checkIfPlayerIsInGroup(String groupToken, String playerName) {
        String url = apiUrlBuilder.getMembershipCheckUrl(playerName);
        if (url == null) return false;

        HttpRequestService.HttpResponse response = httpRequestService.get(url, groupToken);

        return response.isSuccessful();
    }
}
