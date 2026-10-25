package org.kkaemok.skkaemok.appearance;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.kkaemok.skkaemok.chat.*;
import org.kkaemok.skkaemok.display.*;
import org.kkaemok.skkaemok.event.*;
import org.kkaemok.skkaemok.name.*;
import org.kkaemok.skkaemok.skin.*;
import org.kkaemok.skkaemok.storage.AppearanceStorage;
import org.kkaemok.skkaemok.tablist.TablistNameManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.logging.Level;

/** Validates complete changes before touching state, then publishes one client refresh. */
public final class AppearanceService implements AutoCloseable {
    private static final Set<AppearanceField> NAMES =
            Set.of(AppearanceField.NAMETAG, AppearanceField.CHAT, AppearanceField.TABLIST);
    private final JavaPlugin plugin;
    private final NameManager names;
    private final ChatNameManager chat;
    private final TablistNameManager tabs;
    private final SkinManager skins;
    private final SkinService skinService;
    private final ChatNameService chatService;
    private final NametagManager renderer;
    private final NameSnapshots snapshots;
    private final DisplayFormatter formatter;
    private final AppearanceStorage storage;
    private final Map<String, Appearance> presets;
    private final LongSupplier now;
    private final Set<AppearanceScope> applying = new HashSet<>();
    private final Map<AppearanceScope, ChangeResult> results = new LinkedHashMap<>();
    private TimedOverrides temporary = new TimedOverrides();
    private BukkitTask expiryTask;
    private boolean closed;
    private boolean started;

    public AppearanceService(
            JavaPlugin plugin,
            NameManager names,
            ChatNameManager chat,
            TablistNameManager tabs,
            SkinManager skins,
            SkinService skinService,
            ChatNameService chatService,
            NametagManager renderer,
            NameSnapshots snapshots,
            AppearanceStorage storage)
            throws IOException {
        this(
                plugin,
                names,
                chat,
                tabs,
                skins,
                skinService,
                chatService,
                renderer,
                snapshots,
                storage,
                System::currentTimeMillis);
    }

    public AppearanceService(
            JavaPlugin plugin,
            NameManager names,
            ChatNameManager chat,
            TablistNameManager tabs,
            SkinManager skins,
            SkinService skinService,
            ChatNameService chatService,
            NametagManager renderer,
            NameSnapshots snapshots,
            AppearanceStorage storage,
            LongSupplier now)
            throws IOException {
        this.plugin = plugin;
        this.names = names;
        this.chat = chat;
        this.tabs = tabs;
        this.skins = skins;
        this.skinService = skinService;
        this.chatService = chatService;
        this.renderer = renderer;
        this.snapshots = snapshots;
        this.storage = storage;
        this.now = now;
        formatter = new DisplayFormatter(plugin);
        presets = readPresets();
    }

    private Map<String, Appearance> readPresets() throws IOException {
        Map<String, Appearance> result = new LinkedHashMap<>();
        for (var entry : storage.presets().entrySet()) {
            if (!validPreset(entry.getKey())
                    || entry.getValue() == null
                    || result.putIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue())
                            != null)
                throw new IOException("Invalid or duplicate appearance preset entry");
        }
        return result;
    }

    public void reload() throws IOException {
        formatter.reload();
        Map<String, Appearance> next = readPresets();
        presets.clear();
        presets.putAll(next);
    }

    public void start() throws IOException {
        // Temporary effects never become permanent after a crash or restart.
        for (var recovery : storage.recovery()) {
            if (recovery.scope() == null || recovery.base() == null || recovery.fields() == null)
                throw new IOException("Invalid temporary appearance recovery entry");
            ChangeResult result =
                    change(
                            recovery.scope(),
                            recovery.base(),
                            recovery.fields(),
                            null,
                            "recovery",
                            true);
            if (!result.success())
                throw new IOException(
                        "Could not recover temporary appearance: " + result.message());
        }
        storage.saveRecovery(List.of());
        started = true;
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20L, 20L);
    }

    public Appearance capture(AppearanceScope scope) {
        return new Appearance(
                names.raw(scope.target(), scope.viewer()),
                chat.raw(scope.target(), scope.viewer()),
                tabs.raw(scope.target(), scope.viewer()),
                skins.raw(scope.target(), scope.viewer()));
    }

    public ChangeResult lastResult(AppearanceScope scope) {
        return results.getOrDefault(
                scope,
                new ChangeResult(ChangeResult.Status.UNCHANGED, "No change has been requested."));
    }

    public ChangeResult reject(AppearanceScope scope, ChangeResult.Status status, String reason) {
        requireMain();
        return remember(scope, new ChangeResult(status, reason));
    }

    public ChangeResult pendingSkin(AppearanceScope scope) {
        requireMain();
        return remember(
                scope,
                closed
                        ? new ChangeResult(ChangeResult.Status.CLOSED, "Skkaemok is disabled.")
                        : new ChangeResult(ChangeResult.Status.PENDING, "Skin request queued."));
    }

    public List<String> presetNames() {
        return presets.keySet().stream().sorted().toList();
    }

    public String plain(String raw) {
        return PlainTextComponentSerializer.plainText().serialize(formatter.parseNickname(raw));
    }

    public ChangeResult setNickname(AppearanceScope scope, String nickname, Duration duration) {
        Appearance next = capture(scope);
        for (var field : NAMES) next = next.with(field, normalize(nickname));
        return change(scope, next, NAMES, duration, "nickname", false);
    }

    public ChangeResult setName(AppearanceScope scope, NameChannel channel, String value) {
        AppearanceField field = AppearanceField.valueOf(channel.name());
        return change(
                scope,
                capture(scope).with(field, normalize(value)),
                Set.of(field),
                null,
                "set",
                false);
    }

    public ChangeResult setSkin(AppearanceScope scope, SkinData skin) {
        return change(
                scope,
                capture(scope).with(AppearanceField.SKIN, skin),
                Set.of(AppearanceField.SKIN),
                null,
                "skin",
                false);
    }

    public ChangeResult apply(AppearanceScope scope, Appearance appearance, Duration duration) {
        return change(
                scope,
                appearance,
                EnumSet.allOf(AppearanceField.class),
                duration,
                "appearance",
                false);
    }

    public ChangeResult reset(AppearanceScope scope) {
        return apply(scope, Appearance.EMPTY, null);
    }

    public ChangeResult savePreset(String id, AppearanceScope scope) {
        requireMain();
        if (closed)
            return remember(
                    scope, new ChangeResult(ChangeResult.Status.CLOSED, "Skkaemok is disabled."));
        if (!validPreset(id))
            return remember(
                    scope,
                    new ChangeResult(
                            ChangeResult.Status.INVALID_NAME,
                            "Preset names must be 1–64 letters, digits, dots, underscores or"
                                    + " hyphens."));
        Map<String, Appearance> next = new LinkedHashMap<>(presets);
        next.put(id.toLowerCase(Locale.ROOT), capture(scope));
        try {
            storage.savePresets(next);
            presets.clear();
            presets.putAll(next);
            return remember(scope, ChangeResult.ok());
        } catch (IOException ex) {
            return storageFailure(scope, ex);
        }
    }

    public ChangeResult deletePreset(String id) {
        requireMain();
        if (closed) return new ChangeResult(ChangeResult.Status.CLOSED, "Skkaemok is disabled.");
        Map<String, Appearance> next = new LinkedHashMap<>(presets);
        if (id == null || next.remove(id.toLowerCase(Locale.ROOT)) == null)
            return new ChangeResult(
                    ChangeResult.Status.UNKNOWN_PRESET, "Unknown appearance preset.");
        try {
            storage.savePresets(next);
            presets.clear();
            presets.putAll(next);
            return ChangeResult.ok();
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save appearance presets", ex);
            return new ChangeResult(
                    ChangeResult.Status.STORAGE_ERROR,
                    "Could not save presets; see the server log.");
        }
    }

    public ChangeResult applyPreset(String id, AppearanceScope scope, Duration duration) {
        Appearance preset = id == null ? null : presets.get(id.toLowerCase(Locale.ROOT));
        return preset == null
                ? remember(
                        scope,
                        new ChangeResult(
                                ChangeResult.Status.UNKNOWN_PRESET, "Unknown appearance preset."))
                : change(
                        scope,
                        preset,
                        EnumSet.allOf(AppearanceField.class),
                        duration,
                        "preset",
                        false);
    }

    public ChangeResult validate(
            AppearanceScope scope,
            Appearance proposed,
            Set<AppearanceField> fields,
            boolean restoring) {
        if (restoring) return ChangeResult.ok();
        for (var field : fields) {
            Object value = proposed.value(field);
            if (field == AppearanceField.SKIN) {
                if (value != null && !((SkinData) value).isValid())
                    return new ChangeResult(
                            ChangeResult.Status.INVALID_SKIN, "The skin has no texture data.");
                continue;
            }
            ChangeResult result =
                    NameValidation.validate(
                            field,
                            (String) value,
                            this::plain,
                            plugin.getConfig().getBoolean("names.reject-reserved-aliases", true));
            if (!result.success()) return result;
            if (!restoring
                    && field != AppearanceField.TABLIST
                    && value != null
                    && plugin.getConfig().getBoolean("names.reject-ambiguous-aliases", false)) {
                String alias = NameIndex.key(plain((String) value));
                for (var identity : snapshots.forViewer(scope.viewer()).identities()) {
                    if (identity.id().equals(scope.target()) || !identity.visible()) continue;
                    if (NameIndex.key(identity.account()).equals(alias)
                            || identity.aliases().stream()
                                    .anyMatch(a -> NameIndex.key(a).equals(alias)))
                        return new ChangeResult(
                                ChangeResult.Status.AMBIGUOUS_ALIAS,
                                "That command alias already identifies another player for this"
                                        + " viewer.");
                }
            }
        }
        return ChangeResult.ok();
    }

    private ChangeResult change(
            AppearanceScope scope,
            Appearance proposed,
            Set<AppearanceField> requested,
            Duration duration,
            String cause,
            boolean restoring) {
        requireMain();
        if (closed)
            return remember(
                    scope, new ChangeResult(ChangeResult.Status.CLOSED, "Skkaemok is disabled."));
        if (proposed == null)
            return remember(
                    scope,
                    new ChangeResult(
                            ChangeResult.Status.INVALID_NAME, "No appearance was provided."));
        long deadline = 0;
        if (duration != null) {
            try {
                long millis = duration.toMillis();
                if (millis <= 0) throw new ArithmeticException();
                deadline = Math.addExact(now.getAsLong(), millis);
            } catch (ArithmeticException ex) {
                return remember(
                        scope,
                        new ChangeResult(
                                ChangeResult.Status.INVALID_DURATION,
                                "Duration must be positive and finite."));
            }
        }
        if (!applying.add(scope))
            return remember(
                    scope,
                    new ChangeResult(
                            ChangeResult.Status.BUSY,
                            "An appearance change for this scope is already in progress."));
        TimedOverrides previousTemporary = temporary.copy();
        try {
            Appearance previous = capture(scope), next = previous;
            for (var field : requested)
                next =
                        next.with(
                                field,
                                field == AppearanceField.SKIN
                                        ? proposed.value(field)
                                        : normalize((String) proposed.value(field)));
            ChangeResult validation = validate(scope, next, requested, restoring);
            if (!validation.success()) return remember(scope, validation);
            Set<AppearanceField> changed = EnumSet.noneOf(AppearanceField.class);
            for (var field : requested)
                if (!Appearance.same(previous.value(field), next.value(field))) changed.add(field);
            if (!restoring && !changed.isEmpty()) {
                var event = new AppearanceChangeEvent(scope, previous, next, changed, cause);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled())
                    return remember(
                            scope,
                            new ChangeResult(
                                    ChangeResult.Status.CANCELLED,
                                    "The appearance change was cancelled."));
            }
            if (!restoring) {
                TimedOverrides old = temporary.copy();
                boolean hadTemporary = !temporary.keys().isEmpty();
                UUID token = UUID.randomUUID();
                for (var field : requested) {
                    var key = new TimedOverrides.Key(scope, field);
                    if (duration == null) temporary.permanent(key);
                    else
                        temporary.push(
                                key, previous.value(field), next.value(field), token, deadline);
                }
                if (hadTemporary || duration != null)
                    try {
                        storage.saveRecovery(temporary.recovery());
                    } catch (IOException ex) {
                        temporary = old;
                        return storageFailure(scope, ex);
                    }
            }
            if (!restoring && requested.contains(AppearanceField.SKIN))
                skinService.cancelRequest(scope.target(), scope.viewer());
            if (changed.isEmpty())
                return remember(
                        scope,
                        new ChangeResult(
                                ChangeResult.Status.UNCHANGED, "Appearance already matches."));
            Appearance committed = next;
            try {
                snapshots.batch(
                        () -> {
                            try {
                                commit(scope, committed, changed);
                            } catch (UncheckedIOException ex) {
                                restoreRaw(scope, previous, changed);
                                throw ex;
                            }
                        });
            } catch (UncheckedIOException ex) {
                if (!restoring) {
                    temporary = previousTemporary;
                    try {
                        storage.saveRecovery(temporary.recovery());
                    } catch (IOException journalError) {
                        plugin.getLogger()
                                .log(
                                        Level.SEVERE,
                                        "Could not restore appearance recovery data",
                                        journalError);
                    }
                }
                return storageFailure(scope, ex.getCause());
            } catch (RuntimeException ex) {
                plugin.getLogger()
                        .log(
                                Level.SEVERE,
                                "Appearance was stored but its client refresh failed",
                                ex);
                return remember(
                        scope,
                        new ChangeResult(
                                ChangeResult.Status.APPLY_FAILED,
                                "Appearance was stored, but its client refresh failed; see the"
                                        + " server log."));
            }
            ChangeResult result = remember(scope, ChangeResult.ok());
            Bukkit.getPluginManager()
                    .callEvent(new AppearanceChangedEvent(scope, previous, next, changed, cause));
            return result;
        } finally {
            applying.remove(scope);
        }
    }

    private void commit(AppearanceScope scope, Appearance value, Set<AppearanceField> fields) {
        for (var field : fields)
            switch (field) {
                case NAMETAG -> names.store(scope.target(), scope.viewer(), value.nametag());
                case CHAT -> chat.store(scope.target(), scope.viewer(), value.chat());
                case TABLIST -> tabs.store(scope.target(), scope.viewer(), value.tablist());
                case SKIN -> {}
            }
        Player target = Bukkit.getPlayer(scope.target());
        Player viewer = scope.viewer() == null ? null : Bukkit.getPlayer(scope.viewer());
        if (target != null && scope.viewer() == null && fields.contains(AppearanceField.CHAT))
            chatService.applyStoredChatName(target);
        if (fields.contains(AppearanceField.SKIN))
            skinService.commitSkin(scope.target(), scope.viewer(), value.skin());
        else if (target != null) {
            if (scope.viewer() == null) renderer.updateForAllViewers(target);
            else if (viewer != null) renderer.updateForViewer(target, viewer);
        }
        snapshots.refresh();
    }

    private boolean restoreRaw(
            AppearanceScope scope, Appearance value, Set<AppearanceField> fields) {
        boolean saved = true;
        for (var field : fields)
            try {
                switch (field) {
                    case NAMETAG -> names.store(scope.target(), scope.viewer(), value.nametag());
                    case CHAT -> chat.store(scope.target(), scope.viewer(), value.chat());
                    case TABLIST -> tabs.store(scope.target(), scope.viewer(), value.tablist());
                    case SKIN -> skins.store(scope.target(), scope.viewer(), value.skin());
                }
            } catch (UncheckedIOException ex) {
                saved = false;
                plugin.getLogger()
                        .log(
                                Level.SEVERE,
                                "Could not persist restored appearance values; recovery data is"
                                        + " retained",
                                ex);
            }
        return saved;
    }

    public void expire() {
        requireMain();
        if (closed || temporary.keys().isEmpty()) return;
        int fields = temporary.keys().size();
        for (var key : temporary.keys())
            if (!Appearance.same(capture(key.scope()).value(key.field()), temporary.current(key)))
                temporary.permanent(key);
        TimedOverrides previous = temporary.copy();
        Map<TimedOverrides.Key, Object> expired = temporary.expire(now.getAsLong());
        if (expired.isEmpty() && fields == temporary.keys().size()) return;
        if (!restore(expired, "expiry")) {
            temporary = previous;
            return;
        }
        try {
            storage.saveRecovery(temporary.recovery());
        } catch (IOException ex) {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            "Temporary appearances expired, but recovery data could not be updated",
                            ex);
        }
    }

    private boolean restore(Map<TimedOverrides.Key, Object> values, String cause) {
        Map<AppearanceScope, Appearance> next = new LinkedHashMap<>();
        Map<AppearanceScope, Set<AppearanceField>> fields = new LinkedHashMap<>();
        values.forEach(
                (key, value) -> {
                    next.put(
                            key.scope(),
                            next.getOrDefault(key.scope(), capture(key.scope()))
                                    .with(key.field(), value));
                    fields.computeIfAbsent(
                                    key.scope(), ignored -> EnumSet.noneOf(AppearanceField.class))
                            .add(key.field());
                });
        boolean success = true;
        for (var entry : next.entrySet())
            success =
                    change(
                                            entry.getKey(),
                                            entry.getValue(),
                                            fields.get(entry.getKey()),
                                            null,
                                            cause,
                                            true)
                                    .success()
                            && success;
        return success;
    }

    public int temporaryFields(AppearanceScope scope) {
        return (int) temporary.keys().stream().filter(key -> key.scope().equals(scope)).count();
    }

    public List<String> inspect(AppearanceScope scope) {
        requireMain();
        Appearance raw = capture(scope),
                global = capture(new AppearanceScope(scope.target(), null));
        var account = Bukkit.getOfflinePlayer(scope.target());
        String real = account.getName() == null ? scope.target().toString() : account.getName();
        List<String> lines = new ArrayList<>();
        lines.add("Target: " + real + " (" + scope.target() + ")");
        lines.add("View: " + (scope.viewer() == null ? "global" : scope.viewer().toString()));
        for (var field : NAMES.stream().sorted().toList()) {
            Object own = raw.value(field), fallback = global.value(field);
            Object effective = own != null ? own : fallback != null ? fallback : real;
            String origin =
                    own != null
                            ? (scope.viewer() == null ? "global override" : "viewer override")
                            : fallback != null ? "global override" : "account name";
            lines.add(
                    field.name().toLowerCase(Locale.ROOT)
                            + ": "
                            + plain(String.valueOf(effective))
                            + " ["
                            + origin
                            + "]");
        }
        SkinData skin = raw.skin() != null ? raw.skin() : global.skin();
        lines.add(
                "Skin: "
                        + (skin == null
                                ? "native"
                                : skin.getSource() == null ? "stored texture" : skin.getSource()));
        lines.add("Skin request: " + skinService.requestStatus(scope.target(), scope.viewer()));
        Player target = Bukkit.getPlayer(scope.target()),
                viewer = scope.viewer() == null ? null : Bukkit.getPlayer(scope.viewer());
        lines.add(
                "Visible: "
                        + (target == null
                                ? "target offline"
                                : scope.viewer() != null && viewer == null
                                        ? "viewer offline"
                                        : viewer == null || viewer.canSee(target) ? "yes" : "no"));
        if (target != null) {
            NameIndex index = snapshots.forViewer(scope.viewer());
            var identity = index.identity(scope.target());
            if (identity != null)
                for (String alias : identity.aliases().stream().sorted().toList()) {
                    var resolved = index.resolve(alias);
                    lines.add(
                            "Alias "
                                    + alias
                                    + ": "
                                    + (resolved != null
                                                    && resolved.id().equals(scope.target())
                                                    && !NameIndex.reserved(alias)
                                            ? "usable"
                                            : "unavailable, reserved or ambiguous"));
                }
            lines.add("Managed client presentation: " + renderer.hasClientState(target));
        }
        lines.add("Temporary fields: " + temporaryFields(scope));
        ChangeResult last = lastResult(scope);
        lines.add(
                "Last result: "
                        + last.status().name().toLowerCase(Locale.ROOT)
                        + " — "
                        + last.message());
        return List.copyOf(lines);
    }

    private ChangeResult remember(AppearanceScope scope, ChangeResult result) {
        results.put(scope, result);
        while (results.size() > 2048) results.remove(results.keySet().iterator().next());
        return result;
    }

    private ChangeResult storageFailure(AppearanceScope scope, IOException ex) {
        plugin.getLogger().log(Level.SEVERE, "Could not save appearance data", ex);
        return remember(
                scope,
                new ChangeResult(
                        ChangeResult.Status.STORAGE_ERROR,
                        "Could not save appearance data; see the server log."));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean validPreset(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    }

    private static void requireMain() {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Appearance changes must run on the server thread");
    }

    @Override
    public void close() {
        if (closed) return;
        if (expiryTask != null) expiryTask.cancel();
        if (!started && temporary.keys().isEmpty()) {
            closed = true;
            results.clear();
            return;
        }
        boolean saved = true;
        for (var recovery : temporary.recovery())
            saved = restoreRaw(recovery.scope(), recovery.base(), recovery.fields()) && saved;
        temporary.restoreAll();
        try {
            if (saved) storage.saveRecovery(List.of());
        } catch (IOException ex) {
            plugin.getLogger()
                    .log(Level.SEVERE, "Could not clear temporary appearance recovery", ex);
        }
        closed = true;
        results.clear();
    }
}
