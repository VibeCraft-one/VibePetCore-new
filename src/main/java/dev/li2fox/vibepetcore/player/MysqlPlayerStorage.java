package dev.li2fox.vibepetcore.player;

import com.google.gson.Gson;
import java.io.IOException;
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

final class MysqlPlayerStorage implements PlayerStorage {
    private static final String DEFAULT_TABLE = "vibepet_player_data";

    private final Gson gson;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String table;
    private Connection connection;

    MysqlPlayerStorage(Gson gson, String jdbcUrl, String username, String password, String table) {
        this.gson = gson;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.table = sanitizeTableName(table);
    }

    @Override
    public synchronized void enable() throws IOException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            ensureConnection();
            migrate();
        } catch (ClassNotFoundException exception) {
            throw new IOException("MySQL driver is not available", exception);
        } catch (SQLException exception) {
            throw new IOException("Could not open mysql database: " + jdbcUrl, exception);
        }
    }

    @Override
    public synchronized Optional<PlayerData> load(UUID playerId) throws IOException {
        String sql = "SELECT json FROM " + table + " WHERE player_uuid = ?";
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
            }
        } catch (SQLException exception) {
            throw new IOException("Could not load player data for " + playerId, exception);
        }
    }

    @Override
    public synchronized void save(PlayerData data) throws IOException {
        String sql = """
            INSERT INTO %s(player_uuid, json, points, pets, quests_completed, activity_ticks, updated_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                json = VALUES(json),
                points = VALUES(points),
                pets = VALUES(pets),
                quests_completed = VALUES(quests_completed),
                activity_ticks = VALUES(activity_ticks),
                updated_at = VALUES(updated_at)
            """.formatted(table);
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, data.playerId().toString());
                statement.setString(2, gson.toJson(data));
                statement.setLong(3, data.points());
                statement.setInt(4, data.pets().size());
                statement.setLong(5, data.statistics().questsCompleted());
                statement.setLong(6, data.statistics().activityTicks());
                statement.setLong(7, System.currentTimeMillis());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IOException("Could not save player data for " + data.playerId(), exception);
        }
    }

    @Override
    public synchronized List<PlayerData> topByPoints(int limit) throws IOException {
        String sql = "SELECT player_uuid, json FROM " + table + " ORDER BY points DESC, quests_completed DESC, activity_ticks DESC LIMIT ?";
        List<PlayerData> players = new ArrayList<>();
        try {
            ensureConnection();
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
            }
            return players;
        } catch (SQLException exception) {
            throw new IOException("Could not query mysql leaderboard", exception);
        }
    }

    @Override
    public synchronized List<UUID> playerIds() throws IOException {
        String sql = "SELECT player_uuid FROM " + table;
        List<UUID> ids = new ArrayList<>();
        try {
            ensureConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ids.add(UUID.fromString(result.getString("player_uuid")));
                }
            }
            return ids;
        } catch (SQLException exception) {
            throw new IOException("Could not query player ids", exception);
        }
    }

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public synchronized void close() throws IOException {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new IOException("Could not close mysql database", exception);
        }
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS %s(
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    json LONGTEXT NOT NULL,
                    points BIGINT NOT NULL DEFAULT 0,
                    pets INT NOT NULL DEFAULT 0,
                    quests_completed BIGINT NOT NULL DEFAULT 0,
                    activity_ticks BIGINT NOT NULL DEFAULT 0,
                    updated_at BIGINT NOT NULL DEFAULT 0
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """.formatted(table));
            createIndexIfMissing(statement, indexName("points"), "(points, quests_completed)");
            createIndexIfMissing(statement, indexName("activity"), "(activity_ticks)");
        }
    }

    private void createIndexIfMissing(Statement statement, String indexName, String columns) throws SQLException {
        try {
            statement.execute("CREATE INDEX " + indexName + " ON " + table + columns);
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 1061) {
                return;
            }
            throw exception;
        }
    }

    private void ensureConnection() throws SQLException {
        if (connection != null && !connection.isClosed() && connection.isValid(2)) {
            return;
        }
        connection = DriverManager.getConnection(jdbcUrl, username, password);
    }

    private static String sanitizeTableName(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_TABLE;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48);
        }
        return trimmed.matches("[A-Za-z0-9_]+") ? trimmed : DEFAULT_TABLE;
    }

    private String indexName(String suffix) {
        String name = "idx_" + table + "_" + suffix;
        if (name.length() <= 64) {
            return name;
        }
        return name.substring(0, 64);
    }
}
