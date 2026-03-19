package org.kkaemok.skkaemok.service;

import org.bukkit.entity.Player;

public final class NicknameService {
    private static final int MAX_LENGTH = 16;

    private final NameManager nameManager;
    private final NametagManager nametagManager;
    private final SkinManager skinManager;

    public NicknameService(NameManager nameManager, NametagManager nametagManager, SkinManager skinManager) {
        if (nameManager == null || nametagManager == null || skinManager == null) {
            throw new IllegalArgumentException("NameManager, NametagManager, and SkinManager cannot be null");
        }
        this.nameManager = nameManager;
        this.nametagManager = nametagManager;
        this.skinManager = skinManager;
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
        SkinData skinData = skinManager.getRawSkin(player);
        nametagManager.updateForAllViewers(player, normalized, skinData);
        return true;
    }

    public void resetNickname(Player player) {
        if (player == null) {
            return;
        }
        nameManager.resetNickname(player);
        SkinData skinData = skinManager.getRawSkin(player);
        nametagManager.updateForAllViewers(player, player.getName(), skinData);
    }

    public String resolveNickname(Player player) {
        return nameManager.loadNickname(player);
    }
}
