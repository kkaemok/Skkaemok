package org.kkaemok.skkaemok.service;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SkinRequestsTest {
    private final SkinRequests requests = new SkinRequests();
    private final UUID target = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();
    private final SkinRequests.Key key = new SkinRequests.Key(target, viewer);

    @Test
    void newestRequestWinsAndStaleCompletionCannotRemoveIt() {
        UUID first = requests.begin(key);
        UUID second = requests.begin(key);
        assertFalse(requests.current(key, first));
        requests.finish(key, first);
        assertTrue(requests.current(key, second));
    }

    @Test
    void resetCancelsEvenRequestsWhichHaveNotAppliedASkinYet() {
        UUID token = requests.begin(key);
        requests.invalidate(key);
        assertFalse(requests.current(key, token));
    }

    @Test
    void viewerOperationsLeaveOtherViewersAndGlobalRequestsAlone() {
        SkinRequests.Key other = new SkinRequests.Key(target, UUID.randomUUID());
        SkinRequests.Key global = new SkinRequests.Key(target, null);
        UUID otherToken = requests.begin(other);
        UUID globalToken = requests.begin(global);
        requests.invalidate(key);
        assertTrue(requests.current(other, otherToken));
        assertTrue(requests.current(global, globalToken));
    }

    @Test
    void quitInvalidatesBothSidesAndRejoinCannotReuseTokens() {
        UUID token = requests.begin(key);
        requests.remove(viewer);
        assertFalse(requests.current(key, token));
        UUID rejoined = requests.begin(key);
        assertNotEquals(token, rejoined);
        requests.remove(target);
        assertFalse(requests.current(key, rejoined));
    }

    @Test
    void reloadAndDisableInvalidateAllScopes() {
        UUID token = requests.begin(key);
        requests.clear();
        assertFalse(requests.current(key, token));
    }
}
