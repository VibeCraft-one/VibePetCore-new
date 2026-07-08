package dev.li2fox.vibepetcore.license;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LicenseResponse {
    private final LicenseStatus status;
    private final String product;
    private final String licensee;
    private final Long expiresAt;
    private final List<String> features;
    private final String activationId;
    private final long checkedAt;
    private final Long graceUntil;
    private final String nonce;
    private final String signature;
    private final String publicKeyId;
    private final String reason;

    private LicenseResponse(
            LicenseStatus status,
            String product,
            String licensee,
            Long expiresAt,
            List<String> features,
            String activationId,
            long checkedAt,
            Long graceUntil,
            String nonce,
            String signature,
            String publicKeyId,
            String reason
    ) {
        this.status = status;
        this.product = product;
        this.licensee = licensee;
        this.expiresAt = expiresAt;
        this.features = features;
        this.activationId = activationId;
        this.checkedAt = checkedAt;
        this.graceUntil = graceUntil;
        this.nonce = nonce;
        this.signature = signature;
        this.publicKeyId = publicKeyId;
        this.reason = reason;
    }

    public static LicenseResponse fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<String> features = new ArrayList<>();
        if (root.has("features") && root.get("features").isJsonArray()) {
            JsonArray array = root.getAsJsonArray("features");
            for (JsonElement element : array) {
                features.add(element.getAsString());
            }
        }

        return new LicenseResponse(
                LicenseStatus.fromString(getString(root, "status")),
                getString(root, "product"),
                getString(root, "licensee"),
                getLong(root, "expiresAt"),
                Collections.unmodifiableList(features),
                getString(root, "activationId"),
                getLong(root, "checkedAt") == null ? 0L : getLong(root, "checkedAt"),
                getLong(root, "graceUntil"),
                getString(root, "nonce"),
                getString(root, "signature"),
                getString(root, "publicKeyId"),
                getString(root, "reason")
        );
    }

    private static String getString(JsonObject root, String key) {
        return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsString() : null;
    }

    private static Long getLong(JsonObject root, String key) {
        return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsLong() : null;
    }

    public LicenseStatus status() {
        return status;
    }

    public String product() {
        return product;
    }

    public String licensee() {
        return licensee;
    }

    public Long expiresAt() {
        return expiresAt;
    }

    public List<String> features() {
        return features;
    }

    public String activationId() {
        return activationId;
    }

    public long checkedAt() {
        return checkedAt;
    }

    public Long graceUntil() {
        return graceUntil;
    }

    public String nonce() {
        return nonce;
    }

    public String signature() {
        return signature;
    }

    public String publicKeyId() {
        return publicKeyId;
    }

    public String reason() {
        return reason;
    }
}
