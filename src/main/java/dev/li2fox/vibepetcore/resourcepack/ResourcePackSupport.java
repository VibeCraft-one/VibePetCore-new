package dev.li2fox.vibepetcore.resourcepack;

import java.nio.charset.StandardCharsets;
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
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is not available", exception);
        }
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

    private static boolean isHexDigit(int value) {
        return (value >= '0' && value <= '9') || (value >= 'a' && value <= 'f');
    }
}
