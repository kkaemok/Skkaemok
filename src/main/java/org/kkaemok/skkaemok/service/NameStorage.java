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

public final class NameStorage {
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private final JavaPlugin plugin;
    private final Path filePath;
    private final Gson gson;

    public NameStorage(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
        this.filePath = plugin.getDataFolder().toPath().resolve("nicknames.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public Map<UUID, String> load() {
        if (!Files.exists(filePath)) {
            return new HashMap<>();
        }
        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            Map<String, String> raw = gson.fromJson(json, MAP_TYPE);
            if (raw == null || raw.isEmpty()) {
                return new HashMap<>();
            }
            Map<UUID, String> result = new HashMap<>();
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    if (entry.getValue() != null && !entry.getValue().isBlank()) {
                        result.put(uuid, entry.getValue());
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid UUID entries.
                }
            }
            return result;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read nicknames.json", e);
            return new HashMap<>();
        }
    }

    public void save(Map<UUID, String> data) {
        try {
            Files.createDirectories(filePath.getParent());
            Map<String, String> raw = new HashMap<>();
            for (Map.Entry<UUID, String> entry : data.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    raw.put(entry.getKey().toString(), entry.getValue());
                }
            }
            String json = gson.toJson(raw, MAP_TYPE);
            Files.writeString(filePath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save nicknames.json", e);
        }
    }
}
