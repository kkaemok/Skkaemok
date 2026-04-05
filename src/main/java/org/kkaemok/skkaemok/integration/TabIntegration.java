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
        Plugin tab = Bukkit.getPluginManager().getPlugin("TAB");
        return tab == null || !tab.isEnabled();
    }

    public boolean shouldManageDisplay() {
        TabMode mode = mode();
        if (mode == TabMode.OFF || mode == TabMode.PRIORITY) {
            return true;
        }
        if (mode == TabMode.ON) {
            return false;
        }
        return isTabMissing();
    }

    public boolean shouldUseTabCustomTabName() {
        if (isTabMissing()) {
            return false;
        }
        return plugin.getConfig().getBoolean("integration.tab.use-customtabname", true);
    }

    public boolean isPriorityMode() {
        return mode() == TabMode.PRIORITY;
    }

    public int getPriorityDelayTicks() {
        return plugin.getConfig().getInt("integration.tab.priority.delay-ticks", 10);
    }

    private TabMode mode() {
        return TabMode.fromString(plugin.getConfig().getString("integration.tab.mode", "PRIORITY"));
    }
}
