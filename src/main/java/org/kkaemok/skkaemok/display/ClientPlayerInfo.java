package org.kkaemok.skkaemok.display;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.skin.SkinData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Main-thread updates; packet callbacks read immutable desired snapshots. */
final class ClientPlayerInfo implements AutoCloseable {
    @FunctionalInterface
    interface DisplaySender {
        void send(Player target, Player viewer, Component display);
    }
    record Pair(UUID target, UUID viewer) { }
    record Texture(String value, String signature) { }
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
    private final PacketEventsAPI<?> api;
    private final Map<Pair, Appearance> desired = new ConcurrentHashMap<>();
    private final Map<Pair, Appearance> applied = new ConcurrentHashMap<>();
    private final Map<Pair, PlayerInfo> observed = new ConcurrentHashMap<>();
    private final Set<Pair> failed = ConcurrentHashMap.newKeySet();
    private final PacketListenerAbstract listener;
    private final DisplaySender displaySender;
    private volatile boolean closed;

    ClientPlayerInfo(JavaPlugin plugin, PacketEventsAPI<?> api) { this(plugin, api, null); }

    ClientPlayerInfo(JavaPlugin plugin, PacketEventsAPI<?> api, DisplaySender displaySender) {
        this.plugin = plugin;
        this.api = api;
        this.displaySender = displaySender == null ? this::sendDisplay : displaySender;
        this.listener = new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
            @Override public void onPacketSend(PacketSendEvent event) {
                if (closed || event.isCancelled() || (event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE
                        && event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_REMOVE)) return;
                try { rewrite(event); }
                catch (RuntimeException ex) {
                    synchronized (ClientPlayerInfo.this) {
                        if (!closed && event.getUser() != null) {
                            UUID viewer = event.getUser().getUUID();
                            desired.keySet().stream().filter(pair -> pair.viewer().equals(viewer)).forEach(failed::add);
                        }
                    }
                    plugin.getLogger().log(Level.SEVERE, "Failed to preserve client player-info overrides", ex);
                }
            }
        };
        api.getEventManager().registerListener(listener);
    }

    static boolean needsProfileRefresh(Appearance previous, Appearance next, ProfileState nativeProfile) {
        if (!next.ownsProfile() && (previous == null || !previous.ownsProfile())) return false;
        return !next.profile().equals(previous == null ? nativeProfile : previous.profile());
    }

    void apply(Player target, Player viewer, String name, Component display, SkinData skin) {
        if (closed || !target.isOnline() || !viewer.isOnline()) return;
        Pair pair = new Pair(target.getUniqueId(), viewer.getUniqueId());
        PlayerProfile nativeProfile = target.getPlayerProfile();
        Appearance next = appearance(target, name, display, skin, nativeProfile);
        Appearance previous = applied.get(pair);
        desired.put(pair, next);
        if (!viewer.canSee(target)) return;
        boolean refresh = failed.remove(pair) || needsProfileRefresh(previous, next, ProfileState.of(nativeProfile, target.getName(), null));
        try {
            if (refresh) refreshProfile(target, viewer, pair, next);
            else if (previous == null ? display != null : !Objects.equals(previous.display(), display)) displaySender.send(target, viewer, display);
            if (!failed.contains(pair)) applied.put(pair, next);
            if (!next.active()) desired.remove(pair, next);
        } catch (Exception ex) {
            if (refresh) failed.add(pair);
            plugin.getLogger().log(Level.SEVERE, "Failed to update client player info", ex);
        }
    }

    private void refreshProfile(Player target, Player viewer, Pair pair, Appearance next) throws ReflectiveOperationException {
        if (!target.equals(viewer) && target.isVisibleByDefault()) {
            try { viewer.hidePlayer(plugin, target); }
            finally { viewer.showPlayer(plugin, target); }
            return;
        }
        // Reuse observed chat/order/hat fields; partial updates never erase them.
        PlayerInfo previous = observed.get(pair);
        PlayerInfo data = previous == null ? new PlayerInfo(target.getUniqueId()) : copy(previous);
        data.setGameProfile(profile(nativeProfile(target), next));
        data.setListed(viewer.isListed(target));
        data.setLatency(target.getPing());
        data.setGameMode(GameMode.valueOf(target.getGameMode().name()));
        data.setDisplayName(next.display() == null ? target.playerListName() : next.display());
        if (previous == null) data.setShowHat(true);
        boolean tracked = !target.equals(viewer) && target.getTrackedBy().contains(viewer);
        TrackedEntityRefresh.Pairing pairing = tracked ? TrackedEntityRefresh.prepare(target, viewer) : null;
        if (pairing != null) pairing.remove();
        api.getPlayerManager().sendPacketSilently(viewer, new WrapperPlayServerPlayerInfoRemove(target.getUniqueId()));
        EnumSet<Action> actions = EnumSet.of(Action.ADD_PLAYER, Action.INITIALIZE_CHAT, Action.UPDATE_GAME_MODE,
                Action.UPDATE_LATENCY, Action.UPDATE_LISTED, Action.UPDATE_DISPLAY_NAME);
        ServerVersion version = api.getServerManager().getVersion();
        if (version.isNewerThanOrEquals(ServerVersion.V_1_21_2)) actions.add(Action.UPDATE_LIST_ORDER);
        if (version.isNewerThanOrEquals(ServerVersion.V_1_21_4)) actions.add(Action.UPDATE_HAT);
        api.getPlayerManager().sendPacketSilently(viewer, new WrapperPlayServerPlayerInfoUpdate(actions, data));
        if (pairing != null) pairing.add();
    }

    private void sendDisplay(Player target, Player viewer, Component display) {
        PlayerInfo data = new PlayerInfo(target.getUniqueId());
        data.setDisplayName(display == null ? target.playerListName() : display);
        api.getPlayerManager().sendPacketSilently(viewer, new WrapperPlayServerPlayerInfoUpdate(Action.UPDATE_DISPLAY_NAME, data));
    }

    private synchronized void rewrite(PacketSendEvent event) {
        if (closed || event.getUser() == null) return;
        UUID viewer = event.getUser().getUUID();
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_REMOVE) {
            for (UUID target : new WrapperPlayServerPlayerInfoRemove(event).getProfileIds()) observed.remove(new Pair(target, viewer));
            return;
        }
        WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);
        EnumSet<Action> actions = packet.getActions();
        List<PlayerInfo> rewritten = new ArrayList<>();
        boolean changed = false;
        boolean displayAction = false;
        for (PlayerInfo data : packet.getEntries()) {
            Pair pair = new Pair(data.getProfileId(), viewer);
            observed.put(pair, merge(observed.get(pair), data, actions));
            Appearance state = desired.get(pair);
            if (state == null || (!actions.contains(Action.ADD_PLAYER) && !actions.contains(Action.UPDATE_DISPLAY_NAME))) {
                rewritten.add(data);
                continue;
            }
            PlayerInfo next = copy(data);
            if (actions.contains(Action.ADD_PLAYER)) next.setGameProfile(profile(data.getGameProfile(), state));
            if (state.display() != null) {
                next.setDisplayName(state.display());
                displayAction |= actions.contains(Action.ADD_PLAYER);
            }
            rewritten.add(next);
            changed = true;
        }
        if (changed) {
            if (displayAction && !actions.contains(Action.UPDATE_DISPLAY_NAME)) {
                actions = EnumSet.copyOf(actions);
                actions.add(Action.UPDATE_DISPLAY_NAME);
                packet.setActions(actions);
            }
            packet.setEntries(rewritten);
            event.markForReEncode(true);
        }
    }

    static PlayerInfo copy(PlayerInfo entry) {
        PlayerInfo result = new PlayerInfo(entry);
        UserProfile profile = entry.getGameProfile();
        if (profile != null) result.setGameProfile(new UserProfile(profile.getUUID(), profile.getName(), new ArrayList<>(profile.getTextureProperties())));
        return result;
    }

    static PlayerInfo merge(PlayerInfo previous, PlayerInfo delta, Set<Action> actions) {
        PlayerInfo result = previous == null ? new PlayerInfo(delta.getProfileId()) : copy(previous);
        for (Action action : actions) {
            switch (action) {
                case ADD_PLAYER -> result.setGameProfile(copy(delta).getGameProfile());
                case INITIALIZE_CHAT -> result.setChatSession(delta.getChatSession());
                case UPDATE_GAME_MODE -> result.setGameMode(delta.getGameMode());
                case UPDATE_LISTED -> result.setListed(delta.isListed());
                case UPDATE_LATENCY -> result.setLatency(delta.getLatency());
                case UPDATE_DISPLAY_NAME -> result.setDisplayName(delta.getDisplayName());
                case UPDATE_LIST_ORDER -> result.setListOrder(delta.getListOrder());
                case UPDATE_HAT -> result.setShowHat(delta.isShowHat());
            }
        }
        return result;
    }

    private static UserProfile nativeProfile(Player target) {
        List<TextureProperty> properties = target.getPlayerProfile().getProperties().stream()
                .map(property -> new TextureProperty(property.getName(), property.getValue(), property.getSignature())).toList();
        return new UserProfile(target.getUniqueId(), target.getName(), properties);
    }

    private static UserProfile profile(UserProfile original, Appearance desired) {
        List<TextureProperty> properties = new ArrayList<>();
        for (TextureProperty property : original.getTextureProperties()) {
            if (!desired.reskin() || !property.getName().equals("textures")) properties.add(property);
        }
        if (desired.reskin()) for (Texture texture : desired.profile().textures()) properties.add(new TextureProperty("textures", texture.value(), texture.signature()));
        return new UserProfile(original.getUUID(), desired.rename() ? desired.profile().name() : original.getName(), properties);
    }

    void prepare(Player target, Player viewer, String name, Component display, SkinData skin, PlayerProfile nativeProfile) {
        if (closed) return;
        Pair pair = new Pair(target.getUniqueId(), viewer.getUniqueId());
        failed.remove(pair);
        desired.put(pair, appearance(target, name, display, skin, nativeProfile));
    }

    private static Appearance appearance(Player target, String name, Component display, SkinData skin, PlayerProfile nativeProfile) {
        return new Appearance(ProfileState.of(nativeProfile, name, skin), display, !name.equals(target.getName()), skin != null && skin.isValid());
    }

    boolean hasState(UUID target) {
        return applied.keySet().stream().anyMatch(pair -> pair.target().equals(target))
                || desired.keySet().stream().anyMatch(pair -> pair.target().equals(target));
    }

    void nativeRefreshComplete(Player target, Collection<? extends Player> viewers) {
        for (Player viewer : viewers) {
            Pair pair = new Pair(target.getUniqueId(), viewer.getUniqueId());
            Appearance next = desired.get(pair);
            if (next != null && viewer.canSee(target) && !failed.contains(pair)) applied.put(pair, next);
        }
    }

    synchronized void remove(UUID id) {
        desired.keySet().removeIf(pair -> pair.target().equals(id) || pair.viewer().equals(id));
        applied.keySet().removeIf(pair -> pair.target().equals(id) || pair.viewer().equals(id));
        observed.keySet().removeIf(pair -> pair.target().equals(id) || pair.viewer().equals(id));
        failed.removeIf(pair -> pair.target().equals(id) || pair.viewer().equals(id));
    }

    @Override public void close() {
        synchronized (this) { closed = true; desired.clear(); applied.clear(); observed.clear(); failed.clear(); }
        api.getEventManager().unregisterListener(listener);
    }
}
