package org.kkaemok.skkaemok.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TablistNameService {
    private final NametagManager nametagManager;
    private final TablistNameManager tablistNameManager;

    public TablistNameService(NameManager nameManager,
                              SkinManager skinManager,
                              NametagManager nametagManager,
                              TablistNameManager tablistNameManager) {
        if (nameManager == null || skinManager == null || nametagManager == null || tablistNameManager == null) {
            throw new IllegalArgumentException("Managers cannot be null");
        }
        this.nametagManager = nametagManager;
        this.tablistNameManager = tablistNameManager;
    }

    public boolean setTablistName(Player player, String tablistName) {
        if (player == null || tablistName == null) {
            return false;
        }
        String normalized = tablistName.trim();
        if (normalized.isEmpty()) {
            resetTablistName(player);
            return true;
        }
        if (normalized.equals(tablistNameManager.getRawTablistName(player))) {
            return true;
        }
        tablistNameManager.setTablistName(player, normalized);
        nametagManager.updateForAllViewers(player);
        return true;
    }

    public boolean setTablistName(Player player, Player viewer, String tablistName) {
        if (player == null || viewer == null || tablistName == null) {
            return false;
        }
        String normalized = tablistName.trim();
        if (normalized.isEmpty()) {
            resetTablistName(player, viewer);
            return true;
        }
        if (normalized.equals(tablistNameManager.getRawTablistName(player, viewer))) {
            return true;
        }
        tablistNameManager.setTablistName(player, viewer, normalized);
        nametagManager.updateForViewer(player, viewer);
        return true;
    }

    public void resetTablistName(Player player) {
        if (player == null) {
            return;
        }
        if (!tablistNameManager.hasTablistName(player)) {
            return;
        }
        tablistNameManager.resetTablistName(player);
        nametagManager.updateForAllViewers(player);
    }

    public void resetTablistName(Player player, Player viewer) {
        if (player == null || viewer == null) {
            return;
        }
        if (!tablistNameManager.hasTablistName(player, viewer)) {
            return;
        }
        tablistNameManager.resetTablistName(player, viewer);
        nametagManager.updateForViewer(player, viewer);
    }

    public void refreshDisplay(Player player) {
        if (player == null) {
            return;
        }
        nametagManager.updateForAllViewers(player);
    }

    public String resolveTablistName(Player player) {
        return tablistNameManager.resolveTablistName(player, null);
    }

    public String resolveTablistName(Player player, Player viewer) {
        return tablistNameManager.resolveTablistName(player, viewer);
    }

    public boolean hasCustomTablistName(Player player) {
        return tablistNameManager.hasTablistName(player);
    }

    public boolean hasCustomTablistName(Player player, Player viewer) {
        return tablistNameManager.hasTablistName(player, viewer);
    }

    public Player findUniquePlayer(String visibleName, Player viewer) {
        if (visibleName == null) {
            return null;
        }
        Player match = null;
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            String resolved = viewer == null
                    ? resolveTablistName(candidate)
                    : resolveTablistName(candidate, viewer);
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
}
