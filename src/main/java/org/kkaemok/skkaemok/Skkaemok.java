package org.kkaemok.skkaemok;

import ch.njol.skript.Skript;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.command.SkkaemokCommand;
import org.kkaemok.skkaemok.integration.LuckPermsHook;
import org.kkaemok.skkaemok.integration.TabCustomNameBridge;
import org.kkaemok.skkaemok.integration.TabIntegration;
import org.kkaemok.skkaemok.listener.AdvancementListener;
import org.kkaemok.skkaemok.listener.ChatCommandDecorateListener;
import org.kkaemok.skkaemok.listener.CommandInterceptor;
import org.kkaemok.skkaemok.listener.DeathListener;
import org.kkaemok.skkaemok.listener.OutgoingMessageRewriteListener;
import org.kkaemok.skkaemok.listener.PlayerSyncListener;
import org.kkaemok.skkaemok.listener.UpdateNotifyListener;
import org.kkaemok.skkaemok.service.ChatNameManager;
import org.kkaemok.skkaemok.service.ChatNameService;
import org.kkaemok.skkaemok.service.ChatNameStorage;
import org.kkaemok.skkaemok.service.NameManager;
import org.kkaemok.skkaemok.service.NameRewriteService;
import org.kkaemok.skkaemok.service.NametagManager;
import org.kkaemok.skkaemok.service.NameStorage;
import org.kkaemok.skkaemok.service.NicknameService;
import org.kkaemok.skkaemok.service.SkinManager;
import org.kkaemok.skkaemok.service.SkinService;
import org.kkaemok.skkaemok.service.SkinStorage;
import org.kkaemok.skkaemok.service.TablistNameManager;
import org.kkaemok.skkaemok.service.TablistNameService;
import org.kkaemok.skkaemok.service.TablistNameStorage;
import org.kkaemok.skkaemok.service.UpdateChecker;
import org.kkaemok.skkaemok.service.VanillaTeamService;
import org.kkaemok.skkaemok.service.ViewerNameManager;
import org.kkaemok.skkaemok.service.ViewerNameStorage;
import org.kkaemok.skkaemok.service.ViewerSkinStorage;
import org.kkaemok.skkaemok.skript.NametagEffects;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Skkaemok extends JavaPlugin {

    private NameManager nameManager;
    private SkinManager skinManager;
    private NametagManager nametagManager;
    private NicknameService nicknameService;
    private TablistNameManager tablistNameManager;
    private TablistNameService tablistNameService;
    private ChatNameManager chatNameManager;
    private ChatNameService chatNameService;
    private SkinService skinService;
    private NameRewriteService nameRewriteService;
    private LuckPermsHook luckPermsHook;
    private VanillaTeamService vanillaTeamService;
    private OutgoingMessageRewriteListener outgoingMessageRewriteListener;
    private UpdateChecker updateChecker;
    private ViewerNameManager viewerNameManager;
    private final ConcurrentHashMap<UUID, VanillaTeamService.TeamInfo> chatTeamStates = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int pluginId = 30391;
        new Metrics(this, pluginId);

        if (isPluginMissing("ProtocolLib")) {
            getLogger().severe("ProtocolLib is required.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (isPluginMissing("Skript")) {
            getLogger().severe("Skript is required.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        NameStorage storage = new NameStorage(this);
        SkinStorage skinStorage = new SkinStorage(this);
        TablistNameStorage tablistNameStorage = new TablistNameStorage(this);
        ChatNameStorage chatNameStorage = new ChatNameStorage(this);
        this.viewerNameManager = new ViewerNameManager(new ViewerNameStorage(this));
        this.nameManager = new NameManager(storage, viewerNameManager);
        this.skinManager = new SkinManager(skinStorage, new ViewerSkinStorage(this));
        this.tablistNameManager = new TablistNameManager(tablistNameStorage, viewerNameManager);
        this.chatNameManager = new ChatNameManager(chatNameStorage, viewerNameManager);
        TabIntegration tabIntegration = new TabIntegration(this);
        this.luckPermsHook = new LuckPermsHook(this);
        this.vanillaTeamService = new VanillaTeamService();
        TabCustomNameBridge tabCustomNameBridge = new TabCustomNameBridge(this, tabIntegration);
        this.nametagManager = new NametagManager(
                this,
                tabIntegration,
                luckPermsHook,
                tabCustomNameBridge,
                nameManager,
                skinManager,
                tablistNameManager,
                vanillaTeamService
        );
        this.nicknameService = new NicknameService(nameManager, nametagManager, skinManager);
        this.tablistNameService = new TablistNameService(nameManager, skinManager, nametagManager, tablistNameManager);
        this.chatNameService = new ChatNameService(chatNameManager, vanillaTeamService, this);
        this.nametagManager.setVanillaTeamRefreshCallback(this::refreshVanillaTeamState);
        this.skinService = new SkinService(this, nameManager, skinManager, nametagManager);
        this.nameRewriteService = new NameRewriteService(chatNameManager, luckPermsHook, vanillaTeamService, this);
        this.updateChecker = new UpdateChecker(this);
        luckPermsHook.registerMetaListener(uuid -> {
            var player = Bukkit.getPlayer(uuid);
            if (player != null) {
                chatNameService.applyStoredChatName(player);
                nicknameService.refreshDisplay(player);
            }
        });

        Bukkit.getPluginManager().registerEvents(new ChatCommandDecorateListener(this, nameRewriteService), this);
        Bukkit.getPluginManager().registerEvents(new CommandInterceptor(chatNameManager), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(nameRewriteService), this);
        Bukkit.getPluginManager().registerEvents(new AdvancementListener(nameRewriteService), this);
        Bukkit.getPluginManager().registerEvents(new PlayerSyncListener(nameManager, skinManager, nametagManager, tablistNameManager, chatNameService), this);
        Bukkit.getPluginManager().registerEvents(new UpdateNotifyListener(this, updateChecker), this);
        registerOutgoingMessageRewriteListener();
        updateChecker.start();

        Skript.registerAddon(this);
        NametagEffects.register(this, nicknameService, skinService, tablistNameService, chatNameService);

        registerCommands();
        refreshOnlinePlayers();
    }

    @Override
    public void onDisable() {
        unregisterOutgoingMessageRewriteListener();
        if (updateChecker != null) {
            updateChecker.stop();
        }
        if (luckPermsHook != null) {
            luckPermsHook.close();
        }
        if (nametagManager != null) {
            nametagManager.close();
        }
        if (nameManager != null) {
            nameManager.saveNow();
        }
        if (skinManager != null) {
            skinManager.saveNow();
        }
        if (tablistNameManager != null) {
            tablistNameManager.saveNow();
        }
        if (chatNameManager != null) {
            chatNameManager.saveNow();
        }
        if (viewerNameManager != null) {
            viewerNameManager.saveNow();
        }
        chatTeamStates.clear();
    }

    private boolean isPluginMissing(String name) {
        return Bukkit.getPluginManager().getPlugin(name) == null;
    }

    public void reloadSkkaemok() {
        reloadConfig();
        if (luckPermsHook != null) {
            luckPermsHook.reload();
        }
        if (skinService != null) {
            skinService.reloadConfig();
        }
        if (nicknameService != null) {
            nicknameService.reload();
        }
        if (nameManager != null) {
            nameManager.reload();
        }
        if (skinManager != null) {
            skinManager.reload();
        }
        if (tablistNameManager != null) {
            tablistNameManager.reload();
        }
        if (chatNameManager != null) {
            chatNameManager.reload();
        }
        if (viewerNameManager != null) {
            viewerNameManager.reload();
        }
        if (chatNameService != null) {
            chatNameService.reload();
        }
        if (nameRewriteService != null) {
            nameRewriteService.reload();
        }
        registerOutgoingMessageRewriteListener();
        if (updateChecker != null) {
            updateChecker.reload();
        }
        refreshOnlinePlayers();
    }

    private void registerOutgoingMessageRewriteListener() {
        unregisterOutgoingMessageRewriteListener();
        if (nameRewriteService == null) {
            return;
        }
        if (!getConfig().getBoolean("output-name-rewrite.enabled", true)) {
            return;
        }
        outgoingMessageRewriteListener = new OutgoingMessageRewriteListener(this, nameRewriteService);
        outgoingMessageRewriteListener.register();
        getLogger().info("Outgoing message name rewrite has been enabled.");
    }

    private void unregisterOutgoingMessageRewriteListener() {
        if (outgoingMessageRewriteListener == null) {
            return;
        }
        outgoingMessageRewriteListener.unregister();
        outgoingMessageRewriteListener = null;
    }

    private void refreshOnlinePlayers() {
        if (nameManager == null || skinManager == null || nametagManager == null
                || tablistNameManager == null || chatNameService == null) {
            return;
        }
        Bukkit.getOnlinePlayers().forEach(player -> {
            chatNameService.applyStoredChatName(player);
            chatTeamStates.put(player.getUniqueId(), vanillaTeamService.getInfo(player));
            boolean skinActive = skinManager.hasAnyCustomSkin(player);
            boolean tablistActive = tablistNameManager.hasAnyTablistName(player);
            boolean nicknameActive = nameManager.hasAnyNickname(player);
            boolean managedDecorationActive = nametagManager.hasManagedDecoration(player);
            if (nicknameActive || skinActive || tablistActive || managedDecorationActive) {
                nametagManager.updateForAllViewers(player);
            }
        });
    }

    private void refreshVanillaTeamState() {
        if (nametagManager == null || vanillaTeamService == null) {
            return;
        }
        nametagManager.refreshVanillaTeams();

        Set<UUID> onlinePlayers = new HashSet<>();
        for (var player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            onlinePlayers.add(playerId);
            VanillaTeamService.TeamInfo current = vanillaTeamService.getInfo(player);
            VanillaTeamService.TeamInfo previous = chatTeamStates.put(playerId, current);
            if (!current.equals(previous) && chatNameService != null) {
                chatNameService.applyStoredChatName(player);
            }
        }
        chatTeamStates.keySet().retainAll(onlinePlayers);
    }

    private void registerCommands() {
        PluginCommand command = getCommand("skkaemok");
        if (command == null) {
            getLogger().severe("Command 'skkaemok' not found in plugin.yml.");
            return;
        }
        SkkaemokCommand executor = new SkkaemokCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

}
