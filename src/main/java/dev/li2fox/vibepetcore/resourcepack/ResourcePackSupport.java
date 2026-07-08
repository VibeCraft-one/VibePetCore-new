package dev.li2fox.vibepetcore.resourcepack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class ResourcePackSupport {
    private static final String PACK_FILE_NAME = "VibePetCore-resource-pack.zip";

    private ResourcePackSupport() {
    }

    static String packFileName() {
        return PACK_FILE_NAME;
    }

    static byte[] parseSha1Hex(String raw) {
        if (raw == null) {
            return new byte[0];
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new byte[0];
        }
        if (normalized.length() != 40 || !normalized.chars().allMatch(ResourcePackSupport::isHexDigit)) {
            throw new IllegalArgumentException("resource-pack.sha1 must be a 40-character hex string");
        }
        byte[] hash = new byte[20];
        for (int index = 0; index < hash.length; index++) {
            int offset = index * 2;
            hash[index] = (byte) Integer.parseInt(normalized.substring(offset, offset + 2), 16);
        }
        return hash;
    }

    static String sha1Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(data);
            return hex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is not available", exception);
        }
    }

    static String hex(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte value : data) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    static String resolvePublicUrl(
        String configuredUrl,
        boolean autoHostEnabled,
        String publicUrl,
        String publicHost,
        int port
    ) {
        String explicitUrl = configuredUrl == null ? "" : configuredUrl.trim();
        if (!explicitUrl.isEmpty()) {
            return explicitUrl;
        }
        if (!autoHostEnabled) {
            return "";
        }
        String directPublicUrl = publicUrl == null ? "" : publicUrl.trim();
        if (!directPublicUrl.isEmpty()) {
            return directPublicUrl;
        }
        String host = publicHost == null ? "" : publicHost.trim();
        if (host.isEmpty()) {
            return "";
        }
        return "http://" + host + ":" + port + "/" + PACK_FILE_NAME;
    }

    static String sanitizeBindHost(String bindHost) {
        String normalized = bindHost == null ? "" : bindHost.trim();
        return normalized.isEmpty() ? "0.0.0.0" : normalized;
    }

    static void validateLocalPack(Path target, String configuredSha1) throws IOException {
        if (!Files.exists(target) || Files.size(target) == 0L) {
            throw new IOException(missingPackMessage(target));
        }
        String expected = configuredSha1 == null ? "" : configuredSha1.trim();
        if (expected.isEmpty()) {
            return;
        }
        String localSha1 = sha1Hex(Files.readAllBytes(target));
        if (!expected.equalsIgnoreCase(localSha1)) {
            throw new IOException(stalePackMessage(target, localSha1, expected));
        }
    }

    static String missingPackMessage(Path target) {
        return "Resource pack file is missing: " + target
            + System.lineSeparator()
            + "Place " + PACK_FILE_NAME + " into plugins/VibePetCore/resource-pack/."
            + System.lineSeparator()
            + "Build artifact: ./gradlew buildResourcePack (outputs build/resource-pack/) or ./gradlew publishResourcePack (outputs dist/)."
            + System.lineSeparator()
            + "Alternatively host the zip at resource-pack.url and set resource-pack.sha1.";
    }

    static String stalePackMessage(Path target, String localSha1, String expectedSha1) {
        return "Resource pack SHA1 mismatch at " + target
            + System.lineSeparator()
            + "Local: " + localSha1
            + System.lineSeparator()
            + "Expected (resource-pack.sha1): " + expectedSha1
            + System.lineSeparator()
            + "Replace the file manually after plugin upgrade. Do not serve a stale pack with updated custom-model-data.";
    }

    private static boolean isHexDigit(int value) {
        return (value >= '0' && value <= '9') || (value >= 'a' && value <= 'f');
    }
}
