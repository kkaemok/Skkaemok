package org.kkaemok.skkaemok.service;

public enum NameChannel {
    NAMETAG("nametag"),
    TABLIST("tablist"),
    CHAT("chat");

    private final String storageKey;

    NameChannel(String storageKey) {
        this.storageKey = storageKey;
    }

    String storageKey() {
        return storageKey;
    }

    static NameChannel fromStorageKey(String key) {
        for (NameChannel channel : values()) {
            if (channel.storageKey.equalsIgnoreCase(key)) {
                return channel;
            }
        }
        return null;
    }
}
