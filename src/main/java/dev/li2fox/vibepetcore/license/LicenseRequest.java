package dev.li2fox.vibepetcore.license;

public final class LicenseRequest {
    private final String licenseKey;
    private final String productId;
    private final String pluginVersion;
    private final int serverPort;
    private final String serverName;
    private final String installationId;
    private final String minecraftVersion;
    private final String nonce;

    public LicenseRequest(
            String licenseKey,
            String productId,
            String pluginVersion,
            int serverPort,
            String serverName,
            String installationId,
            String minecraftVersion,
            String nonce
    ) {
        this.licenseKey = licenseKey;
        this.productId = productId;
        this.pluginVersion = pluginVersion;
        this.serverPort = serverPort;
        this.serverName = serverName;
        this.installationId = installationId;
        this.minecraftVersion = minecraftVersion;
        this.nonce = nonce;
    }

    public String licenseKey() {
        return licenseKey;
    }

    public String productId() {
        return productId;
    }

    public String pluginVersion() {
        return pluginVersion;
    }

    public int serverPort() {
        return serverPort;
    }

    public String serverName() {
        return serverName;
    }

    public String installationId() {
        return installationId;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public String nonce() {
        return nonce;
    }
}
