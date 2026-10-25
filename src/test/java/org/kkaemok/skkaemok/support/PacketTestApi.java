package org.kkaemok.skkaemok.support;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;

import static org.mockito.Mockito.*;

public final class PacketTestApi {
    public static synchronized void initialize() {
        if (PacketEvents.getAPI() != null) return;
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class, RETURNS_DEEP_STUBS);
        when(api.getSettings()).thenReturn(new com.github.retrooper.packetevents.settings.PacketEventsSettings());
        when(api.getServerManager().getVersion()).thenReturn(ServerVersion.V_1_21_8);
        try {
            NettyManager netty = (NettyManager) Class.forName("io.github.retrooper.packetevents.impl.netty.NettyManagerImpl").getConstructor().newInstance();
            when(api.getNettyManager()).thenReturn(netty);
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
        PacketEvents.setAPI(api);
    }
}
