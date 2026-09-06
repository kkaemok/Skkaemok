package org.kkaemok.skkaemok.service;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewerNameResolutionTest {
    @Test
    void resolvesViewerThenGlobalThenOriginalAndTracksActualOverridesSeparately() {
        ViewerNameStorage viewerStorage = mock(ViewerNameStorage.class);
        when(viewerStorage.load()).thenReturn(emptyViewerData());
        ViewerNameManager viewerNames = new ViewerNameManager(viewerStorage);

        NameStorage globalStorage = mock(NameStorage.class);
        UUID targetId = UUID.randomUUID();
        when(globalStorage.load()).thenReturn(Map.of(targetId, "깨목"));
        NameManager names = new NameManager(globalStorage, viewerNames);

        Player target = player(targetId, "Steve");
        Player alex = player(UUID.randomUUID(), "Alex");
        Player bob = player(UUID.randomUUID(), "Bob");

        assertEquals("깨목", names.loadNickname(target));
        assertEquals("깨목", names.loadNickname(target, alex));
        assertTrue(names.hasNickname(target));
        assertFalse(names.hasNickname(target, alex));

        names.setNickname(target, alex, "Admin");
        assertEquals("Admin", names.loadNickname(target, alex));
        assertEquals("깨목", names.loadNickname(target, bob));
        assertTrue(names.hasNickname(target, alex));

        names.resetNickname(target, alex);
        assertEquals("깨목", names.loadNickname(target, alex));
        assertFalse(names.hasNickname(target, alex));

        names.resetNickname(target);
        assertEquals("Steve", names.loadNickname(target, bob));
        assertNull(names.getRawNickname(target));
    }

    @Test
    void keepsNameChannelsIndependent() {
        ViewerNameStorage viewerStorage = mock(ViewerNameStorage.class);
        when(viewerStorage.load()).thenReturn(emptyViewerData());
        ViewerNameManager names = new ViewerNameManager(viewerStorage);
        Player target = player(UUID.randomUUID(), "Steve");
        Player viewer = player(UUID.randomUUID(), "Alex");

        names.set(NameChannel.NAMETAG, target, viewer, "玩家");
        names.set(NameChannel.TABLIST, target, viewer, "テスト");
        names.set(NameChannel.CHAT, target, viewer, "😀");

        assertEquals("玩家", names.getRaw(NameChannel.NAMETAG, target, viewer));
        assertEquals("テスト", names.getRaw(NameChannel.TABLIST, target, viewer));
        assertEquals("😀", names.getRaw(NameChannel.CHAT, target, viewer));
    }

    @Test
    void tablistAndChatManagersUseTheSameViewerGlobalOriginalOrder() {
        ViewerNameStorage viewerStorage = mock(ViewerNameStorage.class);
        when(viewerStorage.load()).thenReturn(emptyViewerData());
        ViewerNameManager viewerNames = new ViewerNameManager(viewerStorage);
        UUID targetId = UUID.randomUUID();
        Player target = player(targetId, "Steve");
        Player alex = player(UUID.randomUUID(), "Alex");
        Player bob = player(UUID.randomUUID(), "Bob");

        TablistNameStorage tabStorage = mock(TablistNameStorage.class);
        when(tabStorage.load()).thenReturn(Map.of(targetId, "테스트"));
        TablistNameManager tabNames = new TablistNameManager(tabStorage, viewerNames);
        ChatNameStorage chatStorage = mock(ChatNameStorage.class);
        when(chatStorage.load()).thenReturn(Map.of(targetId, "玩家"));
        ChatNameManager chatNames = new ChatNameManager(chatStorage, viewerNames);

        tabNames.setTablistName(target, alex, "Admin");
        chatNames.setChatName(target, alex, "éclair");

        assertEquals("Admin", tabNames.resolveTablistName(target, alex));
        assertEquals("테스트", tabNames.resolveTablistName(target, bob));
        assertEquals("éclair", chatNames.resolveChatName(target, alex));
        assertEquals("玩家", chatNames.resolveChatName(target, bob));
        assertTrue(tabNames.hasTablistName(target, alex));
        assertFalse(tabNames.hasTablistName(target, bob));
        assertTrue(chatNames.hasChatName(target, alex));
        assertFalse(chatNames.hasChatName(target, bob));

        tabNames.resetTablistName(target);
        chatNames.resetChatName(target);
        assertEquals("Steve", tabNames.resolveTablistName(target, bob));
        assertEquals("Steve", chatNames.resolveChatName(target, bob));
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
