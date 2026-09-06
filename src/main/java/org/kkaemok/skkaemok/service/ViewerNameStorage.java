package org.kkaemok.skkaemok.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class ViewerNameStorage {
    private static final Type FILE_TYPE = new TypeToken<Map<String, Map<String, Map<String, String>>>>() { }.getType();

    private final JavaPlugin plugin;
    private final Path filePath;
    private final Gson gson;

    public ViewerNameStorage(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
        this.filePath = plugin.getDataFolder().toPath().resolve("viewer-names.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public Map<NameChannel, Map<UUID, Map<UUID, String>>> load() {
        Map<NameChannel, Map<UUID, Map<UUID, String>>> result = emptyData();
        if (!Files.exists(filePath)) {
            return result;
        }
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            Map<String, Map<String, Map<String, String>>> raw = gson.fromJson(json, FILE_TYPE);
            if (raw == null) {
                return result;
            }
            for (var channelEntry : raw.entrySet()) {
                NameChannel channel = NameChannel.fromStorageKey(channelEntry.getKey());
                if (channel == null || channelEntry.getValue() == null) {
                    continue;
                }
                Map<UUID, Map<UUID, String>> targets = result.get(channel);
                for (var targetEntry : channelEntry.getValue().entrySet()) {
                    UUID targetId = parseUuid(targetEntry.getKey());
                    if (targetId == null || targetEntry.getValue() == null) {
                        continue;
                    }
                    Map<UUID, String> viewers = new HashMap<>();
                    for (var viewerEntry : targetEntry.getValue().entrySet()) {
                        UUID viewerId = parseUuid(viewerEntry.getKey());
                        String value = normalize(viewerEntry.getValue());
                        if (viewerId != null && value != null) {
                            viewers.put(viewerId, value);
                        }
                    }
                    if (!viewers.isEmpty()) {
                        targets.put(targetId, viewers);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read viewer-names.json", e);
        }
        return result;
    }

    public void save(Map<NameChannel, ? extends Map<UUID, ? extends Map<UUID, String>>> data) {
        try {
            Files.createDirectories(filePath.getParent());
            Map<String, Map<String, Map<String, String>>> raw = new HashMap<>();
            for (NameChannel channel : NameChannel.values()) {
                Map<String, Map<String, String>> targets = new HashMap<>();
                Map<UUID, ? extends Map<UUID, String>> channelData = data.get(channel);
                if (channelData != null) {
                    for (var targetEntry : channelData.entrySet()) {
                        Map<String, String> viewers = new HashMap<>();
                        for (var viewerEntry : targetEntry.getValue().entrySet()) {
                            String value = normalize(viewerEntry.getValue());
                            if (value != null) {
                                viewers.put(viewerEntry.getKey().toString(), value);
                            }
                        }
                        if (!viewers.isEmpty()) {
                            targets.put(targetEntry.getKey().toString(), viewers);
                        }
                    }
                }
                raw.put(channel.storageKey(), targets);
            }
            Files.writeString(filePath, gson.toJson(raw, FILE_TYPE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save viewer-names.json", e);
        }
    }

    private Map<NameChannel, Map<UUID, Map<UUID, String>>> emptyData() {
        Map<NameChannel, Map<UUID, Map<UUID, String>>> result = new EnumMap<>(NameChannel.class);
        for (NameChannel channel : NameChannel.values()) {
            result.put(channel, new HashMap<>());
        }
        return result;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
