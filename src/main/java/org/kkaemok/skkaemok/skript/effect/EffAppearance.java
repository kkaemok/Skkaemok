package org.kkaemok.skkaemok.skript.effect;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.*;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.kkaemok.skkaemok.appearance.*;

import java.time.Duration;

/** Explicit forms keep viewer and duration arguments distinct. */
@SuppressWarnings("unchecked")
public final class EffAppearance extends Effect {
    public static AppearanceService service;
    private Expression<?>[] expressions;
    private int pattern;

    @Override
    public boolean init(
            Expression<?>[] expressions,
            int pattern,
            Kleenean delayed,
            SkriptParser.ParseResult result) {
        this.expressions = expressions;
        this.pattern = pattern;
        return true;
    }

    private <T> T value(int index, Event event) {
        return index >= expressions.length || expressions[index] == null
                ? null
                : (T) expressions[index].getSingle(event);
    }

    private Duration duration(int index, Event event) {
        Timespan span = value(index, event);
        return span == null
                ? (index < expressions.length && expressions[index] != null ? Duration.ZERO : null)
                : span.isInfinite() ? Duration.ofSeconds(Long.MAX_VALUE) : span.getDuration();
    }

    @Override
    protected void execute(Event event) {
        ChangeResult result;
        if (pattern == 8) {
            String id = value(0, event);
            if (id != null) report(service.deletePreset(id));
            return;
        }
        boolean viewerSpecific = pattern % 2 == 1;
        int targetIndex = pattern == 4 || pattern == 5 ? 1 : 0;
        OfflinePlayer target = value(targetIndex, event);
        OfflinePlayer viewer = viewerSpecific ? value(targetIndex + 1, event) : null;
        if (target == null || (viewerSpecific && viewer == null)) return;
        AppearanceScope scope =
                new AppearanceScope(
                        target.getUniqueId(), viewer == null ? null : viewer.getUniqueId());
        int offset = targetIndex + (viewerSpecific ? 2 : 1);
        switch (pattern) {
            case 0, 1 -> {
                String nick = value(offset, event);
                if (nick == null) return;
                result = service.setNickname(scope, nick, duration(offset + 1, event));
            }
            case 2, 3 -> {
                String id = value(offset, event);
                if (id == null) return;
                result = service.savePreset(id, scope);
            }
            case 4, 5 -> {
                String id = value(0, event);
                if (id == null) return;
                result = service.applyPreset(id, scope, duration(offset, event));
            }
            case 6, 7 -> result = service.reset(scope);
            case 9, 10 -> throw new IllegalStateException("Batch patterns use their own effect");
            default -> throw new IllegalStateException("Unknown appearance effect");
        }
        report(result);
    }

    static void report(ChangeResult result) {
        if (!result.success()) Skript.warning("Skkaemok: " + result.message());
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "change skkaemok appearance";
    }
}
