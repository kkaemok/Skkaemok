package org.kkaemok.skkaemok.service;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class SkinService {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final JavaPlugin plugin;
    private final NameManager nameManager;
    private final SkinManager skinManager;
    private final NametagManager nametagManager;
    private final Gson gson;
    private final HttpClient httpClient;
    private final String profileApiBase;
    private final String sessionApiBase;
    private final Duration requestTimeout;
    private final boolean allowUnsignedUrl;

    public SkinService(JavaPlugin plugin, NameManager nameManager, SkinManager skinManager, NametagManager nametagManager) {
        if (plugin == null || nameManager == null || skinManager == null || nametagManager == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.plugin = plugin;
        this.nameManager = nameManager;
        this.skinManager = skinManager;
        this.nametagManager = nametagManager;
        this.gson = new Gson();

        FileConfiguration config = plugin.getConfig();
        this.profileApiBase = config.getString("skin.mojang.api-base", "https://api.mojang.com");
        this.sessionApiBase = config.getString("skin.mojang.session-base", "https://sessionserver.mojang.com");
        long connectTimeoutMs = config.getLong("skin.mojang.connect-timeout-ms", 5000L);
        long readTimeoutMs = config.getLong("skin.mojang.read-timeout-ms", 10000L);
        this.requestTimeout = Duration.ofMillis(Math.max(1000L, readTimeoutMs));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000L, connectTimeoutMs)))
                .build();
        this.allowUnsignedUrl = config.getBoolean("skin.url.allow-unsigned", true);
    }

    public boolean setSkinFromPlayer(Player target, Player source) {
        if (target == null || source == null) {
            return false;
        }
        SkinData skinData = extractSkinFromPlayer(source);
        if (skinData == null) {
            warn("Failed to read skin from player " + source.getName());
            return false;
        }
        applySkin(target, skinData);
        return true;
    }

    public void setSkinFromName(Player target, String sourceName) {
        if (target == null) {
            return;
        }
        String normalized = normalizeName(sourceName);
        if (normalized == null) {
            warn("Invalid skin source name: " + sourceName);
            return;
        }

        fetchSkinFromName(normalized).thenAccept(skinData -> {
            if (skinData == null) {
                warn("Failed to fetch skin for " + normalized);
                return;
            }
            applySkin(target, skinData);
        });
    }

    public boolean setSkinFromUrl(Player target, String url) {
        if (target == null) {
            return false;
        }
        if (!allowUnsignedUrl) {
            warn("URL skins are disabled in config.");
            return false;
        }
        SkinData skinData = buildUnsignedSkinFromUrl(url);
        if (skinData == null) {
            warn("Invalid skin URL: " + url);
            return false;
        }
        applySkin(target, skinData);
        return true;
    }

    public void resetSkin(Player target) {
        if (target == null) {
            return;
        }
        skinManager.resetSkin(target);
        String displayName = nameManager.loadNickname(target);
        nametagManager.updateForAllViewers(target, displayName, null);
    }

    private void applySkin(Player target, SkinData skinData) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(skinData, "skinData");
        if (!skinData.isValid()) {
            return;
        }

        Runnable task = () -> {
            skinManager.setSkin(target, skinData);
            if (target.isOnline()) {
                applySelfProfile(target, skinData);
                String displayName = nameManager.loadNickname(target);
                nametagManager.updateForAllViewers(target, displayName, skinData);
                scheduleSelfRefresh(target, skinData);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private SkinData extractSkinFromPlayer(Player source) {
        PlayerProfile profile = source.getPlayerProfile();
        for (ProfileProperty prop : profile.getProperties()) {
            if ("textures".equals(prop.getName()) && prop.getValue() != null) {
                return new SkinData(prop.getValue(), prop.getSignature(), "player:" + source.getName(), System.currentTimeMillis());
            }
        }
        return null;
    }

    private void applySelfProfile(Player target, SkinData skinData) {
        try {
            PlayerProfile profile = target.getPlayerProfile();
            profile.removeProperty("textures");
            if (skinData.getSignature() == null || skinData.getSignature().isBlank()) {
                profile.setProperty(new ProfileProperty("textures", skinData.getValue()));
            } else {
                profile.setProperty(new ProfileProperty("textures", skinData.getValue(), skinData.getSignature()));
            }
            target.setPlayerProfile(profile);
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
            // Paper API not available; skip self profile update.
        }
    }

    private void scheduleSelfRefresh(Player target, SkinData skinData) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isOnline()) {
                return;
            }
            String displayName = nameManager.loadNickname(target);
            nametagManager.updateForViewer(target, target, displayName, skinData);
        }, 2L);
    }

    private CompletableFuture<SkinData> fetchSkinFromName(String name) {
        CompletableFuture<SkinData> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SkinData skinData = fetchSkinFromNameSync(name);
                future.complete(skinData);
            } catch (Exception e) {
                warn("Error fetching skin for " + name + ": " + e.getMessage());
                future.complete(null);
            }
        });
        return future;
    }

    private SkinData fetchSkinFromNameSync(String name) throws Exception {
        String profileUrl = profileApiBase + "/users/profiles/minecraft/" + URLEncoder.encode(name, StandardCharsets.UTF_8);
        HttpRequest profileRequest = HttpRequest.newBuilder()
                .uri(URI.create(profileUrl))
                .timeout(requestTimeout)
                .GET()
                .build();

        HttpResponse<String> profileResponse = httpClient.send(profileRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (profileResponse.statusCode() != 200) {
            return null;
        }

        JsonObject profileJson = gson.fromJson(profileResponse.body(), JsonObject.class);
        if (profileJson == null || !profileJson.has("id")) {
            return null;
        }
        String uuid = profileJson.get("id").getAsString();
        if (uuid == null || uuid.isBlank()) {
            return null;
        }

        String sessionUrl = sessionApiBase + "/session/minecraft/profile/" + uuid + "?unsigned=false";
        HttpRequest sessionRequest = HttpRequest.newBuilder()
                .uri(URI.create(sessionUrl))
                .timeout(requestTimeout)
                .GET()
                .build();

        HttpResponse<String> sessionResponse = httpClient.send(sessionRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (sessionResponse.statusCode() != 200) {
            return null;
        }

        JsonObject sessionJson = gson.fromJson(sessionResponse.body(), JsonObject.class);
        if (sessionJson == null || !sessionJson.has("properties")) {
            return null;
        }

        JsonArray properties = sessionJson.getAsJsonArray("properties");
        if (properties == null) {
            return null;
        }
        for (int i = 0; i < properties.size(); i++) {
            JsonObject prop = properties.get(i).getAsJsonObject();
            if (prop == null) {
                continue;
            }
            String propName = prop.has("name") ? prop.get("name").getAsString() : null;
            if (!"textures".equals(propName)) {
                continue;
            }
            String value = prop.has("value") ? prop.get("value").getAsString() : null;
            String signature = prop.has("signature") ? prop.get("signature").getAsString() : null;
            if (value == null || value.isBlank()) {
                return null;
            }
            return new SkinData(value, signature, "name:" + name, System.currentTimeMillis());
        }
        return null;
    }

    private SkinData buildUnsignedSkinFromUrl(String url) {
        if (url == null) {
            return null;
        }
        String normalized = url.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.startsWith("https://")) {
            return null;
        }

        JsonObject skin = new JsonObject();
        skin.addProperty("url", normalized);
        JsonObject textures = new JsonObject();
        textures.add("SKIN", skin);
        JsonObject root = new JsonObject();
        root.add("textures", textures);

        String json = gson.toJson(root);
        String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return new SkinData(value, null, "url:" + normalized, System.currentTimeMillis());
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (!NAME_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }

    private void warn(String message) {
        plugin.getLogger().warning(message);
    }
}
