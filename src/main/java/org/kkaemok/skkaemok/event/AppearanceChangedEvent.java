package org.kkaemok.skkaemok.event;

import org.bukkit.event.*;
import org.kkaemok.skkaemok.appearance.*;

import java.util.Set;

public final class AppearanceChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final AppearanceScope scope;
    private final Appearance previous, current;
    private final Set<AppearanceField> fields;
    private final String cause;

    public AppearanceChangedEvent(
            AppearanceScope scope,
            Appearance previous,
            Appearance current,
            Set<AppearanceField> fields,
            String cause) {
        this.scope = scope;
        this.previous = previous;
        this.current = current;
        this.fields = Set.copyOf(fields);
        this.cause = cause;
    }

    public AppearanceScope getScope() {
        return scope;
    }

    public Appearance getPrevious() {
        return previous;
    }

    public Appearance getCurrent() {
        return current;
    }

    public Set<AppearanceField> getFields() {
        return fields;
    }

    public String getCause() {
        return cause;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
