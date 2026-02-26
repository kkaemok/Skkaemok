package org.kkaemok.skkaemok.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.kkaemok.skkaemok.service.NameManager;

public final class AdvancementListener implements Listener {
    private final NameManager nameManager;

    public AdvancementListener(NameManager nameManager) {
        if (nameManager == null) {
            throw new IllegalArgumentException("NameManager cannot be null");
        }
        this.nameManager = nameManager;
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        Component originalMessage = event.message();
        if (originalMessage == null) {
            return;
        }

        Player player = event.getPlayer();
        String nickname = nameManager.loadNickname(player);
        Component modifiedMessage = originalMessage.replaceText(builder ->
                builder.matchLiteral(player.getName())
                        .replacement(Component.text(nickname))
        );

        Bukkit.getServer().sendMessage(modifiedMessage);
        event.message(null);
    }
}
