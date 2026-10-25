package org.kkaemok.skkaemok.chat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.name.NameSnapshots;

import java.util.logging.Level;

public final class OutgoingMessageRewriteListener {
    private final JavaPlugin plugin;
    private final NameSnapshots names;
    private final PacketListenerAbstract listener = new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
        @Override public void onPacketSend(PacketSendEvent event) { rewrite(event); }
    };

    public OutgoingMessageRewriteListener(JavaPlugin plugin, NameSnapshots names) { this.plugin = plugin; this.names = names; }
    public void register() { PacketEvents.getAPI().getEventManager().registerListener(listener); }
    public void unregister() { PacketEvents.getAPI().getEventManager().unregisterListener(listener); }

    private void rewrite(PacketSendEvent event) {
        if (event.isCancelled() || event.getUser() == null) return;
        IdentityOutputNames output = new IdentityOutputNames(names.forViewer(event.getUser().getUUID()));
        try {
            if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
                var packet = new WrapperPlayServerSystemChatMessage(event);
                if (packet.isOverlay()) return;
                var original = packet.getMessage(); var next = output.rewrite(original);
                if (next != original) { packet.setMessage(next); event.markForReEncode(true); }
            } else if (event.getPacketType() == PacketType.Play.Server.DISGUISED_CHAT) {
                var packet = new WrapperPlayServerDisguisedChat(event);
                var original = packet.getChatFormatting(); var next = output.rewrite(original, null);
                if (next != original) { packet.setChatFormatting(next); event.markForReEncode(true); }
            } else if (event.getPacketType() == PacketType.Play.Server.CHAT_MESSAGE) {
                var packet = new WrapperPlayServerChatMessage(event);
                var original = packet.getMessage(); var next = output.rewrite(original);
                if (next != original) { packet.setMessage(next); event.markForReEncode(true); }
            } else if (event.getPacketType() == PacketType.Play.Server.DEATH_COMBAT_EVENT) {
                var packet = new WrapperPlayServerDeathCombatEvent(event);
                var original = packet.getDeathMessage(); var next = output.rewrite(original);
                if (next != original) { packet.setDeathMessage(next); event.markForReEncode(true); }
            }
        } catch (RuntimeException ex) { plugin.getLogger().log(Level.WARNING, "Could not rewrite identified player labels in " + event.getPacketType(), ex); }
    }
}
