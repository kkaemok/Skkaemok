package org.kkaemok.skkaemok.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TablistNameManager {
    private final ConcurrentHashMap<UUID, String> tablistNames;
    private final TablistNameStorage storage;
    private final ViewerNameManager viewerNames;

    public TablistNameManager(TablistNameStorage storage) {
        this(storage, null);
    }

    public TablistNameManager(TablistNameStorage storage, ViewerNameManager viewerNames) {
        if (storage == null) {
            throw new IllegalArgumentException("TablistNameStorage cannot be null");
        }
        this.storage = storage;
        this.viewerNames = viewerNames;
        this.tablistNames = new ConcurrentHashMap<>(storage.load());
    }

    public String getRawTablistName(Player player) {
        if (player == null) {
            return null;
        }
        return tablistNames.get(player.getUniqueId());
    }

    public String getRawTablistName(Player player, Player viewer) {
        return viewerNames == null ? null : viewerNames.getRaw(NameChannel.TABLIST, player, viewer);
    }

    public String resolveTablistName(Player player, Player viewer) {
        if (player == null) {
            return null;
        }
        String viewerValue = getRawTablistName(player, viewer);
        if (viewerValue != null) {
            return viewerValue;
        }
        String global = getRawTablistName(player);
        return global != null ? global : player.getName();
    }

    public boolean hasTablistName(Player player) {
        if (player == null) {
            return false;
        }
        return tablistNames.containsKey(player.getUniqueId());
    }

    public boolean hasTablistName(Player player, Player viewer) {
        return viewerNames != null && viewerNames.has(NameChannel.TABLIST, player, viewer);
    }

    public boolean hasAnyTablistName(Player player) {
        return hasTablistName(player) || (viewerNames != null && viewerNames.hasAny(NameChannel.TABLIST, player));
    }

    public void setTablistName(Player player, String tablistName) {
        if (player == null || tablistName == null) {
            return;
        }
        tablistNames.put(player.getUniqueId(), tablistName);
        saveNow();
    }

    public void setTablistName(Player player, Player viewer, String tablistName) {
        if (viewerNames != null) {
            viewerNames.set(NameChannel.TABLIST, player, viewer, tablistName);
        }
    }

    public void resetTablistName(Player player) {
        if (player == null) {
            return;
        }
        tablistNames.remove(player.getUniqueId());
        saveNow();
    }

    public void resetTablistName(Player player, Player viewer) {
        if (viewerNames != null) {
            viewerNames.reset(NameChannel.TABLIST, player, viewer);
        }
    }

    public Map<UUID, String> snapshot() {
        return Map.copyOf(tablistNames);
    }

    public void reload() {
        tablistNames.clear();
        tablistNames.putAll(storage.load());
    }

    public void saveNow() {
        storage.save(tablistNames);
    }
}
