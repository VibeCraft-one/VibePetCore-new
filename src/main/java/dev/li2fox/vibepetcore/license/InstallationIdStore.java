package dev.li2fox.vibepetcore.license;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class InstallationIdStore {
    private final Path storageFile;

    public InstallationIdStore(Path dataFolder) {
        this.storageFile = dataFolder.resolve("installation.id");
    }

    public String getOrCreate() throws IOException {
        if (Files.exists(storageFile)) {
            String existing = Files.readString(storageFile, StandardCharsets.UTF_8).trim();
            if (!existing.isBlank()) {
                return existing;
            }
        }

        String generated = UUID.randomUUID().toString();
        Files.createDirectories(storageFile.getParent());
        Files.writeString(storageFile, generated, StandardCharsets.UTF_8);
        return generated;
    }
}
