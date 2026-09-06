package org.kkaemok.skkaemok.service;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewerStorageTest {
    @TempDir Path tempDir;

    @Test
    void roundTripsUnicodeViewerNames() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        ViewerNameStorage storage = new ViewerNameStorage(plugin);
        UUID target = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        Map<NameChannel, Map<UUID, Map<UUID, String>>> data = emptyViewerData();
        data.get(NameChannel.NAMETAG).put(target, Map.of(viewer, "깨목😀"));
        data.get(NameChannel.TABLIST).put(target, Map.of(viewer, "玩家"));
        data.get(NameChannel.CHAT).put(target, Map.of(viewer, "éclair"));

        storage.save(data);
        Map<NameChannel, Map<UUID, Map<UUID, String>>> loaded = storage.load();

        assertEquals("깨목😀", loaded.get(NameChannel.NAMETAG).get(target).get(viewer));
        assertEquals("玩家", loaded.get(NameChannel.TABLIST).get(target).get(viewer));
        assertEquals("éclair", loaded.get(NameChannel.CHAT).get(target).get(viewer));
    }

    @Test
    void roundTripsViewerSkins() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        ViewerSkinStorage storage = new ViewerSkinStorage(plugin);
        UUID target = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        SkinData skin = new SkinData("texture", "signature", "test", 123L);

        storage.save(Map.of(target, Map.of(viewer, skin)));
        SkinData loaded = storage.load().get(target).get(viewer);

        assertEquals("texture", loaded.getValue());
        assertEquals("signature", loaded.getSignature());
        assertEquals("test", loaded.getSource());
        assertEquals(123L, loaded.getUpdatedAt());
    }

    private static Map<NameChannel, Map<UUID, Map<UUID, String>>> emptyViewerData() {
        Map<NameChannel, Map<UUID, Map<UUID, String>>> data = new EnumMap<>(NameChannel.class);
        for (NameChannel channel : NameChannel.values()) data.put(channel, new HashMap<>());
        return data;
    }
}
