package org.kkaemok.skkaemok.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.kkaemok.skkaemok.service.NameRewriteService;

import java.util.ArrayList;
import java.util.List;

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
        Player deadPlayer = event.getEntity();
        Player killer = deadPlayer.getKiller();
        Component message = event.deathMessage();
        if (message == null) {
            return;
        }

        List<Player> targets = new ArrayList<>();
        targets.add(deadPlayer);
        if (killer != null && !killer.equals(deadPlayer)) {
            targets.add(killer);
        }
        event.deathMessage(nameRewriteService.rewritePlayerNames(message, targets));
    }
}
