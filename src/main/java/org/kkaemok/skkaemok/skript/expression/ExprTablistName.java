package org.kkaemok.skkaemok.skript.expression;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.kkaemok.skkaemok.service.TablistNameService;

@SuppressWarnings("unchecked")
public final class ExprTablistName extends SimpleExpression<String> {
    private static TablistNameService service;
    private Expression<Player> target;
    private Expression<Player> viewer;
    private boolean viewerSpecific;

    public static void bootstrap(TablistNameService tablistNameService) {
        service = tablistNameService;
        Skript.registerExpression(ExprTablistName.class, String.class, ExpressionType.PROPERTY,
                "tablist name of %player%",
                "tablist name of %player% for %player%",
                "tablist name of %player% for all players");
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean delayed,
                        SkriptParser.ParseResult parseResult) {
        target = (Expression<Player>) expressions[0];
        viewerSpecific = matchedPattern == 1;
        if (viewerSpecific) viewer = (Expression<Player>) expressions[1];
        return true;
    }

    @Override
    protected String[] get(Event event) {
        Player targetPlayer = target.getSingle(event);
        Player viewerPlayer = viewerSpecific ? viewer.getSingle(event) : null;
        if (targetPlayer == null || (viewerSpecific && viewerPlayer == null)) return new String[0];
        String value = viewerSpecific
                ? service.resolveTablistName(targetPlayer, viewerPlayer)
                : service.resolveTablistName(targetPlayer);
        return value == null ? new String[0] : new String[]{value};
    }

    @Override
    public Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET -> new Class<?>[]{String.class};
            case RESET, DELETE -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object[] delta, ChangeMode mode) {
        Player targetPlayer = target.getSingle(event);
        Player viewerPlayer = viewerSpecific ? viewer.getSingle(event) : null;
        if (targetPlayer == null || (viewerSpecific && viewerPlayer == null)) return;
        if (mode == ChangeMode.RESET || mode == ChangeMode.DELETE) {
            if (viewerSpecific) service.resetTablistName(targetPlayer, viewerPlayer);
            else service.resetTablistName(targetPlayer);
        } else if (mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] != null) {
            if (viewerSpecific) service.setTablistName(targetPlayer, viewerPlayer, String.valueOf(delta[0]));
            else service.setTablistName(targetPlayer, String.valueOf(delta[0]));
        }
    }

    @Override public boolean isSingle() { return true; }
    @Override public Class<? extends String> getReturnType() { return String.class; }
    @Override public String toString(Event event, boolean debug) { return "tablist name expression"; }
}
