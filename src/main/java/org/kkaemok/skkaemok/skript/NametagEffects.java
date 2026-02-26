package org.kkaemok.skkaemok.skript;

import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.service.NicknameService;

public final class NametagEffects {
    private NametagEffects() {
    }

    public static void register(JavaPlugin plugin, NicknameService nicknameService) {
        SetNametagEffect.bootstrap(plugin, nicknameService);
        ResetNametagEffect.bootstrap(plugin, nicknameService);
    }
}
