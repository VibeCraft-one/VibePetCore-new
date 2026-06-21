package dev.li2fox.vibepetcore.player;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class SqlitePlayerStorage implements PlayerStorage {
    private final Gson gson;
    private final Path databaseFile;
    private Connection connection;

    SqlitePlayerStorage(Gson gson, Path databaseFile) {
        this.gson = gson;
        this.databaseFile = databaseFile;
    }

    @Override
    public synchronized void enable() throws IOException {
        try {
            Files.createDirectories(databaseFile.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            configure();
            migrate();
        } catch (SQLException exception) {
            throw new IOException("Could not open sqlite database: " + databaseFile, exception);
        }
    }

    @Override
    public synchronized Optional<PlayerData> load(UUID playerId) throws IOException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT json FROM player_data WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                PlayerData data = gson.fromJson(result.getString("json"), PlayerData.class);
                if (data == null) {
                    return Optional.empty();
                }
                data.ensurePlayerId(playerId);
                return Optional.of(data);
            }
        } catch (SQLException exception) {
            throw new IOException("Could not load player data for " + playerId, exception);
        }
    }

    @Override
    public synchronized void save(PlayerData data) throws IOException {
        String sql = """
            INSERT INTO player_data(player_uuid, json, points, pets, quests_completed, activity_ticks, updated_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                json = excluded.json,
                points = excluded.points,
                pets = excluded.pets,
                quests_completed = excluded.quests_completed,
                activity_ticks = excluded.activity_ticks,
                updated_at = excluded.updated_at
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, data.playerId().toString());
            statement.setString(2, gson.toJson(data));
            statement.setLong(3, data.points());
            statement.setInt(4, data.pets().size());
            statement.setLong(5, data.statistics().questsCompleted());
            statement.setLong(6, data.statistics().activityTicks());
            statement.setLong(7, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IOException("Could not save player data for " + data.playerId(), exception);
        }
    }

    @Override
    public synchronized List<PlayerData> topByPoints(int limit) throws IOException {
        String sql = "SELECT player_uuid, json FROM player_data ORDER BY points DESC, quests_completed DESC, activity_ticks DESC LIMIT ?";
        List<PlayerData> players = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    UUID playerId = UUID.fromString(result.getString("player_uuid"));
                    PlayerData data = gson.fromJson(result.getString("json"), PlayerData.class);
                    if (data != null) {
                        data.ensurePlayerId(playerId);
                        players.add(data);
                    }
                }
            }
            return players;
        } catch (SQLException exception) {
            throw new IOException("Could not query sqlite leaderboard", exception);
        }
    }

    @Override
    public synchronized List<UUID> playerIds() throws IOException {
        String sql = "SELECT player_uuid FROM player_data";
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                ids.add(UUID.fromString(result.getString("player_uuid")));
            }
            return ids;
        } catch (SQLException exception) {
            throw new IOException("Could not query player ids", exception);
        }
    }

    @Override
    public String name() {
        return "sqlite";
    }

    @Override
    public synchronized void close() throws IOException {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IOException("Could not close sqlite database", exception);
        }
    }

    private void configure() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
        }
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS player_data(
                    player_uuid TEXT PRIMARY KEY,
                    json TEXT NOT NULL,
                    points INTEGER NOT NULL DEFAULT 0,
                    pets INTEGER NOT NULL DEFAULT 0,
                    quests_completed INTEGER NOT NULL DEFAULT 0,
                    activity_ticks INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_data_points ON player_data(points DESC, quests_completed DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_data_activity ON player_data(activity_ticks DESC)");
        }
    }
}
