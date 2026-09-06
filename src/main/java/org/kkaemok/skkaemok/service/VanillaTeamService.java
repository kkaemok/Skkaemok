package org.kkaemok.skkaemok.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.function.Supplier;

/**
 * Reads the authoritative vanilla team using the player's real account name.
 * Synthetic nickname teams are packet-only and are never registered here.
 */
public final class VanillaTeamService {
    public record TeamInfo(String sourceTeamName,
                           Component displayName,
                           Component prefix,
                           Component suffix,
                           TextColor color,
                           boolean allowFriendlyFire,
                           boolean canSeeFriendlyInvisibles,
                           Team.OptionStatus nameTagVisibility,
                           Team.OptionStatus collisionRule,
                           Team.OptionStatus deathMessageVisibility) {
        public boolean hasTeam() {
            return sourceTeamName != null;
        }

        public boolean hasDecoration() {
            return !prefix.equals(Component.empty())
                    || !suffix.equals(Component.empty())
                    || color != null;
        }
    }

    private static final TeamInfo EMPTY = new TeamInfo(
            null,
            Component.empty(),
            Component.empty(),
            Component.empty(),
            null,
            true,
            false,
            Team.OptionStatus.ALWAYS,
            Team.OptionStatus.ALWAYS,
            Team.OptionStatus.ALWAYS
    );

    private final Supplier<Scoreboard> mainScoreboard;

    public VanillaTeamService() {
        this(() -> {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            return manager == null ? null : manager.getMainScoreboard();
        });
    }

    VanillaTeamService(Supplier<Scoreboard> mainScoreboard) {
        if (mainScoreboard == null) {
            throw new IllegalArgumentException("Main scoreboard supplier cannot be null");
        }
        this.mainScoreboard = mainScoreboard;
    }

    public TeamInfo getInfo(Player player) {
        if (player == null) {
            return EMPTY;
        }

        Scoreboard scoreboard = mainScoreboard.get();
        if (scoreboard == null) {
            return EMPTY;
        }

        // A nickname is deliberately never used for this lookup. The real account
        // name remains the authoritative server-side scoreboard entry.
        Team team = scoreboard.getEntryTeam(player.getName());
        if (team == null) {
            return EMPTY;
        }

        return snapshot(team);
    }

    static TeamInfo snapshot(Team team) {
        if (team == null) {
            return EMPTY;
        }
        return new TeamInfo(
                team.getName(),
                safeComponent(team.displayName()),
                safeComponent(team.prefix()),
                safeComponent(team.suffix()),
                team.hasColor() ? team.color() : null,
                team.allowFriendlyFire(),
                team.canSeeFriendlyInvisibles(),
                safeOption(team, Team.Option.NAME_TAG_VISIBILITY),
                safeOption(team, Team.Option.COLLISION_RULE),
                safeOption(team, Team.Option.DEATH_MESSAGE_VISIBILITY)
        );
    }

    static TeamInfo emptyInfo() {
        return EMPTY;
    }

    private static Component safeComponent(Component component) {
        return component == null ? Component.empty() : component;
    }

    private static Team.OptionStatus safeOption(Team team, Team.Option option) {
        Team.OptionStatus status = team.getOption(option);
        return status == null ? Team.OptionStatus.ALWAYS : status;
    }
}
