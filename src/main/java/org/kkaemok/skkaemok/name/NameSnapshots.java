package org.kkaemok.skkaemok.name;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.chat.ChatNameManager;
import org.kkaemok.skkaemok.chat.NameRewriteService;
import org.kkaemok.skkaemok.display.DisplayFormatter;

import java.util.*;

/** Builds snapshots on the server thread, never scans Bukkit players from the network thread. */
public final class NameSnapshots implements Listener, AutoCloseable {
    private static final NameIndex EMPTY = new NameIndex(List.of());
    private final NameManager names;
    private final ChatNameManager chat;
    private final ViewerNameManager viewers;
    private final NameRewriteService output;
    private final DisplayFormatter formatter;
    private final JavaPlugin plugin;
    private volatile NameIndex global = EMPTY;
    private volatile Map<UUID, NameIndex> snapshots = Map.of();
    private boolean closed;
    private int batchDepth;
    private boolean pendingRefresh;

    public NameSnapshots(
            JavaPlugin plugin,
            NameManager names,
            ChatNameManager chat,
            ViewerNameManager viewers,
            NameRewriteService output) {
        this.plugin = plugin;
        this.names = names;
        this.chat = chat;
        this.viewers = viewers;
        this.output = output;
        formatter = new DisplayFormatter(plugin);
        names.setChangeListener(this::refresh);
        chat.setChangeListener(this::refresh);
        viewers.setChangeListener(this::refresh);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refresh();
    }

    public NameIndex forViewer(UUID viewer) {
        return viewer == null ? global : snapshots.getOrDefault(viewer, EMPTY);
    }

    public void refresh() {
        refresh(null);
    }

    private void refresh(UUID excluded) {
        if (closed) return;
        if (batchDepth > 0) {
            pendingRefresh = true;
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) this::refresh);
            return;
        }
        List<? extends Player> players =
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.getUniqueId().equals(excluded))
                        .toList();
        Map<UUID, NameIndex> next = new HashMap<>();
        global = build(players, null);
        for (Player viewer : players) next.put(viewer.getUniqueId(), build(players, viewer));
        snapshots = Map.copyOf(next);
    }

    private NameIndex build(List<? extends Player> players, Player viewer) {
        List<NameIndex.Identity> identities = new ArrayList<>();
        for (Player player : players) {
            String nickname = plain(formatter.parseNickname(names.loadNickname(player, viewer)));
            String chatName = plain(formatter.parseNickname(chat.resolveChatName(player, viewer)));
            String preferred = !nickname.equals(player.getName()) ? nickname : chatName;
            boolean customChat =
                    chat.hasChatName(player)
                            || (viewer != null && chat.hasChatName(player, viewer));
            Component display =
                    customChat
                            ? output.rewritePlayerName(
                                    Component.text(player.getName()), player, viewer)
                            : formatter.parseNickname(names.loadNickname(player, viewer));
            identities.add(
                    new NameIndex.Identity(
                            player.getUniqueId(),
                            player.getName(),
                            preferred,
                            Set.copyOf(new HashSet<>(List.of(nickname, chatName))),
                            display,
                            viewer == null || viewer.canSee(player)));
        }
        return new NameIndex(identities);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public void batch(Runnable changes) {
        batchDepth++;
        try {
            changes.run();
        } finally {
            if (--batchDepth == 0 && pendingRefresh) {
                pendingRefresh = false;
                refresh();
            }
        }
    }

    public void reload() {
        formatter.reload();
        refresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        refresh(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVisibility(PlayerChangedWorldEvent event) {
        refresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHide(PlayerHideEntityEvent event) {
        visibility(event.getPlayer(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShow(PlayerShowEntityEvent event) {
        visibility(event.getPlayer(), event.getEntity());
    }

    private void visibility(Player viewer, org.bukkit.entity.Entity entity) {
        if (closed || !(entity instanceof Player target)) return;
        NameIndex current = snapshots.get(viewer.getUniqueId());
        if (current == null) return;
        List<NameIndex.Identity> next =
                current.identities().stream()
                        .map(
                                identity ->
                                        identity.id().equals(target.getUniqueId())
                                                ? new NameIndex.Identity(
                                                        identity.id(),
                                                        identity.account(),
                                                        identity.preferred(),
                                                        identity.aliases(),
                                                        identity.output(),
                                                        viewer.canSee(target))
                                                : identity)
                        .toList();
        Map<UUID, NameIndex> updated = new HashMap<>(snapshots);
        updated.put(viewer.getUniqueId(), new NameIndex(next));
        snapshots = Map.copyOf(updated);
    }

    @Override
    public void close() {
        closed = true;
        names.setChangeListener(null);
        chat.setChangeListener(null);
        viewers.setChangeListener(null);
        HandlerList.unregisterAll(this);
        global = EMPTY;
        snapshots = Map.of();
    }
}
