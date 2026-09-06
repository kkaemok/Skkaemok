package org.kkaemok.skkaemok.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Builds the explicit PLAYER_INFO display name independently from the profile name. */
final class TablistPresentation {
    static final String DEFAULT_TEMPLATE = "%prefix%%nickname%%suffix%";

    private record Value(String token, Component component) {
    }

    private TablistPresentation() {
    }

    static boolean requiresExplicitDisplayName(String profileName,
                                               String effectiveTablistName,
                                               boolean hasCustomTablistName) {
        return hasCustomTablistName || !Objects.equals(profileName, effectiveTablistName);
    }

    static boolean usesLuckPerms(String template, boolean hasLuckPermsDecoration) {
        return hasLuckPermsDecoration && template != null
                && (template.contains("%lp_prefix%") || template.contains("%lp_suffix%"));
    }

    static Component compose(String template,
                             Component teamPrefix,
                             Component luckPermsPrefix,
                             Component effectiveName,
                             Component luckPermsSuffix,
                             Component teamSuffix,
                             Component originalName,
                             TextColor teamColor,
                             Function<String, Component> literalParser) {
        if (template == null || template.isEmpty()) {
            template = DEFAULT_TEMPLATE;
        }

        List<Value> values = List.of(
                // These legacy aliases retain their existing vanilla-team meaning.
                new Value("%prefix%", colorIfAbsent(teamPrefix, teamColor)),
                new Value("%nickname%", colorIfAbsent(effectiveName, teamColor)),
                new Value("%suffix%", colorIfAbsent(teamSuffix, teamColor)),
                new Value("%original%", colorIfAbsent(originalName, teamColor)),
                new Value("%team_prefix%", colorIfAbsent(teamPrefix, teamColor)),
                new Value("%team_suffix%", colorIfAbsent(teamSuffix, teamColor)),
                new Value("%lp_prefix%", colorIfAbsent(luckPermsPrefix, teamColor)),
                new Value("%lp_suffix%", colorIfAbsent(luckPermsSuffix, teamColor))
        );
        return render(template, values, value -> colorIfAbsent(literalParser.apply(value), teamColor));
    }

    private static Component render(String template,
                                    List<Value> values,
                                    Function<String, Component> literalParser) {
        Component result = Component.empty();
        int index = 0;
        while (index < template.length()) {
            Value nextValue = null;
            int next = -1;
            for (Value value : values) {
                int candidate = template.indexOf(value.token(), index);
                if (candidate >= 0 && (next < 0 || candidate < next)) {
                    next = candidate;
                    nextValue = value;
                }
            }
            if (next < 0) {
                return result.append(literalParser.apply(template.substring(index)));
            }
            if (next > index) {
                result = result.append(literalParser.apply(template.substring(index, next)));
            }
            result = result.append(nextValue.component());
            index = next + nextValue.token().length();
        }
        return result;
    }

    private static Component colorIfAbsent(Component component, TextColor color) {
        Component safe = component == null ? Component.empty() : component;
        return color == null ? safe : safe.colorIfAbsent(color);
    }
}
