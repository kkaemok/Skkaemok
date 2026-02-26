package org.kkaemok.skkaemok.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.kkaemok.skkaemok.service.NameManager;
import org.kkaemok.skkaemok.service.NametagManager;

public final class PlayerSyncListener implements Listener {
    private final NameManager nameManager;
    private final NametagManager nametagManager;

    public PlayerSyncListener(NameManager nameManager, NametagManager nametagManager) {
        if (nameManager == null || nametagManager == null) {
            throw new IllegalArgumentException("Managers cannot be null");
        }
        this.nameManager = nameManager;
        this.nametagManager = nametagManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player viewer = event.getPlayer();

        for (Player target : Bukkit.getOnlinePlayers()) {
            String nickname = nameManager.getRawNickname(target);
            if (nickname != null) {
                nametagManager.updateForViewer(target, viewer, nickname);
            }
        }

        String viewerNickname = nameManager.getRawNickname(viewer);
        if (viewerNickname != null) {
            nametagManager.updateForAllViewers(viewer, viewerNickname);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // No-op for now, kept for future cleanup or persistence.
    }
}
