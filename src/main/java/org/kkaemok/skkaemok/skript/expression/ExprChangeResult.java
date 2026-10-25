package org.kkaemok.skkaemok.skript.expression;

import ch.njol.skript.lang.*;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.kkaemok.skkaemok.appearance.*;

import java.util.Locale;

@SuppressWarnings("unchecked")
public final class ExprChangeResult extends SimpleExpression<String> {
    public static AppearanceService service;
    private Expression<OfflinePlayer> target, viewer;
    private int pattern;

    @Override
    public boolean init(
            Expression<?>[] expressions,
            int pattern,
            Kleenean delayed,
            SkriptParser.ParseResult result) {
        this.pattern = pattern;
        target = (Expression<OfflinePlayer>) expressions[0];
        viewer = pattern % 2 == 1 ? (Expression<OfflinePlayer>) expressions[1] : null;
        return true;
    }

    @Override
    protected String[] get(Event event) {
        OfflinePlayer t = target.getSingle(event),
                v = viewer == null ? null : viewer.getSingle(event);
        if (t == null || (viewer != null && v == null)) return new String[0];
        ChangeResult result =
                service.lastResult(
                        new AppearanceScope(t.getUniqueId(), v == null ? null : v.getUniqueId()));
        return new String[] {
            pattern < 2 ? result.status().name().toLowerCase(Locale.ROOT) : result.message()
        };
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "last skkaemok change result";
    }
}
