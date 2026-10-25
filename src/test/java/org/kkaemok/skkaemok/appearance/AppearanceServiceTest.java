package org.kkaemok.skkaemok.appearance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.kkaemok.skkaemok.chat.*;
import org.kkaemok.skkaemok.display.NametagManager;
import org.kkaemok.skkaemok.event.*;
import org.kkaemok.skkaemok.name.*;
import org.kkaemok.skkaemok.skin.*;
import org.kkaemok.skkaemok.storage.*;
import org.kkaemok.skkaemok.tablist.TablistNameManager;
import org.mockito.MockedStatic;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

class AppearanceServiceTest {
    @TempDir Path folder;
    private MockedStatic<Bukkit> bukkit;
    private JavaPlugin plugin;
    private YamlConfiguration config;
    private NameManager names;
    private ChatNameManager chat;
    private ChatNameStorage chatStorage;
    private TablistNameManager tabs;
    private SkinManager skins;
    private SkinService skinService;
    private ChatNameService chatService;
    private NametagManager renderer;
    private NameSnapshots snapshots;
    private PluginManager events;
    private AppearanceService service;
    private AppearanceStorage storage;
    private final AtomicLong now = new AtomicLong(1000);
    private final UUID target = UUID.randomUUID(), viewer = UUID.randomUUID();
    private final AppearanceScope global = new AppearanceScope(target, null),
            personal = new AppearanceScope(target, viewer);
    private final List<Event> fired = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        plugin = mock(JavaPlugin.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        ViewerNameManager viewerNames = new ViewerNameManager(mock(ViewerNameStorage.class));
        names = new NameManager(mock(NameStorage.class), viewerNames);
        chatStorage = mock(ChatNameStorage.class);
        chat = new ChatNameManager(chatStorage, viewerNames);
        tabs = new TablistNameManager(mock(TablistNameStorage.class), viewerNames);
        skins = new SkinManager(mock(SkinStorage.class), mock(ViewerSkinStorage.class));
        skinService = mock(SkinService.class);
        chatService = mock(ChatNameService.class);
        renderer = mock(NametagManager.class);
        snapshots = mock(NameSnapshots.class);
        doAnswer(
                        call -> {
                            call.getArgument(0, Runnable.class).run();
                            return null;
                        })
                .when(snapshots)
                .batch(any());
        when(snapshots.forViewer(any())).thenReturn(new NameIndex(List.of()));
        doAnswer(
                        call -> {
                            skins.store(
                                    call.getArgument(0), call.getArgument(1), call.getArgument(2));
                            return null;
                        })
                .when(skinService)
                .commitSkin(any(), nullable(UUID.class), nullable(SkinData.class));
        events = mock(PluginManager.class);
        doAnswer(
                        call -> {
                            fired.add(call.getArgument(0));
                            return null;
                        })
                .when(events)
                .callEvent(any());
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getPluginManager).thenReturn(events);
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        storage = new AppearanceStorage(folder);
        service = create();
    }

    private AppearanceService create() throws Exception {
        return new AppearanceService(
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
                now::get);
    }

    private Player online(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        return player;
    }

    @AfterEach
    void cleanup() {
        if (service != null) service.close();
        if (bukkit != null) bukkit.close();
    }

    @Test
    void unifiedNickChangesThreeChannelsAndRefreshesOnlyOnce() {
        Player player = online(target);
        assertTrue(service.setNickname(global, "깨묵", null).success());
        Appearance actual = service.capture(global);
        assertEquals("깨묵", actual.nametag());
        assertEquals("깨묵", actual.chat());
        assertEquals("깨묵", actual.tablist());
        verify(renderer, times(1)).updateForAllViewers(player);
        verify(chatService).applyStoredChatName(player);
        assertEquals(2, fired.size());
        var before = (AppearanceChangeEvent) fired.get(0);
        assertEquals(
                Set.of(AppearanceField.NAMETAG, AppearanceField.CHAT, AppearanceField.TABLIST),
                before.getFields());
        assertNull(before.getPrevious().nametag());
        assertEquals("깨묵", ((AppearanceChangedEvent) fired.get(1)).getCurrent().chat());
    }

    @Test
    void viewerBatchDoesNotTouchGlobalOrOtherViewers() {
        Player player = online(target), recipient = online(viewer);
        service.setNickname(global, "Global", null);
        clearInvocations(renderer);
        service.setNickname(personal, "Private", null);
        assertEquals("Global", service.capture(global).nametag());
        assertEquals("Private", service.capture(personal).nametag());
        assertNull(service.capture(new AppearanceScope(target, UUID.randomUUID())).nametag());
        verify(renderer, times(1)).updateForViewer(player, recipient);
        verify(renderer, never()).updateForAllViewers(any());
    }

    @Test
    void invalidBatchDoesNotPartiallyWriteAnyChannelOrSkin() {
        Appearance before = service.capture(global);
        SkinData skin = new SkinData("texture", "signature", "test", 0);
        var result =
                service.apply(
                        global,
                        new Appearance("name-that-is-over-sixteen", "Chat", "Tab", skin),
                        null);
        assertEquals(ChangeResult.Status.TOO_LONG, result.status());
        assertEquals(before, service.capture(global));
        assertTrue(fired.isEmpty());
        verifyNoInteractions(renderer, skinService);
    }

    @Test
    void cancellationRejectsTheCompleteBatchAndDoesNotCreateATemporaryLease() {
        doAnswer(
                        call -> {
                            Event event = call.getArgument(0);
                            if (event instanceof AppearanceChangeEvent change)
                                change.setCancelled(true);
                            return null;
                        })
                .when(events)
                .callEvent(any());
        assertEquals(
                ChangeResult.Status.CANCELLED,
                service.setNickname(global, "Cancelled", Duration.ofSeconds(5)).status());
        assertEquals(Appearance.EMPTY, service.capture(global));
        assertEquals(0, service.temporaryFields(global));
    }

    @Test
    void completeSkinBatchDelegatesOneProfileRefreshAfterNamesAreWritten() {
        online(target);
        SkinData skin = new SkinData("texture", "signature", "test", 0);
        doAnswer(
                        call -> {
                            assertEquals("Nick", names.raw(target, null));
                            assertEquals("Chat", chat.raw(target, null));
                            assertEquals("Tab", tabs.raw(target, null));
                            skins.store(target, null, call.getArgument(2));
                            return null;
                        })
                .when(skinService)
                .commitSkin(eq(target), isNull(), any());
        assertTrue(
                service.apply(global, new Appearance("Nick", "Chat", "Tab", skin), null).success());
        verify(skinService, times(1)).commitSkin(target, null, skin);
        verifyNoInteractions(renderer);
        assertSame(skin, skins.raw(target, null));
    }

    @Test
    void duplicateAndReservedAliasPoliciesExplainRejectedChanges() {
        assertEquals(
                ChangeResult.Status.RESERVED_ALIAS,
                service.setNickname(global, "@a", null).status());
        config.set("names.reject-reserved-aliases", false);
        assertTrue(service.setNickname(global, "@a", null).success());
        config.set("names.reject-ambiguous-aliases", true);
        var identity =
                new NameIndex.Identity(
                        UUID.randomUUID(),
                        "Other",
                        "Other",
                        Set.of("Other"),
                        net.kyori.adventure.text.Component.text("Other"),
                        true);
        when(snapshots.forViewer(null)).thenReturn(new NameIndex(List.of(identity)));
        assertEquals(
                ChangeResult.Status.AMBIGUOUS_ALIAS,
                service.setNickname(global, "Other", null).status());
        assertEquals("@a", service.capture(global).nametag());
    }

    @Test
    void controlsAndInvisibleNamesAreRejected() {
        for (String name : List.of("line\nbreak", "\u200B", "\u200Bname", "<red></red>"))
            assertEquals(
                    ChangeResult.Status.INVALID_NAME,
                    service.setNickname(global, name, null).status(),
                    name);
    }

    @Test
    void expiryRestoresOnlyFieldsStillOwnedByTheTemporaryChange() {
        service.setNickname(global, "Base", null);
        service.setNickname(global, "Temporary", Duration.ofSeconds(10));
        service.setName(global, NameChannel.CHAT, "NewChat");
        now.addAndGet(11000);
        service.expire();
        assertEquals("Base", service.capture(global).nametag());
        assertEquals("NewChat", service.capture(global).chat());
        assertEquals("Base", service.capture(global).tablist());
    }

    @Test
    void overlappingTemporaryChangesNeverResurrectAnExpiredOlderLayer() {
        service.setNickname(global, "Base", null);
        service.setNickname(global, "First", Duration.ofSeconds(10));
        now.addAndGet(1000);
        service.setNickname(global, "Second", Duration.ofSeconds(20));
        now.set(12000);
        service.expire();
        assertEquals("Second", service.capture(global).nametag());
        now.set(23000);
        service.expire();
        assertEquals("Base", service.capture(global).nametag());
    }

    @Test
    void sameValuePermanentWriteStillCancelsTemporaryRestoration() {
        service.setNickname(global, "Base", null);
        service.setNickname(global, "New", Duration.ofSeconds(10));
        service.setNickname(global, "New", null);
        now.addAndGet(11000);
        service.expire();
        assertEquals("New", service.capture(global).nametag());
        assertEquals(0, service.temporaryFields(global));
    }

    @Test
    void viewerExpiryRestoresInheritanceOfTheCurrentGlobalName() {
        service.setNickname(global, "Base", null);
        service.setNickname(personal, "Private", Duration.ofSeconds(10));
        service.setNickname(global, "NewGlobal", null);
        now.addAndGet(11000);
        service.expire();
        assertNull(service.capture(personal).nametag());
        assertEquals("NewGlobal", service.capture(global).nametag());
    }

    @Test
    void presetsRoundTripTexturesAndCanApplyToAnOfflineUuid() throws Exception {
        SkinData skin = new SkinData("texture", "signature", "saved", 7);
        service.apply(global, new Appearance("Nick", "Chat", "Tab", skin), null);
        assertTrue(service.savePreset("Knight", global).success());
        var restarted = create();
        var offline = new AppearanceScope(UUID.randomUUID(), null);
        assertTrue(restarted.applyPreset("KNIGHT", offline, null).success());
        Appearance result = restarted.capture(offline);
        assertEquals("Nick", result.nametag());
        assertEquals("signature", result.skin().getSignature());
        assertEquals(List.of("knight"), restarted.presetNames());
        assertEquals(
                ChangeResult.Status.INVALID_NAME, restarted.savePreset("../bad", global).status());
        assertTrue(restarted.deletePreset("knight").success());
        restarted.close();
    }

    @Test
    void crashRecoveryRestoresBaseValuesInsteadOfLeavingTemporaryNamesPermanent() throws Exception {
        service.setNickname(global, "Base", null);
        service.setNickname(global, "Temporary", Duration.ofMinutes(5));
        assertFalse(storage.recovery().isEmpty());
        var restarted = create();
        restarted.start();
        assertEquals("Base", restarted.capture(global).nametag());
        assertTrue(storage.recovery().isEmpty());
        restarted.close();
    }

    @Test
    void malformedPresetDataIsPreservedAndNotSilentlyOverwritten() throws Exception {
        Path file = folder.resolve("appearance-presets.json");
        Files.writeString(file, "{broken");
        assertThrows(java.io.IOException.class, this::create);
        assertEquals("{broken", Files.readString(file));
    }

    @Test
    void invalidDurationDoesNotChangeAnyState() {
        for (Duration duration :
                List.of(
                        Duration.ZERO,
                        Duration.ofSeconds(-1),
                        Duration.ofSeconds(Long.MAX_VALUE),
                        Duration.ofNanos(1)))
            assertEquals(
                    ChangeResult.Status.INVALID_DURATION,
                    service.setNickname(global, "Nick", duration).status());
        assertEquals(Appearance.EMPTY, service.capture(global));
    }

    @Test
    void reentrantPreChangeCannotOverwriteOrPartiallyApplyTheOuterChange() {
        doAnswer(
                        call -> {
                            if (call.getArgument(0) instanceof AppearanceChangeEvent)
                                assertEquals(
                                        ChangeResult.Status.BUSY,
                                        service.setNickname(global, "Inner", null).status());
                            return null;
                        })
                .when(events)
                .callEvent(any());
        assertTrue(service.setNickname(global, "Outer", null).success());
        assertEquals("Outer", service.capture(global).nametag());
    }

    @Test
    void shutdownRestoresSavedStateWithoutSendingPacketsFromADisabledPlugin() {
        service.setNickname(global, "Base", null);
        service.setNickname(global, "Temp", Duration.ofSeconds(10));
        clearInvocations(renderer, skinService);
        service.close();
        assertEquals("Base", service.capture(global).nametag());
        verifyNoInteractions(renderer, skinService);
        assertEquals(
                ChangeResult.Status.CLOSED, service.setNickname(global, "Later", null).status());
    }

    @Test
    void expiryDoesNotOverwriteValuesChangedThroughStorageOrAnotherInternalCaller() {
        service.setNickname(global, "Base", null);
        service.setNickname(global, "Temp", Duration.ofSeconds(1));
        chat.store(target, null, "External");
        now.addAndGet(2000);
        service.expire();
        assertEquals("External", service.capture(global).chat());
        assertEquals("Base", service.capture(global).nametag());
    }

    @Test
    void shorterNewTemporaryLayerRevealsAnOlderLayerThatHasNotExpired() {
        service.setNickname(global, "Base", null);
        service.setNickname(global, "Long", Duration.ofSeconds(10));
        service.setNickname(global, "Short", Duration.ofSeconds(2));
        now.addAndGet(3000);
        service.expire();
        assertEquals("Long", service.capture(global).nametag());
        now.addAndGet(8000);
        service.expire();
        assertEquals("Base", service.capture(global).nametag());
    }

    @Test
    void sameValueSkinResetStillCancelsPendingRemoteWork() {
        assertEquals(ChangeResult.Status.UNCHANGED, service.setSkin(global, null).status());
        verify(skinService).cancelRequest(target, null);
    }

    @Test
    void skinExpiryDoesNotCancelANewerRequestThatIsStillDownloading() {
        SkinData base = new SkinData("base", null, "base", 0),
                temp = new SkinData("temporary", null, "temporary", 0);
        service.setSkin(global, base);
        service.apply(global, new Appearance(null, null, null, temp), Duration.ofSeconds(1));
        clearInvocations(skinService);
        now.addAndGet(2000);
        service.expire();
        verify(skinService, never()).cancelRequest(any(), nullable(UUID.class));
        assertEquals("base", service.capture(global).skin().getValue());
    }

    @Test
    void malformedPresetReloadPreservesTheLoadedPresetsAndOriginalFile() throws Exception {
        service.setNickname(global, "Saved", null);
        service.savePreset("saved", global);
        Files.writeString(folder.resolve("appearance-presets.json"), "{broken");
        assertThrows(java.io.IOException.class, service::reload);
        service.setNickname(global, "Changed", null);
        assertTrue(service.applyPreset("saved", global, null).success());
        assertEquals("Saved", service.capture(global).nametag());
        assertEquals("{broken", Files.readString(folder.resolve("appearance-presets.json")));
    }

    @Test
    void transportFailureIsReportedAndDoesNotEmitAnAppliedEvent() {
        doThrow(new IllegalStateException("transport failed"))
                .when(skinService)
                .commitSkin(any(), nullable(UUID.class), any());
        var result =
                service.apply(
                        global,
                        new Appearance(
                                "Nick", "Chat", "Tab", new SkinData("value", null, "test", 0)),
                        null);
        assertEquals(ChangeResult.Status.APPLY_FAILED, result.status());
        assertEquals(1, fired.size());
        assertInstanceOf(AppearanceChangeEvent.class, fired.getFirst());
    }

    @Test
    void failedPersistenceRollsBackAllInMemoryChannelsWithoutPublishingAnAppliedEvent()
            throws Exception {
        service.setNickname(global, "Base", null);
        fired.clear();
        clearInvocations(renderer);
        doThrow(new java.io.IOException("disk full"))
                .doNothing()
                .when(chatStorage)
                .saveChecked(anyMap());
        assertEquals(
                ChangeResult.Status.STORAGE_ERROR,
                service.setNickname(global, "New", null).status());
        assertEquals(new Appearance("Base", "Base", "Base", null), service.capture(global));
        verifyNoInteractions(renderer);
        assertEquals(1, fired.size());
        assertInstanceOf(AppearanceChangeEvent.class, fired.getFirst());
    }

    @Test
    void aFailedExpiryRetainsRecoveryAndRetriesAfterPersistenceRecovers() throws Exception {
        service.setNickname(global, "Base", null);
        service.setNickname(global, "Temp", Duration.ofSeconds(1));
        now.addAndGet(2000);
        doThrow(new java.io.IOException("temporarily unavailable"))
                .when(chatStorage)
                .saveChecked(anyMap());
        service.expire();
        assertEquals("Temp", service.capture(global).nametag());
        assertFalse(storage.recovery().isEmpty());
        assertTrue(service.temporaryFields(global) > 0);
        doNothing().when(chatStorage).saveChecked(anyMap());
        service.expire();
        assertEquals("Base", service.capture(global).nametag());
        assertTrue(storage.recovery().isEmpty());
    }

    @Test
    void malformedRecoveryIsPreservedEvenWhenFailedStartupIsFollowedByDisable() throws Exception {
        Path file = folder.resolve("temporary-appearances.json");
        Files.writeString(file, "{broken");
        var restarted = create();
        assertThrows(java.io.IOException.class, restarted::start);
        restarted.close();
        assertEquals("{broken", Files.readString(file));
    }
}
