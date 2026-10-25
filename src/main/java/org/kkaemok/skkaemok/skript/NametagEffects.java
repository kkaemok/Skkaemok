package org.kkaemok.skkaemok.skript;

import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.chat.ChatNameService;
import org.kkaemok.skkaemok.name.NicknameService;
import org.kkaemok.skkaemok.skin.SkinService;
import org.kkaemok.skkaemok.skript.condition.CondHasCustomChatName;
import org.kkaemok.skkaemok.skript.condition.CondHasCustomNametag;
import org.kkaemok.skkaemok.skript.condition.CondHasCustomSkin;
import org.kkaemok.skkaemok.skript.condition.CondHasCustomTablistName;
import org.kkaemok.skkaemok.skript.effect.ResetChatNameEffect;
import org.kkaemok.skkaemok.skript.effect.ResetNametagEffect;
import org.kkaemok.skkaemok.skript.effect.ResetSkinEffect;
import org.kkaemok.skkaemok.skript.effect.ResetTablistNameEffect;
import org.kkaemok.skkaemok.skript.effect.SetChatNameEffect;
import org.kkaemok.skkaemok.skript.effect.SetNametagEffect;
import org.kkaemok.skkaemok.skript.effect.SetSkinEffect;
import org.kkaemok.skkaemok.skript.effect.SetTablistNameEffect;
import org.kkaemok.skkaemok.skript.expression.ExprChatName;
import org.kkaemok.skkaemok.skript.expression.ExprNametag;
import org.kkaemok.skkaemok.skript.expression.ExprPlayerByDisplayName;
import org.kkaemok.skkaemok.skript.expression.ExprRealName;
import org.kkaemok.skkaemok.skript.expression.ExprTablistName;
import org.kkaemok.skkaemok.tablist.TablistNameService;

public final class NametagEffects {
    private NametagEffects() {}

    public static void register(
            JavaPlugin plugin,
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
