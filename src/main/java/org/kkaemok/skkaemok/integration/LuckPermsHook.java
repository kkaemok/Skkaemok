package org.kkaemok.skkaemok.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Consumer;

public final class LuckPermsHook {
    private final JavaPlugin plugin;
    private LuckPerms luckPerms;
    private boolean enabled;
    private EventSubscription<UserDataRecalculateEvent> subscription;
    private Consumer<UUID> onUpdate;

    public LuckPermsHook(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
        reload();
    }

    public String getPrefix(UUID playerId) {
        LuckPerms api = luckPerms;
        if (!enabled || api == null || playerId == null) {
            return "";
        }
        User user = api.getUserManager().getUser(playerId);
        if (user == null) {
            return "";
        }
        CachedMetaData meta = user.getCachedData().getMetaData();
        String prefix = meta.getPrefix();
        return prefix == null ? "" : prefix;
    }

    public String getSuffix(UUID playerId) {
        LuckPerms api = luckPerms;
        if (!enabled || api == null || playerId == null) {
            return "";
        }
        User user = api.getUserManager().getUser(playerId);
        if (user == null) {
            return "";
        }
        CachedMetaData meta = user.getCachedData().getMetaData();
        String suffix = meta.getSuffix();
        return suffix == null ? "" : suffix;
    }

    public String getPrimaryGroup(UUID playerId) {
        LuckPerms api = luckPerms;
        if (!enabled || api == null || playerId == null) {
            return "";
        }
        User user = api.getUserManager().getUser(playerId);
        if (user == null) {
            return "";
        }
        String group = user.getPrimaryGroup();
        return group == null ? "" : group;
    }

    public int getPrimaryGroupWeight(UUID playerId) {
        LuckPerms api = luckPerms;
        if (!enabled || api == null || playerId == null) {
            return 0;
        }

        String primaryGroup = getPrimaryGroup(playerId);
        if (primaryGroup.isEmpty()) {
            return 0;
        }

        Group group = api.getGroupManager().getGroup(primaryGroup);
        if (group == null) {
            return 0;
        }
        return group.getWeight().orElse(0);
    }

    public synchronized void reload() {
        closeSubscription();

        boolean configEnabled = plugin.getConfig().getBoolean("integration.luckperms.enabled", true);
        if (!configEnabled) {
            this.luckPerms = null;
            this.enabled = false;
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            this.luckPerms = null;
            this.enabled = false;
            plugin.getLogger().info("LuckPerms not found. Prefix/suffix integration disabled.");
            return;
        }

        LuckPerms api;
        try {
            api = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            api = null;
        } catch (LinkageError e) {
            api = null;
        }

        this.luckPerms = api;
        this.enabled = api != null;
        if (!enabled) {
            plugin.getLogger().info("LuckPerms not found. Prefix/suffix integration disabled.");
            return;
        }

        if (onUpdate != null) {
            subscribe(onUpdate);
        }
    }

    public synchronized void registerMetaListener(Consumer<UUID> onUpdate) {
        this.onUpdate = onUpdate;
        closeSubscription();

        if (!enabled || onUpdate == null) {
            return;
        }
        subscribe(onUpdate);
    }

    private void subscribe(Consumer<UUID> onUpdate) {
        LuckPerms api = this.luckPerms;
        if (api == null) {
            return;
        }

        subscription = api.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            UUID uuid = event.getUser().getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> onUpdate.accept(uuid));
        });
    }

    private void closeSubscription() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    public synchronized void close() {
        closeSubscription();
    }
}
