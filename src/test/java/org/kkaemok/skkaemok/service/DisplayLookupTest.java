package org.kkaemok.skkaemok.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DisplayLookupTest {
    @Test
    void lookupUsesTheViewersEffectiveNameAndRejectsAmbiguity() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Player first = player(firstId, "Steve");
        Player second = player(secondId, "Sam");
        Player alex = player(UUID.randomUUID(), "Alex");
        Player bob = player(UUID.randomUUID(), "Bob");

        ViewerNameStorage viewerStorage = mock(ViewerNameStorage.class);
        when(viewerStorage.load()).thenReturn(emptyViewerData());
        ViewerNameManager viewerNames = new ViewerNameManager(viewerStorage);
        NameStorage globalStorage = mock(NameStorage.class);
        when(globalStorage.load()).thenReturn(Map.of(firstId, "깨목", secondId, "Other"));
        NameManager manager = new NameManager(globalStorage, viewerNames);
        manager.setNickname(first, alex, "Admin");
        NicknameService service = new NicknameService(
                manager,
                mock(NametagManager.class),
                mock(SkinManager.class)
        );

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(first, second));
            assertSame(first, service.findUniquePlayer("Admin", alex));
            assertSame(first, service.findUniquePlayer("깨목", bob));

            manager.setNickname(second, "깨목");
            assertNull(service.findUniquePlayer("깨목", bob));
            assertNull(service.findUniquePlayer("missing", alex));
        }
    }

    private static Map<NameChannel, Map<UUID, Map<UUID, String>>> emptyViewerData() {
        Map<NameChannel, Map<UUID, Map<UUID, String>>> data = new EnumMap<>(NameChannel.class);
        for (NameChannel channel : NameChannel.values()) data.put(channel, new HashMap<>());
        return data;
    }

    private static Player player(UUID uuid, String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        return player;
    }
}
