package org.kkaemok.skkaemok.storage;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import org.kkaemok.skkaemok.appearance.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class AppearanceStorage {
    private static final Type PRESETS = new TypeToken<Map<String, Appearance>>() {}.getType();
    private static final Type RECOVERY =
            new TypeToken<List<TimedOverrides.Recovery>>() {}.getType();
    private final Path folder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public AppearanceStorage(Path folder) {
        this.folder = folder;
    }

    public Map<String, Appearance> presets() throws IOException {
        return read("appearance-presets.json", PRESETS, new LinkedHashMap<>());
    }

    public List<TimedOverrides.Recovery> recovery() throws IOException {
        return read("temporary-appearances.json", RECOVERY, List.of());
    }

    public void savePresets(Map<String, Appearance> presets) throws IOException {
        AtomicJsonFile.write(
                folder.resolve("appearance-presets.json"), gson.toJson(presets, PRESETS));
    }

    public void saveRecovery(List<TimedOverrides.Recovery> recovery) throws IOException {
        AtomicJsonFile.write(
                folder.resolve("temporary-appearances.json"), gson.toJson(recovery, RECOVERY));
    }

    private <T> T read(String file, Type type, T empty) throws IOException {
        Path path = folder.resolve(file);
        if (!Files.exists(path)) return empty;
        try {
            T result = gson.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
            if (result == null) throw new IOException("Empty appearance storage: " + file);
            return result;
        } catch (JsonParseException | IllegalArgumentException ex) {
            throw new IOException("Invalid appearance storage: " + file, ex);
        }
    }
}
