package org.kkaemok.skkaemok.display;

import com.github.retrooper.packetevents.protocol.chat.RemoteChatSession;
import com.github.retrooper.packetevents.protocol.player.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.*;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PlayerInfoMergeTest {
    @Test void latencyDeltasDoNotEraseListingChatOrderHatOrTextureState() {
        var profile=new UserProfile(UUID.randomUUID(),"Steve",List.of(new TextureProperty("textures","value","signature")));
        RemoteChatSession session=mock(RemoteChatSession.class);
        PlayerInfo initial=new PlayerInfo(profile,false,12,GameMode.CREATIVE,Component.text("Display"),session,42,false);
        PlayerInfo delta=new PlayerInfo(profile.getUUID()); delta.setLatency(27);
        PlayerInfo next=ClientPlayerInfo.merge(initial,delta,EnumSet.of(Action.UPDATE_LATENCY));
        assertEquals(27,next.getLatency()); assertFalse(next.isListed()); assertFalse(next.isShowHat()); assertEquals(42,next.getListOrder());
        assertSame(session,next.getChatSession()); assertEquals(initial.getDisplayName(),next.getDisplayName());
        assertEquals("value",next.getGameProfile().getTextureProperties().getFirst().getValue());
        assertNotSame(profile,next.getGameProfile()); assertEquals(12,initial.getLatency());
    }
}
