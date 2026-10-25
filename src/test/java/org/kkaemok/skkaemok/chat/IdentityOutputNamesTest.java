package org.kkaemok.skkaemok.chat;

import com.github.retrooper.packetevents.protocol.chat.*;
import com.github.retrooper.packetevents.protocol.chat.filter.FilterMask;
import com.github.retrooper.packetevents.protocol.chat.message.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.*;
import org.kkaemok.skkaemok.name.NameIndex;
import org.kkaemok.skkaemok.support.PacketTestApi;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class IdentityOutputNamesTest {
    private final UUID steve=UUID.randomUUID(), alex=UUID.randomUUID();
    private final IdentityOutputNames output = new IdentityOutputNames(new NameIndex(List.of(
            new NameIndex.Identity(steve,"Steve","깨묵",Set.of("깨묵"),Component.text("깨묵"),true),
            new NameIndex.Identity(alex,"Alex","모험가",Set.of("모험가"),Component.text("모험가"),true))));
    @BeforeAll static void setup(){ PacketTestApi.initialize(); }
    private Component player(UUID id,String name) { return Component.text(name)
            .hoverEvent(HoverEvent.showEntity(Key.key("minecraft:player"),id,Component.text(name)))
            .clickEvent(ClickEvent.suggestCommand("/tell "+name+" ")).insertion(name); }
    private String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }

    @Test void feedbackRetainsTranslationHoverUuidClickAndFormatting() {
        Component original=player(steve,"Steve").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
        var translated=Component.translatable("commands.teleport.success.entity.single",original,player(alex,"Alex"));
        var next=(TranslatableComponent)output.rewrite(translated);
        Component name=(Component)next.arguments().getFirst().value();
        assertEquals(translated.key(),next.key()); assertEquals("깨묵",plain(name));
        assertEquals(original.clickEvent(),name.clickEvent()); assertEquals(original.insertion(),name.insertion());
        assertEquals(NamedTextColor.GOLD,name.color()); assertEquals(TextDecoration.State.TRUE,name.decoration(TextDecoration.BOLD));
        var hover=(HoverEvent.ShowEntity)name.hoverEvent().value(); assertEquals(steve,hover.id()); assertEquals("깨묵",plain(hover.name()));
        assertEquals("Steve",plain(original));
    }
    @Test void literalBodiesConsoleNamesAndNonPlayerEntitiesAreUntouched() {
        Component text=Component.text("Steve Alex /tp Steve").clickEvent(ClickEvent.runCommand("/tp Steve"));
        assertSame(text,output.rewrite(text));
        Component zombie=Component.text("Steve").hoverEvent(HoverEvent.showEntity(Key.key("minecraft:zombie"),steve,Component.text("Steve")));
        assertSame(zombie,output.rewrite(zombie));
        var console=new ChatType.Bound(ChatTypes.SAY_COMMAND,Component.text("Steve"),null);
        assertSame(console,output.rewrite(console,null));
    }
    @Test void whisperLabelsUseAccountFallbackOnlyForWhisperRecipients() {
        var whisper=new ChatType.Bound(ChatTypes.MSG_COMMAND_OUTGOING,player(steve,"Steve"),Component.text("aLeX"));
        var next=output.rewrite(whisper,null);
        assertEquals("깨묵",plain(next.getName())); assertEquals("모험가",plain(next.getTargetName())); assertSame(whisper.getType(),next.getType());
        var team=new ChatType.Bound(ChatTypes.TEAM_MSG_COMMAND_OUTGOING,player(steve,"Steve"),Component.text("Alex"));
        assertEquals("Alex",plain(output.rewrite(team,null).getTargetName()));
    }
    @Test void signedContentAndChainMetadataRemainByteForByteIdentical() {
        byte[] signature=new byte[256]; Arrays.fill(signature,(byte)23);
        var seen=new LastSeenMessages.Packed(List.of());
        var bound=new ChatType.Bound(ChatTypes.MSG_COMMAND_OUTGOING,Component.text("Steve"),player(alex,"Alex"));
        Instant now=Instant.now(); Component unsigned=Component.text("Steve Alex literal body");
        List<ChatMessage_v1_19_3> messages=List.of(
                new ChatMessage_v1_19_3(steve,17,signature,"Steve Alex",now,123L,seen,unsigned,FilterMask.PASS_THROUGH,bound),
                new ChatMessage_v1_21_5(91,steve,17,signature,"Steve Alex",now,123L,seen,unsigned,FilterMask.PASS_THROUGH,bound));
        for(var original:messages) {
            var next=(ChatMessage_v1_19_3)output.rewrite(original);
            assertNotSame(original,next); assertSame(signature,next.getSignature()); assertSame(seen,next.getLastSeenMessagesPacked());
            assertSame(unsigned,next.getUnsignedChatContent().orElseThrow()); assertSame(original.getFilterMask(),next.getFilterMask());
            assertEquals(original.getPlainContent(),next.getPlainContent()); assertEquals(now,next.getTimestamp()); assertEquals(123L,next.getSalt()); assertEquals(17,next.getIndex());
            assertEquals("깨묵",plain(next.getChatFormatting().getName()));
            if(next instanceof ChatMessage_v1_21_5 modern) assertEquals(91,modern.getGlobalIndex());
        }
    }
    @Test void uuidIdentityPreventsGuessingFromAnUnrelatedName() {
        assertEquals("Alex",plain(output.rewrite(player(steve,"Alex"))));
        Component unknown=player(UUID.randomUUID(),"Steve"); assertSame(unknown,output.rewrite(unknown));
    }
}
