package org.kkaemok.skkaemok.skin;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SkinUrlTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "http://nd2.hn21.xyz:20057/skins/zombies/00e377cd4c1e2a79.png",
            "https://example.com/skin.png", "HTTP://example.com/a.png", "https://example.com:443/a.png",
            "http://[2001:db8::1]:8080/skin.png", "https://example.com/a%20b.png?version=2"
    })
    void acceptsHttpAndHttpsWithPorts(String url) {
        assertEquals(url, SkinService.normalizeUrl(url));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "ftp://example.com/a.png", "file:///skin.png", "javascript:alert(1)",
            "https:///skin.png", "//example.com/skin.png", "https://", "https://exa mple.com/a.png",
            "http://user:password@example.com/a.png", "https://user@example.com/a.png",
            "https://example.com/a.png#fragment", "https://example.com:0/a.png",
            "https://example.com:65536/a.png", "https://example.com:-1/a.png",
            "https://example.com:/a.png", "https://example.com\\@evil.test/a.png", "https://%65xample.com/a.png",
            "https://example.com/a%ZZ.png", "https://example.com/\r\nheader"
    })
    void rejectsInvalidOrUnsafeForms(String url) {
        assertNull(SkinService.normalizeUrl(url));
    }
}
