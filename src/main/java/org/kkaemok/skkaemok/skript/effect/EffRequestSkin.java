package org.kkaemok.skkaemok.skript.effect;

import ch.njol.skript.lang.*;
import ch.njol.util.Kleenean;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.kkaemok.skkaemok.appearance.AppearanceScope;
import org.kkaemok.skkaemok.skin.SkinService;

@SuppressWarnings("unchecked")
public final class EffRequestSkin extends Effect {
    public static SkinService service;
    private Expression<OfflinePlayer> target, viewer;
    private Expression<String> source;
    private boolean url;

    @Override
    public boolean init(
            Expression<?>[] expressions,
            int pattern,
            Kleenean delayed,
            SkriptParser.ParseResult result) {
        target = (Expression<OfflinePlayer>) expressions[0];
        boolean personal = pattern % 2 == 1;
        viewer = personal ? (Expression<OfflinePlayer>) expressions[1] : null;
        source = (Expression<String>) expressions[personal ? 2 : 1];
        url = pattern >= 2;
        return true;
    }

    @Override
    protected void execute(Event event) {
        OfflinePlayer t = target.getSingle(event),
                v = viewer == null ? null : viewer.getSingle(event);
        String value = source.getSingle(event);
        if (t == null || value == null || (viewer != null && v == null)) return;
        EffAppearance.report(
                service.requestSkin(
                        new AppearanceScope(t.getUniqueId(), v == null ? null : v.getUniqueId()),
                        value,
                        url));
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "request skkaemok skin";
    }
}
