package org.kkaemok.skkaemok.event;

import org.bukkit.event.*;
import org.kkaemok.skkaemok.appearance.*;

import java.util.Set;

/** Fired before any fields are changed; cancelling rejects the complete batch. */
public final class AppearanceChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final AppearanceScope scope;
    private final Appearance previous, proposed;
    private final Set<AppearanceField> fields;
    private final String cause;
    private boolean cancelled;

    public AppearanceChangeEvent(
            AppearanceScope scope,
            Appearance previous,
            Appearance proposed,
            Set<AppearanceField> fields,
            String cause) {
        this.scope = scope;
        this.previous = previous;
        this.proposed = proposed;
        this.fields = Set.copyOf(fields);
        this.cause = cause;
    }

    public AppearanceScope getScope() {
        return scope;
    }

    public Appearance getPrevious() {
        return previous;
    }

    public Appearance getProposed() {
        return proposed;
    }

    public Set<AppearanceField> getFields() {
        return fields;
    }

    public String getCause() {
        return cause;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean value) {
        cancelled = value;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
