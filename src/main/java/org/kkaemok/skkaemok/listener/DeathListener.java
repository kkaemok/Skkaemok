package org.kkaemok.skkaemok.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.kkaemok.skkaemok.service.NameRewriteService;

public final class DeathListener implements Listener {
    private final NameRewriteService nameRewriteService;

    public DeathListener(NameRewriteService nameRewriteService) {
        if (nameRewriteService == null) {
            throw new IllegalArgumentException("NameRewriteService cannot be null");
        }
        this.nameRewriteService = nameRewriteService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Component message = event.deathMessage();
        if (message == null) {
            return;
        }

        event.deathMessage(nameRewriteService.rewriteOnlinePlayerNames(message));
    }
}
