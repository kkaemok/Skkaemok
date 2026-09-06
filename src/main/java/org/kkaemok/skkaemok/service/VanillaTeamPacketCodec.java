package org.kkaemok.skkaemok.service;

import com.comphenix.protocol.wrappers.EnumWrappers;
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

    static EnumWrappers.ChatFormatting color(TextColor color) {
        if (color instanceof NamedTextColor named) {
            if (named.equals(NamedTextColor.BLACK)) return EnumWrappers.ChatFormatting.BLACK;
            if (named.equals(NamedTextColor.DARK_BLUE)) return EnumWrappers.ChatFormatting.DARK_BLUE;
            if (named.equals(NamedTextColor.DARK_GREEN)) return EnumWrappers.ChatFormatting.DARK_GREEN;
            if (named.equals(NamedTextColor.DARK_AQUA)) return EnumWrappers.ChatFormatting.DARK_AQUA;
            if (named.equals(NamedTextColor.DARK_RED)) return EnumWrappers.ChatFormatting.DARK_RED;
            if (named.equals(NamedTextColor.DARK_PURPLE)) return EnumWrappers.ChatFormatting.DARK_PURPLE;
            if (named.equals(NamedTextColor.GOLD)) return EnumWrappers.ChatFormatting.GOLD;
            if (named.equals(NamedTextColor.GRAY)) return EnumWrappers.ChatFormatting.GRAY;
            if (named.equals(NamedTextColor.DARK_GRAY)) return EnumWrappers.ChatFormatting.DARK_GRAY;
            if (named.equals(NamedTextColor.BLUE)) return EnumWrappers.ChatFormatting.BLUE;
            if (named.equals(NamedTextColor.GREEN)) return EnumWrappers.ChatFormatting.GREEN;
            if (named.equals(NamedTextColor.AQUA)) return EnumWrappers.ChatFormatting.AQUA;
            if (named.equals(NamedTextColor.RED)) return EnumWrappers.ChatFormatting.RED;
            if (named.equals(NamedTextColor.LIGHT_PURPLE)) return EnumWrappers.ChatFormatting.LIGHT_PURPLE;
            if (named.equals(NamedTextColor.YELLOW)) return EnumWrappers.ChatFormatting.YELLOW;
            if (named.equals(NamedTextColor.WHITE)) return EnumWrappers.ChatFormatting.WHITE;
        }
        return EnumWrappers.ChatFormatting.RESET;
    }

    private static Team.OptionStatus safe(Team.OptionStatus status) {
        return status == null ? Team.OptionStatus.ALWAYS : status;
    }
}
