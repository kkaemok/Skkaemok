package org.kkaemok.skkaemok.integration;

import java.util.Locale;

public enum TabMode {
    AUTO,
    ON,
    OFF,
    PRIORITY;

    public static TabMode fromString(String value) {
        if (value == null) {
            return AUTO;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ON" -> ON;
            case "OFF" -> OFF;
            case "PRIORITY" -> PRIORITY;
            default -> AUTO;
        };
    }
}
