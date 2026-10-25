package org.kkaemok.skkaemok.command.nickname;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kkaemok.skkaemok.chat.ChatNameManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandInterceptorTest {
    private final ChatNameManager names = mock(ChatNameManager.class);
    private final Player sender = mock(Player.class);
    private final Player target = mock(Player.class);
    private final CommandInterceptor interceptor = new CommandInterceptor(names);

    @ParameterizedTest
    @ValueSource(strings = {"/msg", "/tell", "/w", "/minecraft:msg", "/essentials:msg"})
    void rewritesOnlyRecipientAndPreservesWhitespaceQuotesAndMessage(String command) {
        when(target.getName()).thenReturn("Steve");
        when(names.resolveChatName(target, sender)).thenReturn("Nick");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(target));
            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(sender,
                    command + "  Nick   say Nick \"Nick\" @a", new java.util.HashSet<>());
            interceptor.onCommandPreprocess(event);
            assertEquals(command + "  Steve   say Nick \"Nick\" @a", event.getMessage());
            assertFalse(event.isCancelled());
            verify(sender, never()).performCommand(anyString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/say Nick", "/give Steve Nick", "/execute as Nick run say Nick",
            "/msg @a Nick", "/msg \"Nick\" Nick", "/msg 'Nick' Nick"})
    void leavesUnknownCommandArgumentsSelectorsAndQuotedRecipientsAlone(String command) {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(sender, command, new java.util.HashSet<>());
        interceptor.onCommandPreprocess(event);
        assertEquals(command, event.getMessage());
        verifyNoInteractions(names);
    }

    @Test
    void duplicateNicknamesDoNotChooseAnArbitraryRecipient() {
        Player second = mock(Player.class);
        when(names.resolveChatName(target, sender)).thenReturn("Nick");
        when(names.resolveChatName(second, sender)).thenReturn("Nick");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(target, second));
            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(sender, "/msg Nick hello", new java.util.HashSet<>());
            interceptor.onCommandPreprocess(event);
            assertEquals("/msg Nick hello", event.getMessage());
        }
    }
}
