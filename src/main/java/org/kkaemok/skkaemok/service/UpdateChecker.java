package org.kkaemok.skkaemok.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {
    private static final String DEFAULT_RELEASE_API_URL = "https://api.github.com/repos/kkaemok/Skkaemok/releases/latest";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final JavaPlugin plugin;
    private final Gson gson;
    private final AtomicBoolean checkInProgress;

    private HttpClient httpClient;
    private BukkitTask scheduledTask;
    private volatile boolean enabled;
    private volatile long checkIntervalTicks;
    private volatile String releaseApiUrl;
    private volatile UpdateInfo availableUpdate;

    public UpdateChecker(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        this.plugin = plugin;
        this.gson = new Gson();
        this.checkInProgress = new AtomicBoolean(false);
        reloadConfig();
    }

    public void start() {
        stop();
        reloadConfig();
        if (!enabled) {
            availableUpdate = null;
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkForUpdateSafely);
        if (checkIntervalTicks > 0L) {
            scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin,
                    this::checkForUpdateSafely,
                    checkIntervalTicks,
                    checkIntervalTicks
            );
        }
    }

    public void reload() {
        start();
    }

    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    public UpdateInfo getAvailableUpdate() {
        return availableUpdate;
    }

    private void reloadConfig() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("update-checker.enabled", true);
        long intervalMinutes = Math.max(0L, config.getLong("update-checker.check-interval-minutes", 360L));
        this.checkIntervalTicks = intervalMinutes <= 0L ? 0L : intervalMinutes * 60L * 20L;
        this.releaseApiUrl = normalizeApiUrl(config.getString("update-checker.github-api-url", DEFAULT_RELEASE_API_URL));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    private String normalizeApiUrl(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_RELEASE_API_URL;
        }
        return configured.trim();
    }

    private void checkForUpdateSafely() {
        if (!checkInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            availableUpdate = fetchAvailableUpdate();
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to check GitHub release update: " + e.getMessage());
        } finally {
            checkInProgress.set(false);
        }
    }

    private UpdateInfo fetchAvailableUpdate() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(releaseApiUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", plugin.getName() + "/" + plugin.getPluginMeta().getVersion())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            plugin.getLogger().fine("GitHub release update check returned HTTP " + response.statusCode() + ".");
            return null;
        }

        JsonObject release = parseJsonObject(response.body());
        if (release == null) {
            return null;
        }
        if (getBoolean(release, "draft") || getBoolean(release, "prerelease")) {
            return null;
        }

        String tagName = getString(release, "tag_name");
        String releaseName = getString(release, "name");
        String latestVersion = firstNonBlank(tagName, releaseName);
        String downloadUrl = firstNonBlank(getString(release, "html_url"), "https://github.com/kkaemok/Skkaemok/releases");
        if (latestVersion == null) {
            return null;
        }

        String currentVersion = plugin.getPluginMeta().getVersion();
        if (!isNewerVersion(currentVersion, latestVersion)) {
            return null;
        }
        return new UpdateInfo(latestVersion, downloadUrl);
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

    private String getString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean getBoolean(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return false;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return false;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private boolean isNewerVersion(String currentVersion, String latestVersion) {
        ParsedVersion current = ParsedVersion.parse(currentVersion);
        ParsedVersion latest = ParsedVersion.parse(latestVersion);
        if (current == null || latest == null) {
            return !normalizeText(currentVersion).equals(normalizeText(latestVersion));
        }
        return latest.compareTo(current) > 0;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record UpdateInfo(String versionName, String downloadUrl) {
    }

    private record ParsedVersion(int[] numbers, String qualifier) implements Comparable<ParsedVersion> {
        private static final Pattern VERSION_PATTERN =
                Pattern.compile("(\\d+(?:[._-]\\d+)*)(?:[-_+ ]?([A-Za-z][A-Za-z0-9._-]*))?");

        private static ParsedVersion parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String normalized = raw.trim()
                    .replaceFirst("(?i)^v", "")
                    .replaceFirst("(?i)^skkaemok[-_ ]*", "");
            Matcher matcher = VERSION_PATTERN.matcher(normalized);
            if (!matcher.find()) {
                return null;
            }

            String[] numberParts = matcher.group(1).split("[._-]");
            int[] numbers = new int[numberParts.length];
            for (int i = 0; i < numberParts.length; i++) {
                try {
                    numbers[i] = Integer.parseInt(numberParts[i]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            String qualifier = matcher.group(2);
            return new ParsedVersion(numbers, qualifier == null ? "" : qualifier.toLowerCase(Locale.ROOT));
        }

        @Override
        public int compareTo(ParsedVersion other) {
            int length = Math.max(numbers.length, other.numbers.length);
            for (int i = 0; i < length; i++) {
                int currentNumber = i < numbers.length ? numbers[i] : 0;
                int otherNumber = i < other.numbers.length ? other.numbers[i] : 0;
                int comparison = Integer.compare(currentNumber, otherNumber);
                if (comparison != 0) {
                    return comparison;
                }
            }
            return Integer.compare(qualifierRank(qualifier), qualifierRank(other.qualifier));
        }

        private static int qualifierRank(String qualifier) {
            if (qualifier == null || qualifier.isBlank()) {
                return 5;
            }
            String normalized = qualifier.toLowerCase(Locale.ROOT);
            if (normalized.contains("snapshot") || normalized.contains("dev")) {
                return 1;
            }
            if (normalized.contains("alpha")) {
                return 2;
            }
            if (normalized.contains("beta")) {
                return 3;
            }
            if (normalized.contains("rc")) {
                return 4;
            }
            return 4;
        }
    }
}
