package org.kkaemok.skkaemok.command.nickname;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDeclareCommands;
import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.name.NameSnapshots;

import java.util.logging.Level;

public final class NicknameCommands implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final NameSnapshots names;
    private final NativeBrigadierAdapter adapter = new NativeBrigadierAdapter();
    private final NicknameCommandTree transformer = new NicknameCommandTree();
    private CommandNode<?> root;
    private volatile boolean active;
    private boolean closed;
    private final PacketListenerAbstract packets = new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
        @Override public void onPacketSend(PacketSendEvent event) {
            if (!active || event.isCancelled() || event.getPacketType() != PacketType.Play.Server.DECLARE_COMMANDS) return;
            WrapperPlayServerDeclareCommands packet = new WrapperPlayServerDeclareCommands(event);
            var original = packet.getNodes();
            var rewritten = transformer.transform(original, PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_26_3));
            if (original != rewritten) { packet.setNodes(rewritten); event.markForReEncode(true); }
        }
    };

    public NicknameCommands(JavaPlugin plugin, NameSnapshots names) { this.plugin = plugin; this.names = names; }

    public void start() {
        try {
            com.mojang.brigadier.arguments.ArgumentType.class.getMethod("parse", com.mojang.brigadier.StringReader.class, Object.class);
        } catch (NoSuchMethodException ex) {
            plugin.getLogger().warning("Nickname command aliases require a server with source-aware Brigadier parsing. Native commands remain available; update Paper to use aliases.");
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PacketEvents.getAPI().getEventManager().registerListener(packets);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            if (closed) return;
            adapter.restore(); root = event.registrar().getDispatcher().getRoot(); install();
            Bukkit.getScheduler().runTask(plugin, () -> { if (!closed) { install(); refreshClients(); } });
        });
    }

    private void install() {
        if (root == null || closed) return;
        try {
            adapter.install(root, source -> names.forViewer(NativeBrigadierAdapter.viewer(source)), text -> {
                if (plugin.getConfig().getBoolean("nickname-commands.debug", false)) plugin.getLogger().info("[NicknameCommands] " + text);
            });
            active = true;
        } catch (RuntimeException ex) {
            active = false;
            adapter.restore();
            plugin.getLogger().log(Level.SEVERE, "Nickname command arguments are unavailable on this server; native commands are retained", ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) public void onLoad(ServerLoadEvent event) { install(); refreshClients(); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onSend(AsyncPlayerSendCommandsEvent<?> event) {
        if (closed) return;
        if (!event.isAsynchronous()) install();
        if (active && (event.isAsynchronous() || !event.hasFiredAsync())) adapter.prepareClient(event.getCommandNode());
    }
    private void refreshClients() { Bukkit.getOnlinePlayers().forEach(Player::updateCommands); }

    @Override public void close() {
        closed = true; active = false;
        PacketEvents.getAPI().getEventManager().unregisterListener(packets);
        HandlerList.unregisterAll(this);
        adapter.restore(); root = null;
        refreshClients();
    }
}
