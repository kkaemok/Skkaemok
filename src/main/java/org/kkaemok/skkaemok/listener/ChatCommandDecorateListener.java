package org.kkaemok.skkaemok.listener;

import io.papermc.paper.event.player.AsyncChatCommandDecorateEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.service.NameRewriteService;

public final class ChatCommandDecorateListener implements Listener {
    private static final int MAX_COMPONENT_LOG_LENGTH = 700;

    private final JavaPlugin plugin;
    private final NameRewriteService nameRewriteService;

    public ChatCommandDecorateListener(JavaPlugin plugin, NameRewriteService nameRewriteService) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (nameRewriteService == null) {
            throw new IllegalArgumentException("NameRewriteService cannot be null");
        }
        this.plugin = plugin;
        this.nameRewriteService = nameRewriteService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatCommandDecorate(AsyncChatCommandDecorateEvent event) {
        if (!plugin.getConfig().getBoolean("output-name-rewrite.chat-command-decorate.enabled", false)) {
            return;
        }

        Component before = event.result();
        Component after = nameRewriteService.rewriteOnlinePlayerNames(before);
        if (plugin.getConfig().getBoolean("output-name-rewrite.chat-command-decorate.debug-marker", false)) {
            after = Component.text("[decorated] ", NamedTextColor.GRAY).append(after);
        }
        event.result(after);

        if (plugin.getConfig().getBoolean("output-name-rewrite.debug", false)) {
            logDebug(event, before, after);
        }
    }

    private void logDebug(AsyncChatCommandDecorateEvent event, Component before, Component after) {
        Player player = event.player();
        String playerName = player == null ? "<none>" : player.getName();
        plugin.getLogger().info("[chat-command-decorate debug] player=" + playerName
                + " changed=" + !before.equals(after));
        plugin.getLogger().info("[chat-command-decorate debug] originalMessage="
                + serialize(event.originalMessage()));
        plugin.getLogger().info("[chat-command-decorate debug] resultBefore="
                + serialize(before));
        plugin.getLogger().info("[chat-command-decorate debug] resultAfter="
                + serialize(after));
    }

    private String serialize(Component component) {
        String json = GsonComponentSerializer.gson().serialize(component);
        if (json.length() <= MAX_COMPONENT_LOG_LENGTH) {
            return json;
        }
        return json.substring(0, MAX_COMPONENT_LOG_LENGTH) + "...";
    }
}
