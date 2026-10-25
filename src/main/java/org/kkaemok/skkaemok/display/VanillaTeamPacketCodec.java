package org.kkaemok.skkaemok.display;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.scoreboard.Team;

/** Converts Bukkit/Paper team values to their exact vanilla wire representations. */
final class VanillaTeamPacketCodec {
    private VanillaTeamPacketCodec() {
    }

    static int friendlyFlags(VanillaTeamService.TeamInfo team) {
        int flags = 0;
        if (team.allowFriendlyFire()) {
            flags |= 0x01;
        }
        if (team.canSeeFriendlyInvisibles()) {
            flags |= 0x02;
        }
        return flags;
    }

    static String nameTagVisibility(Team.OptionStatus status) {
        return switch (safe(status)) {
            case NEVER -> "never";
            case FOR_OTHER_TEAMS -> "hideForOwnTeam";
            case FOR_OWN_TEAM -> "hideForOtherTeams";
            default -> "always";
        };
    }

    static String collisionRule(Team.OptionStatus status) {
        return switch (safe(status)) {
            case NEVER -> "never";
            case FOR_OTHER_TEAMS -> "pushOtherTeams";
            case FOR_OWN_TEAM -> "pushOwnTeam";
            default -> "always";
        };
    }

    static NamedTextColor color(TextColor color) {
        return color instanceof NamedTextColor named ? named : null;
    }

    private static Team.OptionStatus safe(Team.OptionStatus status) {
        return status == null ? Team.OptionStatus.ALWAYS : status;
    }
}
