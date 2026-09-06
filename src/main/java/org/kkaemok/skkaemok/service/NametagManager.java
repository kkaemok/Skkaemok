package org.kkaemok.skkaemok.service;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
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
    private final ConcurrentHashMap<UUID, BukkitTask> priorityTasks;
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
        formatter.reload();
        restartTeamPolling();
    }

    public void updateForAllViewers(Player target) {
        applyForAllViewers(target, true);
    }

    public void updateForViewer(Player target, Player viewer) {
        if (target == null || viewer == null) {
            return;
        }
        DisplayParts globalParts = buildParts(target, null);
        rememberActiveDisplay(target, globalParts);
        applyTabCustomName(target, globalParts);
        applyForViewer(target, viewer, buildParts(target, viewer));
        schedulePriorityReapply(target);
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
        if (target == null) {
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
        if (parts.manageTeam()) {
            updateTeamForViewer(viewer, target, parts.profileName(), parts.prefix(), parts.suffix(), parts.sourceTeam());
        } else {
            removeTeamForViewer(viewer, teamNames.get(target.getUniqueId()));
        }

        WrappedChatComponent displayName = parts.tabListName() == null
                ? null
                : toWrappedComponent(parts.tabListName());
        updatePlayerNameTag(target, viewer, parts.profileName(), displayName, parts.skinData());
    }

    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }
        cancelPriorityTask(player);
        removeTeam(player);
        viewerTeams.remove(player.getUniqueId());
        activeDisplays.remove(player.getUniqueId());
        mirroredTeamState.remove(player.getUniqueId());
        tabCustomNameBridge.resetCustomTabName(player);
    }

    public void close() {
        protocolManager.removePacketListener(scoreboardTeamListener);
        if (teamPollTask != null) {
            teamPollTask.cancel();
            teamPollTask = null;
        }
        for (BukkitTask task : priorityTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        priorityTasks.clear();
        for (String teamName : teamNames.values()) {
            forgetTeamForViewers(teamName);
        }
        teamNames.clear();
        viewerTeams.clear();
        activeDisplays.clear();
        mirroredTeamState.clear();
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
                    updatePlayerNameTag(
                            target,
                            viewer,
                            parts.profileName(),
                            toWrappedComponent(parts.tabListName()),
                            parts.skinData()
                    );
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

    private void schedulePriorityReapply(Player player) {
        if (player == null) {
            cancelPriorityTask(player);
            return;
        }
        boolean nicknameActive = nameManager.hasAnyNickname(player);
        boolean tablistActive = tablistNameManager.hasAnyTablistName(player);
        if (!nicknameActive && !tablistActive) {
            cancelPriorityTask(player);
            return;
        }
        if (!tabIntegration.isReapplyEnabled()) {
            cancelPriorityTask(player);
            return;
        }

        int delayTicks = tabIntegration.getReapplyDelayTicks();
        if (delayTicks <= 0) {
            return;
        }

        cancelPriorityTask(player);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            priorityTasks.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            applyForAllViewers(player, false);
        }, delayTicks);

        priorityTasks.put(player.getUniqueId(), task);
    }

    private void cancelPriorityTask(Player player) {
        if (player == null) {
            return;
        }
        BukkitTask existing = priorityTasks.remove(player.getUniqueId());
        if (existing != null) {
            existing.cancel();
        }
    }

    private void updateTeamForViewer(Player viewer,
                                     Player target,
                                     String entryName,
                                     Component prefix,
                                     Component suffix,
                                     VanillaTeamService.TeamInfo sourceTeam) {
        if (viewer == null || target == null || entryName == null || !viewer.isOnline()) {
            return;
        }

        String teamName = teamNames.computeIfAbsent(target.getUniqueId(), this::createTeamName);
        if (conflictsWithOtherOnlinePlayer(target, entryName)) {
            removeTeamForViewer(viewer, teamName);
            return;
        }

        Set<String> teams = viewerTeams.computeIfAbsent(viewer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        if (teams.remove(teamName)) {
            sendTeamRemovePacket(viewer, teamName);
        }

        try {
            PacketContainer packet = createTeamPacket(
                    teamName,
                    0,
                    createTeamParameters(prefix, suffix, sourceTeam),
                    Collections.singletonList(entryName)
            );
            protocolManager.sendServerPacket(viewer, packet, false);
            teams.add(teamName);
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

    private void updatePlayerNameTag(Player target, Player viewer, String customName, WrappedChatComponent displayName, SkinData skinData) {
        if (!target.isOnline() || !viewer.isOnline()) {
            return;
        }

        boolean samePlayer = target.getUniqueId().equals(viewer.getUniqueId());
        if (!samePlayer) {
            viewer.hidePlayer(plugin, target);
        }

        try {
            PacketContainer removePacket = new PacketContainer(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            removePacket.getUUIDLists().write(0, Collections.singletonList(target.getUniqueId()));
            protocolManager.sendServerPacket(viewer, removePacket);

            PacketContainer infoPacket = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);
            EnumSet<EnumWrappers.PlayerInfoAction> actions = EnumSet.of(
                    EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                    EnumWrappers.PlayerInfoAction.INITIALIZE_CHAT,
                    EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE,
                    EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
                    EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
                    EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME
            );
            infoPacket.getPlayerInfoActions().write(0, actions);

            WrappedGameProfile originalProfile = WrappedGameProfile.fromPlayer(target);
            WrappedGameProfile customProfile = new WrappedGameProfile(originalProfile.getUUID(), customName);
            boolean hasCustomSkin = skinData != null && skinData.isValid();
            var properties = originalProfile.getProperties();
            for (String key : properties.keySet()) {
                for (WrappedSignedProperty prop : properties.get(key)) {
                    if (hasCustomSkin && "textures".equals(prop.getName())) {
                        continue;
                    }
                    customProfile.getProperties().put(prop.getName(), prop);
                }
            }
            if (hasCustomSkin) {
                customProfile.getProperties().put("textures",
                        new WrappedSignedProperty("textures", skinData.getValue(), skinData.getSignature()));
            }

            WrappedRemoteChatSessionData chatSession = WrappedRemoteChatSessionData.fromPlayer(target);

            PlayerInfoData playerInfoData = new PlayerInfoData(
                    target.getUniqueId(),
                    target.getPing(),
                    true,
                    EnumWrappers.NativeGameMode.fromBukkit(target.getGameMode()),
                    customProfile,
                    displayName,
                    chatSession
            );

            List<PlayerInfoData> dataList = Collections.singletonList(playerInfoData);
            try {
                infoPacket.getPlayerInfoDataLists().write(0, dataList);
            } catch (Exception ignored) {
                infoPacket.getPlayerInfoDataLists().write(1, dataList);
            }
            protocolManager.sendServerPacket(viewer, infoPacket);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating nametag", e);
        }

        if (!samePlayer) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> viewer.showPlayer(plugin, target), 2L);
        }
    }

    private WrappedChatComponent toWrappedComponent(Component component) {
        String json = GsonComponentSerializer.gson().serialize(component);
        return WrappedChatComponent.fromJson(json);
    }
}
