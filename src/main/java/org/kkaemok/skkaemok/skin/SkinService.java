package org.kkaemok.skkaemok.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.kkaemok.skkaemok.appearance.AppearanceScope;
import org.kkaemok.skkaemok.appearance.AppearanceService;
import org.kkaemok.skkaemok.appearance.ChangeResult;
import org.kkaemok.skkaemok.display.NametagManager;
import org.kkaemok.skkaemok.event.SkinRequestFailedEvent;
import org.kkaemok.skkaemok.name.NameManager;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class SkinService {
    private final java.util.concurrent.ConcurrentHashMap<SkinRequests.Key, String> requestResults =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<SkinRequests.Key> offlineRequests =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private AppearanceService appearances;

    public void setAppearanceService(AppearanceService appearances) {
        this.appearances = appearances;
    }

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final JavaPlugin plugin;

    private final SkinManager skinManager;
    private final NametagManager nametagManager;
    private final Gson gson;
    private final AtomicBoolean warnedNoKey;
    private HttpClient httpClient;
    private String profileApiBase;
    private String sessionApiBase;
    private Duration requestTimeout;
    private String mineSkinApiBase;
    private String mineSkinApiKey;
    private String mineSkinUserAgent;
    private String mineSkinVisibility;
    private String mineSkinVariant;
    private boolean useMineSkinQueue;
    private boolean requireMineSkinKey;
    private long mineSkinPollIntervalMs;
    private long mineSkinMaxPollMs;
    private Duration mineSkinRequestTimeout;
    private boolean allowUnsignedUrl;
    private final SkinRequests requests = new SkinRequests();
    private final Map<UUID, Set<ProfileProperty>> originalTextures = new java.util.HashMap<>();
    private volatile boolean closed;

    public SkinService(
            JavaPlugin plugin,
            NameManager nameManager,
            SkinManager skinManager,
            NametagManager nametagManager) {
        if (plugin == null
                || nameManager == null
                || skinManager == null
                || nametagManager == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.plugin = plugin;

        this.skinManager = skinManager;
        this.nametagManager = nametagManager;
        this.gson = new Gson();
        this.warnedNoKey = new AtomicBoolean(false);
        reloadConfig();
    }

    public void reloadConfig() {
        requests.clear();
        offlineRequests.clear();
        requestResults.clear();
        FileConfiguration config = plugin.getConfig();
        String profileApiBase = config.getString("skin.mojang.api-base", "https://api.mojang.com");
        String sessionApiBase =
                config.getString("skin.mojang.session-base", "https://sessionserver.mojang.com");
        long connectTimeoutMs = config.getLong("skin.mojang.connect-timeout-ms", 5000L);
        long readTimeoutMs = config.getLong("skin.mojang.read-timeout-ms", 10000L);
        Duration requestTimeout = Duration.ofMillis(Math.max(1000L, readTimeoutMs));
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(Math.max(1000L, connectTimeoutMs)))
                        .build();
        String mineSkinApiBase =
                normalizeApiBase(
                        config.getString("skin.mineskin.api-base", "https://api.mineskin.org"));
        String mineSkinApiKey = trimToNull(config.getString("skin.mineskin.api-key", ""));
        String defaultUserAgent = plugin.getName();
        String mineSkinUserAgent =
                normalizeUserAgent(
                        config.getString("skin.mineskin.user-agent", ""), defaultUserAgent);
        String mineSkinVisibility =
                normalizeVisibility(config.getString("skin.mineskin.visibility", "unlisted"));
        String mineSkinVariant =
                normalizeVariant(config.getString("skin.mineskin.variant", "auto"));
        boolean useMineSkinQueue = config.getBoolean("skin.mineskin.use-queue", true);
        boolean requireMineSkinKey = config.getBoolean("skin.mineskin.require-api-key", false);
        long mineSkinPollIntervalMs =
                Math.max(1000L, config.getLong("skin.mineskin.poll-interval-ms", 1000L));
        long maxPollSeconds = config.getLong("skin.mineskin.max-poll-seconds", 30L);
        long mineSkinMaxPollMs = Math.max(1000L, maxPollSeconds * 1000L);
        long mineSkinTimeoutMs = config.getLong("skin.mineskin.request-timeout-ms", 15000L);
        Duration mineSkinRequestTimeout = Duration.ofMillis(Math.max(1000L, mineSkinTimeoutMs));
        boolean allowUnsignedUrl = config.getBoolean("skin.url.allow-unsigned", false);

        this.profileApiBase = profileApiBase;
        this.sessionApiBase = sessionApiBase;
        this.requestTimeout = requestTimeout;
        this.httpClient = httpClient;
        this.mineSkinApiBase = mineSkinApiBase;
        this.mineSkinApiKey = mineSkinApiKey;
        this.mineSkinUserAgent = mineSkinUserAgent;
        this.mineSkinVisibility = mineSkinVisibility;
        this.mineSkinVariant = mineSkinVariant;
        this.useMineSkinQueue = useMineSkinQueue;
        this.requireMineSkinKey = requireMineSkinKey;
        this.mineSkinPollIntervalMs = mineSkinPollIntervalMs;
        this.mineSkinMaxPollMs = mineSkinMaxPollMs;
        this.mineSkinRequestTimeout = mineSkinRequestTimeout;
        this.allowUnsignedUrl = allowUnsignedUrl;
        this.warnedNoKey.set(false);
    }

    public boolean setSkinFromPlayer(Player target, Player source) {
        if (target == null || source == null) {
            return false;
        }
        SkinData skinData = extractSkinFromPlayer(source);
        if (skinData == null) {
            this.warn("Failed to read skin from player " + source.getName());
            if (appearances != null) {
                var scope = new AppearanceScope(target.getUniqueId(), null);
                skinResult(
                        scope,
                        source.getName(),
                        appearances.reject(
                                scope,
                                ChangeResult.Status.INVALID_SKIN,
                                "The source player has no skin texture."));
            }
            return false;
        }
        if (appearances != null) {
            var scope = new AppearanceScope(target.getUniqueId(), null);
            var result = appearances.setSkin(scope, skinData);
            skinResult(scope, source.getName(), result);
            return result.success();
        }
        requests.invalidate(new SkinRequests.Key(target.getUniqueId(), null));
        applySkin(target, skinData);
        return true;
    }

    public boolean setSkinFromPlayer(Player target, Player viewer, Player source) {
        if (target == null || viewer == null || source == null) {
            return false;
        }
        SkinData skinData = extractSkinFromPlayer(source);
        if (skinData == null) {
            this.warn("Failed to read skin from player " + source.getName());
            if (appearances != null) {
                var scope = new AppearanceScope(target.getUniqueId(), viewer.getUniqueId());
                skinResult(
                        scope,
                        source.getName(),
                        appearances.reject(
                                scope,
                                ChangeResult.Status.INVALID_SKIN,
                                "The source player has no skin texture."));
            }
            return false;
        }
        if (appearances != null) {
            var scope = new AppearanceScope(target.getUniqueId(), viewer.getUniqueId());
            var result = appearances.setSkin(scope, skinData);
            skinResult(scope, source.getName(), result);
            return result.success();
        }
        requests.invalidate(new SkinRequests.Key(target.getUniqueId(), viewer.getUniqueId()));
        applyViewerSkin(target, viewer, skinData);
        return true;
    }

    public void setSkinFromName(Player target, String sourceName) {
        if (appearances != null && target != null) {
            requestSkin(new AppearanceScope(target.getUniqueId(), null), sourceName, false, false);
            return;
        }
        if (target == null) {
            return;
        }
        String normalized = normalizeName(sourceName);
        if (normalized == null) {
            warn("Invalid skin source name: " + sourceName);
            return;
        }

        resolveAsync(target, null, () -> fetchSkinFromName(normalized), null);
    }

    public void setSkinFromName(Player target, Player viewer, String sourceName) {
        if (appearances != null && target != null && viewer != null) {
            requestSkin(
                    new AppearanceScope(target.getUniqueId(), viewer.getUniqueId()),
                    sourceName,
                    false,
                    false);
            return;
        }
        if (target == null || viewer == null) {
            return;
        }
        String normalized = normalizeName(sourceName);
        if (normalized == null) {
            warn("Invalid skin source name: " + sourceName);
            return;
        }
        resolveAsync(target, viewer, () -> fetchSkinFromName(normalized), null);
    }

    public boolean setSkinFromUrl(Player target, String url) {
        if (appearances != null && target != null) {
            return requestSkin(new AppearanceScope(target.getUniqueId(), null), url, true, false)
                    .success();
        }
        if (target == null) {
            return false;
        }
        String normalized = normalizeUrl(url);
        if (normalized == null) {
            warn("Invalid skin URL: " + url);
            return false;
        }
        if (requireMineSkinKey && mineSkinApiKey == null) {
            warn("MineSkin API key is required for URL skins. Configure skin.mineskin.api-key.");
            return false;
        }
        if (mineSkinApiKey == null && warnedNoKey.compareAndSet(false, true)) {
            warn("MineSkin API key is not set. Anonymous requests are rate-limited and may fail.");
        }

        resolveAsync(target, null, () -> fetchMineSkinFromUrlAsync(normalized), normalized);
        return true;
    }

    public boolean setSkinFromUrl(Player target, Player viewer, String url) {
        if (appearances != null && target != null && viewer != null) {
            return requestSkin(
                            new AppearanceScope(target.getUniqueId(), viewer.getUniqueId()),
                            url,
                            true,
                            false)
                    .success();
        }
        if (target == null || viewer == null) {
            return false;
        }
        String normalized = normalizeUrl(url);
        if (normalized == null) {
            warn("Invalid skin URL: " + url);
            return false;
        }
        if (requireMineSkinKey && mineSkinApiKey == null) {
            warn("MineSkin API key is required for URL skins. Configure skin.mineskin.api-key.");
            return false;
        }
        if (mineSkinApiKey == null && warnedNoKey.compareAndSet(false, true)) {
            warn("MineSkin API key is not set. Anonymous requests are rate-limited and may fail.");
        }
        resolveAsync(target, viewer, () -> fetchMineSkinFromUrlAsync(normalized), normalized);
        return true;
    }

    public void resetSkin(Player target) {
        if (appearances != null && target != null) {
            appearances.setSkin(new AppearanceScope(target.getUniqueId(), null), null);
            return;
        }
        if (target == null) return;
        requests.invalidate(new SkinRequests.Key(target.getUniqueId(), null));
        if (!skinManager.hasCustomSkin(target)) return;
        skinManager.resetSkin(target);
        // Restore the captured login textures, without racing a new Mojang lookup
        // against a newer set/reset operation.
        Set<ProfileProperty> original = originalTextures.remove(target.getUniqueId());
        if (original != null && target.isOnline()) {
            PlayerProfile profile = target.getPlayerProfile();
            profile.removeProperty("textures");
            original.forEach(profile::setProperty);
            nametagManager.updateNativeProfile(target, profile);
            return;
        }
        nametagManager.updateForAllViewers(target);
    }

    public void resetSkin(Player target, Player viewer) {
        if (appearances != null && target != null && viewer != null) {
            appearances.setSkin(
                    new AppearanceScope(target.getUniqueId(), viewer.getUniqueId()), null);
            return;
        }
        if (target == null || viewer == null) return;
        requests.invalidate(new SkinRequests.Key(target.getUniqueId(), viewer.getUniqueId()));
        if (!skinManager.hasCustomSkin(target, viewer)) return;
        skinManager.resetSkin(target, viewer);
        nametagManager.updateForViewer(target, viewer);
    }

    private void resolveAsync(
            Player target, Player viewer, Supplier<CompletableFuture<SkinData>> fetch, String url) {
        if (closed || !target.isOnline() || (viewer != null && !viewer.isOnline())) return;
        SkinRequests.Key key =
                new SkinRequests.Key(
                        target.getUniqueId(), viewer == null ? null : viewer.getUniqueId());
        UUID token = requests.begin(key);
        boolean unsigned = allowUnsignedUrl;
        fetch.get()
                .whenComplete(
                        (skin, error) -> {
                            if (closed || !requests.current(key, token)) return;
                            SkinData resolved =
                                    skin == null && unsigned && url != null
                                            ? buildUnsignedSkinFromUrl(url)
                                            : skin;
                            if (error != null || resolved == null) {
                                runSync(
                                        () -> {
                                            if (!requests.current(key, token)) return;
                                            requests.finish(key, token);
                                            Bukkit.getPluginManager()
                                                    .callEvent(
                                                            new SkinRequestFailedEvent(
                                                                    new AppearanceScope(
                                                                            key.target(),
                                                                            key.viewer()),
                                                                    url == null ? "username" : url,
                                                                    error == null
                                                                            ? "No skin texture was"
                                                                                    + " returned."
                                                                            : String.valueOf(
                                                                                    error
                                                                                            .getMessage())));
                                        });
                                warn(
                                        "Remote skin resolution failed"
                                                + (url == null ? "." : " for URL " + url + ".")
                                                + (error == null
                                                        ? " See the preceding Mojang/MineSkin"
                                                                + " response."
                                                        : " " + error.getMessage()));
                                return;
                            }
                            runSync(
                                    () -> {
                                        if (!requests.current(key, token)) return;
                                        requests.finish(key, token);
                                        Player currentTarget = Bukkit.getPlayer(key.target());
                                        Player currentViewer =
                                                key.viewer() == null
                                                        ? null
                                                        : Bukkit.getPlayer(key.viewer());
                                        if (currentTarget == null
                                                || (key.viewer() != null && currentViewer == null))
                                            return;
                                        if (currentViewer == null)
                                            applySkin(currentTarget, resolved);
                                        else
                                            applyViewerSkin(currentTarget, currentViewer, resolved);
                                    });
                        });
    }

    private void runSync(Runnable action) {
        if (closed) return;
        if (Bukkit.isPrimaryThread()) action.run();
        else {
            try {
                Bukkit.getScheduler()
                        .runTask(
                                plugin,
                                () -> {
                                    if (!closed) action.run();
                                });
            } catch (org.bukkit.plugin.IllegalPluginAccessException ex) {
                if (!closed)
                    plugin.getLogger().warning("Skin application skipped: plugin is disabled.");
            }
        }
    }

    public void removePlayer(UUID player) {
        requests.remove(player, offlineRequests);
        requestResults
                .keySet()
                .removeIf(
                        key ->
                                (key.target().equals(player) || player.equals(key.viewer()))
                                        && !offlineRequests.contains(key));
        originalTextures.remove(player);
    }

    public void applyStoredSkin(Player player) {
        if (closed || player == null || !player.isOnline()) return;
        SkinData stored = skinManager.getRawSkin(player);
        if (stored != null && stored.isValid()) {
            if (!sameTexture(extractSkinFromPlayer(player), stored))
                applySelfProfile(player, stored);
            return;
        }
        Set<ProfileProperty> original = originalTextures.remove(player.getUniqueId());
        if (original != null) {
            PlayerProfile profile = player.getPlayerProfile();
            profile.removeProperty("textures");
            original.forEach(profile::setProperty);
            nametagManager.updateNativeProfile(player, profile);
        }
    }

    public void close() {
        closed = true;
        requests.clear();
        // Undo server-profile texture changes so a subsequent enable starts from
        // the login identity rather than treating our custom skin as the original.
        for (var entry : originalTextures.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            try {
                PlayerProfile profile = player.getPlayerProfile();
                SkinData managed = skinManager.getRawSkin(player);
                boolean stillManaged =
                        managed != null
                                && profile.getProperties().stream()
                                        .anyMatch(
                                                property ->
                                                        property.getName().equals("textures")
                                                                && Objects.equals(
                                                                        property.getValue(),
                                                                        managed.getValue())
                                                                && Objects.equals(
                                                                        property.getSignature(),
                                                                        managed.getSignature()));
                if (!stillManaged) continue;
                profile.removeProperty("textures");
                entry.getValue().forEach(profile::setProperty);
                player.setPlayerProfile(profile);
            } catch (RuntimeException ex) {
                plugin.getLogger()
                        .log(
                                java.util.logging.Level.SEVERE,
                                "Failed to restore original skin on disable",
                                ex);
            }
        }
        originalTextures.clear();
    }

    public boolean hasCustomSkin(Player target) {
        return skinManager.hasCustomSkin(target);
    }

    public boolean hasCustomSkin(Player target, Player viewer) {
        return skinManager.hasCustomSkin(target, viewer);
    }

    private void applySkin(Player target, SkinData skinData) {
        if (appearances != null) {
            appearances.setSkin(new AppearanceScope(target.getUniqueId(), null), skinData);
            return;
        }
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(skinData, "skinData");
        if (!skinData.isValid()) {
            return;
        }

        Runnable task =
                () -> {
                    if (closed || !target.isOnline()) return;
                    if (sameTexture(skinManager.getRawSkin(target), skinData)) {
                        return;
                    }
                    skinManager.setSkin(target, skinData);
                    if (target.isOnline()) {
                        applySelfProfile(target, skinData);
                    }
                };

        runSync(task);
    }

    private void applyViewerSkin(Player target, Player viewer, SkinData skinData) {
        if (appearances != null) {
            appearances.setSkin(
                    new AppearanceScope(target.getUniqueId(), viewer.getUniqueId()), skinData);
            return;
        }
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(skinData, "skinData");
        if (!skinData.isValid()) {
            return;
        }
        Runnable task =
                () -> {
                    if (closed || !target.isOnline() || !viewer.isOnline()) return;
                    if (sameTexture(skinManager.getRawSkin(target, viewer), skinData)) {
                        return;
                    }
                    skinManager.setSkin(target, viewer, skinData);
                    if (target.isOnline() && viewer.isOnline()) {
                        nametagManager.updateForViewer(target, viewer);
                    }
                };
        runSync(task);
    }

    private SkinData extractSkinFromPlayer(Player source) {
        PlayerProfile profile = source.getPlayerProfile();
        for (ProfileProperty prop : profile.getProperties()) {
            if (!"textures".equals(prop.getName())) {
                continue;
            }
            String value = prop.getValue();
            if (value.isBlank()) {
                continue;
            }
            return new SkinData(
                    value,
                    prop.getSignature(),
                    "player:" + source.getName(),
                    System.currentTimeMillis());
        }
        return null;
    }

    private boolean sameTexture(SkinData first, SkinData second) {
        return first != null
                && second != null
                && Objects.equals(first.getValue(), second.getValue())
                && Objects.equals(first.getSignature(), second.getSignature());
    }

    private boolean applySelfProfile(Player target, SkinData skinData) {
        try {
            PlayerProfile profile = target.getPlayerProfile();
            originalTextures.computeIfAbsent(
                    target.getUniqueId(),
                    ignored -> {
                        Set<ProfileProperty> textures = new HashSet<>();
                        for (ProfileProperty property : profile.getProperties()) {
                            if (property.getName().equals("textures")) textures.add(property);
                        }
                        return Set.copyOf(textures);
                    });
            profile.removeProperty("textures");
            if (skinData.getSignature() == null || skinData.getSignature().isBlank()) {
                profile.setProperty(new ProfileProperty("textures", skinData.getValue()));
            } else {
                profile.setProperty(
                        new ProfileProperty(
                                "textures", skinData.getValue(), skinData.getSignature()));
            }
            nametagManager.updateNativeProfile(target, profile);
            return true;
        } catch (RuntimeException e) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Failed to update the player's own skin profile",
                            e);
            return false;
        }
    }

    private CompletableFuture<SkinData> fetchSkinFromName(String name) {
        CompletableFuture<SkinData> future = new CompletableFuture<>();
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
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
        String profileUrl =
                profileApiBase
                        + "/users/profiles/minecraft/"
                        + URLEncoder.encode(name, StandardCharsets.UTF_8);
        HttpRequest profileRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(profileUrl))
                        .timeout(requestTimeout)
                        .GET()
                        .build();

        HttpResponse<String> profileResponse =
                httpClient.send(
                        profileRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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

        String sessionUrl =
                sessionApiBase + "/session/minecraft/profile/" + uuid + "?unsigned=false";
        HttpRequest sessionRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(sessionUrl))
                        .timeout(requestTimeout)
                        .GET()
                        .build();

        HttpResponse<String> sessionResponse =
                httpClient.send(
                        sessionRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (sessionResponse.statusCode() != 200) {
            return null;
        }

        JsonObject sessionJson = gson.fromJson(sessionResponse.body(), JsonObject.class);
        return extractSkinFromSessionJson(sessionJson, "name:" + name);
    }

    private SkinData extractSkinFromSessionJson(JsonObject sessionJson, String source) {
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
            return new SkinData(value, signature, source, System.currentTimeMillis());
        }
        return null;
    }

    private CompletableFuture<SkinData> fetchMineSkinFromUrlAsync(String url) {
        if (useMineSkinQueue && mineSkinApiKey != null) {
            return requestMineSkinQueueAsync(url);
        }
        return requestMineSkinGenerateAsync(url);
    }

    private CompletableFuture<SkinData> requestMineSkinQueueAsync(String url) {
        JsonObject payload = buildMineSkinRequestBody(url);
        return postMineSkinAsync("/v2/queue", payload)
                .thenCompose(
                        response -> {
                            if (response == null) {
                                return CompletableFuture.completedFuture(null);
                            }
                            if (response.status == 200) {
                                return CompletableFuture.completedFuture(
                                        parseMineSkin(response.body, "mineskin:url:" + url));
                            }
                            if (response.status == 202) {
                                String jobId = readJobId(response.body);
                                if (jobId == null) {
                                    warn("MineSkin queue response missing job id.");
                                    return CompletableFuture.completedFuture(null);
                                }
                                return pollMineSkinJobAsync(jobId, "mineskin:url:" + url);
                            }
                            if (response.status == 429) {
                                warnRateLimit("MineSkin queue rate limited", response.body);
                                return CompletableFuture.completedFuture(null);
                            }
                            if (response.status == 404 || response.status == 405) {
                                return requestMineSkinGenerateAsync(url);
                            }
                            warn(
                                    "MineSkin queue request failed with status "
                                            + response.status
                                            + ".");
                            logMineSkinErrorDetails(response.body);
                            return CompletableFuture.completedFuture(null);
                        });
    }

    private CompletableFuture<SkinData> requestMineSkinGenerateAsync(String url) {
        JsonObject payload = buildMineSkinRequestBody(url);
        return postMineSkinAsync("/v2/generate", payload)
                .thenApply(
                        response -> {
                            if (response == null) {
                                return null;
                            }
                            if (response.status == 200) {
                                return parseMineSkin(response.body, "mineskin:url:" + url);
                            }
                            if (response.status == 429) {
                                warnRateLimit("MineSkin generate rate limited", response.body);
                                return null;
                            }
                            warn(
                                    "MineSkin generate request failed with status "
                                            + response.status
                                            + ".");
                            logMineSkinErrorDetails(response.body);
                            return null;
                        });
    }

    private CompletableFuture<SkinData> pollMineSkinJobAsync(String jobId, String source) {
        CompletableFuture<SkinData> future = new CompletableFuture<>();
        long deadline = System.currentTimeMillis() + mineSkinMaxPollMs;
        scheduleMineSkinPoll(jobId, source, deadline, future);
        return future;
    }

    private void scheduleMineSkinPoll(
            String jobId, String source, long deadline, CompletableFuture<SkinData> future) {
        if (closed || future.isDone()) {
            future.complete(null);
            return;
        }
        if (System.currentTimeMillis() >= deadline) {
            warn("MineSkin job timed out after " + (mineSkinMaxPollMs / 1000L) + "s.");
            future.complete(null);
            return;
        }

        getMineSkinAsync("/v2/queue/" + jobId)
                .thenAccept(
                        response -> {
                            if (future.isDone()) {
                                return;
                            }
                            if (response == null) {
                                future.complete(null);
                                return;
                            }
                            if (response.status == 200) {
                                String status = readJobStatus(response.body);
                                if ("completed".equalsIgnoreCase(status)) {
                                    future.complete(parseMineSkin(response.body, source));
                                    return;
                                }
                                if ("failed".equalsIgnoreCase(status)) {
                                    warn("MineSkin job failed.");
                                    logMineSkinErrorDetails(response.body);
                                    future.complete(null);
                                    return;
                                }
                            } else if (response.status == 429) {
                                warnRateLimit("MineSkin job status rate limited", response.body);
                            } else {
                                warn(
                                        "MineSkin job status request failed with status "
                                                + response.status
                                                + ".");
                                logMineSkinErrorDetails(response.body);
                                future.complete(null);
                                return;
                            }

                            long delayMs = Math.max(1000L, mineSkinPollIntervalMs);
                            Long waitMs = readRateLimitNextMillis(response.body);
                            if (waitMs != null && waitMs > delayMs) {
                                delayMs = waitMs;
                            }
                            delayMs =
                                    Math.min(
                                            delayMs,
                                            Math.max(1L, deadline - System.currentTimeMillis()));
                            scheduleMineSkinPollLater(jobId, source, deadline, future, delayMs);
                        })
                .exceptionally(
                        ex -> {
                            warn("MineSkin job status request failed: " + ex.getMessage());
                            future.complete(null);
                            return null;
                        });
    }

    private void scheduleMineSkinPollLater(
            String jobId,
            String source,
            long deadline,
            CompletableFuture<SkinData> future,
            long delayMs) {
        if (closed) {
            future.complete(null);
            return;
        }
        long delayTicks = Math.max(1L, delayMs / 50L);
        try {
            Bukkit.getScheduler()
                    .runTaskLaterAsynchronously(
                            plugin,
                            () -> scheduleMineSkinPoll(jobId, source, deadline, future),
                            delayTicks);
        } catch (org.bukkit.plugin.IllegalPluginAccessException ex) {
            future.complete(null);
        }
    }

    private JsonObject buildMineSkinRequestBody(String url) {
        JsonObject payload = new JsonObject();
        payload.addProperty("url", url);
        if (mineSkinVisibility != null) {
            payload.addProperty("visibility", mineSkinVisibility);
        }
        if (mineSkinVariant != null) {
            payload.addProperty("variant", mineSkinVariant);
        }
        return payload;
    }

    private CompletableFuture<MineSkinResponse> postMineSkinAsync(String path, JsonObject payload) {
        return runAsync(() -> postMineSkin(path, payload));
    }

    private CompletableFuture<MineSkinResponse> getMineSkinAsync(String path) {
        return runAsync(() -> getMineSkin(path));
    }

    private MineSkinResponse postMineSkin(String path, JsonObject payload) {
        String json = gson.toJson(payload);
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(mineSkinApiBase + path))
                        .timeout(mineSkinRequestTimeout)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("User-Agent", mineSkinUserAgent);
        if (mineSkinApiKey != null) {
            builder.header("Authorization", "Bearer " + mineSkinApiKey);
        }
        HttpRequest request =
                builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
        return sendMineSkinRequest(request);
    }

    private <T> CompletableFuture<T> runAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (closed) {
            future.complete(null);
            return future;
        }
        try {
            Bukkit.getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                try {
                                    future.complete(supplier.get());
                                } catch (Exception e) {
                                    future.completeExceptionally(e);
                                }
                            });
        } catch (org.bukkit.plugin.IllegalPluginAccessException ex) {
            future.complete(null);
        }
        return future;
    }

    private MineSkinResponse getMineSkin(String path) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(mineSkinApiBase + path))
                        .timeout(mineSkinRequestTimeout)
                        .header("Accept", "application/json")
                        .header("User-Agent", mineSkinUserAgent);
        if (mineSkinApiKey != null) {
            builder.header("Authorization", "Bearer " + mineSkinApiKey);
        }
        HttpRequest request = builder.GET().build();
        return sendMineSkinRequest(request);
    }

    private MineSkinResponse sendMineSkinRequest(HttpRequest request) {
        try {
            HttpResponse<String> response =
                    httpClient.send(
                            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject body = parseJsonObject(response.body());
            return new MineSkinResponse(response.statusCode(), body);
        } catch (Exception e) {
            warn("MineSkin request failed: " + e.getMessage());
            return null;
        }
    }

    private SkinData parseMineSkin(JsonObject body, String source) {
        if (body == null) {
            return null;
        }
        JsonObject skin = getObject(body, "skin");
        JsonObject texture = getObject(skin, "texture");
        JsonObject data = getObject(texture, "data");
        String value = getString(data, "value");
        String signature = getString(data, "signature");
        if (value == null || value.isBlank()) {
            return null;
        }
        if ((signature == null || signature.isBlank()) && !allowUnsignedUrl) {
            warn("MineSkin returned an unsigned texture; skin.url.allow-unsigned is disabled.");
            return null;
        }
        return new SkinData(value, signature, source, System.currentTimeMillis());
    }

    private String readJobId(JsonObject body) {
        return getString(getObject(body, "job"), "id");
    }

    private String readJobStatus(JsonObject body) {
        return getString(getObject(body, "job"), "status");
    }

    private void warnRateLimit(String prefix, JsonObject body) {
        Long waitMs = readRateLimitNextMillis(body);
        if (waitMs != null && waitMs > 0) {
            warn(prefix + ". Retry after " + waitMs + "ms.");
        } else {
            warn(prefix + ".");
        }
    }

    private Long readRateLimitNextMillis(JsonObject body) {
        JsonObject rateLimit = getObject(body, "rateLimit");
        JsonObject next = getObject(rateLimit, "next");
        if (next == null || !next.has("relative")) {
            return null;
        }
        JsonElement element = next.get("relative");
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsLong();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void logMineSkinErrorDetails(JsonObject body) {
        if (body == null) {
            return;
        }
        String message = readFirstErrorMessage(body);
        if (message != null) {
            warn("MineSkin error: " + message);
        }
    }

    private String readFirstErrorMessage(JsonObject body) {
        if (body == null || !body.has("errors")) {
            return null;
        }
        JsonElement element = body.get("errors");
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array == null || array.isEmpty()) {
            return null;
        }
        JsonElement firstElement = array.get(0);
        if (firstElement == null || firstElement.isJsonNull() || !firstElement.isJsonObject()) {
            return null;
        }
        JsonObject first = firstElement.getAsJsonObject();
        return getString(first, "message");
    }

    private JsonObject parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement element = gson.fromJson(json, JsonElement.class);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (Exception ignored) {
            // Ignore parse failures.
        }
        return null;
    }

    private JsonObject getObject(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private String getString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return null;
    }

    private record MineSkinResponse(int status, JsonObject body) {}

    private SkinData buildUnsignedSkinFromUrl(String url) {
        String normalized = normalizeUrl(url);
        if (normalized == null) {
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

    static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String normalized = url.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(normalized).parseServerAuthority();
            String scheme = uri.getScheme();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    || uri.isOpaque()
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || uri.getPort() == 0
                    || uri.getPort() > 65535
                    || uri.getRawAuthority().endsWith(":")) {
                return null;
            }
            // Keep HTTPS when supplied; never downgrade or probe the image locally.
            return uri.toASCIIString();
        } catch (java.net.URISyntaxException | IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeApiBase(String base) {
        if (base == null || base.isBlank()) {
            return "https://api.mineskin.org";
        }
        String trimmed = base.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalizeUserAgent(String userAgent, String fallback) {
        String normalized = trimToNull(userAgent);
        if (normalized != null) {
            return normalized;
        }
        String fallbackValue = trimToNull(fallback);
        return fallbackValue == null ? "plugin" : fallbackValue;
    }

    private String normalizeVisibility(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "unlisted";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "public", "private", "unlisted" -> lower;
            default -> "unlisted";
        };
    }

    private String normalizeVariant(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("auto".equals(lower) || "unknown".equals(lower)) {
            return null;
        }
        if ("classic".equals(lower) || "slim".equals(lower)) {
            return lower;
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void warn(String message) {
        plugin.getLogger().warning(message);
    }

    public void commitSkin(UUID targetId, UUID viewerId, SkinData skin) {
        Player target = Bukkit.getPlayer(targetId);
        skinManager.store(targetId, viewerId, skin);
        if (target == null) return;
        if (viewerId != null) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) nametagManager.updateForViewer(target, viewer);
            return;
        }
        if (skin != null) {
            if (!applySelfProfile(target, skin))
                throw new IllegalStateException("Could not update the native skin profile");
        } else {
            Set<ProfileProperty> original = originalTextures.remove(targetId);
            if (original != null) {
                PlayerProfile profile = target.getPlayerProfile();
                profile.removeProperty("textures");
                original.forEach(profile::setProperty);
                nametagManager.updateNativeProfile(target, profile);
            } else nametagManager.updateForAllViewers(target);
        }
    }

    public void cancelRequest(UUID target, UUID viewer) {
        var key = new SkinRequests.Key(target, viewer);
        requests.invalidate(key);
        offlineRequests.remove(key);
    }

    public String requestStatus(UUID target, UUID viewer) {
        var key = new SkinRequests.Key(target, viewer);
        return requests.pending(key) ? "pending" : requestResults.getOrDefault(key, "idle");
    }

    private void skinResult(AppearanceScope scope, String source, ChangeResult result) {
        var key = new SkinRequests.Key(scope.target(), scope.viewer());
        requestResults.put(
                key,
                result.success()
                        ? "applied"
                        : result.status().name().toLowerCase(java.util.Locale.ROOT)
                                + ": "
                                + result.message());
        while (requestResults.size() > 2048)
            requestResults.remove(requestResults.keySet().iterator().next());
        if (!result.success() && appearances != null)
            Bukkit.getPluginManager()
                    .callEvent(new SkinRequestFailedEvent(scope, source, result.message()));
    }

    public ChangeResult requestSkin(AppearanceScope scope, String source, boolean url) {
        return requestSkin(scope, source, url, true);
    }

    private ChangeResult requestSkin(
            AppearanceScope scope, String source, boolean url, boolean allowOffline) {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Skin requests must start on the server thread");
        if (appearances == null || closed)
            return new ChangeResult(ChangeResult.Status.CLOSED, "Skin service is unavailable.");
        String normalized = url ? normalizeUrl(source) : normalizeName(source);
        if (normalized == null || (url && requireMineSkinKey && mineSkinApiKey == null)) {
            var result =
                    appearances.reject(
                            scope,
                            ChangeResult.Status.INVALID_SKIN,
                            "Invalid skin source or missing required MineSkin API key.");
            skinResult(scope, source, result);
            return result;
        }
        var queued = appearances.pendingSkin(scope);
        if (!queued.success()) return queued;
        var key = new SkinRequests.Key(scope.target(), scope.viewer());
        UUID token = requests.begin(key);
        if (allowOffline) offlineRequests.add(key);
        else offlineRequests.remove(key);
        CompletableFuture<SkinData> future;
        try {
            future = url ? fetchMineSkinFromUrlAsync(normalized) : fetchSkinFromName(normalized);
        } catch (RuntimeException error) {
            future = CompletableFuture.failedFuture(error);
        }
        boolean unsigned = allowUnsignedUrl;
        future.whenComplete(
                (skin, error) ->
                        runSync(
                                () -> {
                                    if (!requests.current(key, token)) return;
                                    requests.finish(key, token);
                                    offlineRequests.remove(key);
                                    SkinData resolved =
                                            skin == null && unsigned && url
                                                    ? buildUnsignedSkinFromUrl(normalized)
                                                    : skin;
                                    var result =
                                            error != null || resolved == null
                                                    ? appearances.reject(
                                                            scope,
                                                            ChangeResult.Status.INVALID_SKIN,
                                                            error == null
                                                                    ? "No skin texture was"
                                                                            + " returned."
                                                                    : String.valueOf(
                                                                            error.getMessage()))
                                                    : appearances.setSkin(scope, resolved);
                                    skinResult(scope, source, result);
                                }));
        return queued;
    }
}
