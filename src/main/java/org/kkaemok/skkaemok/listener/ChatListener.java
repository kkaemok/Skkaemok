package org.kkaemok.skkaemok.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.kkaemok.skkaemok.service.NameManager;

public final class ChatListener implements Listener {
    private final NameManager nameManager;

    public ChatListener(NameManager nameManager) {
        if (nameManager == null) {
            throw new IllegalArgumentException("NameManager cannot be null");
        }
        this.nameManager = nameManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        String nickname = nameManager.loadNickname(event.getPlayer());
        Component name = Component.text(nickname);

        event.renderer((player, sourceDisplayName, message, viewer) ->
                Component.text()
                        .append(Component.text("<"))
                        .append(name)
                        .append(Component.text("> "))
                        .append(message)
                        .build()
        );
    }
}
