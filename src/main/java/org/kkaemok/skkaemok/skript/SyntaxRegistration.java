package org.kkaemok.skkaemok.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.*;

import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.appearance.AppearanceScope;
import org.kkaemok.skkaemok.appearance.AppearanceService;
import org.skriptlang.skript.registration.*;
import org.skriptlang.skript.util.Priority;

/** Owns this addon's syntax registry, including precedence over overlapping vanilla syntax. */
public final class SyntaxRegistration {
    private static SyntaxRegistry registry;
    private static AppearanceService results;
    private static final Priority SPECIFIC = Priority.before(SyntaxInfo.SIMPLE);

    private SyntaxRegistration() {}

    public static void initialize(JavaPlugin plugin) {
        registry =
                Skript.instance()
                        .registerAddon(plugin.getClass(), plugin.getName())
                        .syntaxRegistry();
    }

    public static SyntaxRegistry registry() {
        return registry;
    }

    public static void results(AppearanceService service) {
        results = service;
    }

    public static String failure(org.bukkit.entity.Player target, org.bukkit.entity.Player viewer) {
        if (results == null || target == null) return "Skkaemok rejected the name change.";
        return "Skkaemok: "
                + results.lastResult(
                                new AppearanceScope(
                                        target.getUniqueId(),
                                        viewer == null ? null : viewer.getUniqueId()))
                        .message();
    }

    public static <E extends Effect> void effect(Class<E> type, String... patterns) {
        registry.register(
                SyntaxRegistry.EFFECT,
                SyntaxInfo.builder(type).priority(SPECIFIC).addPatterns(patterns).build());
    }

    public static <E extends Condition> void condition(Class<E> type, String... patterns) {
        registry.register(
                SyntaxRegistry.CONDITION,
                SyntaxInfo.builder(type).priority(SPECIFIC).addPatterns(patterns).build());
    }

    public static <R, E extends Expression<R>> void expression(
            Class<E> type, Class<R> result, String... patterns) {
        registry.register(
                SyntaxRegistry.EXPRESSION,
                DefaultSyntaxInfos.Expression.builder(type, result)
                        .priority(SPECIFIC)
                        .addPatterns(patterns)
                        .build());
    }
}
