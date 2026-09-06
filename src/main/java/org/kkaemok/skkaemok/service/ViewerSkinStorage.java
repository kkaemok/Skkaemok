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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class ViewerSkinStorage {
    private static final Type FILE_TYPE = new TypeToken<Map<String, Map<String, SkinData>>>() { }.getType();

    private final JavaPlugin plugin;
    private final Path filePath;
    private final Gson gson;

    public ViewerSkinStorage(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
        this.filePath = plugin.getDataFolder().toPath().resolve("viewer-skins.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public Map<UUID, Map<UUID, SkinData>> load() {
        Map<UUID, Map<UUID, SkinData>> result = new HashMap<>();
        if (!Files.exists(filePath)) {
            return result;
        }
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            Map<String, Map<String, SkinData>> raw = gson.fromJson(json, FILE_TYPE);
            if (raw == null) {
                return result;
            }
            for (var targetEntry : raw.entrySet()) {
                UUID targetId = parseUuid(targetEntry.getKey());
                if (targetId == null || targetEntry.getValue() == null) {
                    continue;
                }
                Map<UUID, SkinData> viewers = new HashMap<>();
                for (var viewerEntry : targetEntry.getValue().entrySet()) {
                    UUID viewerId = parseUuid(viewerEntry.getKey());
                    SkinData skin = sanitize(viewerEntry.getValue());
                    if (viewerId != null && skin != null) {
                        viewers.put(viewerId, skin);
                    }
                }
                if (!viewers.isEmpty()) {
                    result.put(targetId, viewers);
                }
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read viewer-skins.json", e);
        }
        return result;
    }

    public void save(Map<UUID, ? extends Map<UUID, SkinData>> data) {
        try {
            Files.createDirectories(filePath.getParent());
            Map<String, Map<String, SkinData>> raw = new HashMap<>();
            for (var targetEntry : data.entrySet()) {
                Map<String, SkinData> viewers = new HashMap<>();
                for (var viewerEntry : targetEntry.getValue().entrySet()) {
                    SkinData skin = sanitize(viewerEntry.getValue());
                    if (skin != null) {
                        viewers.put(viewerEntry.getKey().toString(), skin);
                    }
                }
                if (!viewers.isEmpty()) {
                    raw.put(targetEntry.getKey().toString(), viewers);
                }
            }
            Files.writeString(filePath, gson.toJson(raw, FILE_TYPE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save viewer-skins.json", e);
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }
    }

    private SkinData sanitize(SkinData skin) {
        if (skin == null || !skin.isValid()) {
            return null;
        }
        if (skin.getSignature() != null && skin.getSignature().isBlank()) {
            return new SkinData(skin.getValue(), null, skin.getSource(), skin.getUpdatedAt());
        }
        return skin;
    }
}
