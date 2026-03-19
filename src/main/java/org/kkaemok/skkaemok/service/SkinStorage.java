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

public final class SkinStorage {
    private static final Type MAP_TYPE = new TypeToken<Map<String, SkinData>>() {}.getType();

    private final JavaPlugin plugin;
    private final Path filePath;
    private final Gson gson;

    public SkinStorage(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
        this.filePath = plugin.getDataFolder().toPath().resolve("skins.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public Map<UUID, SkinData> load() {
        if (!Files.exists(filePath)) {
            return new HashMap<>();
        }
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            Map<String, SkinData> raw = gson.fromJson(json, MAP_TYPE);
            if (raw == null || raw.isEmpty()) {
                return new HashMap<>();
            }
            Map<UUID, SkinData> result = new HashMap<>();
            for (Map.Entry<String, SkinData> entry : raw.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    SkinData skin = sanitize(entry.getValue());
                    if (skin != null) {
                        result.put(uuid, skin);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid UUID entries.
                }
            }
            return result;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read skins.json", e);
            return new HashMap<>();
        }
    }

    public void save(Map<UUID, SkinData> data) {
        try {
            Files.createDirectories(filePath.getParent());
            Map<String, SkinData> raw = new HashMap<>();
            for (Map.Entry<UUID, SkinData> entry : data.entrySet()) {
                SkinData skin = sanitize(entry.getValue());
                if (skin != null) {
                    raw.put(entry.getKey().toString(), skin);
                }
            }
            String json = gson.toJson(raw, MAP_TYPE);
            Files.writeString(filePath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save skins.json", e);
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
