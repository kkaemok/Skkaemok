package org.kkaemok.skkaemok.display;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;
import org.kkaemok.skkaemok.skin.SkinData;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.kkaemok.skkaemok.display.ClientPlayerInfo.*;
import static org.mockito.Mockito.*;

class ClientProfileStateTest {
    private final ProfileState original = new ProfileState("Steve", Set.of(new Texture("original", "signature")));

    @Test
    void displayFormattingNeverRequiresProfileRefresh() {
        Component formatted = MiniMessage.miniMessage().deserialize("<red><bold>Private</bold></red>");
        Appearance before = new Appearance(original, null);
        Appearance next = new Appearance(original, formatted);
        assertFalse(needsProfileRefresh(before, next, original));
        assertFalse(needsProfileRefresh(null, next, original));
        String json = GsonComponentSerializer.gson().serialize(next.display());
        assertEquals(formatted, GsonComponentSerializer.gson().deserialize(json));
        assertTrue(json.contains("red"));
        assertTrue(json.contains("bold"));
    }

    @Test
    void settingReplacingAndResettingNicknamesRefreshesEvenWhenDesiredNameIsReal() {
        Appearance first = new Appearance(new ProfileState("First", original.textures()), null);
        Appearance second = new Appearance(new ProfileState("Second", original.textures()), null);
        Appearance reset = new Appearance(original, null);
        assertTrue(needsProfileRefresh(null, first, original));
        assertTrue(needsProfileRefresh(first, second, original));
        assertTrue(needsProfileRefresh(second, reset, original));
        assertFalse(needsProfileRefresh(reset, reset, original));
    }

    @Test
    void settingReplacingAndResettingTexturesRefreshesWithoutChangingName() {
        Appearance first = new Appearance(new ProfileState("Steve", Set.of(new Texture("one", "s1"))), null);
        Appearance second = new Appearance(new ProfileState("Steve", Set.of(new Texture("two", "s2"))), null);
        Appearance reset = new Appearance(original, null);
        assertTrue(needsProfileRefresh(null, first, original));
        assertTrue(needsProfileRefresh(first, second, original));
        assertTrue(needsProfileRefresh(second, reset, original));
        assertFalse(needsProfileRefresh(first, first, original));
    }

    @Test
    void comparesTextureContentsAndSignatureRatherThanStorageMetadataOrPropertyOrder() {
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getProperties()).thenReturn(Set.of(new ProfileProperty("textures", "original", "signature")));
        assertEquals(original, ProfileState.of(profile, "Steve", null));
        SkinData same = new SkinData("original", "signature", "another source", 12345);
        assertEquals(original, ProfileState.of(profile, "Steve", same));
        assertNotEquals(original, ProfileState.of(profile, "Steve", new SkinData("original", "new signature", "", 0)));
    }

    @Test
    void displayOnlyOwnershipDoesNotRefreshAnotherPluginsNativeIdentityChanges() {
        Appearance previous = new Appearance(original, Component.text("Display"), false, false);
        ProfileState external = new ProfileState("Steve", Set.of(new Texture("external", "s")));
        Appearance next = new Appearance(external, Component.text("New display"), false, false);
        assertFalse(needsProfileRefresh(previous, next, external));
    }

    @Test
    void relinquishingNicknameAndSkinOwnershipStillRefreshesTheReset() {
        Appearance managed = new Appearance(new ProfileState("Private", Set.of(new Texture("custom", "s"))), null, true, true);
        Appearance reset = new Appearance(original, null, false, false);
        assertTrue(needsProfileRefresh(managed, reset, original));
    }
}
