package org.kkaemok.skkaemok.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.service.SkinService;

@SuppressWarnings("unchecked")
public final class SetSkinEffect extends Effect {
    private static JavaPlugin plugin;
    private static SkinService skinService;

    private Expression<Player> targetPlayerExpr;
    private Expression<String> targetNameExpr;
    private Expression<Player> sourcePlayerExpr;
    private Expression<String> sourceStringExpr;
    private boolean targetIsName;
    private boolean sourceIsPlayer;
    private boolean sourceIsUrl;

    public static void bootstrap(JavaPlugin plugin, SkinService skinService) {
        SetSkinEffect.plugin = plugin;
        SetSkinEffect.skinService = skinService;
        Skript.registerEffect(SetSkinEffect.class,
                "set skin of %player% to %player%",
                "set skin of %string% to %player%",
                "set skin of %player% to %string%",
                "set skin of %string% to %string%",
                "set skin of %player% to url %string%",
                "set skin of %string% to url %string%"
        );
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        switch (matchedPattern) {
            case 0 -> {
                targetPlayerExpr = (Expression<Player>) expressions[0];
                sourcePlayerExpr = (Expression<Player>) expressions[1];
                targetIsName = false;
                sourceIsPlayer = true;
            }
            case 1 -> {
                targetNameExpr = (Expression<String>) expressions[0];
                sourcePlayerExpr = (Expression<Player>) expressions[1];
                targetIsName = true;
                sourceIsPlayer = true;
            }
            case 2 -> {
                targetPlayerExpr = (Expression<Player>) expressions[0];
                sourceStringExpr = (Expression<String>) expressions[1];
                targetIsName = false;
                sourceIsPlayer = false;
                sourceIsUrl = false;
            }
            case 3 -> {
                targetNameExpr = (Expression<String>) expressions[0];
                sourceStringExpr = (Expression<String>) expressions[1];
                targetIsName = true;
                sourceIsPlayer = false;
                sourceIsUrl = false;
            }
            case 4 -> {
                targetPlayerExpr = (Expression<Player>) expressions[0];
                sourceStringExpr = (Expression<String>) expressions[1];
                targetIsName = false;
                sourceIsPlayer = false;
                sourceIsUrl = true;
            }
            case 5 -> {
                targetNameExpr = (Expression<String>) expressions[0];
                sourceStringExpr = (Expression<String>) expressions[1];
                targetIsName = true;
                sourceIsPlayer = false;
                sourceIsUrl = true;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        if (skinService == null) {
            if (plugin != null) {
                plugin.getLogger().severe("SkinService not initialized.");
            }
            return;
        }

        Player target = resolveTarget(event);
        if (target == null) {
            return;
        }

        if (sourceIsPlayer) {
            Player source = sourcePlayerExpr.getSingle(event);
            if (source == null) {
                warn(event, "Source player not found.");
                return;
            }
            boolean ok = skinService.setSkinFromPlayer(target, source);
            if (!ok) {
                warn(event, "Failed to apply skin from player.");
            }
            return;
        }

        String source = sourceStringExpr.getSingle(event);
        if (source == null) {
            return;
        }

        if (sourceIsUrl) {
            boolean ok = skinService.setSkinFromUrl(target, source);
            if (!ok) {
                warn(event, "Failed to apply skin from URL.");
            }
            return;
        }

        skinService.setSkinFromName(target, source);
    }

    private Player resolveTarget(Event event) {
        if (!targetIsName) {
            return targetPlayerExpr.getSingle(event);
        }
        String name = targetNameExpr.getSingle(event);
        if (name == null) {
            return null;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            Skript.warning("Player not found: " + name);
        }
        return target;
    }

    private void warn(Event event, String message) {
        Skript.warning(message);
        if (event instanceof org.bukkit.event.player.PlayerEvent playerEvent) {
            playerEvent.getPlayer().sendMessage(message);
        }
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "set skin effect";
    }
}
