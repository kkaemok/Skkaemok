package org.kkaemok.skkaemok.name;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.kkaemok.skkaemok.display.NametagManager;
import org.kkaemok.skkaemok.skin.SkinManager;

public final class NicknameService {
    private org.kkaemok.skkaemok.appearance.AppearanceService appearances;
    public void setAppearanceService(org.kkaemok.skkaemok.appearance.AppearanceService appearances) { this.appearances=appearances; }

    private static final int MAX_LENGTH = 16;

    private final NameManager nameManager;
    private final NametagManager nametagManager;

    public NicknameService(NameManager nameManager, NametagManager nametagManager, SkinManager skinManager) {
        if (nameManager == null || nametagManager == null || skinManager == null) {
            throw new IllegalArgumentException("NameManager, NametagManager, and SkinManager cannot be null");
        }
        this.nameManager = nameManager;
        this.nametagManager = nametagManager;
    }

    public boolean setNickname(Player player, String nickname) {
        if(appearances!=null && player!=null && nickname!=null) return appearances.setName(new org.kkaemok.skkaemok.appearance.AppearanceScope(player.getUniqueId(),null),org.kkaemok.skkaemok.name.NameChannel.NAMETAG,nickname).success();
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
        if (normalized.equals(nameManager.getRawNickname(player))) {
            return true;
        }
        nameManager.setNickname(player, normalized);
        nametagManager.updateForAllViewers(player);
        return true;
    }

    public boolean setNickname(Player player, Player viewer, String nickname) {
        if(appearances!=null && player!=null && nickname!=null && viewer!=null) return appearances.setName(new org.kkaemok.skkaemok.appearance.AppearanceScope(player.getUniqueId(),viewer.getUniqueId()),org.kkaemok.skkaemok.name.NameChannel.NAMETAG,nickname).success();
        if (player == null || viewer == null || nickname == null) {
            return false;
        }
        String normalized = nickname.trim();
        if (normalized.isEmpty()) {
            resetNickname(player, viewer);
            return true;
        }
        if (normalized.length() > MAX_LENGTH) {
            return false;
        }
        if (normalized.equals(nameManager.getRawNickname(player, viewer))) {
            return true;
        }
        nameManager.setNickname(player, viewer, normalized);
        nametagManager.updateForViewer(player, viewer);
        return true;
    }

    public void resetNickname(Player player) {
        if(appearances!=null && player!=null) { appearances.setName(new org.kkaemok.skkaemok.appearance.AppearanceScope(player.getUniqueId(),null),org.kkaemok.skkaemok.name.NameChannel.NAMETAG,null); return; }
        if (player == null) {
            return;
        }
        if (!nameManager.hasNickname(player)) {
            return;
        }
        nameManager.resetNickname(player);
        nametagManager.updateForAllViewers(player);
    }

    public void resetNickname(Player player, Player viewer) {
        if(appearances!=null && player!=null && viewer!=null) { appearances.setName(new org.kkaemok.skkaemok.appearance.AppearanceScope(player.getUniqueId(),viewer.getUniqueId()),org.kkaemok.skkaemok.name.NameChannel.NAMETAG,null); return; }
        if (player == null || viewer == null) {
            return;
        }
        if (!nameManager.hasNickname(player, viewer)) {
            return;
        }
        nameManager.resetNickname(player, viewer);
        nametagManager.updateForViewer(player, viewer);
    }

    public void refreshDisplay(Player player) {
        if (player == null) {
            return;
        }
        nametagManager.updateForAllViewers(player);
    }

    public String resolveNickname(Player player) {
        return nameManager.loadNickname(player);
    }

    public String resolveNickname(Player player, Player viewer) {
        return nameManager.loadNickname(player, viewer);
    }

    public boolean hasCustomNickname(Player player) {
        return nameManager.hasNickname(player);
    }

    public boolean hasCustomNickname(Player player, Player viewer) {
        return nameManager.hasNickname(player, viewer);
    }

    public Player findUniquePlayer(String visibleName, Player viewer) {
        if (visibleName == null) {
            return null;
        }
        Player match = null;
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            String resolved = viewer == null
                    ? resolveNickname(candidate)
                    : resolveNickname(candidate, viewer);
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

    public void reload() {
        nametagManager.reload();
    }
}
