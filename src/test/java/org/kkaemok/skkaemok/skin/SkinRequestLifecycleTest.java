package org.kkaemok.skkaemok.skin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.kkaemok.skkaemok.appearance.*;
import org.kkaemok.skkaemok.display.NametagManager;
import org.kkaemok.skkaemok.event.SkinRequestFailedEvent;
import org.kkaemok.skkaemok.name.NameManager;
import org.mockito.MockedStatic;

import java.net.http.*;
import java.util.*;
import java.util.logging.Logger;

class SkinRequestLifecycleTest {
    private MockedStatic<Bukkit> bukkit;
    private SkinService service;
    private AppearanceService appearances;
    private PluginManager events;
    private final List<Runnable> work = new ArrayList<>();
    private final AppearanceScope scope = new AppearanceScope(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getName()).thenReturn("test");
        service =
                new SkinService(
                        plugin,
                        mock(NameManager.class),
                        mock(SkinManager.class),
                        mock(NametagManager.class));
        appearances = mock(AppearanceService.class);
        service.setAppearanceService(appearances);
        when(appearances.pendingSkin(any()))
                .thenReturn(new ChangeResult(ChangeResult.Status.PENDING, "queued"));
        when(appearances.setSkin(any(), any())).thenReturn(ChangeResult.ok());
        when(appearances.reject(any(), any(), anyString()))
                .thenAnswer(call -> new ChangeResult(call.getArgument(1), call.getArgument(2)));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        doAnswer(
                        call -> {
                            work.add(call.getArgument(1));
                            return null;
                        })
                .when(scheduler)
                .runTaskAsynchronously(eq(plugin), any(Runnable.class));
        events = mock(PluginManager.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getPluginManager).thenReturn(events);
        HttpClient client = mock(HttpClient.class);
        var field = SkinService.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(service, client);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(
                        call -> {
                            HttpRequest request = call.getArgument(0);
                            String path = request.uri().getPath();
                            HttpResponse<String> response = mock(HttpResponse.class);
                            if (path.endsWith("Missing")) {
                                when(response.statusCode()).thenReturn(404);
                                when(response.body()).thenReturn("{}");
                            } else if (path.contains("/users/profiles/")) {
                                when(response.statusCode()).thenReturn(200);
                                when(response.body())
                                        .thenReturn(
                                                "{\"id\":\""
                                                        + (path.endsWith("FixtureTwo") ? "2" : "1")
                                                                .repeat(32)
                                                        + "\"}");
                            } else {
                                when(response.statusCode()).thenReturn(200);
                                when(response.body())
                                        .thenReturn(
                                                "{\"properties\":[{\"name\":\"textures\",\"value\":\""
                                                        + (path.endsWith("2".repeat(32))
                                                                ? "second"
                                                                : "first")
                                                        + "\"}]}");
                            }
                            return response;
                        });
    }

    @AfterEach
    void cleanup() {
        service.close();
        bukkit.close();
    }

    @Test
    void explicitOfflineRequestsApplyByUuidAfterTargetOrViewerLogout() {
        assertEquals(
                ChangeResult.Status.PENDING, service.requestSkin(scope, "Fixture", false).status());
        assertEquals("pending", service.requestStatus(scope.target(), scope.viewer()));
        service.removePlayer(scope.target());
        service.removePlayer(scope.viewer());
        work.getFirst().run();
        verify(appearances).setSkin(eq(scope), argThat(skin -> skin.getValue().equals("first")));
        assertEquals("applied", service.requestStatus(scope.target(), scope.viewer()));
    }

    @Test
    void legacyOnlineSkinRequestsStillCancelOnQuitRatherThanApplyingOnARejoinedPlayer() {
        var player = mock(org.bukkit.entity.Player.class);
        when(player.getUniqueId()).thenReturn(scope.target());
        service.setSkinFromName(player, "Fixture");
        service.removePlayer(scope.target());
        work.getFirst().run();
        verify(appearances, never()).setSkin(any(), any());
        verify(events, never()).callEvent(any());
    }

    @Test
    void aNewerRequestPreventsAnOlderResponseFromApplying() {
        service.requestSkin(scope, "Fixture", false);
        service.requestSkin(scope, "FixtureTwo", false);
        work.get(1).run();
        work.get(0).run();
        verify(appearances, times(1))
                .setSkin(eq(scope), argThat(skin -> skin.getValue().equals("second")));
        verify(events, never()).callEvent(any());
    }

    @Test
    void cancellationDiscardsInFlightResponseWithoutEmittingAFailureOrApplyingSkin() {
        service.requestSkin(scope, "Fixture", false);
        service.cancelRequest(scope.target(), scope.viewer());
        work.getFirst().run();
        verify(appearances, never()).setSkin(any(), any());
        verify(events, never()).callEvent(any());
        assertNotEquals("pending", service.requestStatus(scope.target(), scope.viewer()));
    }

    @Test
    void remoteFailureHasTheCorrectTargetViewerSourceAndReason() {
        service.requestSkin(scope, "Missing", false);
        work.getFirst().run();
        verify(events)
                .callEvent(
                        argThat(
                                event ->
                                        event instanceof SkinRequestFailedEvent failure
                                                && failure.getScope().equals(scope)
                                                && failure.getSource().equals("Missing")
                                                && !failure.getReason().isBlank()));
        assertTrue(
                service.requestStatus(scope.target(), scope.viewer()).startsWith("invalid_skin:"));
        verify(appearances, never()).setSkin(any(), any());
    }

    @Test
    void invalidSourcesFailImmediatelyWithoutStartingRemoteWork() {
        assertEquals(
                ChangeResult.Status.INVALID_SKIN,
                service.requestSkin(scope, "not a url", true).status());
        assertTrue(work.isEmpty());
        verify(events, times(1)).callEvent(any(SkinRequestFailedEvent.class));
    }

    @Test
    void cancelledApplicationEmitsFailureWithTheCancellationReason() {
        when(appearances.setSkin(any(), any()))
                .thenReturn(new ChangeResult(ChangeResult.Status.CANCELLED, "Blocked by a script"));
        service.requestSkin(scope, "Fixture", false);
        work.getFirst().run();
        verify(events)
                .callEvent(
                        argThat(
                                event ->
                                        event instanceof SkinRequestFailedEvent failure
                                                && failure.getReason()
                                                        .equals("Blocked by a script")));
        assertTrue(service.requestStatus(scope.target(), scope.viewer()).startsWith("cancelled:"));
    }
}
