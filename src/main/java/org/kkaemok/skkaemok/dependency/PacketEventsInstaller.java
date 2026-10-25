package org.kkaemok.skkaemok.dependency;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;
import java.util.jar.JarFile;
import java.util.logging.Level;

/** Downloads only missing dependencies. Loading/restarting plugins stays with the server. */
public final class PacketEventsInstaller implements AutoCloseable {
    static final URI LATEST = URI.create("https://api.github.com/repos/retrooper/packetevents/releases/latest");
    static final long MAX_JAR_BYTES = 32L * 1024 * 1024;
    private static final Set<String> HOSTS = Set.of("api.github.com", "github.com", "release-assets.githubusercontent.com",
            "objects.githubusercontent.com", "github-releases.githubusercontent.com");
    record Asset(String name, URI url, long size, String sha256) { }

    private final JavaPlugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Skkaemok-dependency-download");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;
    private Future<?> download;

    public PacketEventsInstaller(JavaPlugin plugin) { this.plugin = plugin; }

    public void install() {
        plugin.getLogger().info("PacketEvents is missing. Downloading its latest stable Spigot release; restart the server once installation completes.");
        download = executor.submit(() -> {
            try {
                Path plugins = plugin.getDataFolder().toPath().toAbsolutePath().getParent();
                if (installedJarExists(plugins)) throw new IOException("A PacketEvents jar already exists. Restart the server or resolve its loading error; it will not be replaced.");
                try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER).build()) {
                    Asset asset;
                    try (InputStream body = get(client, LATEST)) {
                        byte[] json = body.readNBytes(1024 * 1024 + 1);
                        if (json.length > 1024 * 1024) throw new IOException("Release metadata exceeds the size limit");
                        asset = selectAsset(JsonParser.parseString(new String(json, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
                    }
                    try (InputStream body = get(client, asset.url())) {
                        installJar(body, asset, plugins.resolve("packetevents-spigot.jar"));
                    }
                    if (!closed) plugin.getLogger().info("Installed " + asset.name() + ". Restart the server to enable PacketEvents and Skkaemok.");
                }
            } catch (Exception ex) {
                if (!closed) plugin.getLogger().log(Level.SEVERE,
                        "Could not install PacketEvents. Install the Spigot jar from https://github.com/retrooper/packetevents/releases and restart.", ex);
            } finally {
                executor.shutdown();
                if (!closed && plugin.isEnabled()) {
                    try {
                        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().disablePlugin(plugin));
                    } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) {
                        // Server shutdown already disabled the bootstrap-only plugin.
                    }
                }
            }
        });
    }

    static Asset selectAsset(JsonObject release) throws IOException {
        if ((release.has("draft") && release.get("draft").getAsBoolean())
                || (release.has("prerelease") && release.get("prerelease").getAsBoolean())) throw new IOException("Expected a stable release");
        if (!release.has("assets") || !release.get("assets").isJsonArray()) throw new IOException("Missing release assets");
        Asset selected = null;
        for (var element : release.getAsJsonArray("assets")) {
            JsonObject item = element.getAsJsonObject();
            String name = item.get("name").getAsString();
            if (!name.matches("packetevents-spigot-[0-9][A-Za-z0-9.\\-]*\\.jar")) continue;
            String digest = item.has("digest") && !item.get("digest").isJsonNull() ? item.get("digest").getAsString() : "";
            if (!digest.matches("sha256:[a-fA-F0-9]{64}")) throw new IOException("Official asset has no SHA-256 digest");
            URI url = URI.create(item.get("browser_download_url").getAsString());
            if (!safeUri(url) || !url.getHost().equals("github.com")
                    || !url.getPath().startsWith("/retrooper/packetevents/releases/download/")
                    || !url.getPath().endsWith("/" + name)) throw new IOException("Unexpected release asset URL");
            long size = item.get("size").getAsLong();
            if (size <= 0 || size > MAX_JAR_BYTES || selected != null) throw new IOException("Unexpected release asset size or multiple Spigot assets");
            selected = new Asset(name, url, size, digest.substring(7).toLowerCase(Locale.ROOT));
        }
        if (selected == null) throw new IOException("The latest release has no supported Spigot jar");
        return selected;
    }

    static boolean safeUri(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && HOSTS.contains(uri.getHost())
                && uri.getRawUserInfo() == null && uri.getRawFragment() == null && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    public static boolean supportedVersion(String version) {
        var matcher = java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+).*$").matcher(version);
        if (!matcher.matches()) return false;
        try {
            int major = Integer.parseInt(matcher.group(1)), minor = Integer.parseInt(matcher.group(2));
            return major > 2 || (major == 2 && minor >= 14);
        } catch (NumberFormatException ex) { return false; }
    }

    private static InputStream get(HttpClient client, URI initial) throws IOException, InterruptedException {
        URI uri = initial;
        for (int redirects = 0; redirects <= 3; redirects++) {
            if (!safeUri(uri)) throw new IOException("Rejected dependency download host");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Skkaemok-dependency-installer").header("Accept", "application/vnd.github+json").GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 200) {
                // HttpRequest.timeout covers headers for ofInputStream; bound body reads too.
                InputStream body = response.body();
                class TimedBody extends FilterInputStream {
                    volatile boolean finished;
                    TimedBody() { super(body); }
                    @Override public void close() throws IOException { finished = true; super.close(); }
                }
                TimedBody timed = new TimedBody();
                CompletableFuture.delayedExecutor(20, TimeUnit.SECONDS).execute(() -> {
                    if (!timed.finished) try { timed.close(); } catch (IOException ignored) { }
                });
                return timed;
            }
            response.body().close();
            if (Set.of(301, 302, 303, 307, 308).contains(response.statusCode())) {
                uri = uri.resolve(response.headers().firstValue("Location").orElseThrow(() -> new IOException("Missing redirect location")));
            } else throw new IOException("GitHub returned HTTP " + response.statusCode());
        }
        throw new IOException("Too many dependency download redirects");
    }

    static void installJar(InputStream input, Asset asset, Path destination) throws Exception {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        if (Files.exists(destination)) throw new IOException("Refusing to replace an existing PacketEvents jar");
        Path temporary = Files.createTempFile(destination.toAbsolutePath().getParent(), ".skkaemok-packetevents-", ".download");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long count = 0;
            try (OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Dependency download cancelled");
                    count += length;
                    if (count > asset.size() || count > MAX_JAR_BYTES) throw new IOException("Dependency exceeds declared size");
                    out.write(buffer, 0, length);
                    digest.update(buffer, 0, length);
                }
            }
            if (count != asset.size() || !HexFormat.of().formatHex(digest.digest()).equals(asset.sha256())) throw new IOException("Dependency checksum/size verification failed");
            try (JarFile jar = new JarFile(temporary.toFile())) {
                var entry = jar.getJarEntry("plugin.yml");
                if (entry == null || entry.getSize() > 65536) throw new IOException("Missing or oversized plugin metadata");
                PluginDescriptionFile description;
                try (InputStream metadata = jar.getInputStream(entry)) {
                    description = new PluginDescriptionFile(new ByteArrayInputStream(metadata.readNBytes(65537)));
                }
                if (!description.getName().equalsIgnoreCase("packetevents")
                        || !description.getMain().equals("io.github.retrooper.packetevents.PacketEventsPlugin")) throw new IOException("Unexpected dependency plugin identity");
            }
            // No REPLACE_EXISTING: a concurrent installer/user must never be overwritten.
            Files.move(temporary, destination);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static boolean installedJarExists(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".jar")).toList()) {
                try (JarFile jar = new JarFile(file.toFile())) {
                    var entry = jar.getJarEntry("plugin.yml");
                    if (entry == null || entry.getSize() > 65536) continue;
                    try (InputStream metadata = jar.getInputStream(entry)) {
                        if (new PluginDescriptionFile(metadata).getName().equalsIgnoreCase("packetevents")) return true;
                    }
                } catch (org.bukkit.plugin.InvalidDescriptionException | java.util.zip.ZipException ignored) {
                    // Other plugins' invalid jars are not this dependency.
                }
            }
        }
        return false;
    }

    @Override public void close() {
        closed = true;
        if (download != null) download.cancel(true);
        executor.shutdownNow();
    }
}
