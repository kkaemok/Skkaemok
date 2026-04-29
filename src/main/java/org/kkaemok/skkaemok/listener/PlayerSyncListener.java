package org.kkaemok.skkaemok.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.kkaemok.skkaemok.service.NameManager;
import org.kkaemok.skkaemok.service.NametagManager;
import org.kkaemok.skkaemok.service.SkinData;
import org.kkaemok.skkaemok.service.SkinManager;

public final class PlayerSyncListener implements Listener {
    private final NameManager nameManager;
    private final SkinManager skinManager;
    private final NametagManager nametagManager;

    public PlayerSyncListener(NameManager nameManager, SkinManager skinManager, NametagManager nametagManager) {
        if (nameManager == null || skinManager == null || nametagManager == null) {
            throw new IllegalArgumentException("Managers cannot be null");
        }
        this.nameManager = nameManager;
        this.skinManager = skinManager;
        this.nametagManager = nametagManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player viewer = event.getPlayer();
        applyNicknameToJoinMessage(event, viewer);

        for (Player target : Bukkit.getOnlinePlayers()) {
            String rawNickname = nameManager.getRawNickname(target);
            SkinData skinData = skinManager.getRawSkin(target);
            boolean nicknameActive = rawNickname != null;
            if (nicknameActive || skinData != null) {
                String displayName = nameManager.loadNickname(target);
                nametagManager.updateForViewer(target, viewer, displayName, nicknameActive, skinData);
            }
        }

        String viewerNickname = nameManager.getRawNickname(viewer);
        SkinData viewerSkin = skinManager.getRawSkin(viewer);
        boolean viewerNicknameActive = viewerNickname != null;
        if (viewerNicknameActive || viewerSkin != null) {
            String displayName = nameManager.loadNickname(viewer);
            nametagManager.updateForAllViewers(viewer, displayName, viewerNicknameActive, viewerSkin);
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
