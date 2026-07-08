package dev.li2fox.vibepetcore.license;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class LicenseSignatureVerifier {
    public boolean verify(LicenseResponse response, String publicKeyBase64) {
        if (response.signature() == null || response.signature().isBlank()) {
            return false;
        }
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            return false;
        }

        try {
            Map<String, Object> payload = new TreeMap<>();
            putIfPresent(payload, "status", response.status().name());
            putIfPresent(payload, "product", response.product());
            putIfPresent(payload, "licensee", response.licensee());
            putIfPresent(payload, "expiresAt", response.expiresAt());
            putIfPresent(payload, "features", response.features());
            putIfPresent(payload, "activationId", response.activationId());
            putIfPresent(payload, "checkedAt", response.checkedAt());
            putIfPresent(payload, "graceUntil", response.graceUntil());
            putIfPresent(payload, "nonce", response.nonce());

            String message = new Gson().toJson(payload);
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            byte[] signature = java.util.Base64.getDecoder().decode(response.signature());
            byte[] publicKey = java.util.Base64.getDecoder().decode(publicKeyBase64);

            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("Ed25519");
            java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(wrapEd25519PublicKey(publicKey));
            java.security.PublicKey verifyKey = keyFactory.generatePublic(keySpec);
            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(verifyKey);
            verifier.update(messageBytes);
            return verifier.verify(signature);
        } catch (Exception ex) {
            return false;
        }
    }

    private static void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private static byte[] wrapEd25519PublicKey(byte[] rawKey) {
        byte[] prefix = new byte[] {
                0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        };
        byte[] encoded = new byte[prefix.length + rawKey.length];
        System.arraycopy(prefix, 0, encoded, 0, prefix.length);
        System.arraycopy(rawKey, 0, encoded, prefix.length, rawKey.length);
        return encoded;
    }
}
