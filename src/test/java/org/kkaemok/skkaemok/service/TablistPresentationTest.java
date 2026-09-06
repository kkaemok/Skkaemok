package org.kkaemok.skkaemok.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TablistPresentationTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void viewerNametagDoesNotLeakIntoVanillaTablistName() {
        assertTrue(TablistPresentation.requiresExplicitDisplayName("PRIVATE", "Steve", false));
        assertEquals("[TEAM]Steve★", PLAIN.serialize(compose("Steve")));
    }

    @Test
    void globalNametagDoesNotLeakIntoVanillaTablistName() {
        assertTrue(TablistPresentation.requiresExplicitDisplayName("GLOBAL", "Steve", false));
        assertEquals("[TEAM]Steve★", PLAIN.serialize(compose("Steve")));
    }

    @Test
    void explicitViewerTablistNameWinsIndependently() {
        assertTrue(TablistPresentation.requiresExplicitDisplayName("PRIVATE", "TABPRIVATE", true));
        assertEquals("[TEAM]TABPRIVATE★", PLAIN.serialize(compose("TABPRIVATE")));
    }

    @Test
    void resettingViewerNametagKeepsResolvedTablistName() {
        Component beforeReset = compose("Steve");
        Component afterReset = compose("Steve");

        assertTrue(TablistPresentation.requiresExplicitDisplayName("PRIVATE", "Steve", false));
        assertTrue(TablistPresentation.requiresExplicitDisplayName("GLOBAL", "Steve", false));
        assertEquals(beforeReset, afterReset);
    }

    @Test
    void resettingViewerTablistKeepsNametagAndExplicitlyRestoresVanillaTabName() {
        assertTrue(TablistPresentation.requiresExplicitDisplayName("PRIVATE", "TABPRIVATE", true));
        assertTrue(TablistPresentation.requiresExplicitDisplayName("PRIVATE", "Steve", false));
        assertEquals("[TEAM]Steve★", PLAIN.serialize(compose("Steve")));
    }

    @Test
    void unmodifiedProfileAndTablistCanUseVanillaFallback() {
        assertFalse(TablistPresentation.requiresExplicitDisplayName("Steve", "Steve", false));
    }

    @Test
    void selfAndOtherViewerComponentsHaveIdenticalTeamColorAndFormatting() {
        String template = "%team_prefix%%lp_prefix%%nickname%%lp_suffix%%team_suffix%";
        Component self = compose(template, "GLOBAL");
        Component other = compose(template, "GLOBAL");

        assertEquals(other, self);
        assertEquals("[TEAM][LP]GLOBAL[LS]★", PLAIN.serialize(self));
        assertEquals(NamedTextColor.RED, findText(self, "[TEAM]").color());
        assertEquals(NamedTextColor.RED, findText(self, "GLOBAL").color());
        assertEquals(NamedTextColor.RED, findText(self, "★").color());
        assertEquals(NamedTextColor.GOLD, findText(self, "[LP]").color());
        assertEquals(TextDecoration.State.TRUE, findText(self, "[LP]").decoration(TextDecoration.BOLD));
    }

    private static Component compose(String effectiveName) {
        return compose(TablistPresentation.DEFAULT_TEMPLATE, effectiveName);
    }

    private static Component compose(String template, String effectiveName) {
        return TablistPresentation.compose(
                template,
                Component.text("[TEAM]"),
                Component.text("[LP]", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text(effectiveName),
                Component.text("[LS]", NamedTextColor.AQUA),
                Component.text("★"),
                Component.text("Steve"),
                NamedTextColor.RED,
                Component::text
        );
    }

    private static Component findText(Component component, String value) {
        Component found = findTextOrNull(component, value);
        assertNotNull(found, "Missing text component: " + value);
        return found;
    }

    private static Component findTextOrNull(Component component, String value) {
        if (component instanceof TextComponent text && text.content().equals(value)) {
            return component;
        }
        for (Component child : component.children()) {
            Component found = findTextOrNull(child, value);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
