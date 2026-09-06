package org.kkaemok.skkaemok.service;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatNameService {
    private final ChatNameManager chatNameManager;
    private final VanillaTeamService vanillaTeamService;
    private final DisplayFormatter formatter;

    public ChatNameService(ChatNameManager chatNameManager,
                           VanillaTeamService vanillaTeamService,
                           JavaPlugin plugin) {
        if (chatNameManager == null || vanillaTeamService == null || plugin == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.chatNameManager = chatNameManager;
        this.vanillaTeamService = vanillaTeamService;
        this.formatter = new DisplayFormatter(plugin);
    }

    public void reload() {
        formatter.reload();
    }

    public boolean setChatName(Player player, String chatName) {
        if (player == null || chatName == null) {
            return false;
        }
        String normalized = chatName.trim();
        if (normalized.isEmpty()) {
            resetChatName(player);
            return true;
        }
        if (normalized.equals(chatNameManager.getRawChatName(player))) {
            return true;
        }
        chatNameManager.setChatName(player, normalized);
        applyChatName(player, normalized);
        return true;
    }

    public boolean setChatName(Player player, Player viewer, String chatName) {
        if (player == null || viewer == null || chatName == null) {
            return false;
        }
        String normalized = chatName.trim();
        if (normalized.isEmpty()) {
            resetChatName(player, viewer);
            return true;
        }
        if (normalized.equals(chatNameManager.getRawChatName(player, viewer))) {
            return true;
        }
        chatNameManager.setChatName(player, viewer, normalized);
        return true;
    }

    public void resetChatName(Player player) {
        if (player == null) {
            return;
        }
        if (!chatNameManager.hasChatName(player)) {
            return;
        }
        chatNameManager.resetChatName(player);
        applyChatName(player, null);
    }

    public void resetChatName(Player player, Player viewer) {
        if (player == null || viewer == null) {
            return;
        }
        if (!chatNameManager.hasChatName(player, viewer)) {
            return;
        }
        chatNameManager.resetChatName(player, viewer);
    }

    public String resolveChatName(Player player) {
        return chatNameManager.resolveChatName(player, null);
    }

    public String resolveChatName(Player player, Player viewer) {
        return chatNameManager.resolveChatName(player, viewer);
    }

    public boolean hasCustomChatName(Player player) {
        return chatNameManager.hasChatName(player);
    }

    public boolean hasCustomChatName(Player player, Player viewer) {
        return chatNameManager.hasChatName(player, viewer);
    }

    public Player findUniquePlayer(String visibleName, Player viewer) {
        if (visibleName == null) {
            return null;
        }
        Player match = null;
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            String resolved = viewer == null
                    ? resolveChatName(candidate)
                    : resolveChatName(candidate, viewer);
            if (!visibleName.equals(resolved)) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = candidate;
        }
        return match;
    }

    public void applyStoredChatName(Player player) {
        if (player == null) {
            return;
        }
        String chatName = chatNameManager.getRawChatName(player);
        if (chatName == null || chatName.isBlank()) {
            applyChatName(player, null);
            return;
        }
        applyChatName(player, chatName);
    }

    private void applyChatName(Player player, String chatName) {
        Component cleanName = colorName(player, formatter.parseNickname(
                ChatNameFormatter.formatDisplayName(player.getName(), chatName)));
        player.displayName(decorateName(player, cleanName));
    }

    private Component decorateName(Player player, Component cleanName) {
        VanillaTeamService.TeamInfo teamInfo = vanillaTeamService.getInfo(player);
        return Component.empty()
                .append(applyTeamColor(teamInfo.prefix(), teamInfo))
                .append(cleanName)
                .append(applyTeamColor(teamInfo.suffix(), teamInfo));
    }

    private Component colorName(Player player, Component name) {
        VanillaTeamService.TeamInfo teamInfo = vanillaTeamService.getInfo(player);
        return applyTeamColor(name, teamInfo);
    }

    private Component applyTeamColor(Component component, VanillaTeamService.TeamInfo teamInfo) {
        if (component == null) {
            return Component.empty();
        }
        if (teamInfo.color() == null) {
            return component;
        }
        return component.colorIfAbsent(teamInfo.color());
    }
}
