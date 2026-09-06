package org.kkaemok.skkaemok.skript.condition;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.kkaemok.skkaemok.service.NicknameService;

@SuppressWarnings("unchecked")
public final class CondHasCustomNametag extends Condition {
    private static NicknameService service;
    private Expression<Player> target;
    private Expression<Player> viewer;
    private boolean viewerSpecific;

    public static void bootstrap(NicknameService nicknameService) {
        service = nicknameService;
        Skript.registerCondition(CondHasCustomNametag.class,
                "%player% has [a] custom nametag",
                "%player% does(n't| not) have [a] custom nametag",
                "%player% has [a] custom nametag for %player%",
                "%player% does(n't| not) have [a] custom nametag for %player%");
    }

    @Override
    public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean delayed,
                        SkriptParser.ParseResult parseResult) {
        target = (Expression<Player>) expressions[0];
        viewerSpecific = matchedPattern >= 2;
        if (viewerSpecific) viewer = (Expression<Player>) expressions[1];
        setNegated(matchedPattern % 2 == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        Player targetPlayer = target.getSingle(event);
        Player viewerPlayer = viewerSpecific ? viewer.getSingle(event) : null;
        if (targetPlayer == null || (viewerSpecific && viewerPlayer == null)) return false;
        boolean result = viewerSpecific
                ? service.hasCustomNickname(targetPlayer, viewerPlayer)
                : service.hasCustomNickname(targetPlayer);
        return isNegated() != result;
    }

    @Override public String toString(Event event, boolean debug) { return "has custom nametag"; }
}
