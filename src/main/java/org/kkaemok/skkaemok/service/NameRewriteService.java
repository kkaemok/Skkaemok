package org.kkaemok.skkaemok.service;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class NameRewriteService {
    private static final String USERNAME_CHARACTER_CLASS = "[A-Za-z0-9_]";

    private final NameManager nameManager;

    public NameRewriteService(NameManager nameManager) {
        if (nameManager == null) {
            throw new IllegalArgumentException("NameManager cannot be null");
        }
        this.nameManager = nameManager;
    }

    public Component rewriteOnlinePlayerNames(Component message) {
        if (message == null) {
            return null;
        }

        Component result = message;
        for (NameReplacement replacement : buildReplacements()) {
            result = result.replaceText(builder -> builder
                    .match(replacement.pattern())
                    .replacement(Component.text(replacement.nickname())));
        }
        return result;
    }

    private List<NameReplacement> buildReplacements() {
        List<NameReplacement> replacements = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String originalName = player.getName();
            String nickname = nameManager.loadNickname(player);
            if (nickname == null || nickname.isBlank() || nickname.equals(originalName)) {
                continue;
            }
            replacements.add(new NameReplacement(originalName, nickname, createUsernamePattern(originalName)));
        }

        replacements.sort(Comparator.comparingInt((NameReplacement replacement) ->
                replacement.originalName().length()).reversed());
        return replacements;
    }

    private Pattern createUsernamePattern(String originalName) {
        return Pattern.compile("(?<!" + USERNAME_CHARACTER_CLASS + ")"
                + Pattern.quote(originalName)
                + "(?!" + USERNAME_CHARACTER_CLASS + ")");
    }

    private record NameReplacement(String originalName, String nickname, Pattern pattern) {
    }
}
