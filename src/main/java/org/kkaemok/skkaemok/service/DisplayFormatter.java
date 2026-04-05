package org.kkaemok.skkaemok.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DisplayFormatter {
    public enum FormatMode {
        AUTO,
        LEGACY,
        MINIMESSAGE
    }

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern MINIMESSAGE_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern MINIMESSAGE_TAG_PATTERN =
            Pattern.compile("(?i)</?[a-z][a-z0-9_-]*(?::[^>]+)?>");

    private final JavaPlugin plugin;
    private final LegacyComponentSerializer legacyAmpersand;
    private final LegacyComponentSerializer legacySection;
    private final MiniMessage miniMessage;

    private FormatMode luckPermsFormat;
    private FormatMode nicknameFormat;
    private FormatMode outputFormat;

    public DisplayFormatter(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
        this.legacyAmpersand = LegacyComponentSerializer.builder()
                .character('&')
                .hexColors()
                .useUnusualXRepeatedCharacterHexFormat()
                .build();
        this.legacySection = LegacyComponentSerializer.builder()
                .character('\u00A7')
                .hexColors()
                .useUnusualXRepeatedCharacterHexFormat()
                .build();
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    public void reload() {
        this.luckPermsFormat = parseMode(plugin.getConfig().getString("format.luckperms", "auto"));
        this.nicknameFormat = parseMode(plugin.getConfig().getString("format.nickname", "auto"));
        this.outputFormat = parseMode(plugin.getConfig().getString("format.output", "auto"));
    }

    public Component parseLuckPerms(String input) {
        return parse(input, luckPermsFormat);
    }

    public Component parseNickname(String input) {
        return parse(input, nicknameFormat);
    }

    public Component parseOutput(String input) {
        return parse(input, outputFormat);
    }

    public String serializeLegacy(Component component) {
        if (component == null) {
            return "";
        }
        return legacySection.serialize(component);
    }

    public String serializeAmpersand(Component component) {
        if (component == null) {
            return "";
        }
        return legacyAmpersand.serialize(component);
    }

    private Component parse(String input, FormatMode mode) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        FormatMode resolved = mode == FormatMode.AUTO ? detectFormat(input) : mode;
        return switch (resolved) {
            case MINIMESSAGE -> parseMiniMessage(input);
            case LEGACY -> legacyAmpersand.deserialize(normalizeLegacy(input));
            default -> Component.text(input);
        };
    }

    private FormatMode parseMode(String value) {
        if (value == null) {
            return FormatMode.AUTO;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LEGACY" -> FormatMode.LEGACY;
            case "MINIMESSAGE" -> FormatMode.MINIMESSAGE;
            default -> FormatMode.AUTO;
        };
    }

    private FormatMode detectFormat(String input) {
        if (input == null || input.isEmpty()) {
            return FormatMode.LEGACY;
        }
        String lower = input.toLowerCase(Locale.ROOT);
        boolean isMiniMessage = MINIMESSAGE_PATTERN.matcher(input).find()
                && (lower.contains("<#")
                || lower.contains("<gradient")
                || lower.contains("<color")
                || lower.contains("<rainbow")
                || lower.contains("<bold")
                || lower.contains("<italic")
                || lower.contains("<underlined")
                || lower.contains("<strikethrough")
                || lower.contains("<obfuscated")
                || MINIMESSAGE_TAG_PATTERN.matcher(input).find());
        if (isMiniMessage) {
            return FormatMode.MINIMESSAGE;
        }
        return FormatMode.LEGACY;
    }

    private Component parseMiniMessage(String input) {
        try {
            return miniMessage.deserialize(input);
        } catch (Exception ignored) {
            return legacyAmpersand.deserialize(normalizeLegacy(input));
        }
    }

    private String normalizeLegacy(String input) {
        String normalized = input.replace('\u00A7', '&');
        return replaceHexColors(normalized);
    }

    private String replaceHexColors(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
