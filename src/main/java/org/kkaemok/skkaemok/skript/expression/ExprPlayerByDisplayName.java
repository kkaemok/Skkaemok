package org.kkaemok.skkaemok.skript.expression;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.kkaemok.skkaemok.chat.ChatNameService;
import org.kkaemok.skkaemok.name.NicknameService;
import org.kkaemok.skkaemok.skript.SyntaxRegistration;
import org.kkaemok.skkaemok.tablist.TablistNameService;

@SuppressWarnings("unchecked")
public final class ExprPlayerByDisplayName extends SimpleExpression<Player> {
    private static NicknameService nicknameService;
    private static TablistNameService tablistNameService;
    private static ChatNameService chatNameService;

    private Expression<String> name;
    private Expression<Player> viewer;
    private int channel;
    private boolean viewerSpecific;

    public static void bootstrap(
            NicknameService nicknames, TablistNameService tablistNames, ChatNameService chatNames) {
        nicknameService = nicknames;
        tablistNameService = tablistNames;
        chatNameService = chatNames;
        SyntaxRegistration.expression(
                ExprPlayerByDisplayName.class,
                Player.class,
                "player with nametag %string%",
                "player with nametag %string% for %player%",
                "player with tablist name %string%",
                "player with tablist name %string% for %player%",
                "player with chat name %string%",
                "player with chat name %string% for %player%");
    }

    @Override
    public boolean init(
            Expression<?>[] expressions,
            int matchedPattern,
            Kleenean delayed,
            SkriptParser.ParseResult parseResult) {
        name = (Expression<String>) expressions[0];
        viewerSpecific = matchedPattern % 2 == 1;
        channel = matchedPattern / 2;
        if (viewerSpecific) viewer = (Expression<Player>) expressions[1];
        return true;
    }

    @Override
    protected Player[] get(Event event) {
        String requested = name.getSingle(event);
        Player viewerPlayer = viewerSpecific ? viewer.getSingle(event) : null;
        if (requested == null || (viewerSpecific && viewerPlayer == null)) return new Player[0];
        Player result =
                switch (channel) {
                    case 0 -> nicknameService.findUniquePlayer(requested, viewerPlayer);
                    case 1 -> tablistNameService.findUniquePlayer(requested, viewerPlayer);
                    default -> chatNameService.findUniquePlayer(requested, viewerPlayer);
                };
        return result == null ? new Player[0] : new Player[] {result};
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends Player> getReturnType() {
        return Player.class;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "player by Skkaemok display name";
    }
}
