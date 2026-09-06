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
import org.kkaemok.skkaemok.service.TablistNameService;

@SuppressWarnings("unchecked")
public final class SetTablistNameEffect extends Effect {
    private static JavaPlugin plugin;
    private static TablistNameService tablistNameService;

    private Expression<Player> targetPlayerExpr;
    private Expression<String> targetNameExpr;
    private Expression<String> tablistNameExpr;
    private boolean targetIsName;

    public static void bootstrap(JavaPlugin plugin, TablistNameService tablistNameService) {
        SetTablistNameEffect.plugin = plugin;
        SetTablistNameEffect.tablistNameService = tablistNameService;
        Skript.registerEffect(SetTablistNameEffect.class,
                "set tablist name of %player% to %string%",
                "set tablist name of %string% to %string%"
        );
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        if (matchedPattern == 0) {
            targetPlayerExpr = (Expression<Player>) expressions[0];
            tablistNameExpr = (Expression<String>) expressions[1];
            targetIsName = false;
        } else {
            targetNameExpr = (Expression<String>) expressions[0];
            tablistNameExpr = (Expression<String>) expressions[1];
            targetIsName = true;
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        Player target = resolveTarget(event);
        String tablistName = tablistNameExpr.getSingle(event);
        if (target == null || tablistName == null) {
            return;
        }
        if (tablistNameService == null) {
            if (plugin != null) {
                plugin.getLogger().severe("TablistNameService not initialized.");
            }
            return;
        }
        tablistNameService.setTablistName(target, tablistName);
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
        return "set tablist name effect";
    }
}
