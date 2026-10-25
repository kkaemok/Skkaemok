package org.kkaemok.skkaemok.name;

public enum NameChannel {
    NAMETAG("nametag"),
    TABLIST("tablist"),
    CHAT("chat");

    private final String storageKey;

    NameChannel(String storageKey) {
        this.storageKey = storageKey;
    }

    public String storageKey() {
        return storageKey;
    }

    public static NameChannel fromStorageKey(String key) {
        for (NameChannel channel : values()) {
            if (channel.storageKey.equalsIgnoreCase(key)) {
                return channel;
            }
        }
        return null;
    }
}
