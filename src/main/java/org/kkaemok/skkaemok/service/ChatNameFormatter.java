package org.kkaemok.skkaemok.service;

final class ChatNameFormatter {
    private ChatNameFormatter() {
    }

    static String formatDisplayName(String originalName, String chatName) {
        if (chatName != null && !chatName.isBlank()) {
            return chatName;
        }
        return originalName == null ? "" : originalName;
    }
}
