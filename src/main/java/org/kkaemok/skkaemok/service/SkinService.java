package org.kkaemok.skkaemok.service;

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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
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
    private final String mineSkinApiBase;
    private final String mineSkinApiKey;
    private final String mineSkinUserAgent;
    private final String mineSkinVisibility;
    private final String mineSkinVariant;
    private final boolean useMineSkinQueue;
    private final boolean requireMineSkinKey;
    private final long mineSkinPollIntervalMs;
    private final long mineSkinMaxPollMs;
    private final Duration mineSkinRequestTimeout;
    private final boolean allowUnsignedUrl;
    private final AtomicBoolean warnedNoKey;

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
        this.mineSkinApiBase = normalizeApiBase(config.getString("skin.mineskin.api-base", "https://api.mineskin.org"));
        this.mineSkinApiKey = trimToNull(config.getString("skin.mineskin.api-key", ""));
        String defaultUserAgent = plugin.getName();
        this.mineSkinUserAgent = normalizeUserAgent(config.getString("skin.mineskin.user-agent", ""), defaultUserAgent);
        this.mineSkinVisibility = normalizeVisibility(config.getString("skin.mineskin.visibility", "unlisted"));
        this.mineSkinVariant = normalizeVariant(config.getString("skin.mineskin.variant", "auto"));
        this.useMineSkinQueue = config.getBoolean("skin.mineskin.use-queue", true);
        this.requireMineSkinKey = config.getBoolean("skin.mineskin.require-api-key", false);
        this.mineSkinPollIntervalMs = Math.max(1000L, config.getLong("skin.mineskin.poll-interval-ms", 1000L));
        long maxPollSeconds = config.getLong("skin.mineskin.max-poll-seconds", 30L);
        this.mineSkinMaxPollMs = Math.max(1000L, maxPollSeconds * 1000L);
        long mineSkinTimeoutMs = config.getLong("skin.mineskin.request-timeout-ms", 15000L);
        this.mineSkinRequestTimeout = Duration.ofMillis(Math.max(1000L, mineSkinTimeoutMs));
        this.allowUnsignedUrl = config.getBoolean("skin.url.allow-unsigned", false);
        this.warnedNoKey = new AtomicBoolean(false);
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

        fetchMineSkinFromUrlAsync(normalized).thenAccept(skinData -> {
            SkinData resolved = skinData;
            if (resolved == null && allowUnsignedUrl) {
                resolved = buildUnsignedSkinFromUrl(normalized);
            }
            if (resolved == null) {
                warn("Failed to apply skin from URL: " + normalized);
                return;
            }
            applySkin(target, resolved);
        });
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
            if (!"textures".equals(prop.getName())) {
                continue;
            }
            String value = prop.getValue();
            if (value.isBlank()) {
                continue;
            }
            return new SkinData(value, prop.getSignature(), "player:" + source.getName(), System.currentTimeMillis());
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

    private CompletableFuture<SkinData> fetchMineSkinFromUrlAsync(String url) {
        if (useMineSkinQueue && mineSkinApiKey != null) {
            return requestMineSkinQueueAsync(url).thenCompose(result -> {
                if (result != null) {
                    return CompletableFuture.completedFuture(result);
                }
                return requestMineSkinGenerateAsync(url);
            });
        }
        return requestMineSkinGenerateAsync(url);
    }

    private CompletableFuture<SkinData> requestMineSkinQueueAsync(String url) {
        JsonObject payload = buildMineSkinRequestBody(url);
        return postMineSkinAsync("/v2/queue", payload).thenCompose(response -> {
            if (response == null) {
                return CompletableFuture.completedFuture(null);
            }
            if (response.status == 200) {
                return CompletableFuture.completedFuture(parseMineSkin(response.body, "mineskin:url:" + url));
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
            warn("MineSkin queue request failed with status " + response.status + ".");
            logMineSkinErrorDetails(response.body);
            return CompletableFuture.completedFuture(null);
        });
    }

    private CompletableFuture<SkinData> requestMineSkinGenerateAsync(String url) {
        JsonObject payload = buildMineSkinRequestBody(url);
        return postMineSkinAsync("/v2/generate", payload).thenApply(response -> {
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
            warn("MineSkin generate request failed with status " + response.status + ".");
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

    private void scheduleMineSkinPoll(String jobId, String source, long deadline, CompletableFuture<SkinData> future) {
        if (future.isDone()) {
            return;
        }
        if (System.currentTimeMillis() >= deadline) {
            warn("MineSkin job timed out after " + (mineSkinMaxPollMs / 1000L) + "s.");
            future.complete(null);
            return;
        }

        getMineSkinAsync("/v2/queue/" + jobId).thenAccept(response -> {
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
                warn("MineSkin job status request failed with status " + response.status + ".");
                logMineSkinErrorDetails(response.body);
                future.complete(null);
                return;
            }

            long delayMs = Math.max(1000L, mineSkinPollIntervalMs);
            Long waitMs = readRateLimitNextMillis(response.body);
            if (waitMs != null && waitMs > delayMs) {
                delayMs = waitMs;
            }
            scheduleMineSkinPollLater(jobId, source, deadline, future, delayMs);
        }).exceptionally(ex -> {
            warn("MineSkin job status request failed: " + ex.getMessage());
            future.complete(null);
            return null;
        });
    }

    private void scheduleMineSkinPollLater(String jobId, String source, long deadline, CompletableFuture<SkinData> future, long delayMs) {
        long delayTicks = Math.max(1L, delayMs / 50L);
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () ->
                scheduleMineSkinPoll(jobId, source, deadline, future), delayTicks);
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
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(mineSkinApiBase + path))
                .timeout(mineSkinRequestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", mineSkinUserAgent);
        if (mineSkinApiKey != null) {
            builder.header("Authorization", "Bearer " + mineSkinApiKey);
        }
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        return sendMineSkinRequest(request);
    }

    private <T> CompletableFuture<T> runAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private MineSkinResponse getMineSkin(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
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
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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

    private record MineSkinResponse(int status, JsonObject body) {
    }

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

    private String normalizeUrl(String url) {
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
        return normalized;
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
}
