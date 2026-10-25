package org.kkaemok.skkaemok.tablist;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.kkaemok.skkaemok.appearance.AppearanceScope;
import org.kkaemok.skkaemok.appearance.AppearanceService;
import org.kkaemok.skkaemok.display.NametagManager;
import org.kkaemok.skkaemok.name.NameChannel;
import org.kkaemok.skkaemok.name.NameManager;
import org.kkaemok.skkaemok.skin.SkinManager;

public final class TablistNameService {
    private AppearanceService appearances;

    public void setAppearanceService(AppearanceService appearances) {
        this.appearances = appearances;
    }

    private final NametagManager nametagManager;
    private final TablistNameManager tablistNameManager;

    public TablistNameService(
            NameManager nameManager,
            SkinManager skinManager,
            NametagManager nametagManager,
            TablistNameManager tablistNameManager) {
        if (nameManager == null
                || skinManager == null
                || nametagManager == null
                || tablistNameManager == null) {
            throw new IllegalArgumentException("Managers cannot be null");
        }
        this.nametagManager = nametagManager;
        this.tablistNameManager = tablistNameManager;
    }

    public boolean setTablistName(Player player, String tablistName) {
        if (appearances != null && player != null && tablistName != null)
            return appearances
                    .setName(
                            new AppearanceScope(player.getUniqueId(), null),
                            NameChannel.TABLIST,
                            tablistName)
                    .success();
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
        if (appearances != null && player != null && tablistName != null && viewer != null)
            return appearances
                    .setName(
                            new AppearanceScope(player.getUniqueId(), viewer.getUniqueId()),
                            NameChannel.TABLIST,
                            tablistName)
                    .success();
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
        if (appearances != null && player != null) {
            appearances.setName(
                    new AppearanceScope(player.getUniqueId(), null), NameChannel.TABLIST, null);
            return;
        }
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
        if (appearances != null && player != null && viewer != null) {
            appearances.setName(
                    new AppearanceScope(player.getUniqueId(), viewer.getUniqueId()),
                    NameChannel.TABLIST,
                    null);
            return;
        }
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
            String resolved =
                    viewer == null
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
