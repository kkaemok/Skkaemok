package org.kkaemok.skkaemok.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
        if (!tabIntegration.shouldUseTabCustomTabName()) {
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
            Method setNameMethod = resolveSetNameMethod(manager.getClass(), tabPlayer.getClass());
            if (setNameMethod == null) {
                if (!warned) {
                    warned = true;
                    plugin.getLogger().warning("TAB customtabname bridge could not find a compatible setName method.");
                }
                return;
            }
            if (setNameMethod.getParameterCount() == 2) {
                setNameMethod.invoke(manager, tabPlayer, customName);
            } else if (setNameMethod.getParameterCount() == 3) {
                setNameMethod.invoke(manager, tabPlayer, customName, Boolean.TRUE);
            } else if (!warned) {
                warned = true;
                plugin.getLogger().warning("TAB customtabname bridge found an unsupported setName signature.");
            }
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Failed to update TAB customtabname via API: " + e.getMessage());
            }
        }
    }

    private Method resolveSetNameMethod(Class<?> managerClass, Class<?> tabPlayerClass) {
        if (setName != null) {
            return setName;
        }

        synchronized (lock) {
            if (setName != null) {
                return setName;
            }

            for (Method method : managerClass.getMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                if (!method.getName().equals("setName")) {
                    continue;
                }

                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 2
                        && parameterTypes[0].isAssignableFrom(tabPlayerClass)
                        && parameterTypes[1] == String.class) {
                    setName = method;
                    return setName;
                }
                if (parameterTypes.length == 3
                        && parameterTypes[0].isAssignableFrom(tabPlayerClass)
                        && parameterTypes[1] == String.class
                        && (parameterTypes[2] == boolean.class || parameterTypes[2] == Boolean.class)) {
                    setName = method;
                    return setName;
                }
            }

            return null;
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

                getInstance = tabApiClass.getMethod("getInstance");
                getPlayerByUuid = tabApiClass.getMethod("getPlayer", UUID.class);
                getTabListFormatManager = tabApiClass.getMethod("getTabListFormatManager");
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
