package org.kkaemok.skkaemok.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TabIntegration {
    private final JavaPlugin plugin;

    public TabIntegration(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
    }

    public boolean isTabMissing() {
        return !isLocalTabAvailable();
    }

    public boolean isLocalTabAvailable() {
        Plugin tab = Bukkit.getPluginManager().getPlugin("TAB");
        return tab != null && tab.isEnabled();
    }

    public TabStrategy strategy() {
        if (!plugin.getConfig().getBoolean("tab.integration",
                plugin.getConfig().getBoolean("integration.tab.use-customtabname", true))) {
            return TabStrategy.DIRECT;
        }
        return isLocalTabAvailable() ? TabStrategy.TAB_API : TabStrategy.DIRECT;
    }

    public boolean shouldManageDisplay() {
        return true;
    }

    public boolean shouldUseTabCustomTabName() {
        return strategy() == TabStrategy.TAB_API;
    }

    public boolean isReapplyEnabled() {
        return plugin.getConfig().getBoolean("tab.reapply.enabled",
                plugin.getConfig().getBoolean("integration.tab.priority-reapply.enabled", true));
    }

    public int getReapplyDelayTicks() {
        return plugin.getConfig().getInt("tab.reapply.delay-ticks",
                plugin.getConfig().getInt("integration.tab.priority.delay-ticks", 10));
    }

    public String getCustomNameFormat() {
        return plugin.getConfig().getString("tab.custom-name-format",
                plugin.getConfig().getString("integration.tab.customtabname.format", "%nickname%"));
    }
}
