package dev.li2fox.vibepetcore.resourcepack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ResourcePackSupportTest {

    @Test
    void parseSha1HexAcceptsLowercaseHex() {
        byte[] parsed = ResourcePackSupport.parseSha1Hex("2123c95717a1bab66ed75f1d39e77c1d54374649");
        assertEquals(20, parsed.length);
        assertEquals(0x21, parsed[0] & 0xFF);
        assertEquals(0x49, parsed[19] & 0xFF);
    }

    @Test
    void sha1HexRoundTrip() {
        String hex = ResourcePackSupport.sha1Hex("vibepetcore-pack".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(ResourcePackSupport.parseSha1Hex(hex), ResourcePackSupport.parseSha1Hex(hex));
    }

    @Test
    void resolvePublicUrlPrefersExplicitUrl() {
        String url = ResourcePackSupport.resolvePublicUrl(
            "https://example.com/pack.zip",
            true,
            "",
            "play.example.com",
            25512
        );
        assertEquals("https://example.com/pack.zip", url);
    }

    @Test
    void resolvePublicUrlBuildsAutoHostUrl() {
        String url = ResourcePackSupport.resolvePublicUrl(
            "",
            true,
            "",
            "play.example.com",
            25512
        );
        assertEquals("http://play.example.com:25512/VibePetCore-resource-pack.zip", url);
    }

    @Test
    void parseSha1HexRejectsInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> ResourcePackSupport.parseSha1Hex("not-a-sha1"));
    }
}
