package org.kkaemok.skkaemok.service;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatNameManager {
    private final ConcurrentHashMap<UUID, String> chatNames;
    private final ChatNameStorage storage;
    private final ViewerNameManager viewerNames;

    public ChatNameManager(ChatNameStorage storage) {
        this(storage, null);
    }

    public ChatNameManager(ChatNameStorage storage, ViewerNameManager viewerNames) {
        if (storage == null) {
            throw new IllegalArgumentException("ChatNameStorage cannot be null");
        }
        this.storage = storage;
        this.viewerNames = viewerNames;
        this.chatNames = new ConcurrentHashMap<>(storage.load());
    }

    public String getRawChatName(Player player) {
        if (player == null) {
            return null;
        }
        return chatNames.get(player.getUniqueId());
    }

    public String getRawChatName(Player player, Player viewer) {
        return viewerNames == null ? null : viewerNames.getRaw(NameChannel.CHAT, player, viewer);
    }

    public String resolveChatName(Player player, Player viewer) {
        if (player == null) {
            return null;
        }
        String viewerValue = getRawChatName(player, viewer);
        if (viewerValue != null) {
            return viewerValue;
        }
        String global = getRawChatName(player);
        return global != null ? global : player.getName();
    }

    public boolean hasChatName(Player player) {
        return getRawChatName(player) != null;
    }

    public boolean hasChatName(Player player, Player viewer) {
        return viewerNames != null && viewerNames.has(NameChannel.CHAT, player, viewer);
    }

    public boolean hasAnyChatName(Player player) {
        return hasChatName(player) || (viewerNames != null && viewerNames.hasAny(NameChannel.CHAT, player));
    }

    public void setChatName(Player player, String chatName) {
        if (player == null || chatName == null) {
            return;
        }
        chatNames.put(player.getUniqueId(), chatName);
        saveNow();
    }

    public void setChatName(Player player, Player viewer, String chatName) {
        if (viewerNames != null) {
            viewerNames.set(NameChannel.CHAT, player, viewer, chatName);
        }
    }

    public void resetChatName(Player player) {
        if (player == null) {
            return;
        }
        chatNames.remove(player.getUniqueId());
        saveNow();
    }

    public void resetChatName(Player player, Player viewer) {
        if (viewerNames != null) {
            viewerNames.reset(NameChannel.CHAT, player, viewer);
        }
    }

    public Map<UUID, String> snapshot() {
        return Map.copyOf(chatNames);
    }

    public void reload() {
        chatNames.clear();
        chatNames.putAll(storage.load());
    }

    public void saveNow() {
        storage.save(chatNames);
    }
}
