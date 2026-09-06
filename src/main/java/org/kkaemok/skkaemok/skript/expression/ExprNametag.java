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
import org.kkaemok.skkaemok.service.NicknameService;

@SuppressWarnings("unchecked")
public final class ExprNametag extends SimpleExpression<String> {
    private static NicknameService service;

    private Expression<Player> target;
    private Expression<Player> viewer;
    private boolean viewerSpecific;

    public static void bootstrap(NicknameService nicknameService) {
        service = nicknameService;
        Skript.registerExpression(
                ExprNametag.class,
                String.class,
                ExpressionType.PROPERTY,
                "nametag of %player%",
                "nametag of %player% for %player%",
                "nametag of %player% for all players"
        );
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean delayed,
                        SkriptParser.ParseResult parseResult) {
        target = (Expression<Player>) expressions[0];
        viewerSpecific = matchedPattern == 1;
        if (viewerSpecific) {
            viewer = (Expression<Player>) expressions[1];
        }
        return true;
    }

    @Override
    protected String[] get(Event event) {
        Player targetPlayer = target.getSingle(event);
        Player viewerPlayer = viewerSpecific ? viewer.getSingle(event) : null;
        if (targetPlayer == null || (viewerSpecific && viewerPlayer == null)) {
            return new String[0];
        }
        String value = viewerSpecific
                ? service.resolveNickname(targetPlayer, viewerPlayer)
                : service.resolveNickname(targetPlayer);
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
        if (targetPlayer == null || (viewerSpecific && viewerPlayer == null)) {
            return;
        }
        if (mode == ChangeMode.RESET || mode == ChangeMode.DELETE) {
            if (viewerSpecific) service.resetNickname(targetPlayer, viewerPlayer);
            else service.resetNickname(targetPlayer);
            return;
        }
        if (mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] != null) {
            boolean valid = viewerSpecific
                    ? service.setNickname(targetPlayer, viewerPlayer, String.valueOf(delta[0]))
                    : service.setNickname(targetPlayer, String.valueOf(delta[0]));
            if (!valid) {
                Skript.warning("Nametag must be 16 characters or fewer.");
            }
        }
    }

    @Override public boolean isSingle() { return true; }
    @Override public Class<? extends String> getReturnType() { return String.class; }
    @Override public String toString(Event event, boolean debug) { return "nametag expression"; }
}
