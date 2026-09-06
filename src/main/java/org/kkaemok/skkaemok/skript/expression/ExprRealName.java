package org.kkaemok.skkaemok.skript.expression;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

@SuppressWarnings("unchecked")
public final class ExprRealName extends SimpleExpression<String> {
    private Expression<Player> player;

    public static void bootstrap() {
        Skript.registerExpression(ExprRealName.class, String.class, ExpressionType.PROPERTY,
                "real name of %player%", "original name of %player%");
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean delayed,
                        SkriptParser.ParseResult parseResult) {
        player = (Expression<Player>) expressions[0];
        return true;
    }

    @Override
    protected String[] get(Event event) {
        Player value = player.getSingle(event);
        return value == null ? new String[0] : new String[]{value.getName()};
    }

    @Override public boolean isSingle() { return true; }
    @Override public Class<? extends String> getReturnType() { return String.class; }
    @Override public String toString(Event event, boolean debug) { return "real player name"; }
}
