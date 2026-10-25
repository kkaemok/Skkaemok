package org.kkaemok.skkaemok.name;

import org.bukkit.entity.Player;
import org.kkaemok.skkaemok.storage.ViewerNameStorage;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ViewerNameManager {
    private Runnable changeListener = () -> {};

    public void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {} : listener;
    }

    private final ViewerNameStorage storage;
    private final EnumMap<NameChannel, ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, String>>>
            values;

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
        if (channel == null
                || target == null
                || viewer == null
                || value == null
                || value.isBlank()) {
            return;
        }
        String previous =
                values.get(channel)
                        .computeIfAbsent(target.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                        .put(viewer.getUniqueId(), value);
        if (value.equals(previous)) return;
        saveNow();
        changeListener.run();
    }

    public void reset(NameChannel channel, Player target, Player viewer) {
        if (channel == null || target == null || viewer == null) {
            return;
        }
        ConcurrentHashMap<UUID, String> viewers = values.get(channel).get(target.getUniqueId());
        if (viewers == null) {
            return;
        }
        if (viewers.remove(viewer.getUniqueId()) == null) return;
        if (viewers.isEmpty()) {
            values.get(channel).remove(target.getUniqueId(), viewers);
        }
        saveNow();
        changeListener.run();
    }

    public void reload() {
        replaceWith(storage.load());
        changeListener.run();
    }

    public void saveNow() {
        storage.save(values);
    }

    private void replaceWith(Map<NameChannel, Map<UUID, Map<UUID, String>>> loaded) {
        for (NameChannel channel : NameChannel.values()) {
            ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, String>> channelValues =
                    values.get(channel);
            channelValues.clear();
            Map<UUID, Map<UUID, String>> loadedChannel = loaded.get(channel);
            if (loadedChannel == null) {
                continue;
            }
            for (var targetEntry : loadedChannel.entrySet()) {
                channelValues.put(
                        targetEntry.getKey(), new ConcurrentHashMap<>(targetEntry.getValue()));
            }
        }
    }

    public String raw(NameChannel channel, UUID target, UUID viewer) {
        Map<UUID, String> entries = values.get(channel).get(target);
        return entries == null ? null : entries.get(viewer);
    }

    public void store(NameChannel channel, UUID target, UUID viewer, String value) {
        var entries = values.get(channel);
        if (value == null) {
            var viewers = entries.get(target);
            if (viewers != null) {
                viewers.remove(viewer);
                if (viewers.isEmpty()) entries.remove(target, viewers);
            }
        } else
            entries.computeIfAbsent(target, ignored -> new ConcurrentHashMap<>())
                    .put(viewer, value);
        try {
            storage.saveChecked(values);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
        changeListener.run();
    }
}
