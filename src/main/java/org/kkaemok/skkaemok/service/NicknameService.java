package org.kkaemok.skkaemok.service;

import org.bukkit.entity.Player;

public final class NicknameService {
    private static final int MAX_LENGTH = 16;

    private final NameManager nameManager;
    private final NametagManager nametagManager;

    public NicknameService(NameManager nameManager, NametagManager nametagManager) {
        if (nameManager == null || nametagManager == null) {
            throw new IllegalArgumentException("NameManager and NametagManager cannot be null");
        }
        this.nameManager = nameManager;
        this.nametagManager = nametagManager;
    }

    public boolean setNickname(Player player, String nickname) {
        if (player == null || nickname == null) {
            return false;
        }
        String normalized = nickname.trim();
        if (normalized.isEmpty()) {
            resetNickname(player);
            return true;
        }
        if (normalized.length() > MAX_LENGTH) {
            return false;
        }
        nameManager.setNickname(player, normalized);
        nametagManager.updateForAllViewers(player, normalized);
        return true;
    }

    public void resetNickname(Player player) {
        if (player == null) {
            return;
        }
        nameManager.resetNickname(player);
        nametagManager.updateForAllViewers(player, player.getName());
    }

    public String resolveNickname(Player player) {
        return nameManager.loadNickname(player);
    }
}
