package dev.li2fox.vibepetcore.license;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

final class LicenseGraceCache {
    private static final Gson GSON = new Gson();

    private LicenseGraceCache() {
    }

    static void write(Path cacheFile, LicenseResponse response) throws IOException {
        JsonObject cache = new JsonObject();
        cache.addProperty("payload", responseToJson(response));
        Files.createDirectories(cacheFile.getParent());
        Files.writeString(cacheFile, GSON.toJson(cache), StandardCharsets.UTF_8);
    }

    static LicenseResponse readVerified(Path cacheFile, String productId, String publicKey, LicenseSignatureVerifier verifier) {
        try {
            if (!Files.exists(cacheFile)) {
                return null;
            }
            JsonObject cache = GSON.fromJson(Files.readString(cacheFile, StandardCharsets.UTF_8), JsonObject.class);
            if (cache == null || !cache.has("payload")) {
                return null;
            }
            LicenseResponse response = LicenseResponse.fromJson(cache.get("payload").getAsString());
            if (!verifier.verify(response, publicKey)) {
                return null;
            }
            if (!response.status().isValid()) {
                return null;
            }
            if (response.product() == null || !productId.equalsIgnoreCase(response.product())) {
                return null;
            }
            Long graceUntil = response.graceUntil();
            if (graceUntil == null || graceUntil <= Instant.now().getEpochSecond()) {
                return null;
            }
            return response;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String responseToJson(LicenseResponse response) {
        JsonObject json = new JsonObject();
        json.addProperty("status", response.status().name());
        if (response.product() != null) {
            json.addProperty("product", response.product());
        }
        if (response.licensee() != null) {
            json.addProperty("licensee", response.licensee());
        }
        if (response.expiresAt() != null) {
            json.addProperty("expiresAt", response.expiresAt());
        }
        if (response.features() != null) {
            json.add("features", GSON.toJsonTree(response.features()));
        }
        if (response.activationId() != null) {
            json.addProperty("activationId", response.activationId());
        }
        json.addProperty("checkedAt", response.checkedAt());
        if (response.graceUntil() != null) {
            json.addProperty("graceUntil", response.graceUntil());
        }
        if (response.nonce() != null) {
            json.addProperty("nonce", response.nonce());
        }
        if (response.signature() != null) {
            json.addProperty("signature", response.signature());
        }
        if (response.publicKeyId() != null) {
            json.addProperty("publicKeyId", response.publicKeyId());
        }
        if (response.reason() != null) {
            json.addProperty("reason", response.reason());
        }
        return GSON.toJson(json);
    }
}
