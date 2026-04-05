package org.kkaemok.skkaemok.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NameManager {
    private final ConcurrentHashMap<UUID, String> nicknames;
    private final NameStorage storage;

    public NameManager(NameStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("NameStorage cannot be null");
        }
        this.storage = storage;
        this.nicknames = new ConcurrentHashMap<>(storage.load());
    }

    public String loadNickname(Player player) {
        if (player == null) {
            return null;
        }
        return nicknames.getOrDefault(player.getUniqueId(), player.getName());
    }

    public String getRawNickname(Player player) {
        if (player == null) {
            return null;
        }
        return nicknames.get(player.getUniqueId());
    }

    public void setNickname(Player player, String nickname) {
        if (player == null || nickname == null) {
            return;
        }
        nicknames.put(player.getUniqueId(), nickname);
        saveNow();
    }

    public void resetNickname(Player player) {
        if (player == null) {
            return;
        }
        nicknames.remove(player.getUniqueId());
        saveNow();
    }

    public boolean hasNickname(Player player) {
        if (player == null) {
            return false;
        }
        return nicknames.containsKey(player.getUniqueId());
    }

    public Map<UUID, String> snapshot() {
        return Map.copyOf(nicknames);
    }

    public void reload() {
        nicknames.clear();
        nicknames.putAll(storage.load());
    }

    public void saveNow() {
        storage.save(nicknames);
    }
}
