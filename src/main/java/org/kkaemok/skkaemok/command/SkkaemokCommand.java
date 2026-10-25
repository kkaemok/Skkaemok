package org.kkaemok.skkaemok.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.kkaemok.skkaemok.Skkaemok;
import org.kkaemok.skkaemok.appearance.AppearanceScope;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SkkaemokCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION_RELOAD = "skkaemok.op";
    private final Skkaemok plugin;

    public SkkaemokCommand(Skkaemok plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("inspect".equals(sub)) {
            if (!sender.hasPermission("skkaemok.inspect")
                    && !sender.hasPermission(PERMISSION_RELOAD)) {
                sender.sendMessage("You do not have permission to do that.");
                return true;
            }
            if (args.length < 2 || args.length > 3) {
                sender.sendMessage(
                        "Usage: /"
                                + label
                                + " inspect <account name or UUID> [viewer account name or UUID]");
                return true;
            }
            var target = resolve(args[1]);
            var viewer = args.length == 3 ? resolve(args[2]) : null;
            if (target == null || (args.length == 3 && viewer == null)) {
                sender.sendMessage("Unknown player. Use a known account name or UUID.");
                return true;
            }
            plugin.getAppearances()
                    .inspect(
                            new AppearanceScope(
                                    target.getUniqueId(),
                                    viewer == null ? null : viewer.getUniqueId()))
                    .forEach(sender::sendMessage);
            return true;
        }
        if ("reload".equals(sub)) {
            if (!sender.hasPermission(PERMISSION_RELOAD)) {
                sender.sendMessage("You do not have permission to do that.");
                return true;
            }
            plugin.reloadSkkaemok();
            sender.sendMessage("Skkaemok reloaded.");
            return true;
        }

        sendUsage(sender, label);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return java.util.stream.Stream.of("reload", "inspect")
                    .filter(sub -> sub.startsWith(prefix))
                    .filter(
                            sub ->
                                    sender.hasPermission(PERMISSION_RELOAD)
                                            || (sub.equals("inspect")
                                                    && sender.hasPermission("skkaemok.inspect")))
                    .toList();
        }
        if (args.length >= 2
                && args.length <= 3
                && args[0].equalsIgnoreCase("inspect")
                && (sender.hasPermission(PERMISSION_RELOAD)
                        || sender.hasPermission("skkaemok.inspect")))
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(
                            name ->
                                    name.toLowerCase(Locale.ROOT)
                                            .startsWith(
                                                    args[args.length - 1].toLowerCase(Locale.ROOT)))
                    .sorted()
                    .toList();
        return Collections.emptyList();
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " reload | inspect <target> [viewer]");
    }

    private static org.bukkit.OfflinePlayer resolve(String value) {
        try {
            return org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return org.bukkit.Bukkit.getOfflinePlayerIfCached(value);
        }
    }
}
