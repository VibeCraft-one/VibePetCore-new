package dev.li2fox.vibepetcore.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.li2fox.vibepetcore.core.CoreModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerDataManager implements CoreModule {
    private final JavaPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, PlayerData> loadedPlayers = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final List<PlayerStorage> migrationFallbacks = new ArrayList<>();
    private PlayerStorage storage;
    private JsonPlayerStorage jsonFallback;

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        Path playerDataFolder = plugin.getDataFolder().toPath().resolve("players");
        jsonFallback = new JsonPlayerStorage(gson, playerDataFolder);
        migrationFallbacks.clear();
        migrationFallbacks.add(jsonFallback);
        storage = createStorage(playerDataFolder);
        try {
            jsonFallback.enable();
            storage.enable();
            enableMigrationFallbacks();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not enable " + storage.name() + " player storage, falling back to local storage: " + exception.getMessage());
            closeQuietly(storage, "failed player storage " + storage.name());
            try {
                storage = firstEnabledFallback();
            } catch (IOException fallbackException) {
                throw new IllegalStateException("Could not enable player data storage", fallbackException);
            }
        }
        plugin.getLogger().info("Player data storage: " + storage.name());
        migrateLocalPlayersToSqlIfConfigured(playerDataFolder);

        for (Player player : Bukkit.getOnlinePlayers()) {
            load(player.getUniqueId());
        }
    }

    @Override
    public void disable() {
        saveAll();
        Set<PlayerStorage> closed = ConcurrentHashMap.newKeySet();
        if (storage != null) {
            try {
                storage.close();
                closed.add(storage);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not close player storage: " + exception.getMessage());
            }
        }
        for (PlayerStorage fallback : migrationFallbacks) {
            if (closed.contains(fallback)) {
                continue;
            }
            closeQuietly(fallback, "player storage migration fallback " + fallback.name());
            closed.add(fallback);
        }
        loadedPlayers.clear();
        dirtyPlayers.clear();
        migrationFallbacks.clear();
    }

    public PlayerData getOrLoad(UUID playerId) {
        return loadedPlayers.computeIfAbsent(playerId, this::readOrCreate);
    }

    public Optional<PlayerData> loaded(UUID playerId) {
        return Optional.ofNullable(loadedPlayers.get(playerId));
    }

    public void load(UUID playerId) {
        getOrLoad(playerId);
    }

    public boolean save(UUID playerId) {
        PlayerData data = loadedPlayers.get(playerId);
        if (data == null || !dirtyPlayers.contains(playerId)) {
            return true;
        }

        try {
            storage.save(data);
            dirtyPlayers.remove(playerId);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save player data for " + playerId + ": " + exception.getMessage());
            return false;
        }
    }

    public void unload(UUID playerId) {
        if (!save(playerId)) {
            plugin.getLogger().warning("Keeping unsaved player data in memory for retry: " + playerId);
            return;
        }
        loadedPlayers.remove(playerId);
        dirtyPlayers.remove(playerId);
    }

    public void saveAll() {
        for (UUID playerId : List.copyOf(dirtyPlayers)) {
            save(playerId);
        }
    }

    public Collection<PlayerData> loadedPlayers() {
        return loadedPlayers.values();
    }

    public List<PlayerData> topByPoints(int limit) {
        int normalizedLimit = Math.max(1, limit);
        Map<UUID, PlayerData> candidates = new LinkedHashMap<>();
        try {
            for (PlayerData data : storage.topByPoints(normalizedLimit)) {
                if (data.playerId() != null) {
                    candidates.put(data.playerId(), data);
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not read leaderboard from " + storage.name() + ": " + exception.getMessage());
        }

        for (PlayerData data : loadedPlayers.values()) {
            if (data.playerId() != null) {
                candidates.put(data.playerId(), data);
            }
        }

        return candidates.values().stream()
            .sorted(java.util.Comparator.comparingLong(PlayerData::points).reversed())
            .limit(normalizedLimit)
            .toList();
    }

    public int loadedCount() {
        return loadedPlayers.size();
    }

    public List<UUID> knownPlayerIds() {
        Set<UUID> ids = ConcurrentHashMap.newKeySet();
        ids.addAll(loadedPlayers.keySet());
        try {
            ids.addAll(storage.playerIds());
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not read known player ids from " + storage.name() + ": " + exception.getMessage());
        }
        for (PlayerStorage fallback : migrationFallbacks) {
            if (fallback == storage) {
                continue;
            }
            try {
                ids.addAll(fallback.playerIds());
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not read known player ids from " + fallback.name() + " migration fallback: " + exception.getMessage());
            }
        }
        return List.copyOf(ids);
    }

    private PlayerData readOrCreate(UUID playerId) {
        try {
            Optional<PlayerData> data = storage.load(playerId);
            if (data.isPresent()) {
                return attachDirtyTracking(playerId, data.get());
            }
            for (PlayerStorage fallback : migrationFallbacks) {
                if (fallback == storage) {
                    continue;
                }
                Optional<PlayerData> legacy = fallback.load(playerId);
                if (legacy.isPresent()) {
                    storage.save(legacy.get());
                    plugin.getLogger().info("Migrated player data for " + playerId + " from " + fallback.name() + " to " + storage.name());
                    return attachDirtyTracking(playerId, legacy.get());
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load player data for " + playerId + ": " + exception.getMessage());
        }
        return attachDirtyTracking(playerId, new PlayerData(playerId));
    }

    private PlayerStorage createStorage(Path playerDataFolder) {
        String backend = plugin.getConfig().getString("storage.backend", "sqlite").toLowerCase(Locale.ROOT);
        if (backend.equals("json")) {
            return jsonFallback;
        }
        String file = plugin.getConfig().getString("storage.sqlite.file", "players.db");
        Path sqliteFile = plugin.getDataFolder().toPath().resolve(file);
        if (backend.equals("mysql") || backend.equals("mariadb")) {
            if (Files.exists(sqliteFile)) {
                migrationFallbacks.add(new SqlitePlayerStorage(gson, sqliteFile));
            }
            return new MysqlPlayerStorage(
                gson,
                mysqlJdbcUrl(),
                plugin.getConfig().getString("storage.mysql.username", "vibepetcore"),
                plugin.getConfig().getString("storage.mysql.password", "change_me"),
                plugin.getConfig().getString("storage.mysql.table", "vibepet_player_data")
            );
        }
        return new SqlitePlayerStorage(gson, sqliteFile);
    }

    private void migrateLocalPlayersToSqlIfConfigured(Path playerDataFolder) {
        if (!isSqlBackend() || !plugin.getConfig().getBoolean("storage.migration.migrate-all-on-enable", true)) {
            return;
        }

        boolean success = true;
        int migrated = 0;
        int skipped = 0;
        for (PlayerStorage fallback : List.copyOf(migrationFallbacks)) {
            if (fallback == storage) {
                continue;
            }
            try {
                for (UUID playerId : fallback.playerIds()) {
                    if (storage.load(playerId).isPresent()) {
                        skipped++;
                        continue;
                    }
                    Optional<PlayerData> legacy = fallback.load(playerId);
                    if (legacy.isEmpty()) {
                        continue;
                    }
                    storage.save(legacy.get());
                    migrated++;
                }
            } catch (IOException exception) {
                success = false;
                plugin.getLogger().warning("Could not migrate all player data from " + fallback.name() + " to " + storage.name() + ": " + exception.getMessage());
            }
        }

        if (migrated > 0 || skipped > 0) {
            plugin.getLogger().info("Player data SQL migration checked: migrated " + migrated + ", already present " + skipped + ".");
        }
        if (success && (migrated > 0 || skipped > 0) && plugin.getConfig().getBoolean("storage.migration.archive-local-after-success", true)) {
            archiveLocalStorageAfterSqlMigration(playerDataFolder);
        }
    }

    private void archiveLocalStorageAfterSqlMigration(Path playerDataFolder) {
        for (PlayerStorage fallback : List.copyOf(migrationFallbacks)) {
            if (fallback != storage) {
                closeQuietly(fallback, "migrated local player storage " + fallback.name());
                migrationFallbacks.remove(fallback);
            }
        }

        List<Path> localPaths = localStoragePaths(playerDataFolder).stream()
            .filter(Files::exists)
            .toList();
        if (localPaths.isEmpty()) {
            return;
        }

        String archiveFolderName = plugin.getConfig().getString("storage.migration.archive-folder", "migrated-local-storage");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path archiveFolder = plugin.getDataFolder().toPath().resolve(archiveFolderName == null || archiveFolderName.isBlank() ? "migrated-local-storage" : archiveFolderName).resolve(timestamp);
        try {
            Files.createDirectories(archiveFolder);
            for (Path path : localPaths) {
                Files.move(path, archiveFolder.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getLogger().info("Archived local player storage after SQL migration: " + archiveFolder);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not archive local player storage after SQL migration: " + exception.getMessage());
        }
    }

    private List<Path> localStoragePaths(Path playerDataFolder) {
        String file = plugin.getConfig().getString("storage.sqlite.file", "players.db");
        Path sqliteFile = plugin.getDataFolder().toPath().resolve(file);
        return List.of(
            sqliteFile,
            sqliteFile.resolveSibling(sqliteFile.getFileName() + "-wal"),
            sqliteFile.resolveSibling(sqliteFile.getFileName() + "-shm"),
            playerDataFolder
        );
    }

    private boolean isSqlBackend() {
        String backend = plugin.getConfig().getString("storage.backend", "sqlite").toLowerCase(Locale.ROOT);
        return backend.equals("mysql") || backend.equals("mariadb");
    }

    private void enableMigrationFallbacks() {
        for (PlayerStorage fallback : migrationFallbacks) {
            if (fallback == storage || fallback == jsonFallback) {
                continue;
            }
            try {
                fallback.enable();
                plugin.getLogger().info("Player data migration fallback enabled: " + fallback.name());
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not enable " + fallback.name() + " migration fallback: " + exception.getMessage());
            }
        }
    }

    private PlayerStorage firstEnabledFallback() throws IOException {
        IOException lastException = null;
        for (PlayerStorage fallback : migrationFallbacks) {
            if (fallback != jsonFallback) {
                try {
                    fallback.enable();
                    plugin.getLogger().info("Player data emergency fallback enabled: " + fallback.name());
                    return fallback;
                } catch (IOException exception) {
                    lastException = exception;
                    plugin.getLogger().warning("Could not enable " + fallback.name() + " emergency fallback: " + exception.getMessage());
                }
            }
        }
        if (jsonFallback != null) {
            jsonFallback.enable();
            plugin.getLogger().info("Player data emergency fallback enabled: " + jsonFallback.name());
            return jsonFallback;
        }
        throw lastException == null ? new IOException("No player storage fallback is available") : lastException;
    }

    private void closeQuietly(PlayerStorage playerStorage, String description) {
        if (playerStorage == null) {
            return;
        }
        try {
            playerStorage.close();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not close " + description + ": " + exception.getMessage());
        }
    }

    private String mysqlJdbcUrl() {
        String directUrl = plugin.getConfig().getString("storage.mysql.jdbc-url", "");
        if (directUrl != null && !directUrl.isBlank()) {
            return directUrl.trim();
        }
        String host = plugin.getConfig().getString("storage.mysql.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        String database = plugin.getConfig().getString("storage.mysql.database", "vibepetcore");
        boolean useSsl = plugin.getConfig().getBoolean("storage.mysql.use-ssl", false);
        boolean allowPublicKeyRetrieval = plugin.getConfig().getBoolean("storage.mysql.allow-public-key-retrieval", true);
        String parameters = plugin.getConfig().getString("storage.mysql.connection-parameters", "serverTimezone=UTC&characterEncoding=utf8");
        StringBuilder url = new StringBuilder("jdbc:mysql://")
            .append(host)
            .append(':')
            .append(port)
            .append('/')
            .append(database)
            .append("?useSSL=")
            .append(useSsl)
            .append("&allowPublicKeyRetrieval=")
            .append(allowPublicKeyRetrieval);
        if (parameters != null && !parameters.isBlank()) {
            url.append('&').append(parameters);
        }
        return url.toString();
    }

    private PlayerData attachDirtyTracking(UUID playerId, PlayerData data) {
        data.attachDirtyMarker(() -> dirtyPlayers.add(playerId));
        data.ensurePlayerId(playerId);
        return data;
    }
}
