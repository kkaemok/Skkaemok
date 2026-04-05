package org.kkaemok.skkaemok.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

public final class LuckPermsHook {
    private final JavaPlugin plugin;
    private final Object luckPerms;
    private final boolean enabled;
    private Object subscription;
    private boolean warned;

    public LuckPermsHook(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;

        boolean configEnabled = plugin.getConfig().getBoolean("integration.luckperms.enabled", true);
        if (!configEnabled) {
            this.luckPerms = null;
            this.enabled = false;
            return;
        }

        Object api = resolveApi();

        this.luckPerms = api;
        this.enabled = api != null;
        if (!enabled) {
            plugin.getLogger().info("LuckPerms not found. Prefix/suffix integration disabled.");
        }
    }

    public String getPrefix(UUID playerId) {
        return getMetaString(playerId, "getPrefix");
    }

    public String getSuffix(UUID playerId) {
        if (!enabled || playerId == null) {
            return "";
        }
        return getMetaString(playerId, "getSuffix");
    }

    public void registerMetaListener(Consumer<UUID> onUpdate) {
        if (!enabled || onUpdate == null) {
            return;
        }
        if (subscription != null) {
            closeSubscription(subscription);
            subscription = null;
        }
        try {
            Method getEventBus = luckPerms.getClass().getMethod("getEventBus");
            Object eventBus = getEventBus.invoke(luckPerms);
            if (eventBus == null) {
                return;
            }

            Class<?> eventClass = Class.forName("net.luckperms.api.event.user.UserDataRecalculateEvent");
            Method subscribe = eventBus.getClass().getMethod("subscribe", Object.class, Class.class, Consumer.class);

            Consumer<Object> listener = event -> {
                UUID uuid = extractUserId(event);
                if (uuid != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> onUpdate.accept(uuid));
                }
            };

            subscription = subscribe.invoke(eventBus, plugin, eventClass, listener);
        } catch (Exception e) {
            warnOnce("Failed to register LuckPerms listener: " + e.getMessage());
        }
    }

    public void close() {
        if (subscription != null) {
            closeSubscription(subscription);
            subscription = null;
        }
    }

    private Object resolveApi() {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            return getMethod.invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getMetaString(UUID playerId, String methodName) {
        if (!enabled || playerId == null) {
            return "";
        }
        try {
            Object user = getUser(playerId);
            if (user == null) {
                return "";
            }
            Method getCachedData = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedData.invoke(user);
            if (cachedData == null) {
                return "";
            }
            Method getMetaData = cachedData.getClass().getMethod("getMetaData");
            Object metaData = getMetaData.invoke(cachedData);
            if (metaData == null) {
                return "";
            }
            Method method = metaData.getClass().getMethod(methodName);
            Object value = method.invoke(metaData);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception e) {
            warnOnce("Failed to read LuckPerms meta: " + e.getMessage());
            return "";
        }
    }

    private Object getUser(UUID playerId) throws Exception {
        Method getUserManager = luckPerms.getClass().getMethod("getUserManager");
        Object userManager = getUserManager.invoke(luckPerms);
        if (userManager == null) {
            return null;
        }
        Method getUser = userManager.getClass().getMethod("getUser", UUID.class);
        return getUser.invoke(userManager, playerId);
    }

    private UUID extractUserId(Object event) {
        if (event == null) {
            return null;
        }
        try {
            Method getUser = event.getClass().getMethod("getUser");
            Object user = getUser.invoke(event);
            if (user == null) {
                return null;
            }
            Method getUniqueId = user.getClass().getMethod("getUniqueId");
            Object uuid = getUniqueId.invoke(user);
            if (uuid instanceof UUID playerId) {
                return playerId;
            }
        } catch (Exception e) {
            warnOnce("Failed to resolve LuckPerms event user: " + e.getMessage());
        }
        return null;
    }

    private void closeSubscription(Object subscription) {
        if (subscription == null) {
            return;
        }
        try {
            Method close = subscription.getClass().getMethod("close");
            close.invoke(subscription);
        } catch (Exception e) {
            warnOnce("Failed to close LuckPerms listener: " + e.getMessage());
        }
    }

    private void warnOnce(String message) {
        if (!warned) {
            warned = true;
            plugin.getLogger().warning(message);
        }
    }
}
