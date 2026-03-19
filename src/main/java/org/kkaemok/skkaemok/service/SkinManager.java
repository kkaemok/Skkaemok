package org.kkaemok.skkaemok.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinManager {
    private final ConcurrentHashMap<UUID, SkinData> skins;
    private final SkinStorage storage;

    public SkinManager(SkinStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("SkinStorage cannot be null");
        }
        this.storage = storage;
        this.skins = new ConcurrentHashMap<>(storage.load());
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

    public Map<UUID, SkinData> snapshot() {
        return Map.copyOf(skins);
    }

    public void saveNow() {
        storage.save(skins);
    }
}
