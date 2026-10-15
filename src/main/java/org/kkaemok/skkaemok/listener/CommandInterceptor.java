package org.kkaemok.skkaemok.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.kkaemok.skkaemok.service.ChatNameManager;

import java.util.Locale;
import java.util.Set;

public final class CommandInterceptor implements Listener {
    private static final Set<String> PRIVATE_MESSAGE_COMMANDS = Set.of("msg", "tell", "w");

    private final ChatNameManager chatNameManager;

    public CommandInterceptor(ChatNameManager chatNameManager) {
        if (chatNameManager == null) {
            throw new IllegalArgumentException("ChatNameManager cannot be null");
        }
        this.chatNameManager = chatNameManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        PrivateMessageTarget target = resolvePrivateMessageTarget(event.getMessage(), event.getPlayer());
        if (target != null && target.modifiedCommand() != null) {
            // Continue through the normal event pipeline, preserving other plugins' checks.
            event.setMessage(target.modifiedCommand());
        }
    }

    private PrivateMessageTarget resolvePrivateMessageTarget(String message, Player viewer) {
        if (message == null || message.isBlank()) {
            return null;
        }

        int commandStart = message.startsWith("/") ? 1 : 0;
        int commandEnd = findWhitespace(message, commandStart);
        if (commandEnd < 0) {
            return null;
        }

        String commandName = normalizeCommandName(message.substring(commandStart, commandEnd));
        if (!PRIVATE_MESSAGE_COMMANDS.contains(commandName)) {
            return null;
        }

        int targetStart = skipWhitespace(message, commandEnd);
        if (targetStart >= message.length()) {
            return null;
        }

        int targetEnd = findWhitespace(message, targetStart);
        if (targetEnd < 0) {
            targetEnd = message.length();
        }

        String targetInput = message.substring(targetStart, targetEnd);
        if (targetInput.startsWith("@") || targetInput.indexOf('"') >= 0 || targetInput.indexOf('\'') >= 0) {
            return null;
        }

        Player target = findPlayerByChatName(targetInput, viewer);
        if (target == null) {
            return null;
        }

        String originalName = target.getName();
        if (originalName.equals(targetInput)) {
            return new PrivateMessageTarget(target, null);
        }

        String modifiedCommand = message.substring(0, targetStart)
                + originalName
                + message.substring(targetEnd);
        return new PrivateMessageTarget(target, modifiedCommand);
    }

    private String normalizeCommandName(String commandToken) {
        if (commandToken == null || commandToken.isBlank()) {
            return "";
        }

        String normalized = commandToken.startsWith("/")
                ? commandToken.substring(1)
                : commandToken;
        normalized = normalized.toLowerCase(Locale.ROOT);

        int namespaceIndex = normalized.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < normalized.length() - 1) {
            normalized = normalized.substring(namespaceIndex + 1);
        }
        return normalized;
    }

    private int skipWhitespace(String value, int start) {
        int index = start;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private int findWhitespace(String value, int start) {
        for (int i = start; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private Player findPlayerByChatName(String chatName, Player viewer) {
        if (chatName == null || chatName.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(chatName);
        if (exact != null) {
            return exact;
        }
        Player match = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String configuredChatName = chatNameManager.resolveChatName(player, viewer);
            if (chatName.equalsIgnoreCase(configuredChatName)) {
                if (match != null) {
                    return null;
                }
                match = player;
            }
        }
        return match;
    }

    private record PrivateMessageTarget(Player target, String modifiedCommand) {
    }
}
