package org.kkaemok.skkaemok.skript;

import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.service.ChatNameService;
import org.kkaemok.skkaemok.service.NicknameService;
import org.kkaemok.skkaemok.service.SkinService;
import org.kkaemok.skkaemok.service.TablistNameService;
import org.kkaemok.skkaemok.skript.condition.CondHasCustomChatName;
import org.kkaemok.skkaemok.skript.condition.CondHasCustomNametag;
import org.kkaemok.skkaemok.skript.condition.CondHasCustomSkin;
import org.kkaemok.skkaemok.skript.condition.CondHasCustomTablistName;
import org.kkaemok.skkaemok.skript.expression.ExprChatName;
import org.kkaemok.skkaemok.skript.expression.ExprNametag;
import org.kkaemok.skkaemok.skript.expression.ExprPlayerByDisplayName;
import org.kkaemok.skkaemok.skript.expression.ExprRealName;
import org.kkaemok.skkaemok.skript.expression.ExprTablistName;

public final class NametagEffects {
    private NametagEffects() {
    }

    public static void register(JavaPlugin plugin,
                                NicknameService nicknameService,
                                SkinService skinService,
                                TablistNameService tablistNameService,
                                ChatNameService chatNameService) {
        SetNametagEffect.bootstrap(plugin, nicknameService);
        ResetNametagEffect.bootstrap(plugin, nicknameService);
        SetSkinEffect.bootstrap(plugin, skinService);
        ResetSkinEffect.bootstrap(plugin, skinService);
        SetTablistNameEffect.bootstrap(plugin, tablistNameService);
        ResetTablistNameEffect.bootstrap(plugin, tablistNameService);
        SetChatNameEffect.bootstrap(plugin, chatNameService);
        ResetChatNameEffect.bootstrap(plugin, chatNameService);
        ExprNametag.bootstrap(nicknameService);
        ExprTablistName.bootstrap(tablistNameService);
        ExprChatName.bootstrap(chatNameService);
        ExprRealName.bootstrap();
        ExprPlayerByDisplayName.bootstrap(nicknameService, tablistNameService, chatNameService);
        CondHasCustomNametag.bootstrap(nicknameService);
        CondHasCustomTablistName.bootstrap(tablistNameService);
        CondHasCustomChatName.bootstrap(chatNameService);
        CondHasCustomSkin.bootstrap(skinService);
    }
}
