package org.kkaemok.skkaemok.service;

import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ViewerNameManager {
    private final ViewerNameStorage storage;
    private final EnumMap<NameChannel, ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, String>>> values;

    public ViewerNameManager(ViewerNameStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("Storage cannot be null");
        }
        this.storage = storage;
        this.values = new EnumMap<>(NameChannel.class);
        for (NameChannel channel : NameChannel.values()) {
            values.put(channel, new ConcurrentHashMap<>());
        }
        replaceWith(storage.load());
    }

    public String getRaw(NameChannel channel, Player target, Player viewer) {
        if (channel == null || target == null || viewer == null) {
            return null;
        }
        Map<UUID, String> viewers = values.get(channel).get(target.getUniqueId());
        return viewers == null ? null : viewers.get(viewer.getUniqueId());
    }

    public boolean has(NameChannel channel, Player target, Player viewer) {
        return getRaw(channel, target, viewer) != null;
    }

    public boolean hasAny(NameChannel channel, Player target) {
        if (channel == null || target == null) {
            return false;
        }
        Map<UUID, String> viewers = values.get(channel).get(target.getUniqueId());
        return viewers != null && !viewers.isEmpty();
    }

    public void set(NameChannel channel, Player target, Player viewer, String value) {
        if (channel == null || target == null || viewer == null || value == null || value.isBlank()) {
            return;
        }
        values.get(channel)
                .computeIfAbsent(target.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(viewer.getUniqueId(), value);
        saveNow();
    }

    public void reset(NameChannel channel, Player target, Player viewer) {
        if (channel == null || target == null || viewer == null) {
            return;
        }
        ConcurrentHashMap<UUID, String> viewers = values.get(channel).get(target.getUniqueId());
        if (viewers == null) {
            return;
        }
        viewers.remove(viewer.getUniqueId());
        if (viewers.isEmpty()) {
            values.get(channel).remove(target.getUniqueId(), viewers);
        }
        saveNow();
    }

    public void reload() {
        replaceWith(storage.load());
    }

    public void saveNow() {
        storage.save(values);
    }

    private void replaceWith(Map<NameChannel, Map<UUID, Map<UUID, String>>> loaded) {
        for (NameChannel channel : NameChannel.values()) {
            ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, String>> channelValues = values.get(channel);
            channelValues.clear();
            Map<UUID, Map<UUID, String>> loadedChannel = loaded.get(channel);
            if (loadedChannel == null) {
                continue;
            }
            for (var targetEntry : loadedChannel.entrySet()) {
                channelValues.put(targetEntry.getKey(), new ConcurrentHashMap<>(targetEntry.getValue()));
            }
        }
    }
}
