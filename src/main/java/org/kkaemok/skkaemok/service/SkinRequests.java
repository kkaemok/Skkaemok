package org.kkaemok.skkaemok.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tokens are never reused, including across quit/rejoin and configuration reload. */
final class SkinRequests {
    record Key(UUID target, UUID viewer) { }
    private final Map<Key, UUID> requests = new ConcurrentHashMap<>();

    UUID begin(Key key) {
        UUID token = UUID.randomUUID();
        requests.put(key, token);
        return token;
    }

    boolean current(Key key, UUID token) {
        return token.equals(requests.get(key));
    }

    void finish(Key key, UUID token) {
        requests.remove(key, token);
    }

    void invalidate(Key key) {
        requests.remove(key);
    }

    void remove(UUID player) {
        requests.keySet().removeIf(key -> key.target().equals(player) || player.equals(key.viewer()));
    }

    void clear() {
        requests.clear();
    }
}
