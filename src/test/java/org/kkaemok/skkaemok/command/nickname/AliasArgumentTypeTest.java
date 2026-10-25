package org.kkaemok.skkaemok.command.nickname;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.junit.jupiter.api.Test;

class AliasArgumentTypeTest {
    @Test
    void aliasReturnsNativeValueAndRetainsOriginalInputRange() throws Exception {
        var type =
                new AliasArgumentType<>(
                        StringArgumentType.word(), input -> input.equals("깨묵") ? "Steve" : null);
        StringReader reader = new StringReader("tp 깨묵 minecraft:stone");
        reader.setCursor(3);
        assertEquals("Steve", type.parse(reader));
        assertEquals(5, reader.getCursor());
        assertEquals(" minecraft:stone", reader.getRemaining());
    }

    @Test
    void quotedLongNamesRetainDownstreamCompletionRange() throws Exception {
        var type =
                new AliasArgumentType<>(
                        StringArgumentType.word(),
                        input -> input.equals("아주 긴 별명 이름") ? "Steve" : null);
        StringReader reader = new StringReader("\"아주 긴 별명 이름\" next");
        assertEquals("Steve", type.parse(reader));
        assertEquals(" next", reader.getRemaining());
    }

    @Test
    void sourceControlsViewerSpecificAliasResolution() throws Exception {
        var type =
                new AliasArgumentType<>(
                        StringArgumentType.word(),
                        (input, source) ->
                                "Alice".equals(source) && input.equals("Private") ? "Steve" : null);
        assertEquals("Steve", type.parse(new StringReader("Private"), "Alice"));
        assertEquals("Private", type.parse(new StringReader("Private"), "Bob"));
    }

    @Test
    void unknownInputsAreParsedByTheOriginalArgumentIncludingErrors() throws Exception {
        var type = new AliasArgumentType<>(StringArgumentType.string(), input -> null);
        for (String value : new String[] {"@a", "*", "#counter", "Steve"})
            assertEquals(
                    StringArgumentType.string().parse(new StringReader(value)),
                    type.parse(new StringReader(value)));
        assertThrows(
                CommandSyntaxException.class, () -> type.parse(new StringReader("\"unclosed")));
    }

    @Test
    void suggestionsRoundTripThroughTheClientQuotedStringParser() throws Exception {
        for (String name :
                java.util.List.of(
                        "Steve", "깨묵", "Two words", "O'Connor", "tag:name", "quote\"slash\\")) {
            String suggested = AliasArgumentType.suggestion(name, "");
            assertEquals(name, StringArgumentType.string().parse(new StringReader(suggested)));
        }
        assertEquals("\"Steve\"", AliasArgumentType.suggestion("Steve", "\"St"));
    }
}
