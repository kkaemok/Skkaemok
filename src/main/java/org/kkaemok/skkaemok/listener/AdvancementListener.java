package org.kkaemok.skkaemok.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.kkaemok.skkaemok.service.NameRewriteService;

public final class AdvancementListener implements Listener {
    private final NameRewriteService nameRewriteService;

    public AdvancementListener(NameRewriteService nameRewriteService) {
        if (nameRewriteService == null) {
            throw new IllegalArgumentException("NameRewriteService cannot be null");
        }
        this.nameRewriteService = nameRewriteService;
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        Component originalMessage = event.message();
        if (originalMessage == null) {
            return;
        }

        event.message(nameRewriteService.rewriteOnlinePlayerNames(originalMessage));
    }
}
