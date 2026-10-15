package org.kkaemok.skkaemok.service;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinProfileLifecycleTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Player player = mock(Player.class);
    private final SkinManager skins = mock(SkinManager.class);
    private final NametagManager tags = mock(NametagManager.class);
    private final Set<ProfileProperty> original = Set.of(new ProfileProperty("textures", "original", "signed"));
    private final AtomicReference<Set<ProfileProperty>> nativeProperties = new AtomicReference<>(original);
    private final AtomicReference<SkinData> stored = new AtomicReference<>(new SkinData("custom", "signed", "", 0));
    private SkinService service;

    @BeforeEach
    void setup() {
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getName()).thenReturn("skkaemok");
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Steve");
        when(player.isOnline()).thenReturn(true);
        when(player.getPlayerProfile()).thenAnswer(ignored -> profile(nativeProperties.get()));
        when(skins.getRawSkin(player)).thenAnswer(ignored -> stored.get());
        doAnswer(call -> {
            nativeProperties.set(Set.copyOf(call.getArgument(1, PlayerProfile.class).getProperties()));
            return null;
        }).when(tags).updateNativeProfile(eq(player), any());
        doAnswer(call -> {
            nativeProperties.set(Set.copyOf(call.getArgument(0, PlayerProfile.class).getProperties()));
            return null;
        }).when(player).setPlayerProfile(any());
        service = new SkinService(plugin, mock(NameManager.class), skins, tags);
    }

    @Test
    void storedSkinAppliesOnJoinAndStorageResetOnReloadRestoresLoginTextures() {
        service.applyStoredSkin(player);
        assertEquals(Set.of(new ProfileProperty("textures", "custom", "signed")), nativeProperties.get());
        service.reloadConfig();
        stored.set(null);
        service.applyStoredSkin(player);
        assertEquals(original, nativeProperties.get());
        service.applyStoredSkin(player);
        verify(tags, times(2)).updateNativeProfile(eq(player), any());
    }

    @Test
    void replacingStoredSkinRetainsTheFirstOriginalForSubsequentReset() {
        service.applyStoredSkin(player);
        stored.set(new SkinData("replacement", "signed", "", 1));
        service.applyStoredSkin(player);
        assertEquals(Set.of(new ProfileProperty("textures", "replacement", "signed")), nativeProperties.get());
        stored.set(null);
        service.applyStoredSkin(player);
        assertEquals(original, nativeProperties.get());
    }

    @Test
    void disableRestoresServerProfileBeforeAnotherEnableCanCaptureCustomAsOriginal() {
        service.applyStoredSkin(player);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            service.close();
        }
        assertEquals(original, nativeProperties.get());
        service.applyStoredSkin(player);
        verify(tags, times(1)).updateNativeProfile(eq(player), any());
    }

    @Test
    void disableDoesNotOverwriteAProfileReplacedByAnotherPlugin() {
        service.applyStoredSkin(player);
        Set<ProfileProperty> external = Set.of(new ProfileProperty("textures", "external", "other"));
        nativeProperties.set(external);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            service.close();
        }
        assertEquals(external, nativeProperties.get());
        verify(player, never()).setPlayerProfile(any());
    }

    private static PlayerProfile profile(Set<ProfileProperty> values) {
        PlayerProfile profile = mock(PlayerProfile.class);
        Set<ProfileProperty> properties = new HashSet<>(values);
        when(profile.getProperties()).thenReturn(properties);
        doAnswer(call -> properties.removeIf(property -> property.getName().equals(call.getArgument(0))))
                .when(profile).removeProperty(anyString());
        doAnswer(call -> { properties.add(call.getArgument(0)); return null; })
                .when(profile).setProperty(any());
        return profile;
    }
}
