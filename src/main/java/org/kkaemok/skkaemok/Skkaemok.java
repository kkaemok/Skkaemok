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
import org.kkaemok.skkaemok.listener.ChatListener;
import org.kkaemok.skkaemok.listener.CommandInterceptor;
import org.kkaemok.skkaemok.listener.DeathListener;
import org.kkaemok.skkaemok.listener.PlayerSyncListener;
import org.kkaemok.skkaemok.service.NameManager;
import org.kkaemok.skkaemok.service.NametagManager;
import org.kkaemok.skkaemok.service.NameStorage;
import org.kkaemok.skkaemok.service.NicknameService;
import org.kkaemok.skkaemok.service.SkinData;
import org.kkaemok.skkaemok.service.SkinManager;
import org.kkaemok.skkaemok.service.SkinService;
import org.kkaemok.skkaemok.service.SkinStorage;
import org.kkaemok.skkaemok.skript.NametagEffects;

public final class Skkaemok extends JavaPlugin {

    private NameManager nameManager;
    private SkinManager skinManager;
    private NametagManager nametagManager;
    private NicknameService nicknameService;
    private SkinService skinService;
    private LuckPermsHook luckPermsHook;

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
        this.nameManager = new NameManager(storage);
        this.skinManager = new SkinManager(skinStorage);
        TabIntegration tabIntegration = new TabIntegration(this);
        this.luckPermsHook = new LuckPermsHook(this);
        TabCustomNameBridge tabCustomNameBridge = new TabCustomNameBridge(this, tabIntegration);
        this.nametagManager = new NametagManager(this, tabIntegration, luckPermsHook, tabCustomNameBridge);
        this.nicknameService = new NicknameService(nameManager, nametagManager, skinManager);
        this.skinService = new SkinService(this, nameManager, skinManager, nametagManager);
        luckPermsHook.registerMetaListener(uuid -> {
            var player = Bukkit.getPlayer(uuid);
            if (player != null) {
                nicknameService.refreshDisplay(player);
            }
        });

        Bukkit.getPluginManager().registerEvents(new ChatListener(nameManager), this);
        Bukkit.getPluginManager().registerEvents(new CommandInterceptor(nameManager), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(nameManager), this);
        Bukkit.getPluginManager().registerEvents(new AdvancementListener(nameManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerSyncListener(nameManager, skinManager, nametagManager), this);

        Skript.registerAddon(this);
        NametagEffects.register(this, nicknameService, skinService);

        registerCommands();
        refreshOnlinePlayers();
    }

    @Override
    public void onDisable() {
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
    }

    private boolean isPluginMissing(String name) {
        return Bukkit.getPluginManager().getPlugin(name) == null;
    }

    public void reloadSkkaemok() {
        reloadConfig();
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
        refreshOnlinePlayers();
    }

    private void refreshOnlinePlayers() {
        if (nameManager == null || skinManager == null || nametagManager == null) {
            return;
        }
        Bukkit.getOnlinePlayers().forEach(player -> {
            String nickname = nameManager.getRawNickname(player);
            SkinData skinData = skinManager.getRawSkin(player);
            boolean nicknameActive = nickname != null;
            if (nicknameActive || skinData != null) {
                String displayName = nameManager.loadNickname(player);
                nametagManager.updateForAllViewers(player, displayName, nicknameActive, skinData);
            }
        });
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
