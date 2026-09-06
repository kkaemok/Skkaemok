package org.kkaemok.skkaemok.service;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewerSkinResolutionTest {
    @Test
    void resolvesViewerSkinThenGlobalSkinAndTracksOverridesSeparately() {
        UUID targetId = UUID.randomUUID();
        SkinData global = new SkinData("global", "sig", "global", 1L);
        SkinData override = new SkinData("viewer", "sig", "viewer", 2L);
        SkinStorage globalStorage = mock(SkinStorage.class);
        ViewerSkinStorage viewerStorage = mock(ViewerSkinStorage.class);
        when(globalStorage.load()).thenReturn(new HashMap<>(Map.of(targetId, global)));
        when(viewerStorage.load()).thenReturn(new HashMap<>());
        SkinManager manager = new SkinManager(globalStorage, viewerStorage);
        Player target = player(targetId);
        Player alex = player(UUID.randomUUID());
        Player bob = player(UUID.randomUUID());

        assertSame(global, manager.resolveSkin(target, alex));
        assertTrue(manager.hasCustomSkin(target));
        assertFalse(manager.hasCustomSkin(target, alex));

        manager.setSkin(target, alex, override);
        assertSame(override, manager.resolveSkin(target, alex));
        assertSame(global, manager.resolveSkin(target, bob));
        assertTrue(manager.hasCustomSkin(target, alex));

        manager.resetSkin(target, alex);
        assertSame(global, manager.resolveSkin(target, alex));
        assertFalse(manager.hasCustomSkin(target, alex));
    }

    private static Player player(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }
}
