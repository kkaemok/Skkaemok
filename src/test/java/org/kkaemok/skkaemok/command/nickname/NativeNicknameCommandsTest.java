package org.kkaemok.skkaemok.command.nickname;

import com.mojang.brigadier.*;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.tree.*;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.*;
import org.kkaemok.skkaemok.name.NameIndex;

import java.util.*;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in fixtures run the actual Paper argument implementations, not imitation parsers. */
class NativeNicknameCommandsTest {
    private static final UUID STEVE=UUID.fromString("12345678-1234-1234-1234-123456789abc");
    @BeforeAll static void setup() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("skkaemok.nativeCommands"));
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
    }
    @SuppressWarnings("unchecked") private ArgumentType<Object> nativeType(String name,String factory) throws Exception {
        return (ArgumentType<Object>)Class.forName("net.minecraft.commands.arguments."+name).getMethod(factory).invoke(null);
    }
    private NameIndex names(boolean alice) {
        String name=alice?"깨묵 이름":"Other";
        return new NameIndex(List.of(new NameIndex.Identity(STEVE,"Steve",name,Set.of(name,"깨묵"),Component.text(name),true)));
    }
    @Test void nativeResultTypesSelectorsAndFakeScoreHoldersSurviveAliasParsing() throws Exception {
        for(var type:List.of(nativeType("EntityArgument","entities"),nativeType("GameProfileArgument","gameProfile"),nativeType("ScoreHolderArgument","scoreHolders"))) {
            var alias=new AliasArgumentType<>(type,input->names(true).aliasAccount(input));
            Object nativeResult=type.parse(new StringReader("Steve"));
            StringReader reader=new StringReader("\"깨묵 이름\" next");
            assertEquals(nativeResult.getClass(),alias.parse(reader).getClass()); assertEquals(" next",reader.getRemaining());
            for(String selector:List.of("@a","@p","@r","@s")) {
                StringReader source=new StringReader(selector), next=new StringReader(selector);
                Object expected;
                try { expected=type.parse(source); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                    var actual=assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,()->alias.parse(next));
                    assertEquals(error.getMessage(),actual.getMessage()); continue;
                }
                assertEquals(expected.getClass(),alias.parse(next).getClass()); assertEquals(source.getCursor(),next.getCursor());
            }
        }
        var holder=new AliasArgumentType<>(nativeType("ScoreHolderArgument","scoreHolders"),input->names(true).aliasAccount(input));
        for(String input:List.of("*","#counter","fake-holder","@e[type=zombie,limit=1]")) {
            StringReader reader=new StringReader(input); assertNotNull(holder.parse(reader)); assertEquals(input.length(),reader.getCursor());
        }
    }
    @Test void authoritativeTreeResolvesAliasesAndPreservesExecutionAndRedirects() throws Exception {
        CommandDispatcher<Object> dispatcher=new CommandDispatcher<>();
        ArgumentType<Object> entity=nativeType("EntityArgument","entities");
        var give=dispatcher.register(literal("give").then(argument("targets",entity).then(argument("item",StringArgumentType.greedyString())
                .suggests((context,builder)->builder.suggest("minecraft:stone").buildFuture()).executes(context->7))));
        dispatcher.register(literal("alias").redirect(give));
        dispatcher.register(literal("execute").then(literal("as").then(argument("targets",entity).then(literal("run").redirect(dispatcher.getRoot())))));
        var adapter=new NativeBrigadierAdapter();
        adapter.install(dispatcher.getRoot(),source->names(!"Bob".equals(source)),ignored->{});
        assertSame(give,dispatcher.getRoot().getChild("alias").getRedirect());
        for(String command:List.of("give 깨묵 minecraft:stone","give \"깨묵 이름\" minecraft:stone","give Steve minecraft:stone",
                "alias 깨묵 minecraft:stone","execute as 깨묵 run give Steve minecraft:stone")) {
            var result=dispatcher.parse(command,"Alice"); assertFalse(result.getReader().canRead(),command+result.getExceptions()); assertTrue(result.getExceptions().isEmpty(),command);
            assertEquals(7,dispatcher.execute(result));
        }
        var completed=dispatcher.getCompletionSuggestions(dispatcher.parse("give \"깨묵 이름\" ","Alice")).join();
        assertEquals(List.of("minecraft:stone"),completed.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList());
        String selectorCommand="give @e[type=zombie,limit=1] minecraft:stone";
        var wrappedSelector=dispatcher.parse(selectorCommand,"Alice");
        adapter.restore();
        var nativeSelector=dispatcher.parse(selectorCommand,"Alice");
        assertEquals(nativeSelector.getReader().getCursor(),wrappedSelector.getReader().getCursor());
        assertEquals(nativeSelector.getExceptions().values().stream().map(Exception::getMessage).toList(),
                wrappedSelector.getExceptions().values().stream().map(Exception::getMessage).toList());
        assertSame(entity,((ArgumentCommandNode<?,?>)give.getChild("targets")).getType());
    }
    @Test void viewerSpecificSourceReachesNativeAliasParser() throws Exception {
        CommandDispatcher<Object> dispatcher=new CommandDispatcher<>();
        java.util.concurrent.atomic.AtomicReference<Object> selected=new java.util.concurrent.atomic.AtomicReference<>();
        dispatcher.register(literal("target").then(argument("player",nativeType("EntityArgument","players")).executes(context->{selected.set(context.getArgument("player",Object.class));return 1;})));
        var adapter=new NativeBrigadierAdapter(); adapter.install(dispatcher.getRoot(),source->names("Alice".equals(source)),ignored->{});
        assertEquals(1,dispatcher.execute("target \"깨묵 이름\"","Alice"));
        Object alice=selected.get();
        assertEquals("Steve",playerName(alice));
        dispatcher.execute("target \"깨묵 이름\"","Bob");
        assertEquals("깨묵 이름",playerName(selected.get()));
        adapter.restore();
    }
    private static Object playerName(Object selector) throws Exception {
        var field=selector.getClass().getDeclaredField("playerName"); field.setAccessible(true); return field.get(selector);
    }
}
