package org.kkaemok.skkaemok.event;

import org.bukkit.event.*;
import org.kkaemok.skkaemok.appearance.AppearanceScope;

public final class SkinRequestFailedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final AppearanceScope scope;
    private final String source, reason;

    public SkinRequestFailedEvent(AppearanceScope scope, String source, String reason) {
        this.scope = scope;
        this.source = source;
        this.reason = reason;
    }

    public AppearanceScope getScope() {
        return scope;
    }

    public String getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
