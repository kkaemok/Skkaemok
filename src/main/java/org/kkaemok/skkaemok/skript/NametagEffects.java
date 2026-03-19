package org.kkaemok.skkaemok.skript;

import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.service.NicknameService;
import org.kkaemok.skkaemok.service.SkinService;

public final class NametagEffects {
    private NametagEffects() {
    }

    public static void register(JavaPlugin plugin, NicknameService nicknameService, SkinService skinService) {
        SetNametagEffect.bootstrap(plugin, nicknameService);
        ResetNametagEffect.bootstrap(plugin, nicknameService);
        SetSkinEffect.bootstrap(plugin, skinService);
        ResetSkinEffect.bootstrap(plugin, skinService);
    }
}
