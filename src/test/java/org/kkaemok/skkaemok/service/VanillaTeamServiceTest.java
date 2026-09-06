package org.kkaemok.skkaemok.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VanillaTeamServiceTest {
    @Test
    void readsEveryPaperTeamPropertyUsingTheRealAccountName() {
        Player player = mock(Player.class);
        Scoreboard scoreboard = mock(Scoreboard.class);
        Team team = mock(Team.class);
        when(player.getName()).thenReturn("ImSoVeryCUTE");
        when(scoreboard.getEntryTeam("ImSoVeryCUTE")).thenReturn(team);
        when(team.getName()).thenReturn("red");
        when(team.displayName()).thenReturn(Component.text("Red team"));
        when(team.prefix()).thenReturn(Component.text("[RED]"));
        when(team.suffix()).thenReturn(Component.text("★"));
        when(team.hasColor()).thenReturn(true);
        when(team.color()).thenReturn(NamedTextColor.RED);
        when(team.allowFriendlyFire()).thenReturn(false);
        when(team.canSeeFriendlyInvisibles()).thenReturn(true);
        when(team.getOption(Team.Option.NAME_TAG_VISIBILITY)).thenReturn(Team.OptionStatus.NEVER);
        when(team.getOption(Team.Option.COLLISION_RULE)).thenReturn(Team.OptionStatus.FOR_OTHER_TEAMS);
        when(team.getOption(Team.Option.DEATH_MESSAGE_VISIBILITY)).thenReturn(Team.OptionStatus.FOR_OWN_TEAM);

        VanillaTeamService.TeamInfo info = new VanillaTeamService(() -> scoreboard).getInfo(player);

        assertEquals("red", info.sourceTeamName());
        assertEquals(Component.text("Red team"), info.displayName());
        assertEquals(Component.text("[RED]"), info.prefix());
        assertEquals(Component.text("★"), info.suffix());
        assertEquals(NamedTextColor.RED, info.color());
        assertFalse(info.allowFriendlyFire());
        assertTrue(info.canSeeFriendlyInvisibles());
        assertEquals(Team.OptionStatus.NEVER, info.nameTagVisibility());
        assertEquals(Team.OptionStatus.FOR_OTHER_TEAMS, info.collisionRule());
        assertEquals(Team.OptionStatus.FOR_OWN_TEAM, info.deathMessageVisibility());
        verify(scoreboard).getEntryTeam("ImSoVeryCUTE");
    }

    @Test
    void leavingATeamRestoresVanillaDefaults() {
        Player player = mock(Player.class);
        Scoreboard scoreboard = mock(Scoreboard.class);
        when(player.getName()).thenReturn("ImSoVeryCUTE");

        VanillaTeamService.TeamInfo info = new VanillaTeamService(() -> scoreboard).getInfo(player);

        assertNull(info.sourceTeamName());
        assertEquals(Component.empty(), info.prefix());
        assertEquals(Component.empty(), info.suffix());
        assertNull(info.color());
        assertTrue(info.allowFriendlyFire());
        assertFalse(info.canSeeFriendlyInvisibles());
        assertEquals(Team.OptionStatus.ALWAYS, info.nameTagVisibility());
        assertEquals(Team.OptionStatus.ALWAYS, info.collisionRule());
        assertEquals(Team.OptionStatus.ALWAYS, info.deathMessageVisibility());
    }

    @Test
    void acceptsARealTeamWhoseNameLooksLikeALegacySyntheticTeam() {
        Player player = mock(Player.class);
        Scoreboard scoreboard = mock(Scoreboard.class);
        Team team = mock(Team.class);
        when(player.getName()).thenReturn("Steve");
        when(scoreboard.getEntryTeam("Steve")).thenReturn(team);
        when(team.getName()).thenReturn("sm1234567890123");

        VanillaTeamService.TeamInfo info = new VanillaTeamService(() -> scoreboard).getInfo(player);

        assertEquals("sm1234567890123", info.sourceTeamName());
        assertTrue(info.hasTeam());
    }

    @ParameterizedTest
    @EnumSource(Team.OptionStatus.class)
    void preservesEveryDeathMessageVisibilityStatus(Team.OptionStatus status) {
        Team team = mock(Team.class);
        when(team.getName()).thenReturn("red");
        when(team.getOption(Team.Option.DEATH_MESSAGE_VISIBILITY)).thenReturn(status);

        assertEquals(status, VanillaTeamService.snapshot(team).deathMessageVisibility());
    }
}
