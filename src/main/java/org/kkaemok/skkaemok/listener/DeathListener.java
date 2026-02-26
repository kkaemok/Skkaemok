package org.kkaemok.skkaemok.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.kkaemok.skkaemok.service.NameManager;

public final class DeathListener implements Listener {
    private final NameManager nameManager;

    public DeathListener(NameManager nameManager) {
        if (nameManager == null) {
            throw new IllegalArgumentException("NameManager cannot be null");
        }
        this.nameManager = nameManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Component message = event.deathMessage();
        if (message == null) {
            return;
        }

        Player deadPlayer = event.getEntity();
        String deadNick = nameManager.loadNickname(deadPlayer);
        Component result = message.replaceText(builder ->
                builder.matchLiteral(deadPlayer.getName())
                        .replacement(Component.text(deadNick))
        );

        Player killer = deadPlayer.getKiller();
        if (killer != null) {
            String killerNick = nameManager.loadNickname(killer);
            result = result.replaceText(builder ->
                    builder.matchLiteral(killer.getName())
                            .replacement(Component.text(killerNick))
            );
        }

        event.deathMessage(result);
    }
}
