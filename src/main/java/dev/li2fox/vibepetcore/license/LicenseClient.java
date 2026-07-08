package dev.li2fox.vibepetcore.license;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

public final class LicenseClient {
    private static final Gson GSON = new Gson();

    private final String serverUrl;
    private final String productId;
    private final String licenseKey;
    private final String publicKey;
    private final Path cacheFile;
    private final HttpClient httpClient;
    private final LicenseSignatureVerifier verifier;
    private final Consumer<String> logger;

    public LicenseClient(
            String serverUrl,
            String productId,
            String licenseKey,
            String publicKey,
            Path dataFolder,
            Consumer<String> logger
    ) {
        this.serverUrl = trimTrailingSlash(serverUrl);
        this.productId = productId;
        this.licenseKey = licenseKey;
        this.publicKey = publicKey;
        this.cacheFile = dataFolder.resolve("license-cache.json");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.verifier = new LicenseSignatureVerifier();
        this.logger = logger == null ? message -> {} : logger;
    }

    public LicenseResult validate(String pluginVersion, String minecraftVersion, int serverPort, String serverName, String installationId) {
        return request("/api/v1/license/validate", pluginVersion, minecraftVersion, serverPort, serverName, installationId, true);
    }

    public LicenseResult heartbeat(String pluginVersion, String minecraftVersion, int serverPort, String serverName, String installationId) {
        return request("/api/v1/license/heartbeat", pluginVersion, minecraftVersion, serverPort, serverName, installationId, false);
    }

    public void heartbeatAsync(String pluginVersion, String minecraftVersion, int serverPort, String serverName, String installationId) {
        Thread.startVirtualThread(() -> heartbeat(pluginVersion, minecraftVersion, serverPort, serverName, installationId));
    }

    private LicenseResult request(
            String path,
            String pluginVersion,
            String minecraftVersion,
            int serverPort,
            String serverName,
            String installationId,
            boolean allowGraceOnNetworkError
    ) {
        try {
            LicenseResponse response = post(path, buildRequest(pluginVersion, minecraftVersion, serverPort, serverName, installationId));
            if (!verifier.verify(response, publicKey)) {
                logger.accept("License response signature verification failed.");
                return allowGraceOnNetworkError
                        ? useGraceOrFail("Invalid response signature.")
                        : LicenseResult.invalid(LicenseStatus.SERVER_ERROR, "Invalid response signature.");
            }

            if (response.status().isValid()) {
                writeCache(response);
                return LicenseResult.valid(response);
            }

            return LicenseResult.invalid(response.status(), response.reason());
        } catch (Exception ex) {
            logger.accept("License request failed: " + ex.getMessage());
            if (allowGraceOnNetworkError) {
                return useGraceOrFail(ex.getMessage());
            }
            return useVerifiedGraceOrFail(ex.getMessage());
        }
    }

    private LicenseRequest buildRequest(
            String pluginVersion,
            String minecraftVersion,
            int serverPort,
            String serverName,
            String installationId
    ) {
        return new LicenseRequest(
                licenseKey,
                productId,
                pluginVersion,
                serverPort,
                serverName,
                installationId,
                minecraftVersion,
                UUID.randomUUID().toString()
        );
    }

    private LicenseResult useGraceOrFail(String reason) {
        LicenseResponse cached = LicenseGraceCache.readVerified(cacheFile, productId, publicKey, verifier);
        if (cached != null) {
            logger.accept("Using verified grace-period cache until " + cached.graceUntil());
            return LicenseResult.grace(cached);
        }
        return LicenseResult.invalid(LicenseStatus.SERVER_ERROR, reason);
    }

    private LicenseResult useVerifiedGraceOrFail(String reason) {
        LicenseResponse cached = LicenseGraceCache.readVerified(cacheFile, productId, publicKey, verifier);
        if (cached != null) {
            return LicenseResult.grace(cached);
        }
        return LicenseResult.invalid(LicenseStatus.SERVER_ERROR, reason);
    }

    private LicenseResponse post(String path, LicenseRequest request) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("licenseKey", request.licenseKey());
        body.addProperty("productId", request.productId());
        body.addProperty("pluginVersion", request.pluginVersion());
        body.addProperty("serverPort", request.serverPort());
        body.addProperty("serverName", request.serverName());
        body.addProperty("installationId", request.installationId());
        body.addProperty("minecraftVersion", request.minecraftVersion());
        body.addProperty("nonce", request.nonce());

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (httpResponse.statusCode() >= 500) {
            throw new IOException("Server returned HTTP " + httpResponse.statusCode());
        }
        return LicenseResponse.fromJson(httpResponse.body());
    }

    private void writeCache(LicenseResponse response) {
        try {
            LicenseGraceCache.write(cacheFile, response);
        } catch (IOException ex) {
            logger.accept("Failed to write license cache: " + ex.getMessage());
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    public record LicenseResult(LicenseStatus status, LicenseResponse response, boolean grace, String message) {
        public static LicenseResult valid(LicenseResponse response) {
            return new LicenseResult(LicenseStatus.VALID, response, false, null);
        }

        public static LicenseResult grace(LicenseResponse response) {
            return new LicenseResult(LicenseStatus.VALID, response, true, "grace");
        }

        public static LicenseResult invalid(LicenseStatus status, String message) {
            return new LicenseResult(status, null, false, message);
        }

        public boolean allowed() {
            return status.isValid();
        }
    }
}
