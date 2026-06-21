package dev.li2fox.vibepetcore.player;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class JsonPlayerStorage implements PlayerStorage {
    private final Gson gson;
    private final Path folder;

    JsonPlayerStorage(Gson gson, Path folder) {
        this.gson = gson;
        this.folder = folder;
    }

    @Override
    public void enable() throws IOException {
        Files.createDirectories(folder);
    }

    @Override
    public Optional<PlayerData> load(UUID playerId) throws IOException {
        Path file = fileFor(playerId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            PlayerData data = gson.fromJson(reader, PlayerData.class);
            if (data == null) {
                return Optional.empty();
            }
            data.ensurePlayerId(playerId);
            return Optional.of(data);
        }
    }

    @Override
    public void save(PlayerData data) throws IOException {
        Path file = fileFor(data.playerId());
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        }
    }

    @Override
    public List<PlayerData> topByPoints(int limit) throws IOException {
        if (!Files.exists(folder)) {
            return List.of();
        }
        try (var stream = Files.list(folder)) {
            return stream
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(this::readSafe)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingLong(PlayerData::points).reversed())
                .limit(Math.max(1, limit))
                .toList();
        }
    }

    @Override
    public List<UUID> playerIds() throws IOException {
        if (!Files.exists(folder)) {
            return List.of();
        }
        try (var stream = Files.list(folder)) {
            return stream
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> path.getFileName().toString())
                .map(fileName -> fileName.substring(0, fileName.length() - ".json".length()))
                .map(this::parseUuidSafe)
                .flatMap(Optional::stream)
                .toList();
        }
    }

    @Override
    public String name() {
        return "json";
    }

    private Optional<PlayerData> readSafe(Path path) {
        String fileName = path.getFileName().toString();
        try {
            UUID playerId = UUID.fromString(fileName.substring(0, fileName.length() - ".json".length()));
            return load(playerId);
        } catch (RuntimeException | IOException ignored) {
            return Optional.empty();
        }
    }

    private Optional<UUID> parseUuidSafe(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Path fileFor(UUID playerId) {
        return folder.resolve(playerId + ".json");
    }
}
