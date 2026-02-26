package org.kkaemok.skkaemok.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.kkaemok.skkaemok.service.NameManager;

import java.util.HashMap;
import java.util.Map;

public final class CommandInterceptor implements Listener {
    private final NameManager nameManager;

    public CommandInterceptor(NameManager nameManager) {
        if (nameManager == null) {
            throw new IllegalArgumentException("NameManager cannot be null");
        }
        this.nameManager = nameManager;
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || !message.startsWith("/")) {
            return;
        }

        String[] args = message.split(" ");
        if (args.length <= 1) {
            return;
        }

        Map<String, String> nicknameToOriginal = createNicknameMap();
        boolean changed = false;
        StringBuilder modified = new StringBuilder(args[0]);

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            String original = nicknameToOriginal.getOrDefault(arg, arg);
            if (!original.equals(arg)) {
                changed = true;
            }
            modified.append(" ").append(original);
        }

        if (!changed) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().performCommand(modified.substring(1));
    }

    private Map<String, String> createNicknameMap() {
        Map<String, String> map = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String nickname = nameManager.loadNickname(player);
            String original = player.getName();
            if (nickname != null && !nickname.equals(original)) {
                map.put(nickname, original);
            }
        }
        return map;
    }
}
