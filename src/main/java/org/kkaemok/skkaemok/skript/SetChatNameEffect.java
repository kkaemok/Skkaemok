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
import org.kkaemok.skkaemok.service.ChatNameService;

@SuppressWarnings("unchecked")
public final class SetChatNameEffect extends Effect {
    private static JavaPlugin plugin;
    private static ChatNameService chatNameService;

    private Expression<Player> targetPlayerExpr;
    private Expression<String> targetNameExpr;
    private Expression<String> chatNameExpr;
    private boolean targetIsName;

    public static void bootstrap(JavaPlugin plugin, ChatNameService chatNameService) {
        SetChatNameEffect.plugin = plugin;
        SetChatNameEffect.chatNameService = chatNameService;
        Skript.registerEffect(SetChatNameEffect.class,
                "set chat name of %player% to %string%",
                "set chat name of %string% to %string%"
        );
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        if (matchedPattern == 0) {
            targetPlayerExpr = (Expression<Player>) expressions[0];
            chatNameExpr = (Expression<String>) expressions[1];
            targetIsName = false;
        } else {
            targetNameExpr = (Expression<String>) expressions[0];
            chatNameExpr = (Expression<String>) expressions[1];
            targetIsName = true;
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        Player target = resolveTarget(event);
        String chatName = chatNameExpr.getSingle(event);
        if (target == null || chatName == null) {
            return;
        }
        if (chatNameService == null) {
            if (plugin != null) {
                plugin.getLogger().severe("ChatNameService not initialized.");
            }
            return;
        }
        chatNameService.setChatName(target, chatName);
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
        return "set chat name effect";
    }
}
