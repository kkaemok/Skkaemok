package org.kkaemok.skkaemok.command.nickname;

import com.github.retrooper.packetevents.protocol.chat.*;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import org.kkaemok.skkaemok.support.PacketTestApi;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NicknameCommandTreeTest {
    @BeforeAll static void setup() { PacketTestApi.initialize(); }
    private Node root(List<Integer> children) { return new Node((byte)0,children,0,null,(Parsers.Parser)null,null,null); }
    private Node argument(String name, Parsers.Parser parser, byte flags, List<Object> properties, ResourceLocation provider) {
        return new Node(flags,List.of(),0,name,parser,properties,provider);
    }
    @Test void nativeBranchesAndMetadataSurviveAndTransformIsIdempotent() {
        Node target = new Node((byte)0x3e,List.of(2),0,"targets",Parsers.ENTITY,List.of((byte)2),new ResourceLocation("minecraft:available_sounds"));
        Node item = argument("item",Parsers.ITEM_STACK,(byte)2,null,null);
        List<Node> source = List.of(root(List.of(1)),target,item);
        var result = new NicknameCommandTree().transform(source,false);
        Node preserved=result.get(1);
        assertEquals(target.getFlags(),preserved.getFlags()); assertEquals(target.getParser(),preserved.getParser());
        assertEquals(target.getProperties(),preserved.getProperties()); assertEquals(target.getSuggestionsType(),preserved.getSuggestionsType());
        assertEquals(target.getRedirectNodeIndex(),preserved.getRedirectNodeIndex()); assertSame(item,result.get(2));
        assertEquals(Parsers.SCORE_HOLDER,result.get(3).getParser().orElseThrow());
        assertEquals(List.of((byte)1),result.get(3).getProperties().orElseThrow());
        assertEquals(List.of(2),result.get(3).getChildren());
        assertEquals(Parsers.BRIGADIER_STRING,result.get(4).getParser().orElseThrow());
        assertSame(result,new NicknameCommandTree().transform(result,false));
    }
    @Test void gameProfilesAndScoreHoldersRetainNativeSelectorsAndGainQuotedAliases() {
        List<Node> source=List.of(root(List.of(1,2)),argument("profile",Parsers.GAME_PROFILE,(byte)2,null,null),
                argument("holder",Parsers.SCORE_HOLDER,(byte)2,List.of((byte)0),null));
        var result=new NicknameCommandTree().transform(source,false);
        assertSame(source.get(1),result.get(1)); assertSame(source.get(2),result.get(2));
        assertEquals(Parsers.BRIGADIER_STRING,result.get(3).getParser().orElseThrow());
        assertEquals(1,result.getFirst().getChildren().stream().filter(i->result.get(i).getSuggestionsType().orElse(null)!=null).count());
    }
    @Test void existingServerProvidersDoNotCreateCompetingRequests() {
        for(String provider:List.of("minecraft:ask_server","example:provider","minecraft:post_effects")) {
            List<Node> source=List.of(root(List.of(1,2)),argument("target",Parsers.ENTITY,(byte)2,List.of((byte)1),null),
                    argument("other",Parsers.BRIGADIER_STRING,(byte)0x12,List.of(1),new ResourceLocation(provider)));
            var result=new NicknameCommandTree().transform(source,false);
            assertSame(source.get(2),result.get(2));
            assertEquals(1,result.getFirst().getChildren().stream().filter(i->(result.get(i).getFlags()&Node.FLAG_CUSTOM_SUGGESTIONS)!=0).count());
        }
    }
    @Test void unrelatedArgumentsAreNotRewritten() {
        var source=List.of(root(List.of(1)),argument("item",Parsers.ITEM_STACK,(byte)2,null,null));
        assertSame(source,new NicknameCommandTree().transform(source,false));
    }
}
