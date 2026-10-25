package org.kkaemok.skkaemok.skript.event;

import ch.njol.skript.lang.*;

import org.bukkit.event.Event;
import org.kkaemok.skkaemok.appearance.AppearanceField;
import org.kkaemok.skkaemok.event.*;

import java.util.Set;

public final class EvtAppearance extends SkriptEvent {
    private int pattern;

    @Override
    public boolean init(Literal<?>[] arguments, int pattern, SkriptParser.ParseResult result) {
        this.pattern = pattern;
        return true;
    }

    @Override
    public boolean check(Event event) {
        if (pattern == 13) return event instanceof SkinRequestFailedEvent;
        if (pattern == 12)
            return event instanceof AppearanceChangedEvent changed
                    && changed.getFields().contains(AppearanceField.SKIN)
                    && changed.getCurrent().skin() != null;
        boolean before = pattern % 2 == 0;
        Set<AppearanceField> fields;
        if (before && event instanceof AppearanceChangeEvent change) fields = change.getFields();
        else if (!before && event instanceof AppearanceChangedEvent changed)
            fields = changed.getFields();
        else return false;
        return switch (pattern / 2) {
            case 0 -> true;
            case 1 -> fields.contains(AppearanceField.NAMETAG);
            case 2 -> fields.contains(AppearanceField.CHAT);
            case 3 -> fields.contains(AppearanceField.TABLIST);
            case 4 -> fields.contains(AppearanceField.SKIN);
            case 5 -> fields.stream().anyMatch(field -> field != AppearanceField.SKIN);
            default -> false;
        };
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "skkaemok appearance event";
    }
}
