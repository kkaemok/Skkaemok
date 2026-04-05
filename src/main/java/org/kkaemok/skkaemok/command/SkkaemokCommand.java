package org.kkaemok.skkaemok.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.kkaemok.skkaemok.Skkaemok;

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
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(prefix) && sender.hasPermission(PERMISSION_RELOAD)) {
                return List.of("reload");
            }
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " reload");
    }
}
