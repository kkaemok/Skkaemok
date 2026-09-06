package org.kkaemok.skkaemok.service;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.function.Function;

final class NametagComposition {
    static final String DEFAULT_TEMPLATE = "%team_prefix%%prefix%%nickname%%suffix%%team_suffix%";

    record Parts(Component prefix, Component suffix) {
    }

    private record Value(String token, Component component) {
    }

    private NametagComposition() {
    }

    static Parts compose(String template,
                         Component teamPrefix,
                         Component luckPermsPrefix,
                         Component luckPermsSuffix,
                         Component teamSuffix,
                         Component original,
                         Function<String, Component> literalParser) {
        if (template == null || !template.contains("%nickname%")) {
            template = DEFAULT_TEMPLATE;
        }

        int nicknameToken = template.indexOf("%nickname%");
        List<Value> values = List.of(
                new Value("%team_prefix%", teamPrefix),
                new Value("%prefix%", luckPermsPrefix),
                new Value("%nickname%", Component.empty()),
                new Value("%suffix%", luckPermsSuffix),
                new Value("%team_suffix%", teamSuffix),
                new Value("%original%", original)
        );
        Component prefix = render(template.substring(0, nicknameToken), values, literalParser);
        Component suffix = render(
                template.substring(nicknameToken + "%nickname%".length()),
                values,
                literalParser
        );
        return new Parts(prefix, suffix);
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
}
