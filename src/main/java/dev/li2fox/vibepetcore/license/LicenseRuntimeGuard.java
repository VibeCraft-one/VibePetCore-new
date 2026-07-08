package dev.li2fox.vibepetcore.license;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Периодически перепроверяет лицензию во время работы сервера.
 * Если ключ отозван / невалиден — плагин отключается.
 */
public final class LicenseRuntimeGuard {
    private LicenseRuntimeGuard() {
    }

    public static void start(
            JavaPlugin plugin,
            LicenseClient client,
            String installationId,
            String pluginVersion,
            String minecraftVersion,
            int serverPort,
            String serverName
    ) {
        long heartbeatHours = Math.max(1L, plugin.getConfig().getLong("license.heartbeat-interval-hours", 6L));
        long heartbeatTicks = heartbeatHours * 60L * 60L * 20L;
        long recheckTicks = 30L * 60L * 20L;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            LicenseClient.LicenseResult result = client.heartbeat(
                    pluginVersion, minecraftVersion, serverPort, serverName, installationId
            );
            handleRuntimeResult(plugin, result, "heartbeat");
        }, heartbeatTicks, heartbeatTicks);

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            LicenseClient.LicenseResult result = client.validate(
                    pluginVersion, minecraftVersion, serverPort, serverName, installationId
            );
            handleRuntimeResult(plugin, result, "recheck");
        }, recheckTicks, recheckTicks);
    }

    private static void handleRuntimeResult(JavaPlugin plugin, LicenseClient.LicenseResult result, String source) {
        if (result.allowed()) {
            if (result.grace()) {
                plugin.getLogger().warning("[License] " + source + ": API unavailable, grace-period active.");
            }
            return;
        }

        String reason = result.message() == null ? result.status().name() : result.message();
        plugin.getLogger().severe("[License] " + source + " failed: " + reason + " — disabling plugin.");
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().disablePlugin(plugin));
    }
}
