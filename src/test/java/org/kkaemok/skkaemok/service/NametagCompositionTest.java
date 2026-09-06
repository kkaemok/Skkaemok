package org.kkaemok.skkaemok.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NametagCompositionTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void defaultCompositionKeepsVanillaAndLuckPermsDecorations() {
        NametagComposition.Parts parts = compose(NametagComposition.DEFAULT_TEMPLATE);

        assertEquals("[TEAM][LP]", PLAIN.serialize(parts.prefix()));
        assertEquals("[LS]★", PLAIN.serialize(parts.suffix()));
    }

    @Test
    void customCompositionControlsBothSidesOfTheProfileName() {
        NametagComposition.Parts parts = compose(
                "%prefix% <%team_prefix%%nickname%%team_suffix%> %suffix% (%original%)"
        );

        assertEquals("[LP] <[TEAM]", PLAIN.serialize(parts.prefix()));
        assertEquals("★> [LS] (ImSoVeryCUTE)", PLAIN.serialize(parts.suffix()));
    }

    @Test
    void missingNicknameTokenFallsBackToSafeDefault() {
        NametagComposition.Parts parts = compose("%team_prefix% only");

        assertEquals("[TEAM][LP]", PLAIN.serialize(parts.prefix()));
        assertEquals("[LS]★", PLAIN.serialize(parts.suffix()));
    }

    private static NametagComposition.Parts compose(String template) {
        return NametagComposition.compose(
                template,
                Component.text("[TEAM]"),
                Component.text("[LP]"),
                Component.text("[LS]"),
                Component.text("★"),
                Component.text("ImSoVeryCUTE"),
                Component::text
        );
    }
}
