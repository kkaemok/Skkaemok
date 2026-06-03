package org.kkaemok.skkaemok.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.service.UpdateChecker;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UpdateNotifyListener implements Listener {
    private final JavaPlugin plugin;
    private final UpdateChecker updateChecker;
    private final Set<UUID> notifiedPlayers;

    public UpdateNotifyListener(JavaPlugin plugin, UpdateChecker updateChecker) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (updateChecker == null) {
            throw new IllegalArgumentException("UpdateChecker cannot be null");
        }
        this.plugin = plugin;
        this.updateChecker = updateChecker;
        this.notifiedPlayers = ConcurrentHashMap.newKeySet();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!canReceiveUpdateNotice(player)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> sendUpdateMessage(player), 40L);
    }

    private boolean canReceiveUpdateNotice(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
            return false;
        }
        return player.isOp() || player.hasPermission("skkaemok.op");
    }

    private void sendUpdateMessage(Player player) {
        if (!canReceiveUpdateNotice(player)) {
            return;
        }

        UpdateChecker.UpdateInfo update = updateChecker.getAvailableUpdate();
        if (update == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("update-checker.notify-per-login", true)
                && !notifiedPlayers.add(player.getUniqueId())) {
            return;
        }

        player.sendMessage(Component.text("New update available: ")
                .append(Component.text("skkaemok").decorate(TextDecoration.BOLD))
                .append(Component.text(" " + update.versionName())));
        player.sendMessage(Component.text("Download it on GitHub: ")
                .append(Component.text(update.downloadUrl())
                        .clickEvent(ClickEvent.openUrl(update.downloadUrl()))));
    }
}
