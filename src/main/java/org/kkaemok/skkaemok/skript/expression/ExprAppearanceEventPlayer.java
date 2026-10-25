package org.kkaemok.skkaemok.skript.expression;

import ch.njol.skript.lang.*;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;

import org.bukkit.*;
import org.bukkit.event.Event;
import org.kkaemok.skkaemok.skript.event.AppearanceEventContext;

public final class ExprAppearanceEventPlayer extends SimpleExpression<OfflinePlayer> {
    private boolean viewer;

    @Override
    public boolean init(
            Expression<?>[] expressions,
            int pattern,
            Kleenean delayed,
            SkriptParser.ParseResult result) {
        viewer = pattern == 1;
        return true;
    }

    @Override
    protected OfflinePlayer[] get(Event event) {
        var scope = AppearanceEventContext.scope(event);
        if (scope == null) return new OfflinePlayer[0];
        var id = viewer ? scope.viewer() : scope.target();
        return id == null
                ? new OfflinePlayer[0]
                : new OfflinePlayer[] {Bukkit.getOfflinePlayer(id)};
    }

    @Override
    public Class<? extends OfflinePlayer> getReturnType() {
        return OfflinePlayer.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "appearance event player";
    }
}
