package org.kkaemok.skkaemok.service;

import com.comphenix.protocol.wrappers.EnumWrappers;
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
                                                 EnumWrappers.ChatFormatting expected) {
        assertEquals(expected, VanillaTeamPacketCodec.color(color));
    }

    static Stream<Arguments> colors() {
        return Stream.of(
                Arguments.of(NamedTextColor.BLACK, EnumWrappers.ChatFormatting.BLACK),
                Arguments.of(NamedTextColor.DARK_BLUE, EnumWrappers.ChatFormatting.DARK_BLUE),
                Arguments.of(NamedTextColor.DARK_GREEN, EnumWrappers.ChatFormatting.DARK_GREEN),
                Arguments.of(NamedTextColor.DARK_AQUA, EnumWrappers.ChatFormatting.DARK_AQUA),
                Arguments.of(NamedTextColor.DARK_RED, EnumWrappers.ChatFormatting.DARK_RED),
                Arguments.of(NamedTextColor.DARK_PURPLE, EnumWrappers.ChatFormatting.DARK_PURPLE),
                Arguments.of(NamedTextColor.GOLD, EnumWrappers.ChatFormatting.GOLD),
                Arguments.of(NamedTextColor.GRAY, EnumWrappers.ChatFormatting.GRAY),
                Arguments.of(NamedTextColor.DARK_GRAY, EnumWrappers.ChatFormatting.DARK_GRAY),
                Arguments.of(NamedTextColor.BLUE, EnumWrappers.ChatFormatting.BLUE),
                Arguments.of(NamedTextColor.GREEN, EnumWrappers.ChatFormatting.GREEN),
                Arguments.of(NamedTextColor.AQUA, EnumWrappers.ChatFormatting.AQUA),
                Arguments.of(NamedTextColor.RED, EnumWrappers.ChatFormatting.RED),
                Arguments.of(NamedTextColor.LIGHT_PURPLE, EnumWrappers.ChatFormatting.LIGHT_PURPLE),
                Arguments.of(NamedTextColor.YELLOW, EnumWrappers.ChatFormatting.YELLOW),
                Arguments.of(NamedTextColor.WHITE, EnumWrappers.ChatFormatting.WHITE)
        );
    }

    @Test
    void missingColorUsesVanillaReset() {
        assertEquals(EnumWrappers.ChatFormatting.RESET, VanillaTeamPacketCodec.color(null));
    }

    private static VanillaTeamService.TeamInfo team(boolean friendlyFire, boolean friendlyInvisibles) {
        return new VanillaTeamService.TeamInfo(
                "test", Component.empty(), Component.empty(), Component.empty(), null,
                friendlyFire, friendlyInvisibles,
                Team.OptionStatus.ALWAYS, Team.OptionStatus.ALWAYS, Team.OptionStatus.ALWAYS
        );
    }
}
