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
public final class ResetSkinEffect extends Effect {
    private static JavaPlugin plugin;
    private static SkinService skinService;

    private Expression<Player> targetPlayerExpr;
    private Expression<String> targetNameExpr;
    private Expression<Player> viewerExpr;
    private boolean targetIsName;
    private boolean viewerSpecific;

    public static void bootstrap(JavaPlugin plugin, SkinService skinService) {
        ResetSkinEffect.plugin = plugin;
        ResetSkinEffect.skinService = skinService;
        Skript.registerEffect(ResetSkinEffect.class,
                "reset skin of %player%",
                "reset skin of %string%",
                "reset skin of %player% for %player%"
        );
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        if (matchedPattern == 0) {
            targetPlayerExpr = (Expression<Player>) expressions[0];
            targetIsName = false;
        } else if (matchedPattern == 1) {
            targetNameExpr = (Expression<String>) expressions[0];
            targetIsName = true;
        } else {
            targetPlayerExpr = (Expression<Player>) expressions[0];
            viewerExpr = (Expression<Player>) expressions[1];
            viewerSpecific = true;
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
        if (viewerSpecific) {
            Player viewer = viewerExpr.getSingle(event);
            if (viewer != null) {
                skinService.resetSkin(target, viewer);
            }
        } else {
            skinService.resetSkin(target);
        }
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

    @Override
    public String toString(Event event, boolean debug) {
        return "reset skin effect";
    }
}
