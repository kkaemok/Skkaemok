package org.kkaemok.skkaemok.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.integration.LuckPermsHook;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class NameRewriteService {
    private static final String USERNAME_CHARACTER_CLASS = "[A-Za-z0-9_]";
    private static final String NO_REWRITE_MARKER = "\uE000skkaemok:no-rewrite\uE000";
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final ChatNameManager chatNameManager;
    private final LuckPermsHook luckPermsHook;
    private final VanillaTeamService vanillaTeamService;
    private final DisplayFormatter formatter;

    public NameRewriteService(ChatNameManager chatNameManager,
                              LuckPermsHook luckPermsHook,
                              VanillaTeamService vanillaTeamService,
                              JavaPlugin plugin) {
        if (chatNameManager == null || luckPermsHook == null || vanillaTeamService == null || plugin == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.chatNameManager = chatNameManager;
        this.luckPermsHook = luckPermsHook;
        this.vanillaTeamService = vanillaTeamService;
        this.formatter = new DisplayFormatter(plugin);
    }

    public void reload() {
        formatter.reload();
    }

    public static Component noRewrite(Component message) {
        if (message == null) {
            return Component.empty();
        }
        return Component.text(NO_REWRITE_MARKER).append(message);
    }

    public Component rewriteOnlinePlayerNames(Component message) {
        return rewriteOnlinePlayerNames(message, null);
    }

    public Component rewriteOnlinePlayerNames(Component message, Player viewer) {
        if (message == null) {
            return null;
        }
        if (containsNoRewriteMarker(message)) {
            return stripNoRewriteMarker(message);
        }

        Component result = message;
        for (NameReplacement replacement : buildReplacements(viewer)) {
            result = applyReplacement(result, replacement);
        }
        return result;
    }

    public Component rewritePlayerName(Component message, Player player) {
        return rewritePlayerName(message, player, null);
    }

    public Component rewritePlayerName(Component message, Player player, Player viewer) {
        if (message == null || player == null) {
            return message;
        }
        if (containsNoRewriteMarker(message)) {
            return stripNoRewriteMarker(message);
        }
        return applyReplacement(message, buildReplacement(player, viewer, true));
    }

    public Component rewritePlayerNames(Component message, Collection<Player> players) {
        return rewritePlayerNames(message, players, null);
    }

    public Component rewritePlayerNames(Component message, Collection<Player> players, Player viewer) {
        if (message == null || players == null || players.isEmpty()) {
            return message;
        }
        if (containsNoRewriteMarker(message)) {
            return stripNoRewriteMarker(message);
        }

        Component result = message;
        List<NameReplacement> replacements = new ArrayList<>();
        for (Player player : players) {
            if (player != null) {
                replacements.add(buildReplacement(player, viewer, true));
            }
        }
        replacements.sort(Comparator.comparingInt(NameReplacement::longestCandidateLength).reversed());

        for (NameReplacement replacement : replacements) {
            result = applyReplacement(result, replacement);
        }
        return result;
    }

    private List<NameReplacement> buildReplacements(Player viewer) {
        List<NameReplacement> replacements = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            NameReplacement replacement = buildReplacement(player, viewer, false);
            if (!replacement.noop()) {
                replacements.add(replacement);
            }
        }

        replacements.sort(Comparator.comparingInt(NameReplacement::longestCandidateLength).reversed());
        return replacements;
    }

    private NameReplacement buildReplacement(Player player, Player viewer, boolean includeUnchanged) {
        String originalName = player.getName();
        String chatName = chatNameManager.resolveChatName(player, viewer);
        boolean hasCustomChatName = chatNameManager.hasChatName(player)
                || (viewer != null && chatNameManager.hasChatName(player, viewer));
        if (!hasCustomChatName) {
            return NameReplacement.noop(originalName);
        }

        String cleanDisplayName = ChatNameFormatter.formatDisplayName(originalName, chatName);
        VanillaTeamService.TeamInfo teamInfo = vanillaTeamService.getInfo(player);
        Component displayComponent = buildVanillaCommandDisplay(cleanDisplayName, teamInfo);
        String displayName = plain(displayComponent);
        Set<String> candidates = new LinkedHashSet<>();

        candidates.add(originalName);
        String globalChatName = chatNameManager.getRawChatName(player);
        if (globalChatName != null && !globalChatName.isBlank()) {
            candidates.add(globalChatName);
        }
        addPlainCandidate(candidates, player.displayName());
        addLuckPermsCandidates(candidates, player, originalName, cleanDisplayName);
        addVanillaTeamCandidates(candidates, teamInfo, originalName, cleanDisplayName);
        candidates.removeIf(candidate -> candidate == null
                || candidate.isBlank()
                || candidate.equals(displayName));

        if (!includeUnchanged && displayName.equals(originalName) && candidates.size() <= 1) {
            candidates.clear();
        }

        return new NameReplacement(originalName, displayName, displayComponent, candidates,
                createUsernamePattern(originalName, plain(teamInfo.prefix()), plain(teamInfo.suffix())));
    }

    private Component applyReplacement(Component message, NameReplacement replacement) {
        if (message == null || replacement == null || replacement.noop()) {
            return message;
        }

        Component result = message;
        for (String candidate : replacement.candidates()) {
            if (candidate.equals(replacement.originalName())) {
                continue;
            }
            result = result.replaceText(builder -> builder
                    .matchLiteral(candidate)
                    .replacement(replacement.displayComponent()));
        }
        result = rewriteMatchingSubtrees(result, replacement);
        result = result.replaceText(builder -> builder
                .match(replacement.originalNamePattern())
                .replacement(replacement.displayComponent()));
        return result;
    }

    private boolean containsNoRewriteMarker(Component message) {
        return PLAIN_TEXT.serialize(message).contains(NO_REWRITE_MARKER);
    }

    private Component stripNoRewriteMarker(Component message) {
        return message.replaceText(builder -> builder
                .matchLiteral(NO_REWRITE_MARKER)
                .replacement(Component.empty()));
    }

    private Component rewriteMatchingSubtrees(Component component, NameReplacement replacement) {
        String plain = PLAIN_TEXT.serialize(component);
        if (replacement.matchesCandidate(plain)) {
            return replacement.displayComponent();
        }

        List<Component> children = component.children();
        if (children.isEmpty()) {
            return component;
        }

        List<Component> rewrittenChildren = new ArrayList<>(children.size());
        boolean changed = false;
        for (Component child : children) {
            Component rewrittenChild = rewriteMatchingSubtrees(child, replacement);
            rewrittenChildren.add(rewrittenChild);
            changed |= rewrittenChild != child;
        }
        return changed ? component.children(rewrittenChildren) : component;
    }

    private void addLuckPermsCandidates(Set<String> candidates,
                                        Player player,
                                        String originalName,
                                        String displayName) {
        String prefix = plain(formatter.parseLuckPerms(luckPermsHook.getPrefix(player.getUniqueId())));
        String suffix = plain(formatter.parseLuckPerms(luckPermsHook.getSuffix(player.getUniqueId())));
        if (prefix.isEmpty() && suffix.isEmpty()) {
            return;
        }

        addDecoratedCandidates(candidates, prefix, originalName, suffix);
        addDecoratedCandidates(candidates, prefix, displayName, suffix);
    }

    private void addVanillaTeamCandidates(Set<String> candidates,
                                          VanillaTeamService.TeamInfo teamInfo,
                                          String originalName,
                                          String displayName) {
        String prefix = plain(teamInfo.prefix());
        String suffix = plain(teamInfo.suffix());
        if (prefix.isEmpty() && suffix.isEmpty()) {
            return;
        }

        addDecoratedCandidates(candidates, prefix, originalName, suffix);
        addDecoratedCandidates(candidates, prefix, displayName, suffix);
    }

    private Component buildVanillaCommandDisplay(String displayName, VanillaTeamService.TeamInfo teamInfo) {
        Component prefix = applyTeamColor(teamInfo.prefix(), teamInfo);
        Component name = applyTeamColor(formatter.parseOutput(displayName), teamInfo);
        Component suffix = applyTeamColor(teamInfo.suffix(), teamInfo);
        return Component.empty()
                .append(prefix)
                .append(name)
                .append(suffix);
    }

    private Component applyTeamColor(Component component, VanillaTeamService.TeamInfo teamInfo) {
        if (component == null) {
            return Component.empty();
        }
        if (teamInfo.color() == null) {
            return component;
        }
        return component.colorIfAbsent(teamInfo.color());
    }

    private void addDecoratedCandidates(Set<String> candidates, String prefix, String name, String suffix) {
        if (name == null || name.isBlank()) {
            return;
        }
        candidates.add(prefix + name + suffix);
        if (!prefix.isEmpty()) {
            candidates.add(prefix + name);
        }
        if (!suffix.isEmpty()) {
            candidates.add(name + suffix);
        }
    }

    private void addPlainCandidate(Set<String> candidates, Component component) {
        String plain = plain(component);
        if (!plain.isBlank()) {
            candidates.add(plain);
        }
    }

    private String plain(Component component) {
        if (component == null) {
            return "";
        }
        return PLAIN_TEXT.serialize(component);
    }

    private Pattern createUsernamePattern(String originalName, String protectedPrefix, String protectedSuffix) {
        StringBuilder pattern = new StringBuilder();
        pattern.append("(?<!").append(USERNAME_CHARACTER_CLASS).append(")");
        if (protectedPrefix != null && !protectedPrefix.isEmpty()) {
            pattern.append("(?<!").append(Pattern.quote(protectedPrefix)).append(")");
        }
        pattern.append(Pattern.quote(originalName));
        if (protectedSuffix != null && !protectedSuffix.isEmpty()) {
            pattern.append("(?!").append(Pattern.quote(protectedSuffix)).append(")");
        }
        pattern.append("(?!").append(USERNAME_CHARACTER_CLASS).append(")");
        return Pattern.compile(pattern.toString());
    }

    private record NameReplacement(String originalName,
                                   String displayName,
                                   Component displayComponent,
                                   Set<String> candidates,
                                   Pattern originalNamePattern) {
        private boolean noop() {
            return candidates.isEmpty() && originalName.equals(displayName);
        }

        private boolean matchesCandidate(String value) {
            return value != null && candidates.contains(value);
        }

        private int longestCandidateLength() {
            int longest = originalName.length();
            for (String candidate : candidates) {
                longest = Math.max(longest, candidate.length());
            }
            return longest;
        }

        private static NameReplacement noop(String originalName) {
            return new NameReplacement(
                    originalName,
                    originalName,
                    Component.text(originalName),
                    Set.of(),
                    Pattern.compile(Pattern.quote(originalName))
            );
        }
    }
}
