package dev.li2fox.vibepetcore.license;

import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class LicenseGuard {
    private LicenseGuard() {
    }

    public static boolean verifyOrDisable(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();

        if (!config.getBoolean("license.enabled", true)) {
            return fail(plugin, "License check is mandatory. Set license.enabled: true and provide a purchased key.");
        }

        String licenseKey = config.getString("license.key", "").trim();

        if (LicenseConstants.isPlaceholderKey(licenseKey)) {
            return fail(plugin, "No license key configured. Purchase a license and set license.key in config.yml.");
        }

        try {
            InstallationIdStore installationIdStore = new InstallationIdStore(plugin.getDataFolder().toPath());
            String installationId = installationIdStore.getOrCreate();

            LicenseClient client = new LicenseClient(
                    LicenseConstants.SERVER_URL,
                    LicenseConstants.PRODUCT_ID,
                    licenseKey,
                    LicenseConstants.PUBLIC_KEY,
                    plugin.getDataFolder().toPath(),
                    message -> plugin.getLogger().warning("[License] " + message)
            );

            String pluginVersion = plugin.getPluginMeta().getVersion();
            String minecraftVersion = Bukkit.getBukkitVersion();
            int serverPort = Bukkit.getPort();
            String serverName = resolveServerName();

            LicenseClient.LicenseResult result = client.validate(
                    pluginVersion,
                    minecraftVersion,
                    serverPort,
                    serverName,
                    installationId
            );

            if (!result.allowed()) {
                String reason = result.message() == null ? result.status().name() : result.message();
                return fail(plugin, "License rejected: " + reason);
            }

            if (result.grace()) {
                plugin.getLogger().warning("[License] Running on grace-period cache. Connect to license server soon.");
            } else {
                plugin.getLogger().info("[License] Valid for " + (result.response() == null
                        ? LicenseConstants.PRODUCT_ID
                        : result.response().licensee()));
            }

            LicenseRuntimeGuard.start(plugin, client, installationId, pluginVersion, minecraftVersion, serverPort, serverName);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "[License] Validation failed", exception);
            return fail(plugin, exception.getMessage() == null ? "unknown error" : exception.getMessage());
        }
    }

    private static String resolveServerName() {
        String motd = Bukkit.getMotd();
        if (motd != null && !motd.isBlank()) {
            return motd;
        }
        return Bukkit.getServer().getName();
    }

    private static boolean fail(JavaPlugin plugin, String reason) {
        plugin.getLogger().severe("[License] " + reason);
        plugin.getLogger().severe("[License] VibePetCore requires a valid license. Get one at license.vibecraft.one");
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().disablePlugin(plugin));
        return false;
    }
}
