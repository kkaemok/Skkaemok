package org.kkaemok.skkaemok.service;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.kkaemok.skkaemok.integration.LuckPermsHook;
import org.kkaemok.skkaemok.integration.TabCustomNameBridge;
import org.kkaemok.skkaemok.integration.TabIntegration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class NametagManager {
    private record DisplayParts(String profileName,
                                Component tabListName,
                                Component prefix,
                                Component suffix,
                                String tabCustomName,
                                boolean manageTeam) {
    }

    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;
    private final TabIntegration tabIntegration;
    private final LuckPermsHook luckPermsHook;
    private final TabCustomNameBridge tabCustomNameBridge;
    private final DisplayFormatter formatter;
    private final Scoreboard scoreboard;
    private final ConcurrentHashMap<UUID, String> teamNames;
    private final ConcurrentHashMap<UUID, BukkitTask> priorityTasks;

    public NametagManager(JavaPlugin plugin,
                          TabIntegration tabIntegration,
                          LuckPermsHook luckPermsHook,
                          TabCustomNameBridge tabCustomNameBridge) {
        if (plugin == null || tabIntegration == null || luckPermsHook == null || tabCustomNameBridge == null) {
            throw new IllegalStateException("Plugin instance cannot be null");
        }
        this.plugin = plugin;
        this.tabIntegration = tabIntegration;
        this.luckPermsHook = luckPermsHook;
        this.tabCustomNameBridge = tabCustomNameBridge;
        this.formatter = new DisplayFormatter(plugin);
        this.teamNames = new ConcurrentHashMap<>();
        this.priorityTasks = new ConcurrentHashMap<>();
        Scoreboard resolvedScoreboard = null;
        if (Bukkit.getScoreboardManager() != null) {
            resolvedScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        }
        this.scoreboard = resolvedScoreboard;
        try {
            this.protocolManager = ProtocolLibrary.getProtocolManager();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize ProtocolLib", e);
        }
    }

    public void reload() {
        formatter.reload();
    }

    public void updateForAllViewers(Player target, String customName) {
        if (target == null || customName == null) {
            return;
        }
        boolean nicknameActive = !target.getName().equals(customName);
        updateForAllViewers(target, customName, nicknameActive, null);
    }

    public void updateForViewer(Player target, Player viewer, String customName) {
        if (target == null || viewer == null || customName == null) {
            return;
        }
        boolean nicknameActive = !target.getName().equals(customName);
        updateForViewer(target, viewer, customName, nicknameActive, null);
    }

    public void updateForAllViewers(Player target, String customName, SkinData skinData) {
        if (target == null || customName == null) {
            return;
        }
        boolean nicknameActive = !target.getName().equals(customName);
        updateForAllViewers(target, customName, nicknameActive, skinData);
    }

    public void updateForViewer(Player target, Player viewer, String customName, SkinData skinData) {
        if (target == null || viewer == null || customName == null) {
            return;
        }
        boolean nicknameActive = !target.getName().equals(customName);
        updateForViewer(target, viewer, customName, nicknameActive, skinData);
    }

    public void updateForAllViewers(Player target, String customName, boolean nicknameActive, SkinData skinData) {
        if (target == null || customName == null) {
            return;
        }
        applyForAllViewers(target, customName, nicknameActive, skinData, true);
    }

    private void applyForAllViewers(Player target,
                                    String customName,
                                    boolean nicknameActive,
                                    SkinData skinData,
                                    boolean schedulePriority) {
        if (target == null || customName == null) {
            return;
        }
        DisplayParts parts = buildParts(target, customName, nicknameActive);
        applyTabCustomName(target, parts);
        if (parts.manageTeam()) {
            updateTeam(target, parts.profileName(), parts.prefix(), parts.suffix());
        } else {
            removeTeam(target);
        }

        WrappedChatComponent displayName = parts.tabListName() == null ? null : toWrappedComponent(parts.tabListName());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updatePlayerNameTag(target, viewer, parts.profileName(), displayName, skinData);
        }

        if (schedulePriority) {
            schedulePriorityReapply(target, customName, nicknameActive, skinData);
        }
    }

    public void updateForViewer(Player target, Player viewer, String customName, boolean nicknameActive, SkinData skinData) {
        if (target == null || viewer == null || customName == null) {
            return;
        }

        DisplayParts parts = buildParts(target, customName, nicknameActive);
        applyTabCustomName(target, parts);
        if (parts.manageTeam()) {
            updateTeam(target, parts.profileName(), parts.prefix(), parts.suffix());
        } else {
            removeTeam(target);
        }

        WrappedChatComponent displayName = parts.tabListName() == null ? null : toWrappedComponent(parts.tabListName());
        updatePlayerNameTag(target, viewer, parts.profileName(), displayName, skinData);
    }

    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }
        cancelPriorityTask(player);
        removeTeam(player);
        tabCustomNameBridge.resetCustomTabName(player);
    }

    public void close() {
        for (BukkitTask task : priorityTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        priorityTasks.clear();
    }

    private void applyTabCustomName(Player target, DisplayParts parts) {
        if (parts.tabCustomName() != null) {
            tabCustomNameBridge.setCustomTabName(target, parts.tabCustomName());
        } else {
            tabCustomNameBridge.resetCustomTabName(target);
        }
    }

    private DisplayParts buildParts(Player target, String customName, boolean nicknameActive) {
        if (!nicknameActive) {
            return new DisplayParts(
                    target.getName(),
                    tabIntegration.shouldUseTabCustomTabName() ? null : Component.text(target.getName()),
                    Component.empty(),
                    Component.empty(),
                    null,
                    false
            );
        }

        boolean manageDisplay = tabIntegration.shouldManageDisplay();
        boolean useTabCustomName = tabIntegration.shouldUseTabCustomTabName();

        Component prefix = formatter.parseLuckPerms(luckPermsHook.getPrefix(target.getUniqueId()));
        Component suffix = formatter.parseLuckPerms(luckPermsHook.getSuffix(target.getUniqueId()));
        Component nickname = formatter.parseNickname(customName);
        Component original = Component.text(target.getName());

        String tabTemplate = plugin.getConfig().getString("output.tablist", "%prefix%%nickname%%suffix%");
        Component tabList = applyTemplate(tabTemplate, prefix, nickname, suffix, original);
        String tabCustomName = useTabCustomName ? formatter.serializeAmpersand(nickname) : null;

        return new DisplayParts(
                manageDisplay ? customName : target.getName(),
                manageDisplay && !useTabCustomName ? tabList : null,
                prefix,
                suffix,
                tabCustomName,
                manageDisplay
        );
    }

    private Component applyTemplate(String template,
                                    Component prefix,
                                    Component nickname,
                                    Component suffix,
                                    Component original) {
        if (template == null || template.isEmpty()) {
            return Component.empty().append(prefix).append(nickname).append(suffix);
        }

        Component result = Component.empty();
        int index = 0;
        while (index < template.length()) {
            int next = nextTokenIndex(template, index);
            if (next < 0) {
                result = result.append(formatter.parseOutput(template.substring(index)));
                break;
            }
            if (next > index) {
                result = result.append(formatter.parseOutput(template.substring(index, next)));
            }

            if (template.startsWith("%prefix%", next)) {
                result = result.append(prefix);
                index = next + "%prefix%".length();
            } else if (template.startsWith("%nickname%", next)) {
                result = result.append(nickname);
                index = next + "%nickname%".length();
            } else if (template.startsWith("%suffix%", next)) {
                result = result.append(suffix);
                index = next + "%suffix%".length();
            } else if (template.startsWith("%original%", next)) {
                result = result.append(original);
                index = next + "%original%".length();
            } else {
                result = result.append(formatter.parseOutput(template.substring(next, next + 1)));
                index = next + 1;
            }
        }
        return result;
    }

    private int nextTokenIndex(String template, int start) {
        int prefix = template.indexOf("%prefix%", start);
        int nickname = template.indexOf("%nickname%", start);
        int suffix = template.indexOf("%suffix%", start);
        int original = template.indexOf("%original%", start);

        int next = -1;
        if (prefix >= 0) {
            next = prefix;
        }
        if (nickname >= 0) {
            next = next < 0 ? nickname : Math.min(next, nickname);
        }
        if (suffix >= 0) {
            next = next < 0 ? suffix : Math.min(next, suffix);
        }
        if (original >= 0) {
            next = next < 0 ? original : Math.min(next, original);
        }
        return next;
    }

    private void schedulePriorityReapply(Player player, String customName, boolean nicknameActive, SkinData skinData) {
        if (player == null || !nicknameActive) {
            cancelPriorityTask(player);
            return;
        }
        if (!tabIntegration.isPriorityMode() || tabIntegration.isTabMissing() || !tabIntegration.shouldManageDisplay()) {
            cancelPriorityTask(player);
            return;
        }

        int delayTicks = tabIntegration.getPriorityDelayTicks();
        if (delayTicks <= 0) {
            return;
        }

        cancelPriorityTask(player);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            priorityTasks.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            applyForAllViewers(player, customName, true, skinData, false);
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

    private void updateTeam(Player player, String entryName, Component prefix, Component suffix) {
        if (player == null || entryName == null || scoreboard == null) {
            return;
        }

        String teamName = teamNames.computeIfAbsent(player.getUniqueId(), this::createTeamName);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.prefix(prefix == null ? Component.empty() : prefix);
        team.suffix(suffix == null ? Component.empty() : suffix);

        if (!team.hasEntry(entryName)) {
            for (String entry : new HashSet<>(team.getEntries())) {
                team.removeEntry(entry);
            }
            team.addEntry(entryName);
        }
    }

    private void removeTeam(Player player) {
        if (player == null || scoreboard == null) {
            return;
        }
        String teamName = teamNames.remove(player.getUniqueId());
        if (teamName == null) {
            return;
        }
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
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
                    EnumWrappers.PlayerInfoAction.UPDATE_LISTED
            );
            if (displayName != null) {
                actions.add(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
            }
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
