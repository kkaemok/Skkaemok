package org.kkaemok.skkaemok;

import ch.njol.skript.Skript;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.appearance.AppearanceService;
import org.kkaemok.skkaemok.chat.AdvancementListener;
import org.kkaemok.skkaemok.chat.ChatCommandDecorateListener;
import org.kkaemok.skkaemok.chat.ChatNameManager;
import org.kkaemok.skkaemok.chat.ChatNameService;
import org.kkaemok.skkaemok.chat.DeathListener;
import org.kkaemok.skkaemok.chat.NameRewriteService;
import org.kkaemok.skkaemok.chat.OutgoingMessageRewriteListener;
import org.kkaemok.skkaemok.command.SkkaemokCommand;
import org.kkaemok.skkaemok.command.nickname.CommandInterceptor;
import org.kkaemok.skkaemok.command.nickname.NicknameCommands;
import org.kkaemok.skkaemok.dependency.PacketEventsInstaller;
import org.kkaemok.skkaemok.display.NametagManager;
import org.kkaemok.skkaemok.display.VanillaTeamService;
import org.kkaemok.skkaemok.integration.LuckPermsHook;
import org.kkaemok.skkaemok.integration.TabCustomNameBridge;
import org.kkaemok.skkaemok.integration.TabIntegration;
import org.kkaemok.skkaemok.lifecycle.PlayerSyncListener;
import org.kkaemok.skkaemok.name.NameManager;
import org.kkaemok.skkaemok.name.NameSnapshots;
import org.kkaemok.skkaemok.name.NicknameService;
import org.kkaemok.skkaemok.name.ViewerNameManager;
import org.kkaemok.skkaemok.skin.SkinManager;
import org.kkaemok.skkaemok.skin.SkinService;
import org.kkaemok.skkaemok.skript.AppearanceSyntax;
import org.kkaemok.skkaemok.skript.NametagEffects;
import org.kkaemok.skkaemok.skript.SyntaxRegistration;
import org.kkaemok.skkaemok.storage.AppearanceStorage;
import org.kkaemok.skkaemok.storage.ChatNameStorage;
import org.kkaemok.skkaemok.storage.NameStorage;
import org.kkaemok.skkaemok.storage.SkinStorage;
import org.kkaemok.skkaemok.storage.TablistNameStorage;
import org.kkaemok.skkaemok.storage.ViewerNameStorage;
import org.kkaemok.skkaemok.storage.ViewerSkinStorage;
import org.kkaemok.skkaemok.tablist.TablistNameManager;
import org.kkaemok.skkaemok.tablist.TablistNameService;
import org.kkaemok.skkaemok.update.UpdateChecker;
import org.kkaemok.skkaemok.update.UpdateNotifyListener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Skkaemok extends JavaPlugin {

    private AppearanceService appearances;

    public AppearanceService getAppearances() {
        return appearances;
    }

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
    private PacketEventsInstaller dependencyInstaller;
    private NameSnapshots nameSnapshots;
    private NicknameCommands nicknameCommands;
    private final ConcurrentHashMap<UUID, VanillaTeamService.TeamInfo> chatTeamStates =
            new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (isPluginMissing("packetevents")) {
            if (getConfig().getBoolean("dependencies.packetevents.auto-download", true)) {
                dependencyInstaller = new PacketEventsInstaller(this);
                dependencyInstaller.install();
            } else {
                getLogger()
                        .severe(
                                "PacketEvents is required. Install its Spigot jar and restart the"
                                        + " server.");
                Bukkit.getPluginManager().disablePlugin(this);
            }
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            getLogger()
                    .severe(
                            "PacketEvents is installed but disabled. Resolve its startup error and"
                                    + " restart; the existing jar will not be replaced.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (!PacketEventsInstaller.supportedVersion(
                Bukkit.getPluginManager()
                        .getPlugin("packetevents")
                        .getDescription()
                        .getVersion())) {
            getLogger()
                    .severe(
                            "PacketEvents 2.14.0 or newer is required. Update the existing"
                                    + " PacketEvents jar and restart.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        int pluginId = 30391;
        new Metrics(this, pluginId);

        if (isPluginMissing("Skript")) {
            getLogger().severe("Skript is required.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (Skript.getVersion().compareTo(new ch.njol.skript.util.Version("2.17.1")) < 0) {
            getLogger()
                    .severe(
                            "Skript 2.17.1 or newer is required. Update Skript and restart the"
                                    + " server.");
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
        this.nametagManager =
                new NametagManager(
                        this,
                        tabIntegration,
                        luckPermsHook,
                        tabCustomNameBridge,
                        nameManager,
                        skinManager,
                        tablistNameManager,
                        vanillaTeamService);
        this.nicknameService = new NicknameService(nameManager, nametagManager, skinManager);
        this.tablistNameService =
                new TablistNameService(
                        nameManager, skinManager, nametagManager, tablistNameManager);
        this.chatNameService = new ChatNameService(chatNameManager, vanillaTeamService, this);
        this.nametagManager.setVanillaTeamRefreshCallback(this::refreshVanillaTeamState);
        this.skinService = new SkinService(this, nameManager, skinManager, nametagManager);
        this.nameRewriteService =
                new NameRewriteService(chatNameManager, luckPermsHook, vanillaTeamService, this);
        this.nameSnapshots =
                new NameSnapshots(
                        this, nameManager, chatNameManager, viewerNameManager, nameRewriteService);
        try {
            this.appearances =
                    new AppearanceService(
                            this,
                            nameManager,
                            chatNameManager,
                            tablistNameManager,
                            skinManager,
                            skinService,
                            chatNameService,
                            nametagManager,
                            nameSnapshots,
                            new AppearanceStorage(getDataFolder().toPath()));
            nicknameService.setAppearanceService(appearances);
            chatNameService.setAppearanceService(appearances);
            tablistNameService.setAppearanceService(appearances);
            skinService.setAppearanceService(appearances);
            appearances.start();
        } catch (java.io.IOException ex) {
            getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Could not load appearance data; existing files have been preserved",
                            ex);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.nicknameCommands = new NicknameCommands(this, nameSnapshots);
        nicknameCommands.start();
        this.updateChecker = new UpdateChecker(this);
        luckPermsHook.registerMetaListener(
                uuid -> {
                    var player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        chatNameService.applyStoredChatName(player);
                        nicknameService.refreshDisplay(player);
                        nameSnapshots.refresh();
                    }
                });

        Bukkit.getPluginManager()
                .registerEvents(new ChatCommandDecorateListener(this, nameRewriteService), this);
        Bukkit.getPluginManager().registerEvents(new CommandInterceptor(chatNameManager), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(nameRewriteService), this);
        Bukkit.getPluginManager().registerEvents(new AdvancementListener(nameRewriteService), this);
        Bukkit.getPluginManager()
                .registerEvents(
                        new PlayerSyncListener(
                                nameManager,
                                skinManager,
                                nametagManager,
                                tablistNameManager,
                                chatNameService,
                                skinService),
                        this);
        Bukkit.getPluginManager()
                .registerEvents(new UpdateNotifyListener(this, updateChecker), this);
        registerOutgoingMessageRewriteListener();
        updateChecker.start();

        SyntaxRegistration.initialize(this);
        NametagEffects.register(
                this, nicknameService, skinService, tablistNameService, chatNameService);
        AppearanceSyntax.register(appearances, skinService);

        registerCommands();
        refreshOnlinePlayers();
    }

    @Override
    public void onDisable() {
        if (dependencyInstaller != null) dependencyInstaller.close();
        if (nicknameCommands != null) nicknameCommands.close();
        if (nameSnapshots != null) nameSnapshots.close();
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
        if (skinService != null) skinService.close();
        if (appearances != null) appearances.close();
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
        if (appearances != null) {
            try {
                appearances.reload();
            } catch (java.io.IOException ex) {
                getLogger()
                        .log(
                                java.util.logging.Level.SEVERE,
                                "Could not reload appearance presets; the previous presets and"
                                        + " existing file have been preserved",
                                ex);
            }
        }
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
        if (nameSnapshots != null) nameSnapshots.reload();
    }

    private void registerOutgoingMessageRewriteListener() {
        unregisterOutgoingMessageRewriteListener();
        if (nameRewriteService == null) {
            return;
        }
        if (!getConfig().getBoolean("output-name-rewrite.enabled", true)) {
            return;
        }
        outgoingMessageRewriteListener = new OutgoingMessageRewriteListener(this, nameSnapshots);
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
        if (nameManager == null
                || skinManager == null
                || nametagManager == null
                || tablistNameManager == null
                || chatNameService == null) {
            return;
        }
        Bukkit.getOnlinePlayers()
                .forEach(
                        player -> {
                            if (skinService != null) skinService.applyStoredSkin(player);
                            chatNameService.applyStoredChatName(player);
                            chatTeamStates.put(
                                    player.getUniqueId(), vanillaTeamService.getInfo(player));
                            // Also reconcile resets loaded from disk/configuration on reload.
                            if (nameManager.hasAnyNickname(player)
                                    || skinManager.hasAnyCustomSkin(player)
                                    || tablistNameManager.hasAnyTablistName(player)
                                    || nametagManager.hasManagedDecoration(player)
                                    || nametagManager.hasClientState(player)) {
                                nametagManager.updateForAllViewers(player);
                            }
                        });
    }

    private void refreshVanillaTeamState() {
        if (nametagManager == null || vanillaTeamService == null) {
            return;
        }
        nametagManager.refreshVanillaTeams();

        boolean namesChanged = false;
        Set<UUID> onlinePlayers = new HashSet<>();
        for (var player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            onlinePlayers.add(playerId);
            VanillaTeamService.TeamInfo current = vanillaTeamService.getInfo(player);
            VanillaTeamService.TeamInfo previous = chatTeamStates.put(playerId, current);
            if (!current.equals(previous) && chatNameService != null) {
                chatNameService.applyStoredChatName(player);
                namesChanged = true;
            }
        }
        chatTeamStates.keySet().retainAll(onlinePlayers);
        if (namesChanged && nameSnapshots != null) nameSnapshots.refresh();
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
