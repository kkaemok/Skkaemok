package org.kkaemok.skkaemok.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VanillaTeamPacketCodecTest {
    @ParameterizedTest
    @MethodSource("friendlyFlags")
    void serializesBothFriendlyFlags(boolean friendlyFire, boolean friendlyInvisibles, int expected) {
        assertEquals(expected, VanillaTeamPacketCodec.friendlyFlags(team(friendlyFire, friendlyInvisibles)));
    }

    static Stream<Arguments> friendlyFlags() {
        return Stream.of(
                Arguments.of(false, false, 0),
                Arguments.of(true, false, 1),
                Arguments.of(false, true, 2),
                Arguments.of(true, true, 3)
        );
    }

    @ParameterizedTest
    @MethodSource("visibilityValues")
    void serializesEveryNameTagVisibilityExactly(Team.OptionStatus status, String expected) {
        assertEquals(expected, VanillaTeamPacketCodec.nameTagVisibility(status));
    }

    static Stream<Arguments> visibilityValues() {
        return Stream.of(
                Arguments.of(Team.OptionStatus.ALWAYS, "always"),
                Arguments.of(Team.OptionStatus.NEVER, "never"),
                Arguments.of(Team.OptionStatus.FOR_OWN_TEAM, "hideForOtherTeams"),
                Arguments.of(Team.OptionStatus.FOR_OTHER_TEAMS, "hideForOwnTeam")
        );
    }

    @ParameterizedTest
    @MethodSource("collisionValues")
    void serializesEveryCollisionRuleExactly(Team.OptionStatus status, String expected) {
        assertEquals(expected, VanillaTeamPacketCodec.collisionRule(status));
    }

    static Stream<Arguments> collisionValues() {
        return Stream.of(
                Arguments.of(Team.OptionStatus.ALWAYS, "always"),
                Arguments.of(Team.OptionStatus.NEVER, "never"),
                Arguments.of(Team.OptionStatus.FOR_OWN_TEAM, "pushOwnTeam"),
                Arguments.of(Team.OptionStatus.FOR_OTHER_TEAMS, "pushOtherTeams")
        );
    }

    @ParameterizedTest
    @MethodSource("colors")
    void serializesEverySupportedNamedTeamColor(NamedTextColor color,
                                                 NamedTextColor expected) {
        assertEquals(expected, VanillaTeamPacketCodec.color(color));
    }

    static Stream<Arguments> colors() {
        return Stream.of(
                Arguments.of(NamedTextColor.BLACK, NamedTextColor.BLACK),
                Arguments.of(NamedTextColor.DARK_BLUE, NamedTextColor.DARK_BLUE),
                Arguments.of(NamedTextColor.DARK_GREEN, NamedTextColor.DARK_GREEN),
                Arguments.of(NamedTextColor.DARK_AQUA, NamedTextColor.DARK_AQUA),
                Arguments.of(NamedTextColor.DARK_RED, NamedTextColor.DARK_RED),
                Arguments.of(NamedTextColor.DARK_PURPLE, NamedTextColor.DARK_PURPLE),
                Arguments.of(NamedTextColor.GOLD, NamedTextColor.GOLD),
                Arguments.of(NamedTextColor.GRAY, NamedTextColor.GRAY),
                Arguments.of(NamedTextColor.DARK_GRAY, NamedTextColor.DARK_GRAY),
                Arguments.of(NamedTextColor.BLUE, NamedTextColor.BLUE),
                Arguments.of(NamedTextColor.GREEN, NamedTextColor.GREEN),
                Arguments.of(NamedTextColor.AQUA, NamedTextColor.AQUA),
                Arguments.of(NamedTextColor.RED, NamedTextColor.RED),
                Arguments.of(NamedTextColor.LIGHT_PURPLE, NamedTextColor.LIGHT_PURPLE),
                Arguments.of(NamedTextColor.YELLOW, NamedTextColor.YELLOW),
                Arguments.of(NamedTextColor.WHITE, NamedTextColor.WHITE)
        );
    }

    @Test
    void missingColorUsesVanillaReset() {
        org.junit.jupiter.api.Assertions.assertNull(VanillaTeamPacketCodec.color(null));
    }

    private static VanillaTeamService.TeamInfo team(boolean friendlyFire, boolean friendlyInvisibles) {
        return new VanillaTeamService.TeamInfo(
                "test", Component.empty(), Component.empty(), Component.empty(), null,
                friendlyFire, friendlyInvisibles,
                Team.OptionStatus.ALWAYS, Team.OptionStatus.ALWAYS, Team.OptionStatus.ALWAYS
        );
    }
}
