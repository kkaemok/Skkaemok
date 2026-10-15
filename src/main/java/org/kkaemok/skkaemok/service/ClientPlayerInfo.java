package org.kkaemok.skkaemok.service;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.*;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Main-thread updates; the packet listener only reads immutable desired snapshots. */
final class ClientPlayerInfo implements AutoCloseable {
    @FunctionalInterface
    interface DisplaySender {
        void send(Player target, Player viewer, Component display);
    }
    record Pair(UUID target, UUID viewer) { }
    record Texture(String value, String signature) { }
    private record InfoExtras(int order, boolean hat) { }
    record ProfileState(String name, Set<Texture> textures) {
        ProfileState {
            textures = Set.copyOf(textures);
        }

        static ProfileState of(Player target, String name, SkinData skin) {
            return of(target.getPlayerProfile(), name, skin);
        }

        static ProfileState of(PlayerProfile nativeProfile, String name, SkinData skin) {
            if (skin != null && skin.isValid()) {
                return new ProfileState(name, Set.of(new Texture(skin.getValue(), skin.getSignature())));
            }
            Set<Texture> textures = new HashSet<>();
            for (ProfileProperty property : nativeProfile.getProperties()) {
                if (property.getName().equals("textures")) {
                    textures.add(new Texture(property.getValue(), property.getSignature()));
                }
            }
            return new ProfileState(name, textures);
        }
    }
    record Appearance(ProfileState profile, Component display, boolean rename, boolean reskin) {
        Appearance(ProfileState profile, Component display) {
            this(profile, display, true, true);
        }

        boolean ownsProfile() { return rename || reskin; }
        boolean active() { return ownsProfile() || display != null; }
    }

    private final JavaPlugin plugin;
    private final ProtocolManager protocol;
    private final Map<Pair, Appearance> desired = new ConcurrentHashMap<>();
    private final Map<Pair, Appearance> applied = new ConcurrentHashMap<>();
    private final Map<Pair, InfoExtras> observed = new ConcurrentHashMap<>();
    private final Set<Pair> failed = ConcurrentHashMap.newKeySet();
    private final PacketAdapter listener;
    private final DisplaySender displaySender;
    private volatile boolean closed;

    ClientPlayerInfo(JavaPlugin plugin, ProtocolManager protocol) {
        this(plugin, protocol, null);
    }

    ClientPlayerInfo(JavaPlugin plugin, ProtocolManager protocol, DisplaySender displaySender) {
        this.plugin = plugin;
        this.protocol = protocol;
        this.displaySender = displaySender == null ? this::sendDisplay : displaySender;
        listener = new PacketAdapter(plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.PLAYER_INFO, PacketType.Play.Server.PLAYER_INFO_REMOVE) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (closed || event.isCancelled()) return;
                try {
                    rewrite(event);
                } catch (RuntimeException ex) {
                    // Do not claim a native profile refresh succeeded if its overlay failed.
                    var actions = event.getPacket().getPlayerInfoActions().readSafely(0);
                    if (actions != null && actions.contains(EnumWrappers.PlayerInfoAction.ADD_PLAYER)) {
                        UUID viewer = event.getPlayer().getUniqueId();
                        desired.keySet().stream().filter(pair -> pair.viewer().equals(viewer)).forEach(failed::add);
                    }
                    plugin.getLogger().log(Level.SEVERE, "Failed to preserve client player-info overrides", ex);
                }
            }
        };
        protocol.addPacketListener(listener);
    }

    static boolean needsProfileRefresh(Appearance previous, Appearance next, ProfileState nativeProfile) {
        if (!next.ownsProfile() && (previous == null || !previous.ownsProfile())) return false;
        return !next.profile().equals(previous == null ? nativeProfile : previous.profile());
    }

    void apply(Player target, Player viewer, String name, Component display, SkinData skin) {
        if (closed || !target.isOnline() || !viewer.isOnline()) return;
        Pair pair = new Pair(target.getUniqueId(), viewer.getUniqueId());
        Appearance next = appearance(target, name, display, skin, target.getPlayerProfile());
        Appearance previous = applied.get(pair);
        desired.put(pair, next);
        // A hidden entity must never be added back to player info by this addon.
        // Its next native initialization will pick up the desired snapshot.
        if (!viewer.canSee(target)) return;
        ProfileState nativeProfile = ProfileState.of(target, target.getName(), null);
        boolean refresh = failed.remove(pair) || needsProfileRefresh(previous, next, nativeProfile);
        try {
            if (refresh) {
                refreshProfile(target, viewer, pair, next);
            } else if (previous == null ? display != null : !Objects.equals(previous.display(), display)) {
                displaySender.send(target, viewer, display);
            }
            if (!failed.contains(pair)) applied.put(pair, next);
            if (!next.active()) desired.remove(pair, next);
        } catch (Exception ex) {
            // A failed display send retries as a display delta. A partly sent
            // profile reset must retry the complete refresh, even to the real name.
            if (refresh) failed.add(pair);
            plugin.getLogger().log(Level.SEVERE, "Failed to update client player info", ex);
        }
    }

    private void refreshProfile(Player target, Player viewer, Pair pair, Appearance next) {
        if (!target.equals(viewer) && target.isVisibleByDefault()) {
            // Paper supplies the current chat session, listed flag and version-specific
            // fields, then re-tracks the entity AFTER our listener rewrites initialization.
            // Same tick: there is no delayed show capable of reviving stale state.
            try {
                viewer.hidePlayer(plugin, target);
            } finally {
                // Only remove our visibility claim; other plugins retain theirs.
                viewer.showPlayer(plugin, target);
            }
            return;
        }

        // hide/show cannot refresh self, or entities explicitly shown by another
        // plugin with visibleByDefault=false (it would acquire a visibility claim).
        InfoExtras old = observed.get(pair);
        WrappedGameProfile profile = profile(WrappedGameProfile.fromPlayer(target), next);
        PlayerInfoData data = new PlayerInfoData(target.getUniqueId(), target.getPing(), viewer.isListed(target),
                EnumWrappers.NativeGameMode.fromBukkit(target.getGameMode()), profile, wrapped(next.display()),
                old == null || old.hat(), old == null ? 0 : old.order(),
                WrappedRemoteChatSessionData.fromPlayer(target));
        boolean tracked = !target.equals(viewer) && protocol.getEntityTrackers(target).contains(viewer);
        if (tracked) {
            PacketContainer destroy = protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().write(0, List.of(target.getEntityId()));
            protocol.sendServerPacket(viewer, destroy);
        }
        PacketContainer remove = protocol.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        remove.getUUIDLists().write(0, List.of(target.getUniqueId()));
        protocol.sendServerPacket(viewer, remove);
        EnumSet<EnumWrappers.PlayerInfoAction> actions = EnumSet.of(
                EnumWrappers.PlayerInfoAction.ADD_PLAYER, EnumWrappers.PlayerInfoAction.INITIALIZE_CHAT,
                EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE, EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
                EnumWrappers.PlayerInfoAction.UPDATE_LISTED, EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
        // These actions do not exist in the native enum on baseline 1.21.
        if (new com.comphenix.protocol.utility.MinecraftVersion("1.21.2").atOrAbove()) {
            actions.add(EnumWrappers.PlayerInfoAction.UPDATE_LIST_ORDER);
        }
        if (new com.comphenix.protocol.utility.MinecraftVersion("1.21.4").atOrAbove()) {
            actions.add(EnumWrappers.PlayerInfoAction.UPDATE_HAT);
        }
        send(viewer, actions, data);
        if (tracked) protocol.updateEntity(target, List.of(viewer));
    }

    private void sendDisplay(Player target, Player viewer, Component display) {
        // Only UPDATE_DISPLAY_NAME is serialized. No other player-info field changes.
        send(viewer, EnumSet.of(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME),
                new PlayerInfoData(target.getUniqueId(), 0, viewer.isListed(target), null, null,
                        wrapped(display == null ? target.playerListName() : display)));
    }

    private void send(Player viewer, EnumSet<EnumWrappers.PlayerInfoAction> actions, PlayerInfoData data) {
        PacketContainer packet = protocol.createPacket(PacketType.Play.Server.PLAYER_INFO);
        packet.getPlayerInfoActions().write(0, actions);
        // In modern PLAYER_INFO index 0 is the action EnumSet; the data List is index 1.
        packet.getPlayerInfoDataLists().write(1, List.of(data));
        protocol.sendServerPacket(viewer, packet);
    }

    private synchronized void rewrite(PacketEvent event) {
        if (closed) return;
        PacketContainer packet = event.getPacket();
        if (packet.getType().equals(PacketType.Play.Server.PLAYER_INFO_REMOVE)) {
            UUID viewer = event.getPlayer().getUniqueId();
            for (UUID target : packet.getUUIDLists().read(0)) observed.remove(new Pair(target, viewer));
            return;
        }
        Set<EnumWrappers.PlayerInfoAction> actions = packet.getPlayerInfoActions().read(0);
        List<PlayerInfoData> entries = packet.getPlayerInfoDataLists().read(1);
        if (entries == null) return;
        List<PlayerInfoData> rewritten = new ArrayList<>(entries.size());
        boolean changed = false;
        boolean addDisplayAction = false;
        for (PlayerInfoData data : entries) {
            Pair pair = new Pair(data.getProfileId(), event.getPlayer().getUniqueId());
            // Keep the last order/hat fields even when packets carry only a delta.
            InfoExtras old = observed.get(pair);
            int order = actions.contains(EnumWrappers.PlayerInfoAction.UPDATE_LIST_ORDER) ? data.getListOrder()
                    : old == null ? 0 : old.order();
            boolean hat = actions.contains(EnumWrappers.PlayerInfoAction.UPDATE_HAT) ? data.isShowHat()
                    : old == null || old.hat();
            observed.put(pair, new InfoExtras(order, hat));
            Appearance state = desired.get(pair);
            boolean add = actions.contains(EnumWrappers.PlayerInfoAction.ADD_PLAYER);
            boolean display = actions.contains(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
            if (state == null || (!add && !display)) {
                rewritten.add(data);
                continue;
            }
            WrappedGameProfile profile = add ? profile(data.getProfile(), state) : data.getProfile();
            if (add && state.display() != null && !display) addDisplayAction = true;
            // Null means we no longer own tab formatting: preserve TAB/native display.
            WrappedChatComponent name = state.display() == null ? data.getDisplayName() : wrapped(state.display());
            rewritten.add(copy(data, profile, name, data.isShowHat(), data.getListOrder()));
            changed = true;
        }
        if (changed) {
            PacketContainer copy = packet.shallowClone();
            if (addDisplayAction) {
                EnumSet<EnumWrappers.PlayerInfoAction> expanded = EnumSet.copyOf(actions);
                expanded.add(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
                copy.getPlayerInfoActions().write(0, expanded);
            }
            copy.getPlayerInfoDataLists().write(1, rewritten);
            event.setPacket(copy); // Never modify a packet shared by multiple recipients.
        }
    }

    static PlayerInfoData copy(PlayerInfoData data, WrappedGameProfile profile, WrappedChatComponent display,
                               boolean hat, int order) {
        return new PlayerInfoData(data.getProfileId(), data.getLatency(), data.isListed(), data.getGameMode(),
                profile, display, hat, order, data.getRemoteChatSessionData());
    }

    private static WrappedGameProfile profile(WrappedGameProfile original, Appearance desired) {
        WrappedGameProfile result = new WrappedGameProfile(original.getUUID(), desired.rename() ? desired.profile().name() : original.getName());
        original.getProperties().forEach((key, value) -> {
            if (!desired.reskin() || !key.equals("textures")) result.getProperties().put(key, value);
        });
        if (desired.reskin()) {
            for (Texture texture : desired.profile().textures()) {
                result.getProperties().put("textures", new WrappedSignedProperty("textures", texture.value(), texture.signature()));
            }
        }
        return result;
    }

    private static WrappedChatComponent wrapped(Component display) {
        return display == null ? null : WrappedChatComponent.fromJson(GsonComponentSerializer.gson().serialize(display));
    }

    void prepare(Player target, Player viewer, String name, Component display, SkinData skin, PlayerProfile nativeProfile) {
        Pair pair = new Pair(target.getUniqueId(), viewer.getUniqueId());
        failed.remove(pair);
        desired.put(pair, appearance(target, name, display, skin, nativeProfile));
    }

    private static Appearance appearance(Player target, String name, Component display, SkinData skin, PlayerProfile nativeProfile) {
        return new Appearance(ProfileState.of(nativeProfile, name, skin), display,
                !name.equals(target.getName()), skin != null && skin.isValid());
    }

    boolean hasState(UUID target) {
        return applied.keySet().stream().anyMatch(pair -> pair.target().equals(target))
                || desired.keySet().stream().anyMatch(pair -> pair.target().equals(target));
    }

    void nativeRefreshComplete(Player target, Collection<? extends Player> viewers) {
        for (Player viewer : viewers) {
            Pair pair = new Pair(target.getUniqueId(), viewer.getUniqueId());
            Appearance next = desired.get(pair);
            if (next != null && viewer.canSee(target) && !failed.contains(pair)) {
                applied.put(pair, next);
            }
        }
    }

    synchronized void remove(UUID id) {
        desired.keySet().removeIf(pair -> pair.target().equals(id) || pair.viewer().equals(id));
        applied.keySet().removeIf(pair -> pair.target().equals(id) || pair.viewer().equals(id));
        observed.keySet().removeIf(pair -> pair.target().equals(id) || pair.viewer().equals(id));
        failed.removeIf(pair -> pair.target().equals(id) || pair.viewer().equals(id));
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
            desired.clear();
            applied.clear();
            observed.clear();
            failed.clear();
        }
        protocol.removePacketListener(listener);
    }
}
