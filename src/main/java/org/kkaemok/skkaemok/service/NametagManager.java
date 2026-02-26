package org.kkaemok.skkaemok.service;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;

public final class NametagManager {
    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;

    public NametagManager(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalStateException("Plugin instance cannot be null");
        }
        this.plugin = plugin;
        try {
            this.protocolManager = ProtocolLibrary.getProtocolManager();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize ProtocolLib", e);
        }
    }

    public void updateForAllViewers(Player target, String customName) {
        if (target == null || customName == null) {
            return;
        }
        WrappedChatComponent displayName = toWrappedComponent(Component.text(customName));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updatePlayerNameTag(target, viewer, customName, displayName);
        }
    }

    public void updateForViewer(Player target, Player viewer, String customName) {
        if (target == null || viewer == null || customName == null) {
            return;
        }
        updatePlayerNameTag(target, viewer, customName, toWrappedComponent(Component.text(customName)));
    }

    private void updatePlayerNameTag(Player target, Player viewer, String customName, WrappedChatComponent displayName) {
        if (!target.isOnline() || !viewer.isOnline()) {
            return;
        }

        boolean samePlayer = target.getUniqueId().equals(viewer.getUniqueId());
        if (!samePlayer) {
            viewer.hidePlayer(plugin, target);
        }

        try {
            PacketContainer removePacket = new PacketContainer(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            removePacket.getUUIDLists().write(0, Collections.singletonList(target.getUniqueId()));
            protocolManager.sendServerPacket(viewer, removePacket);

            PacketContainer infoPacket = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
            infoPacket.getPlayerInfoActions().write(0, EnumSet.of(
                    EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                    EnumWrappers.PlayerInfoAction.INITIALIZE_CHAT,
                    EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME,
                    EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE,
                    EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
                    EnumWrappers.PlayerInfoAction.UPDATE_LISTED
            ));

            WrappedGameProfile originalProfile = WrappedGameProfile.fromPlayer(target);
            WrappedGameProfile customProfile = new WrappedGameProfile(originalProfile.getUUID(), customName);
            for (WrappedSignedProperty prop : originalProfile.getProperties().values()) {
                customProfile.getProperties().put(prop.getName(), prop);
            }

            WrappedRemoteChatSessionData chatSession = WrappedRemoteChatSessionData.fromPlayer(target);

            PlayerInfoData playerInfoData = new PlayerInfoData(
                    target.getUniqueId(),
                    target.getPing(),
                    true,
                    EnumWrappers.NativeGameMode.fromBukkit(target.getGameMode()),
                    customProfile,
                    displayName,
                    chatSession
            );

            List<PlayerInfoData> dataList = Collections.singletonList(playerInfoData);
            try {
                infoPacket.getPlayerInfoDataLists().write(0, dataList);
            } catch (Exception ignored) {
                infoPacket.getPlayerInfoDataLists().write(1, dataList);
            }
            protocolManager.sendServerPacket(viewer, infoPacket);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating nametag", e);
        }

        if (!samePlayer) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> viewer.showPlayer(plugin, target), 2L);
        }
    }

    private WrappedChatComponent toWrappedComponent(Component component) {
        String json = GsonComponentSerializer.gson().serialize(component);
        return WrappedChatComponent.fromJson(json);
    }
}
