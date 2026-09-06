package org.kkaemok.skkaemok.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.kkaemok.skkaemok.service.ChatNameManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
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

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String originalMessage = event.getMessage();
        String[] args = splitCommand(originalMessage);
        if (args.length == 0) {
            return;
        }

        String commandName = normalizeCommandName(args[0]);
        if (shouldRewritePrivateMessageTarget(commandName)) {
            PrivateMessageTarget privateMessageTarget = resolvePrivateMessageTarget(originalMessage, event.getPlayer());
            if (privateMessageTarget != null) {
                if (privateMessageTarget.modifiedCommand() != null
                        && !privateMessageTarget.modifiedCommand().equals(originalMessage)) {
                    executeModifiedCommand(event, privateMessageTarget.modifiedCommand());
                }
                return;
            }
        }

        Set<Integer> replacementIndexes = resolveReplacementIndexes(args);
        if (replacementIndexes.isEmpty()) {
            return;
        }

        String modifiedCommand = processCommand(args, replacementIndexes, event.getPlayer());
        if (!modifiedCommand.equals(originalMessage)) {
            executeModifiedCommand(event, modifiedCommand);
        }
    }

    private void executeModifiedCommand(PlayerCommandPreprocessEvent event, String modifiedCommand) {
        event.setCancelled(true);

        String command = modifiedCommand.startsWith("/")
                ? modifiedCommand.substring(1)
                : modifiedCommand;
        event.getPlayer().performCommand(command);
    }

    private String[] splitCommand(String message) {
        if (message == null || message.isBlank()) {
            return new String[0];
        }
        return message.trim().split("\\s+");
    }

    private Set<Integer> resolveReplacementIndexes(String[] args) {
        if (args.length <= 1) {
            return Collections.emptySet();
        }

        Set<Integer> allIndexes = new HashSet<>();
        for (int i = 1; i < args.length; i++) {
            allIndexes.add(i);
        }
        return allIndexes;
    }

    private boolean shouldRewritePrivateMessageTarget(String commandName) {
        return commandName != null && PRIVATE_MESSAGE_COMMANDS.contains(commandName);
    }

    private String processCommand(String[] args, Set<Integer> replacementIndexes, Player viewer) {
        Map<String, Player> nicknameToPlayer = createNicknameMap(viewer);
        return buildModifiedCommand(args, nicknameToPlayer, replacementIndexes);
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
        if (targetInput.startsWith("@")) {
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

    private String buildModifiedCommand(String[] args,
                                        Map<String, Player> nicknameToPlayer,
                                        Set<Integer> replacementIndexes) {
        StringBuilder modified = new StringBuilder(args[0]);

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (replacementIndexes.contains(i)) {
                Player player = nicknameToPlayer.get(arg.toLowerCase(Locale.ROOT));
                if (player != null) {
                    modified.append(" ").append(player.getName());
                } else {
                    modified.append(" ").append(arg);
                }
            } else {
                modified.append(" ").append(arg);
            }
        }
        return modified.toString();
    }

    private Map<String, Player> createNicknameMap(Player viewer) {
        Map<String, Player> map = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String nickname = chatNameManager.resolveChatName(player, viewer);
            String original = player.getName();
            if (nickname != null && !nickname.isBlank() && !nickname.equals(original)) {
                String key = nickname.toLowerCase(Locale.ROOT);
                if (ambiguous.contains(key)) {
                    continue;
                }
                Player previous = map.putIfAbsent(key, player);
                if (previous != null && !previous.equals(player)) {
                    map.remove(key);
                    ambiguous.add(key);
                }
            }
        }
        return map;
    }

    private record PrivateMessageTarget(Player target, String modifiedCommand) {
    }
}
