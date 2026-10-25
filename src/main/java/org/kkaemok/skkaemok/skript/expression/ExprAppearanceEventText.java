package org.kkaemok.skkaemok.skript.expression;

import ch.njol.skript.lang.*;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;

import org.bukkit.event.Event;
import org.kkaemok.skkaemok.appearance.*;
import org.kkaemok.skkaemok.event.SkinRequestFailedEvent;
import org.kkaemok.skkaemok.skript.event.AppearanceEventContext;

public final class ExprAppearanceEventText extends SimpleExpression<String> {
    private int pattern;

    @Override
    public boolean init(
            Expression<?>[] expressions,
            int pattern,
            Kleenean delayed,
            SkriptParser.ParseResult result) {
        this.pattern = pattern;
        return true;
    }

    @Override
    protected String[] get(Event event) {
        String value;
        if (pattern == 6) value = AppearanceEventContext.cause(event);
        else if (pattern == 7)
            value = event instanceof SkinRequestFailedEvent failed ? failed.getReason() : null;
        else if (pattern == 8)
            value = event instanceof SkinRequestFailedEvent failed ? failed.getSource() : null;
        else if (pattern >= 9) {
            Appearance appearance =
                    pattern % 2 == 1
                            ? AppearanceEventContext.previous(event)
                            : AppearanceEventContext.next(event);
            var skin = appearance == null ? null : appearance.skin();
            value =
                    skin == null
                            ? null
                            : pattern < 11
                                    ? skin.getSource()
                                    : pattern < 13 ? skin.getValue() : skin.getSignature();
        } else {
            Appearance appearance =
                    pattern % 2 == 0
                            ? AppearanceEventContext.previous(event)
                            : AppearanceEventContext.next(event);
            value =
                    appearance == null
                            ? null
                            : switch (pattern / 2) {
                                case 0 -> appearance.nametag();
                                case 1 -> appearance.chat();
                                case 2 -> appearance.tablist();
                                default -> null;
                            };
        }
        return value == null ? new String[0] : new String[] {value};
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
        return "appearance event value";
    }
}
