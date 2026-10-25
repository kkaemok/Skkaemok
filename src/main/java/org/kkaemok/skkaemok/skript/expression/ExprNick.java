package org.kkaemok.skkaemok.skript.expression;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.*;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.kkaemok.skkaemok.appearance.*;

@SuppressWarnings("unchecked")
public final class ExprNick extends SimpleExpression<String> {
    public static AppearanceService service;
    private Expression<OfflinePlayer> target, viewer;

    @Override
    public boolean init(
            Expression<?>[] expressions,
            int pattern,
            Kleenean delayed,
            SkriptParser.ParseResult result) {
        target = (Expression<OfflinePlayer>) expressions[0];
        viewer = pattern == 1 ? (Expression<OfflinePlayer>) expressions[1] : null;
        return true;
    }

    private AppearanceScope scope(Event event) {
        OfflinePlayer t = target.getSingle(event),
                v = viewer == null ? null : viewer.getSingle(event);
        return t == null || (viewer != null && v == null)
                ? null
                : new AppearanceScope(t.getUniqueId(), v == null ? null : v.getUniqueId());
    }

    @Override
    protected String[] get(Event event) {
        AppearanceScope scope = scope(event);
        if (scope == null) return new String[0];
        String name = service.capture(scope).nametag();
        if (name == null && scope.viewer() != null)
            name = service.capture(new AppearanceScope(scope.target(), null)).nametag();
        if (name == null) {
            OfflinePlayer player = target.getSingle(event);
            name = player.getName() == null ? player.getUniqueId().toString() : player.getName();
        }
        return new String[] {name};
    }

    @Override
    public Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET -> new Class<?>[] {String.class};
            case RESET, DELETE -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object[] delta, ChangeMode mode) {
        AppearanceScope scope = scope(event);
        if (scope == null) return;
        String value =
                mode == ChangeMode.SET && delta != null && delta.length > 0
                        ? String.valueOf(delta[0])
                        : null;
        var result = service.setNickname(scope, value, null);
        if (!result.success()) ch.njol.skript.Skript.warning("Skkaemok: " + result.message());
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
        return "skkaemok nick";
    }
}
