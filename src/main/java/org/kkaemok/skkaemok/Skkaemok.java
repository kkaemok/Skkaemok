package org.kkaemok.skkaemok;

import ch.njol.skript.Skript;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.listener.AdvancementListener;
import org.kkaemok.skkaemok.listener.ChatListener;
import org.kkaemok.skkaemok.listener.CommandInterceptor;
import org.kkaemok.skkaemok.listener.DeathListener;
import org.kkaemok.skkaemok.listener.PlayerSyncListener;
import org.kkaemok.skkaemok.service.NameManager;
import org.kkaemok.skkaemok.service.NametagManager;
import org.kkaemok.skkaemok.service.NameStorage;
import org.kkaemok.skkaemok.service.NicknameService;
import org.kkaemok.skkaemok.skript.NametagEffects;

public final class Skkaemok extends JavaPlugin {

    private NameManager nameManager;
    private NametagManager nametagManager;
    private NicknameService nicknameService;

    @Override
    public void onEnable() {
        if (!isPluginPresent("ProtocolLib")) {
            getLogger().severe("ProtocolLib is required.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (!isPluginPresent("Skript")) {
            getLogger().severe("Skript is required.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        NameStorage storage = new NameStorage(this);
        this.nameManager = new NameManager(storage);
        this.nametagManager = new NametagManager(this);
        this.nicknameService = new NicknameService(nameManager, nametagManager);

        Bukkit.getPluginManager().registerEvents(new ChatListener(nameManager), this);
        Bukkit.getPluginManager().registerEvents(new CommandInterceptor(nameManager), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(nameManager), this);
        Bukkit.getPluginManager().registerEvents(new AdvancementListener(nameManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerSyncListener(nameManager, nametagManager), this);

        Skript.registerAddon(this);
        NametagEffects.register(this, nicknameService);

        Bukkit.getOnlinePlayers().forEach(player -> {
            String nickname = nameManager.getRawNickname(player);
            if (nickname != null) {
                nametagManager.updateForAllViewers(player, nickname);
            }
        });
    }

    @Override
    public void onDisable() {
        if (nameManager != null) {
            nameManager.saveNow();
        }
    }

    private boolean isPluginPresent(String name) {
        return Bukkit.getPluginManager().getPlugin(name) != null;
    }

    public NicknameService getNicknameService() {
        return nicknameService;
    }
}
