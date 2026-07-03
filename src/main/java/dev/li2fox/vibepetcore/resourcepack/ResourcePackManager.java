package dev.li2fox.vibepetcore.resourcepack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.CoreModule;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourcePackManager implements CoreModule, Listener {
    private static final String BUNDLED_RESOURCE = "resource-pack/VibePetCore-resource-pack.zip";

    private final JavaPlugin plugin;
    private final BalanceConfig balanceConfig;

    private HttpServer httpServer;
    private Path packFile;
    private byte[] packSha1 = new byte[0];
    private String resolvedUrl = "";

    public ResourcePackManager(JavaPlugin plugin, BalanceConfig balanceConfig) {
        this.plugin = plugin;
        this.balanceConfig = balanceConfig;
    }

    @Override
    public void enable() {
        if (!balanceConfig.resourcePackEnabled()) {
            return;
        }
        try {
            packFile = ensurePackFile();
            packSha1 = resolveSha1();
            resolvedUrl = resolveUrl();
            if (resolvedUrl.isBlank()) {
                plugin.getLogger().warning("Resource pack is enabled, but no URL is configured and auto-host could not build a public URL.");
                return;
            }
            if (balanceConfig.resourcePackAutoHostEnabled() && balanceConfig.resourcePackUrl().isBlank()) {
                startAutoHost();
            }
            plugin.getLogger().info("Resource pack ready: " + resolvedUrl + " sha1=" + ResourcePackSupport.sha1Hex(packSha1));
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyTo(player);
            }
        } catch (IOException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Resource pack setup failed: " + exception.getMessage());
        }
    }

    @Override
    public void disable() {
        stopAutoHost();
        packFile = null;
        packSha1 = new byte[0];
        resolvedUrl = "";
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!balanceConfig.resourcePackEnabled() || resolvedUrl.isBlank() || packSha1.length == 0) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> applyTo(event.getPlayer()));
    }

    void applyTo(Player player) {
        if (player == null || !player.isOnline() || resolvedUrl.isBlank() || packSha1.length == 0) {
            return;
        }
        Component prompt = Component.text(balanceConfig.resourcePackPrompt());
        player.setResourcePack(resolvedUrl, packSha1, prompt, balanceConfig.resourcePackRequired());
    }

    private Path ensurePackFile() throws IOException {
        Path directory = plugin.getDataFolder().toPath().resolve("resource-pack");
        Files.createDirectories(directory);
        Path target = directory.resolve(ResourcePackSupport.packFileName());
        if (Files.exists(target) && Files.size(target) > 0L) {
            return target;
        }
        try (InputStream input = plugin.getResource(BUNDLED_RESOURCE)) {
            if (input == null) {
                throw new IOException("Bundled resource pack is missing from plugin jar: " + BUNDLED_RESOURCE);
            }
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private byte[] resolveSha1() throws IOException {
        String configured = balanceConfig.resourcePackSha1();
        if (configured != null && !configured.isBlank()) {
            return ResourcePackSupport.parseSha1Hex(configured);
        }
        byte[] fileBytes = Files.readAllBytes(packFile);
        return ResourcePackSupport.parseSha1Hex(ResourcePackSupport.sha1Hex(fileBytes));
    }

    private String resolveUrl() {
        return ResourcePackSupport.resolvePublicUrl(
            balanceConfig.resourcePackUrl(),
            balanceConfig.resourcePackAutoHostEnabled(),
            balanceConfig.resourcePackAutoHostPublicUrl(),
            balanceConfig.resourcePackAutoHostPublicHost(),
            balanceConfig.resourcePackAutoHostPort()
        );
    }

    private void startAutoHost() throws IOException {
        stopAutoHost();
        byte[] payload = Files.readAllBytes(packFile);
        String bindHost = ResourcePackSupport.sanitizeBindHost(balanceConfig.resourcePackAutoHostBindHost());
        int port = balanceConfig.resourcePackAutoHostPort();
        httpServer = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
        httpServer.createContext("/" + ResourcePackSupport.packFileName(), exchange -> servePack(exchange, payload));
        httpServer.start();
        plugin.getLogger().info("Resource pack auto-host listening on " + bindHost + ":" + port);
    }

    private void servePack(HttpExchange exchange, byte[] payload) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        } finally {
            exchange.close();
        }
    }

    private void stopAutoHost() {
        if (httpServer == null) {
            return;
        }
        httpServer.stop(0);
        httpServer = null;
    }
}
