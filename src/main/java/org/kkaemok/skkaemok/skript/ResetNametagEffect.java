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
import org.kkaemok.skkaemok.service.NicknameService;

public final class ResetNametagEffect extends Effect {
    private static JavaPlugin plugin;
    private static NicknameService nicknameService;

    private Expression<Player> targetPlayerExpr;
    private Expression<String> targetNameExpr;
    private boolean targetIsName;

    public static void bootstrap(JavaPlugin plugin, NicknameService nicknameService) {
        ResetNametagEffect.plugin = plugin;
        ResetNametagEffect.nicknameService = nicknameService;
        Skript.registerEffect(ResetNametagEffect.class,
                "reset nametag of %player%",
                "reset nametag of %string%"
        );
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        if (matchedPattern == 0) {
            targetPlayerExpr = (Expression<Player>) exprs[0];
            targetIsName = false;
        } else {
            targetNameExpr = (Expression<String>) exprs[0];
            targetIsName = true;
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        Player target = resolveTarget(event);
        if (target == null) {
            return;
        }
        if (nicknameService == null) {
            if (plugin != null) {
                plugin.getLogger().severe("NicknameService not initialized.");
            }
            return;
        }
        nicknameService.resetNickname(target);
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
        return "reset nametag effect";
    }
}
