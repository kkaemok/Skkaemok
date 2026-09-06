package org.kkaemok.skkaemok.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.kkaemok.skkaemok.service.ChatNameService;
import org.kkaemok.skkaemok.service.NameManager;
import org.kkaemok.skkaemok.service.NametagManager;
import org.kkaemok.skkaemok.service.SkinManager;
import org.kkaemok.skkaemok.service.TablistNameManager;

public final class PlayerSyncListener implements Listener {
    private final NameManager nameManager;
    private final SkinManager skinManager;
    private final NametagManager nametagManager;
    private final TablistNameManager tablistNameManager;
    private final ChatNameService chatNameService;

    public PlayerSyncListener(NameManager nameManager,
                              SkinManager skinManager,
                              NametagManager nametagManager,
                              TablistNameManager tablistNameManager,
                              ChatNameService chatNameService) {
        if (nameManager == null || skinManager == null || nametagManager == null
                || tablistNameManager == null || chatNameService == null) {
            throw new IllegalArgumentException("Managers cannot be null");
        }
        this.nameManager = nameManager;
        this.skinManager = skinManager;
        this.nametagManager = nametagManager;
        this.tablistNameManager = tablistNameManager;
        this.chatNameService = chatNameService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player viewer = event.getPlayer();
        chatNameService.applyStoredChatName(viewer);
        applyNicknameToJoinMessage(event, viewer);

        for (Player target : Bukkit.getOnlinePlayers()) {
            boolean nicknameActive = nameManager.hasAnyNickname(target);
            boolean skinActive = skinManager.hasAnyCustomSkin(target);
            boolean tablistActive = tablistNameManager.hasAnyTablistName(target);
            boolean managedDecorationActive = nametagManager.hasManagedDecoration(target);
            if (nicknameActive || skinActive || tablistActive || managedDecorationActive) {
                nametagManager.updateForViewer(target, viewer);
            }
        }

        boolean viewerNicknameActive = nameManager.hasAnyNickname(viewer);
        boolean viewerSkinActive = skinManager.hasAnyCustomSkin(viewer);
        boolean viewerTablistActive = tablistNameManager.hasAnyTablistName(viewer);
        boolean viewerManagedDecorationActive = nametagManager.hasManagedDecoration(viewer);
        if (viewerNicknameActive || viewerSkinActive || viewerTablistActive || viewerManagedDecorationActive) {
            nametagManager.updateForAllViewers(viewer);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        applyNicknameToQuitMessage(event, player);
        nametagManager.removePlayer(player);
    }

    private void applyNicknameToJoinMessage(PlayerJoinEvent event, Player player) {
        Component originalMessage = event.joinMessage();
        if (originalMessage == null) {
            return;
        }
        String nickname = nameManager.loadNickname(player);
        if (player.getName().equals(nickname)) {
            return;
        }
        event.joinMessage(replaceOriginalName(originalMessage, player, nickname));
    }

    private void applyNicknameToQuitMessage(PlayerQuitEvent event, Player player) {
        Component originalMessage = event.quitMessage();
        if (originalMessage == null) {
            return;
        }
        String nickname = nameManager.loadNickname(player);
        if (player.getName().equals(nickname)) {
            return;
        }
        event.quitMessage(replaceOriginalName(originalMessage, player, nickname));
    }

    private Component replaceOriginalName(Component message, Player player, String nickname) {
        return message.replaceText(builder ->
                builder.matchLiteral(player.getName())
                        .replacement(Component.text(nickname))
        );
    }
}
