package org.kkaemok.skkaemok.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinManager {
    private final ConcurrentHashMap<UUID, SkinData> skins;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, SkinData>> viewerSkins;
    private final SkinStorage storage;
    private final ViewerSkinStorage viewerStorage;

    public SkinManager(SkinStorage storage) {
        this(storage, null);
    }

    public SkinManager(SkinStorage storage, ViewerSkinStorage viewerStorage) {
        if (storage == null) {
            throw new IllegalArgumentException("SkinStorage cannot be null");
        }
        this.storage = storage;
        this.viewerStorage = viewerStorage;
        this.skins = new ConcurrentHashMap<>(storage.load());
        this.viewerSkins = new ConcurrentHashMap<>();
        loadViewerSkins();
    }

    public SkinData getRawSkin(Player player) {
        if (player == null) {
            return null;
        }
        return skins.get(player.getUniqueId());
    }

    public SkinData getRawSkin(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return skins.get(uuid);
    }

    public SkinData getRawSkin(Player target, Player viewer) {
        if (target == null || viewer == null) {
            return null;
        }
        Map<UUID, SkinData> viewers = viewerSkins.get(target.getUniqueId());
        return viewers == null ? null : viewers.get(viewer.getUniqueId());
    }

    public SkinData resolveSkin(Player target, Player viewer) {
        SkinData viewerSkin = getRawSkin(target, viewer);
        return viewerSkin != null ? viewerSkin : getRawSkin(target);
    }

    public boolean hasCustomSkin(Player player) {
        return getRawSkin(player) != null;
    }

    public boolean hasCustomSkin(Player target, Player viewer) {
        return getRawSkin(target, viewer) != null;
    }

    public boolean hasAnyCustomSkin(Player target) {
        if (hasCustomSkin(target)) {
            return true;
        }
        Map<UUID, SkinData> viewers = target == null ? null : viewerSkins.get(target.getUniqueId());
        return viewers != null && !viewers.isEmpty();
    }

    public void setSkin(Player player, SkinData skinData) {
        if (player == null || skinData == null || !skinData.isValid()) {
            return;
        }
        skins.put(player.getUniqueId(), skinData);
        saveNow();
    }

    public void setSkin(UUID uuid, SkinData skinData) {
        if (uuid == null || skinData == null || !skinData.isValid()) {
            return;
        }
        skins.put(uuid, skinData);
        saveNow();
    }

    public void setSkin(Player target, Player viewer, SkinData skinData) {
        if (target == null || viewer == null || skinData == null || !skinData.isValid()) {
            return;
        }
        viewerSkins.computeIfAbsent(target.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(viewer.getUniqueId(), skinData);
        saveViewerSkins();
    }

    public void resetSkin(Player player) {
        if (player == null) {
            return;
        }
        skins.remove(player.getUniqueId());
        saveNow();
    }

    public void resetSkin(UUID uuid) {
        if (uuid == null) {
            return;
        }
        skins.remove(uuid);
        saveNow();
    }

    public void resetSkin(Player target, Player viewer) {
        if (target == null || viewer == null) {
            return;
        }
        ConcurrentHashMap<UUID, SkinData> viewers = viewerSkins.get(target.getUniqueId());
        if (viewers == null) {
            return;
        }
        viewers.remove(viewer.getUniqueId());
        if (viewers.isEmpty()) {
            viewerSkins.remove(target.getUniqueId(), viewers);
        }
        saveViewerSkins();
    }

    public Map<UUID, SkinData> snapshot() {
        return Map.copyOf(skins);
    }

    public void reload() {
        skins.clear();
        skins.putAll(storage.load());
        viewerSkins.clear();
        loadViewerSkins();
    }

    public void saveNow() {
        storage.save(skins);
        saveViewerSkins();
    }

    private void loadViewerSkins() {
        if (viewerStorage == null) {
            return;
        }
        for (var targetEntry : viewerStorage.load().entrySet()) {
            viewerSkins.put(targetEntry.getKey(), new ConcurrentHashMap<>(targetEntry.getValue()));
        }
    }

    private void saveViewerSkins() {
        if (viewerStorage != null) {
            viewerStorage.save(viewerSkins);
        }
    }
}
