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

public final class SetNametagEffect extends Effect {
    private static JavaPlugin plugin;
    private static NicknameService nicknameService;

    private Expression<Player> targetPlayerExpr;
    private Expression<String> targetNameExpr;
    private Expression<String> nicknameExpr;
    private boolean targetIsName;

    public static void bootstrap(JavaPlugin plugin, NicknameService nicknameService) {
        SetNametagEffect.plugin = plugin;
        SetNametagEffect.nicknameService = nicknameService;
        Skript.registerEffect(SetNametagEffect.class,
                "set nametag of %player% to %string%",
                "set nametag of %string% to %string%"
        );
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        if (matchedPattern == 0) {
            targetPlayerExpr = (Expression<Player>) exprs[0];
            nicknameExpr = (Expression<String>) exprs[1];
            targetIsName = false;
        } else {
            targetNameExpr = (Expression<String>) exprs[0];
            nicknameExpr = (Expression<String>) exprs[1];
            targetIsName = true;
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        Player target = resolveTarget(event);
        String nickname = nicknameExpr.getSingle(event);
        if (target == null || nickname == null) {
            return;
        }
        if (nicknameService == null) {
            if (plugin != null) {
                plugin.getLogger().severe("NicknameService not initialized.");
            }
            return;
        }
        boolean ok = nicknameService.setNickname(target, nickname);
        if (!ok) {
            String msg = "Nickname must be 16 characters or fewer.";
            Skript.warning(msg);
            if (event instanceof org.bukkit.event.player.PlayerEvent playerEvent) {
                playerEvent.getPlayer().sendMessage(msg);
            }
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
        return "set nametag effect";
    }
}
