package org.kkaemok.skkaemok.skript;

import org.bukkit.OfflinePlayer;
import org.kkaemok.skkaemok.appearance.AppearanceService;
import org.kkaemok.skkaemok.event.*;
import org.kkaemok.skkaemok.skin.SkinService;
import org.kkaemok.skkaemok.skript.effect.*;
import org.kkaemok.skkaemok.skript.event.EvtAppearance;
import org.kkaemok.skkaemok.skript.expression.*;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;

public final class AppearanceSyntax {
    private AppearanceSyntax() {}

    public static void register(AppearanceService service, SkinService skins) {
        SyntaxRegistration.results(service);
        EffRequestSkin.service = skins;
        EffAppearance.service = service;
        EffBatchAppearance.service = service;
        ExprNick.service = service;
        ExprChangeResult.service = service;
        ExprAppearancePresets.service = service;
        SyntaxRegistration.expression(
                ExprNick.class,
                String.class,
                "[skkaemok] nick of %offlineplayer%",
                "[skkaemok] nick of %offlineplayer% for %offlineplayer%");
        SyntaxRegistration.effect(
                EffAppearance.class,
                "set [skkaemok] nick of %offlineplayer% to %string% [for %-timespan%]",
                "set [skkaemok] nick of %offlineplayer% for %offlineplayer% to %string% [for"
                    + " %-timespan%]",
                "save [skkaemok] appearance of %offlineplayer% as [preset] %string%",
                "save [skkaemok] appearance of %offlineplayer% for %offlineplayer% as [preset]"
                    + " %string%",
                "apply [skkaemok] appearance preset %string% to %offlineplayer% [for %-timespan%]",
                "apply [skkaemok] appearance preset %string% to %offlineplayer% for viewer"
                    + " %offlineplayer% [for %-timespan%]",
                "reset [skkaemok] appearance of %offlineplayer%",
                "reset [skkaemok] appearance of %offlineplayer% for %offlineplayer%",
                "delete [skkaemok] appearance preset %string%");
        SyntaxRegistration.effect(
                EffBatchAppearance.class,
                "apply [skkaemok] appearance to %offlineplayer% with nametag %string%[,] chat"
                    + " %string%[,] tablist %string% [and skin of %-player%] [for %-timespan%]",
                "apply [skkaemok] appearance to %offlineplayer% for viewer %offlineplayer% with"
                    + " nametag %string%[,] chat %string%[,] tablist %string% [and skin of"
                    + " %-player%] [for %-timespan%]");
        SyntaxRegistration.effect(
                EffRequestSkin.class,
                "request [skkaemok] skin of %offlineplayer% from username %string%",
                "request [skkaemok] skin of %offlineplayer% for %offlineplayer% from username"
                    + " %string%",
                "request [skkaemok] skin of %offlineplayer% from url %string%",
                "request [skkaemok] skin of %offlineplayer% for %offlineplayer% from url %string%");
        SyntaxRegistration.expression(
                ExprChangeResult.class,
                String.class,
                "[last] skkaemok change (result|status) of %offlineplayer%",
                "[last] skkaemok change (result|status) of %offlineplayer% for %offlineplayer%",
                "[last] skkaemok change (reason|message) of %offlineplayer%",
                "[last] skkaemok change (reason|message) of %offlineplayer% for %offlineplayer%");
        SyntaxRegistration.expression(
                ExprAppearancePresets.class, String.class, "[all] [skkaemok] appearance presets");
        SyntaxRegistration.registry()
                .register(
                        BukkitSyntaxInfos.Event.KEY,
                        BukkitSyntaxInfos.Event.builder(EvtAppearance.class, "Skkaemok appearance")
                                .addEvent(AppearanceChangeEvent.class)
                                .addEvent(AppearanceChangedEvent.class)
                                .addEvent(SkinRequestFailedEvent.class)
                                .addPatterns(
                                        "[skkaemok] appearance chang(e|ing)",
                                        "[skkaemok] appearance changed",
                                        "[skkaemok] nametag chang(e|ing)",
                                        "[skkaemok] nametag changed",
                                        "[skkaemok] chat name chang(e|ing)",
                                        "[skkaemok] chat name changed",
                                        "[skkaemok] tablist name chang(e|ing)",
                                        "[skkaemok] tablist name changed",
                                        "[skkaemok] skin chang(e|ing)",
                                        "[skkaemok] skin changed",
                                        "[skkaemok] nick chang(e|ing)",
                                        "[skkaemok] nick changed",
                                        "[skkaemok] skin applied",
                                        "[skkaemok] skin request fail[ed]")
                                .build());
        SyntaxRegistration.expression(
                ExprAppearanceEventPlayer.class,
                OfflinePlayer.class,
                "appearance event (target|player)",
                "appearance event viewer");
        SyntaxRegistration.expression(
                ExprAppearanceEventText.class,
                String.class,
                "appearance event old nametag",
                "appearance event new nametag",
                "appearance event old chat name",
                "appearance event new chat name",
                "appearance event old tablist name",
                "appearance event new tablist name",
                "appearance event cause",
                "appearance event (reason|failure reason)",
                "appearance event skin source",
                "appearance event old skin source",
                "appearance event new skin source",
                "appearance event old skin texture",
                "appearance event new skin texture",
                "appearance event old skin signature",
                "appearance event new skin signature");
    }
}
