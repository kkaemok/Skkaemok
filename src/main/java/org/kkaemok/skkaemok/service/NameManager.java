package org.kkaemok.skkaemok.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NameManager {
    private final ConcurrentHashMap<UUID, String> nicknames;
    private final NameStorage storage;
    private final ViewerNameManager viewerNames;

    public NameManager(NameStorage storage) {
        this(storage, null);
    }

    public NameManager(NameStorage storage, ViewerNameManager viewerNames) {
        if (storage == null) {
            throw new IllegalArgumentException("NameStorage cannot be null");
        }
        this.storage = storage;
        this.viewerNames = viewerNames;
        this.nicknames = new ConcurrentHashMap<>(storage.load());
    }

    public String loadNickname(Player player) {
        if (player == null) {
            return null;
        }
        return nicknames.getOrDefault(player.getUniqueId(), player.getName());
    }

    public String loadNickname(Player player, Player viewer) {
        if (player == null) {
            return null;
        }
        String viewerValue = getRawNickname(player, viewer);
        return viewerValue != null ? viewerValue : loadNickname(player);
    }

    public String getRawNickname(Player player, Player viewer) {
        return viewerNames == null ? null : viewerNames.getRaw(NameChannel.NAMETAG, player, viewer);
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

    public void setNickname(Player player, Player viewer, String nickname) {
        if (viewerNames != null) {
            viewerNames.set(NameChannel.NAMETAG, player, viewer, nickname);
        }
    }

    public void resetNickname(Player player) {
        if (player == null) {
            return;
        }
        nicknames.remove(player.getUniqueId());
        saveNow();
    }

    public void resetNickname(Player player, Player viewer) {
        if (viewerNames != null) {
            viewerNames.reset(NameChannel.NAMETAG, player, viewer);
        }
    }

    public boolean hasNickname(Player player) {
        if (player == null) {
            return false;
        }
        return nicknames.containsKey(player.getUniqueId());
    }

    public boolean hasNickname(Player player, Player viewer) {
        return viewerNames != null && viewerNames.has(NameChannel.NAMETAG, player, viewer);
    }

    public boolean hasAnyNickname(Player player) {
        return hasNickname(player) || (viewerNames != null && viewerNames.hasAny(NameChannel.NAMETAG, player));
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
