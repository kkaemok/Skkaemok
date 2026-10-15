package org.kkaemok.skkaemok.service;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientPlayerInfoTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final ProtocolManager protocol = mock(ProtocolManager.class);
    private final ClientPlayerInfo.DisplaySender displays = mock(ClientPlayerInfo.DisplaySender.class);
    private final Player target = mock(Player.class);
    private final Player viewer = mock(Player.class);
    private ClientPlayerInfo info;

    @BeforeEach
    void setup() {
        com.comphenix.protocol.utility.MinecraftVersion.setCurrentVersion(
                new com.comphenix.protocol.utility.MinecraftVersion("1.21.8"));
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.getName()).thenReturn("Steve");
        when(target.isOnline()).thenReturn(true);
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.canSee(target)).thenReturn(true);
        when(target.isVisibleByDefault()).thenReturn(true);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getProperties()).thenReturn(Set.of(new ProfileProperty("textures", "native", "signature")));
        when(target.getPlayerProfile()).thenReturn(profile);
        try (var reflection = mockStatic(com.comphenix.protocol.utility.MinecraftReflection.class)) {
            info = new ClientPlayerInfo(plugin, protocol, displays);
        }
    }

    @Test
    void displayOnlyPacketDoesNotRebuildInfoOrChangeVisibilityOrListing() {
        when(viewer.isListed(target)).thenReturn(false);
        var name = net.kyori.adventure.text.Component.text("Private");
        info.apply(target, viewer, "Steve", name, null);
        verify(displays).send(target, viewer, name);
        verify(protocol, never()).createPacket(any());
        verify(viewer, never()).listPlayer(any());
        verify(viewer, never()).unlistPlayer(any());
        verify(viewer, never()).hidePlayer(any(), any());
        verify(viewer, never()).showPlayer(any(), any());
        info.apply(target, viewer, "Steve", name, null);
        verify(displays, times(1)).send(target, viewer, name);
    }

    @Test
    void miniMessageDisplayChangesUseOnlyTheDisplaySenderAndKeepTheComponent() {
        var first = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red><bold>PRIVATE</bold></red>");
        var latest = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gold>NEWEST</gold>");
        info.apply(target, viewer, "Steve", first, null);
        info.apply(target, viewer, "Steve", latest, null);
        info.apply(target, viewer, "Steve", latest, null);
        verify(displays).send(target, viewer, first);
        verify(displays, times(1)).send(target, viewer, latest);
        verify(viewer, never()).hidePlayer(any(), any());
        verify(viewer, never()).showPlayer(any(), any());
        verify(protocol, never()).createPacket(any());
    }

    @Test
    void copyingInfoPreservesListingLatencyGamemodeOrderHatAndChatSession() {
        var data = new PlayerInfoData(target.getUniqueId(), 137, false,
                EnumWrappers.NativeGameMode.SPECTATOR, null, null, false, 42,
                (com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData) null);
        var copy = ClientPlayerInfo.copy(data, null, null, data.isShowHat(), data.getListOrder());
        assertEquals(data.getProfileId(), copy.getProfileId());
        assertEquals(137, copy.getLatency());
        assertFalse(copy.isListed());
        assertEquals(EnumWrappers.NativeGameMode.SPECTATOR, copy.getGameMode());
        assertEquals(42, copy.getListOrder());
        assertFalse(copy.isShowHat());
        assertSame(data.getRemoteChatSessionData(), copy.getRemoteChatSessionData());
    }

    @Test
    void nicknameSetReplaceResetRefreshOnceEachAndNeverRefreshUnrelatedViewer() {
        info.apply(target, viewer, "First", null, null);
        info.apply(target, viewer, "First", null, null);
        info.apply(target, viewer, "Second", null, null);
        info.apply(target, viewer, "Steve", null, null);
        verify(viewer, times(3)).hidePlayer(plugin, target);
        verify(viewer, times(3)).showPlayer(plugin, target);
        verify(protocol, never()).updateEntity(any(), any());
    }

    @Test
    void skinsSetReplaceResetRefreshWithoutNicknameChange() {
        info.apply(target, viewer, "Steve", null, new SkinData("first", "s", "one", 1));
        info.apply(target, viewer, "Steve", null, new SkinData("first", "s", "two", 2));
        info.apply(target, viewer, "Steve", null, new SkinData("second", "s", "two", 3));
        info.apply(target, viewer, "Steve", null, null);
        verify(viewer, times(3)).hidePlayer(plugin, target);
        verify(viewer, times(3)).showPlayer(plugin, target);
    }

    @Test
    void hiddenPlayersAreNeverAddedBackAndDisconnectClearsPreviouslyAppliedIdentity() {
        when(viewer.canSee(target)).thenReturn(false);
        info.apply(target, viewer, "Private", null, null);
        verify(protocol, never()).sendServerPacket(any(Player.class), any(PacketContainer.class));
        verify(viewer, never()).showPlayer(any(), any());
        when(viewer.canSee(target)).thenReturn(true);
        info.apply(target, viewer, "Private", null, null);
        info.remove(target.getUniqueId());
        clearInvocations(viewer, protocol);
        info.apply(target, viewer, "Steve", null, null);
        verify(viewer, never()).hidePlayer(any(), any());
        verifyNoInteractions(displays);
    }

    @Test
    void closeUnregistersListener() {
        info.close();
        verify(protocol).removePacketListener(any());
    }
}
