package org.kkaemok.skkaemok.skript.event;

import org.bukkit.event.Event;
import org.kkaemok.skkaemok.appearance.*;
import org.kkaemok.skkaemok.event.*;

public final class AppearanceEventContext {
    private AppearanceEventContext() {}

    public static AppearanceScope scope(Event event) {
        return event instanceof AppearanceChangeEvent change
                ? change.getScope()
                : event instanceof AppearanceChangedEvent changed
                        ? changed.getScope()
                        : event instanceof SkinRequestFailedEvent failed ? failed.getScope() : null;
    }

    public static Appearance previous(Event event) {
        return event instanceof AppearanceChangeEvent change
                ? change.getPrevious()
                : event instanceof AppearanceChangedEvent changed ? changed.getPrevious() : null;
    }

    public static Appearance next(Event event) {
        return event instanceof AppearanceChangeEvent change
                ? change.getProposed()
                : event instanceof AppearanceChangedEvent changed ? changed.getCurrent() : null;
    }

    public static String cause(Event event) {
        return event instanceof AppearanceChangeEvent change
                ? change.getCause()
                : event instanceof AppearanceChangedEvent changed
                        ? changed.getCause()
                        : event instanceof SkinRequestFailedEvent ? "skin request" : null;
    }
}
