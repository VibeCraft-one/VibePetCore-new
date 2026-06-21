package dev.li2fox.vibepetcore.player;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PlayerStorage extends AutoCloseable {
    void enable() throws IOException;

    Optional<PlayerData> load(UUID playerId) throws IOException;

    void save(PlayerData data) throws IOException;

    List<PlayerData> topByPoints(int limit) throws IOException;

    List<UUID> playerIds() throws IOException;

    String name();

    @Override
    default void close() throws IOException {
    }
}
