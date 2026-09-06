package org.kkaemok.skkaemok.skript.condition;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.kkaemok.skkaemok.service.TablistNameService;

@SuppressWarnings("unchecked")
public final class CondHasCustomTablistName extends Condition {
    private static TablistNameService service;
    private Expression<Player> target;
    private Expression<Player> viewer;
    private boolean viewerSpecific;

    public static void bootstrap(TablistNameService tablistNameService) {
        service = tablistNameService;
        Skript.registerCondition(CondHasCustomTablistName.class,
                "%player% has [a] custom tablist name",
                "%player% does(n't| not) have [a] custom tablist name",
                "%player% has [a] custom tablist name for %player%",
                "%player% does(n't| not) have [a] custom tablist name for %player%");
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
                ? service.hasCustomTablistName(targetPlayer, viewerPlayer)
                : service.hasCustomTablistName(targetPlayer);
        return isNegated() != result;
    }

    @Override public String toString(Event event, boolean debug) { return "has custom tablist name"; }
}
