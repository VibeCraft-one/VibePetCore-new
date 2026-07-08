package dev.li2fox.vibepetcore.resourcepack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void hexFormatsParsedSha1WithoutRehashing() {
        String expected = "996118a5a85f33292f85157780201ec8cc1381d4";
        assertEquals(expected, ResourcePackSupport.hex(ResourcePackSupport.parseSha1Hex(expected)));
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

    @Test
    void validateLocalPackRejectsMissingFile(@TempDir Path directory) {
        Path target = directory.resolve(ResourcePackSupport.packFileName());
        IOException exception = assertThrows(IOException.class, () -> ResourcePackSupport.validateLocalPack(target, ""));
        assertTrue(exception.getMessage().contains("Resource pack file is missing"));
        assertTrue(exception.getMessage().contains(ResourcePackSupport.packFileName()));
    }

    @Test
    void validateLocalPackAcceptsMatchingSha1(@TempDir Path directory) throws IOException {
        Path target = directory.resolve(ResourcePackSupport.packFileName());
        byte[] payload = "vibepetcore-pack".getBytes(StandardCharsets.UTF_8);
        Files.write(target, payload);
        String sha1 = ResourcePackSupport.sha1Hex(payload);
        ResourcePackSupport.validateLocalPack(target, sha1);
    }

    @Test
    void validateLocalPackRejectsStaleSha1(@TempDir Path directory) throws IOException {
        Path target = directory.resolve(ResourcePackSupport.packFileName());
        Files.write(target, "old-pack".getBytes(StandardCharsets.UTF_8));
        IOException exception = assertThrows(
            IOException.class,
            () -> ResourcePackSupport.validateLocalPack(target, "996118a5a85f33292f85157780201ec8cc1381d4")
        );
        assertTrue(exception.getMessage().contains("SHA1 mismatch"));
        assertTrue(exception.getMessage().contains("Replace the file manually"));
    }
}
