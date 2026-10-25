package org.kkaemok.skkaemok.skript.expression;

import ch.njol.skript.lang.*;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;

import org.bukkit.event.Event;
import org.kkaemok.skkaemok.appearance.AppearanceService;

public final class ExprAppearancePresets extends SimpleExpression<String> {
    public static AppearanceService service;

    @Override
    public boolean init(
            Expression<?>[] expressions,
            int pattern,
            Kleenean delayed,
            SkriptParser.ParseResult result) {
        return true;
    }

    @Override
    protected String[] get(Event event) {
        return service.presetNames().toArray(String[]::new);
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    public boolean isSingle() {
        return false;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "skkaemok appearance presets";
    }
}
