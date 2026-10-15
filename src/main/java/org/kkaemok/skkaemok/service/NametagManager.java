package org.kkaemok.skkaemok.service;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedTeamParameters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.kkaemok.skkaemok.integration.LuckPermsHook;
import org.kkaemok.skkaemok.integration.TabCustomNameBridge;
import org.kkaemok.skkaemok.integration.TabIntegration;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class NametagManager {
    private record DisplayParts(String profileName,
                                Component tabListName,
                                Component prefix,
                                Component suffix,
                                String tabCustomName,
                                VanillaTeamService.TeamInfo sourceTeam,
                                SkinData skinData,
                                boolean manageTeam) {
    }

    private record DisplayDecoration(Component prefix,
                                     Component suffix,
                                     Component luckPermsPrefix,
                                     Component luckPermsSuffix,
                                     boolean luckPerms) {
    }

    private record ActiveDisplay() {
    }

    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;
    private final TabIntegration tabIntegration;
    private final LuckPermsHook luckPermsHook;
    private final TabCustomNameBridge tabCustomNameBridge;
    private final NameManager nameManager;
    private final SkinManager skinManager;
    private final TablistNameManager tablistNameManager;
    private final VanillaTeamService vanillaTeamService;
    private final DisplayFormatter formatter;
    private final ConcurrentHashMap<UUID, String> teamNames;
    private final ConcurrentHashMap<UUID, Set<String>> viewerTeams;
    private final ConcurrentHashMap<ClientPlayerInfo.Pair, BukkitTask> priorityTasks;
    private final ConcurrentHashMap<UUID, BukkitTask> globalPriorityTasks = new ConcurrentHashMap<>();
    private final ClientPlayerInfo clientPlayerInfo;
    private record TeamState(String entry, Component prefix, Component suffix, VanillaTeamService.TeamInfo source) { }
    private final ConcurrentHashMap<ClientPlayerInfo.Pair, TeamState> appliedTeams = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ActiveDisplay> activeDisplays;
    private final ConcurrentHashMap<UUID, VanillaTeamService.TeamInfo> mirroredTeamState;
    private final AtomicBoolean teamRefreshScheduled;
    private final PacketAdapter scoreboardTeamListener;
    private volatile Runnable vanillaTeamRefreshCallback;
    private BukkitTask teamPollTask;

    public NametagManager(JavaPlugin plugin,
                          TabIntegration tabIntegration,
                          LuckPermsHook luckPermsHook,
                          TabCustomNameBridge tabCustomNameBridge,
                          NameManager nameManager,
                          SkinManager skinManager,
                          TablistNameManager tablistNameManager,
                          VanillaTeamService vanillaTeamService) {
        if (plugin == null || tabIntegration == null || luckPermsHook == null
                || tabCustomNameBridge == null || nameManager == null || skinManager == null
                || tablistNameManager == null || vanillaTeamService == null) {
            throw new IllegalStateException("Plugin instance cannot be null");
        }
        this.plugin = plugin;
        this.tabIntegration = tabIntegration;
        this.luckPermsHook = luckPermsHook;
        this.tabCustomNameBridge = tabCustomNameBridge;
        this.nameManager = nameManager;
        this.skinManager = skinManager;
        this.tablistNameManager = tablistNameManager;
        this.vanillaTeamService = vanillaTeamService;
        this.formatter = new DisplayFormatter(plugin);
        this.teamNames = new ConcurrentHashMap<>();
        this.viewerTeams = new ConcurrentHashMap<>();
        this.priorityTasks = new ConcurrentHashMap<>();
        this.activeDisplays = new ConcurrentHashMap<>();
        this.mirroredTeamState = new ConcurrentHashMap<>();
        this.teamRefreshScheduled = new AtomicBoolean();
        this.vanillaTeamRefreshCallback = this::refreshVanillaTeams;
        try {
            this.protocolManager = ProtocolLibrary.getProtocolManager();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize ProtocolLib", e);
        }
        this.clientPlayerInfo = new ClientPlayerInfo(plugin, protocolManager);

        this.scoreboardTeamListener = new PacketAdapter(
                plugin,
                ListenerPriority.MONITOR,
                PacketType.Play.Server.SCOREBOARD_TEAM
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                scheduleVanillaTeamRefresh();
            }
        };
        protocolManager.addPacketListener(scoreboardTeamListener);
        restartTeamPolling();
    }

    public void reload() {
        cancelPriorityTasks();
        formatter.reload();
        restartTeamPolling();
    }

    public void updateForAllViewers(Player target) {
        applyForAllViewers(target, true);
    }

    public boolean hasClientState(Player target) {
        return target != null && clientPlayerInfo.hasState(target.getUniqueId());
    }

    void updateNativeProfile(Player target, com.destroystokyo.paper.profile.PlayerProfile profile) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            DisplayParts parts = buildParts(target, viewer);
            clientPlayerInfo.prepare(target, viewer, parts.profileName(), parts.tabListName(), parts.skinData(), profile);
        }
        target.setPlayerProfile(profile);
        clientPlayerInfo.nativeRefreshComplete(target, Bukkit.getOnlinePlayers());
        updateForAllViewers(target);
    }

    public void updateForViewer(Player target, Player viewer) {
        if (target == null || viewer == null || !target.isOnline() || !viewer.isOnline()) {
            return;
        }
        DisplayParts globalParts = buildParts(target, null);
        rememberActiveDisplay(target, globalParts);
        applyForViewer(target, viewer, buildParts(target, viewer));
        schedulePriorityReapply(target, viewer);
    }

    // Compatibility entry points retained for existing internal and external callers.
    public void updateForAllViewers(Player target, String ignoredName) {
        updateForAllViewers(target);
    }

    public void updateForViewer(Player target, Player viewer, String ignoredName) {
        updateForViewer(target, viewer);
    }

    public void updateForAllViewers(Player target, String ignoredName, SkinData ignoredSkin) {
        updateForAllViewers(target);
    }

    public void updateForViewer(Player target, Player viewer, String ignoredName, SkinData ignoredSkin) {
        updateForViewer(target, viewer);
    }

    public void updateForAllViewers(Player target,
                                    String ignoredName,
                                    boolean ignoredNicknameActive,
                                    SkinData ignoredSkin) {
        updateForAllViewers(target);
    }

    public void updateForViewer(Player target,
                                Player viewer,
                                String ignoredName,
                                boolean ignoredNicknameActive,
                                SkinData ignoredSkin) {
        updateForViewer(target, viewer);
    }

    private void applyForAllViewers(Player target, boolean schedulePriority) {
        if (target == null || !target.isOnline()) {
            return;
        }
        DisplayParts globalParts = buildParts(target, null);
        rememberActiveDisplay(target, globalParts);
        applyTabCustomName(target, globalParts);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            applyForViewer(target, viewer, buildParts(target, viewer));
        }
        discardUnusedTeamName(target);

        if (schedulePriority) {
            schedulePriorityReapply(target);
        }
    }

    private void applyForViewer(Player target, Player viewer, DisplayParts parts) {
        applyForViewer(target, viewer, parts, false);
    }

    private void applyForViewer(Player target, Player viewer, DisplayParts parts, boolean reapplyTeam) {
        if (parts.manageTeam()) {
            updateTeamForViewer(viewer, target, parts.profileName(), parts.prefix(), parts.suffix(), parts.sourceTeam(), reapplyTeam);
        } else {
            removeTeamForViewer(viewer, teamNames.get(target.getUniqueId()));
        }

        clientPlayerInfo.apply(target, viewer, parts.profileName(), parts.tabListName(), parts.skinData());
    }

    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        BukkitTask globalTask = globalPriorityTasks.remove(id);
        if (globalTask != null) globalTask.cancel();
        priorityTasks.entrySet().removeIf(entry -> {
            if (!entry.getKey().target().equals(id) && !entry.getKey().viewer().equals(id)) return false;
            entry.getValue().cancel();
            return true;
        });
        clientPlayerInfo.remove(id);
        appliedTeams.keySet().removeIf(pair -> pair.target().equals(id) || pair.viewer().equals(id));
        removeTeam(player);
        viewerTeams.remove(player.getUniqueId());
        activeDisplays.remove(player.getUniqueId());
        mirroredTeamState.remove(player.getUniqueId());
        tabCustomNameBridge.resetCustomTabName(player);
    }

    public void close() {
        clientPlayerInfo.close();
        protocolManager.removePacketListener(scoreboardTeamListener);
        if (teamPollTask != null) {
            teamPollTask.cancel();
            teamPollTask = null;
        }
        cancelPriorityTasks();
        for (String teamName : teamNames.values()) {
            forgetTeamForViewers(teamName);
        }
        teamNames.clear();
        viewerTeams.clear();
        activeDisplays.clear();
        mirroredTeamState.clear();
        appliedTeams.clear();
        teamRefreshScheduled.set(false);
    }

    public void setVanillaTeamRefreshCallback(Runnable callback) {
        vanillaTeamRefreshCallback = callback == null ? this::refreshVanillaTeams : callback;
    }

    /**
     * Compares only players whose current display depends on vanilla team state.
     * This is called once after a burst of outgoing vanilla scoreboard-team packets.
     */
    public Set<Player> refreshVanillaTeams() {
        if (!Bukkit.isPrimaryThread()) {
            scheduleVanillaTeamRefresh();
            return Collections.emptySet();
        }

        Set<Player> changedPlayers = new HashSet<>();
        for (var entry : activeDisplays.entrySet()) {
            UUID playerId = entry.getKey();
            Player target = Bukkit.getPlayer(playerId);
            if (target == null || !target.isOnline()) {
                activeDisplays.remove(playerId, entry.getValue());
                mirroredTeamState.remove(playerId);
                continue;
            }

            VanillaTeamService.TeamInfo current = vanillaTeamService.getInfo(target);
            VanillaTeamService.TeamInfo previous = mirroredTeamState.get(playerId);
            if (current.equals(previous)) {
                continue;
            }
            changedPlayers.add(target);

            DisplayParts globalParts = buildParts(target, null);
            mirroredTeamState.put(playerId, globalParts.sourceTeam());
            applyTabCustomName(target, globalParts);

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                DisplayParts parts = buildParts(target, viewer);
                if (parts.manageTeam()) {
                    updateTeamForViewer(viewer, target, parts.profileName(), parts.prefix(), parts.suffix(), parts.sourceTeam());
                } else {
                    removeTeamForViewer(viewer, teamNames.get(playerId));
                }

                // A direct custom tablist name is carried by PLAYER_INFO rather than
                // the scoreboard team, so it must be resent when team decoration changes.
                if (parts.tabListName() != null) {
                    clientPlayerInfo.apply(target, viewer, parts.profileName(), parts.tabListName(), parts.skinData());
                }
            }
            discardUnusedTeamName(target);
        }
        return changedPlayers;
    }

    public boolean hasManagedDecoration(Player target) {
        if (target == null) {
            return false;
        }
        VanillaTeamService.TeamInfo teamInfo = vanillaTeamService.getInfo(target);
        return resolveDecoration(target, teamInfo).luckPerms();
    }

    private void rememberActiveDisplay(Player target, DisplayParts parts) {
        UUID playerId = target.getUniqueId();
        boolean dependsOnTeam = nameManager.hasAnyNickname(target)
                || tablistNameManager.hasAnyTablistName(target)
                || parts.manageTeam();
        if (!dependsOnTeam) {
            activeDisplays.remove(playerId);
            mirroredTeamState.remove(playerId);
            return;
        }

        activeDisplays.put(playerId, new ActiveDisplay());
        mirroredTeamState.put(playerId, parts.sourceTeam());
    }

    private void scheduleVanillaTeamRefresh() {
        if (!plugin.isEnabled() || !teamRefreshScheduled.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            teamRefreshScheduled.set(false);
            vanillaTeamRefreshCallback.run();
        });
    }

    private void restartTeamPolling() {
        if (teamPollTask != null) {
            teamPollTask.cancel();
            teamPollTask = null;
        }
        long interval = plugin.getConfig().getLong("vanilla-team-sync.fallback-interval-ticks", 20L);
        if (interval <= 0L) {
            return;
        }
        interval = Math.max(10L, interval);
        teamPollTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> vanillaTeamRefreshCallback.run(),
                interval,
                interval
        );
    }

    private void applyTabCustomName(Player target, DisplayParts parts) {
        if (parts.tabCustomName() != null) {
            tabCustomNameBridge.setCustomTabName(target, parts.tabCustomName());
        } else {
            tabCustomNameBridge.resetCustomTabName(target);
        }
    }

    private DisplayParts buildParts(Player target, Player viewer) {
        VanillaTeamService.TeamInfo teamInfo = vanillaTeamService.getInfo(target);
        String customName = nameManager.loadNickname(target, viewer);
        boolean nicknameActive = nameManager.hasNickname(target)
                || (viewer != null && nameManager.hasNickname(target, viewer));
        boolean hasGlobalTablistName = tablistNameManager.hasTablistName(target);
        boolean hasViewerTablistName = viewer != null && tablistNameManager.hasTablistName(target, viewer);
        boolean hasTablistName = hasGlobalTablistName || hasViewerTablistName;
        boolean useTabCustomName = tabIntegration.shouldUseTabCustomTabName();

        DisplayDecoration decoration = resolveDecoration(target, teamInfo);
        boolean manageDisplay = tabIntegration.shouldManageDisplay();
        String effectiveTablistName = tablistNameManager.resolveTablistName(target, viewer);
        boolean shouldUseCustomProfileName = nicknameActive && manageDisplay;
        String profileName = shouldUseCustomProfileName ? customName : target.getName();

        String tabTemplate = useTabCustomName && viewer == null
                ? tabIntegration.getCustomNameFormat()
                : plugin.getConfig().getString("output.tablist", TablistPresentation.DEFAULT_TEMPLATE);
        Component tabList = TablistPresentation.compose(
                tabTemplate,
                teamInfo.prefix(),
                decoration.luckPermsPrefix(),
                formatter.parseNickname(effectiveTablistName),
                decoration.luckPermsSuffix(),
                teamInfo.suffix(),
                Component.text(target.getName()),
                teamInfo.color(),
                formatter::parseOutput
        );
        boolean shouldManageTeam = shouldUseCustomProfileName || decoration.luckPerms();
        boolean shouldApplyTabCustomName = viewer == null && ((nicknameActive && manageDisplay)
                || hasGlobalTablistName
                || teamInfo.hasDecoration());
        // PLAYER_INFO falls back to the profile name when displayName is null.
        // A nametag/profile rewrite must therefore carry the independently
        // resolved tablist component even when no tablist override is stored.
        boolean shouldSendTabListName = TablistPresentation.requiresExplicitDisplayName(
                profileName,
                effectiveTablistName,
                hasTablistName
        ) || TablistPresentation.usesLuckPerms(tabTemplate, decoration.luckPerms());
        String tabCustomName = useTabCustomName && shouldApplyTabCustomName
                ? formatter.serializeAmpersand(tabList)
                : null;

        return new DisplayParts(
                profileName,
                shouldSendTabListName ? tabList : null,
                decoration.prefix(),
                decoration.suffix(),
                tabCustomName,
                teamInfo,
                skinManager.resolveSkin(target, viewer),
                shouldManageTeam
        );
    }

    private DisplayDecoration resolveDecoration(Player target, VanillaTeamService.TeamInfo teamInfo) {
        String luckPermsPrefixRaw = luckPermsHook.getPrefix(target.getUniqueId());
        String luckPermsSuffixRaw = luckPermsHook.getSuffix(target.getUniqueId());
        boolean hasLuckPermsDecoration = hasText(luckPermsPrefixRaw) || hasText(luckPermsSuffixRaw);

        Component luckPermsPrefix = formatter.parseLuckPerms(luckPermsPrefixRaw);
        Component luckPermsSuffix = formatter.parseLuckPerms(luckPermsSuffixRaw);
        String template = plugin.getConfig().getString(
                "output.nametag",
                NametagComposition.DEFAULT_TEMPLATE
        );
        NametagComposition.Parts composition = NametagComposition.compose(
                template,
                teamInfo.prefix(),
                luckPermsPrefix,
                luckPermsSuffix,
                teamInfo.suffix(),
                Component.text(target.getName()),
                formatter::parseOutput
        );

        return new DisplayDecoration(
                composition.prefix(),
                composition.suffix(),
                luckPermsPrefix,
                luckPermsSuffix,
                hasLuckPermsDecoration
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void schedulePriorityReapply(Player target) {
        UUID id = target.getUniqueId();
        BukkitTask previous = globalPriorityTasks.remove(id);
        if (previous != null) previous.cancel();
        int delay = tabIntegration.getReapplyDelayTicks();
        if (tabIntegration.isReapplyEnabled() && delay > 0) {
            globalPriorityTasks.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
                globalPriorityTasks.remove(id);
                Player current = Bukkit.getPlayer(id);
                if (current != null && plugin.isEnabled()) applyTabCustomName(current, buildParts(current, null));
            }, delay));
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            schedulePriorityReapply(target, viewer);
        }
    }

    private void schedulePriorityReapply(Player target, Player viewer) {
        ClientPlayerInfo.Pair pair = new ClientPlayerInfo.Pair(target.getUniqueId(), viewer.getUniqueId());
        BukkitTask previous = priorityTasks.remove(pair);
        if (previous != null) previous.cancel();
        int delay = tabIntegration.getReapplyDelayTicks();
        if (!tabIntegration.isReapplyEnabled() || delay <= 0
                || (!nameManager.hasAnyNickname(target) && !tablistNameManager.hasAnyTablistName(target))) return;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            priorityTasks.remove(pair);
            Player currentTarget = Bukkit.getPlayer(pair.target());
            Player currentViewer = Bukkit.getPlayer(pair.viewer());
            if (currentTarget == null || currentViewer == null || !plugin.isEnabled()) return;
            // Resolve current values at execution time, never capture stale parts.
            applyForViewer(currentTarget, currentViewer, buildParts(currentTarget, currentViewer), true);
        }, delay);
        priorityTasks.put(pair, task);
    }

    private void cancelPriorityTasks() {
        globalPriorityTasks.values().forEach(BukkitTask::cancel);
        globalPriorityTasks.clear();
        priorityTasks.values().forEach(BukkitTask::cancel);
        priorityTasks.clear();
    }

    private void updateTeamForViewer(Player viewer,
                                     Player target,
                                     String entryName,
                                     Component prefix,
                                     Component suffix,
                                     VanillaTeamService.TeamInfo sourceTeam) {
        updateTeamForViewer(viewer, target, entryName, prefix, suffix, sourceTeam, false);
    }

    private void updateTeamForViewer(Player viewer,
                                     Player target,
                                     String entryName,
                                     Component prefix,
                                     Component suffix,
                                     VanillaTeamService.TeamInfo sourceTeam,
                                     boolean reapplyEntry) {
        if (viewer == null || target == null || entryName == null || !viewer.isOnline()) {
            return;
        }

        String teamName = teamNames.computeIfAbsent(target.getUniqueId(), this::createTeamName);
        if (conflictsWithOtherOnlinePlayer(target, entryName)) {
            removeTeamForViewer(viewer, teamName);
            return;
        }

        Set<String> teams = viewerTeams.computeIfAbsent(viewer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        ClientPlayerInfo.Pair pair = new ClientPlayerInfo.Pair(target.getUniqueId(), viewer.getUniqueId());
        TeamState next = new TeamState(entryName, prefix, suffix, sourceTeam);
        TeamState previous = appliedTeams.get(pair);
        if (next.equals(previous) && teams.contains(teamName)) {
            if (reapplyEntry) {
                try {
                    // TAB may have reassigned this entry after our original update.
                    protocolManager.sendServerPacket(viewer,
                            createTeamPacket(teamName, 3, null, Collections.singletonList(entryName)), false);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to reapply nametag team membership", ex);
                }
            }
            return;
        }
        boolean sameEntry = previous != null && previous.entry().equals(entryName) && teams.contains(teamName);
        if (!sameEntry && teams.remove(teamName)) {
            sendTeamRemovePacket(viewer, teamName);
        }

        try {
            PacketContainer packet = createTeamPacket(
                    teamName,
                    sameEntry ? 2 : 0,
                    createTeamParameters(prefix, suffix, sourceTeam),
                    sameEntry ? Collections.emptyList() : Collections.singletonList(entryName)
            );
            protocolManager.sendServerPacket(viewer, packet, false);
            teams.add(teamName);
            appliedTeams.put(pair, next);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error sending fake nametag team packet.", e);
        }
    }

    private boolean conflictsWithOtherOnlinePlayer(Player target, String entryName) {
        Player entryPlayer = Bukkit.getPlayerExact(entryName);
        return entryPlayer != null && !entryPlayer.getUniqueId().equals(target.getUniqueId());
    }

    private PacketContainer createTeamPacket(String teamName,
                                             int mode,
                                             WrappedTeamParameters parameters,
                                             Collection<String> entries) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.SCOREBOARD_TEAM);
        packet.getStrings().write(0, teamName);
        packet.getIntegers().write(0, mode);
        packet.getOptionalTeamParameters().write(0, java.util.Optional.ofNullable(parameters));
        packet.getSpecificModifier(Collection.class).write(0, entries);
        return packet;
    }

    private WrappedTeamParameters createTeamParameters(Component prefix,
                                                       Component suffix,
                                                       VanillaTeamService.TeamInfo sourceTeam) {
        return WrappedTeamParameters.newBuilder()
                .displayName(toWrappedComponent(sourceTeam.displayName()))
                .prefix(toWrappedComponent(prefix == null ? Component.empty() : prefix))
                .suffix(toWrappedComponent(suffix == null ? Component.empty() : suffix))
                .nametagVisibility(VanillaTeamPacketCodec.nameTagVisibility(sourceTeam.nameTagVisibility()))
                .collisionRule(VanillaTeamPacketCodec.collisionRule(sourceTeam.collisionRule()))
                .color(VanillaTeamPacketCodec.color(sourceTeam.color()))
                .options(VanillaTeamPacketCodec.friendlyFlags(sourceTeam))
                .build();
    }

    private void removeTeam(Player player) {
        if (player == null) {
            return;
        }
        String teamName = teamNames.remove(player.getUniqueId());
        if (teamName == null) {
            return;
        }
        forgetTeamForViewers(teamName);
    }

    private void discardUnusedTeamName(Player target) {
        String teamName = teamNames.get(target.getUniqueId());
        if (teamName == null) {
            return;
        }
        for (Set<String> teams : viewerTeams.values()) {
            if (teams.contains(teamName)) {
                return;
            }
        }
        teamNames.remove(target.getUniqueId(), teamName);
    }

    private void removeTeamForViewer(Player viewer, String teamName) {
        if (viewer == null || teamName == null || !viewer.isOnline()) {
            return;
        }
        Set<String> teams = viewerTeams.get(viewer.getUniqueId());
        if (teams == null || !teams.remove(teamName)) {
            return;
        }
        sendTeamRemovePacket(viewer, teamName);
        appliedTeams.keySet().removeIf(pair -> pair.viewer().equals(viewer.getUniqueId())
                && teamName.equals(teamNames.get(pair.target())));
        if (teams.isEmpty()) {
            viewerTeams.remove(viewer.getUniqueId(), teams);
        }
    }

    private void forgetTeamForViewers(String teamName) {
        if (teamName == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            removeTeamForViewer(viewer, teamName);
        }
    }

    private void sendTeamRemovePacket(Player viewer, String teamName) {
        if (viewer == null || !viewer.isOnline() || teamName == null) {
            return;
        }
        try {
            PacketContainer packet = createTeamPacket(teamName, 1, null, Collections.emptyList());
            protocolManager.sendServerPacket(viewer, packet, false);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing fake nametag team packet.", e);
        }
    }

    private String createTeamName(UUID uuid) {
        String raw = uuid.toString().replace("-", "");
        return "sm" + raw.substring(0, 13);
    }

    private WrappedChatComponent toWrappedComponent(Component component) {
        String json = GsonComponentSerializer.gson().serialize(component);
        return WrappedChatComponent.fromJson(json);
    }
}
