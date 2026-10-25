package org.kkaemok.skkaemok.skript.effect;

import ch.njol.skript.lang.*;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.kkaemok.skkaemok.appearance.*;
import org.kkaemok.skkaemok.skin.SkinData;

@SuppressWarnings("unchecked")
public final class EffBatchAppearance extends Effect {
    public static AppearanceService service;
    private Expression<?>[] expressions;
    private boolean viewerSpecific;

    @Override
    public boolean init(
            Expression<?>[] expressions,
            int pattern,
            Kleenean delayed,
            SkriptParser.ParseResult result) {
        this.expressions = expressions;
        viewerSpecific = pattern == 1;
        return true;
    }

    private <T> T value(int index, Event event) {
        return index >= expressions.length || expressions[index] == null
                ? null
                : (T) expressions[index].getSingle(event);
    }

    @Override
    protected void execute(Event event) {
        OfflinePlayer target = value(0, event), viewer = viewerSpecific ? value(1, event) : null;
        if (target == null || (viewerSpecific && viewer == null)) return;
        int offset = viewerSpecific ? 2 : 1;
        String tag = value(offset, event),
                chat = value(offset + 1, event),
                tab = value(offset + 2, event);
        if (tag == null || chat == null || tab == null) return;
        AppearanceScope scope =
                new AppearanceScope(
                        target.getUniqueId(), viewer == null ? null : viewer.getUniqueId());
        Player source = value(offset + 3, event);
        SkinData skin = service.capture(scope).skin();
        if (expressions[offset + 3] != null) {
            if (source == null) return;
            var texture =
                    source.getPlayerProfile().getProperties().stream()
                            .filter(p -> p.getName().equals("textures"))
                            .findFirst();
            if (texture.isEmpty()) {
                EffAppearance.report(
                        service.reject(
                                scope,
                                ChangeResult.Status.INVALID_SKIN,
                                "The source player has no skin texture."));
                return;
            }
            var property = texture.get();
            skin =
                    new SkinData(
                            property.getValue(),
                            property.getSignature(),
                            source.getName(),
                            System.currentTimeMillis());
        }
        Timespan span = value(offset + 4, event);
        java.time.Duration duration =
                span == null
                        ? (expressions[offset + 4] == null ? null : java.time.Duration.ZERO)
                        : span.isInfinite()
                                ? java.time.Duration.ofSeconds(Long.MAX_VALUE)
                                : span.getDuration();
        EffAppearance.report(service.apply(scope, new Appearance(tag, chat, tab, skin), duration));
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "apply skkaemok appearance batch";
    }
}
