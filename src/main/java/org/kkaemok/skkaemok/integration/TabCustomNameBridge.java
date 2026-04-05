package org.kkaemok.skkaemok.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class TabCustomNameBridge {
    private final JavaPlugin plugin;
    private final TabIntegration tabIntegration;
    private final Object lock = new Object();

    private boolean initialized;
    private boolean available;
    private boolean warned;

    private Method getInstance;
    private Method getPlayerByUuid;
    private Method getTabListFormatManager;
    private Method setName;

    public TabCustomNameBridge(JavaPlugin plugin, TabIntegration tabIntegration) {
        if (plugin == null || tabIntegration == null) {
            throw new IllegalArgumentException("Plugin and TabIntegration cannot be null");
        }
        this.plugin = plugin;
        this.tabIntegration = tabIntegration;
    }

    public void setCustomTabName(Player player, String customName) {
        if (player == null || customName == null || customName.isBlank()) {
            return;
        }
        if (!tabIntegration.shouldUseTabCustomTabName()) {
            return;
        }
        invokeSetName(player.getUniqueId(), customName);
    }

    public void resetCustomTabName(Player player) {
        if (player == null) {
            return;
        }
        if (tabIntegration.isTabMissing()) {
            return;
        }
        invokeSetName(player.getUniqueId(), null);
    }

    private void invokeSetName(UUID playerId, String customName) {
        if (playerId == null) {
            return;
        }
        if (!ensureInitialized()) {
            return;
        }
        try {
            Object api = getInstance.invoke(null);
            if (api == null) {
                return;
            }
            Object tabPlayer = getPlayerByUuid.invoke(api, playerId);
            if (tabPlayer == null) {
                return;
            }
            Object manager = getTabListFormatManager.invoke(api);
            if (manager == null) {
                return;
            }
            setName.invoke(manager, tabPlayer, customName);
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Failed to update TAB customtabname via API: " + e.getMessage());
            }
        }
    }

    private boolean ensureInitialized() {
        synchronized (lock) {
            if (initialized) {
                return available;
            }
            initialized = true;
            if (tabIntegration.isTabMissing()) {
                available = false;
                return false;
            }

            try {
                ClassLoader classLoader = plugin.getClass().getClassLoader();
                Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI", true, classLoader);
                Class<?> tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer", true, classLoader);
                Class<?> managerClass = Class.forName(
                        "me.neznamy.tab.api.tablist.nameformatting.TabListFormatManager",
                        true,
                        classLoader
                );

                getInstance = tabApiClass.getMethod("getInstance");
                getPlayerByUuid = tabApiClass.getMethod("getPlayer", UUID.class);
                getTabListFormatManager = tabApiClass.getMethod("getTabListFormatManager");
                setName = managerClass.getMethod("setName", tabPlayerClass, String.class);
                available = true;
                return true;
            } catch (Exception e) {
                available = false;
                if (!warned) {
                    warned = true;
                    plugin.getLogger().warning("TAB API not available for customtabname bridge: " + e.getMessage());
                }
                return false;
            }
        }
    }
}
