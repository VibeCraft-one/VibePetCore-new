package dev.li2fox.vibepetcore.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlUtf8IO {
    private YamlUtf8IO() {
    }

    public static YamlConfiguration load(File file) {
        return load(file, null, null);
    }

    public static YamlConfiguration load(File file, Logger logger, String context) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (file == null || !file.exists()) {
            return yaml;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }
            yaml.loadFromString(content);
        } catch (IOException exception) {
            warn(logger, file, context, exception);
            return new YamlConfiguration();
        } catch (Exception exception) {
            warn(logger, file, context, exception);
            return new YamlConfiguration();
        }
        return yaml;
    }

    private static void warn(Logger logger, File file, String context, Exception exception) {
        if (logger == null) {
            return;
        }
        String label = context == null || context.isBlank() ? file.getName() : context;
        logger.warning("Could not load YAML " + label + " (" + file.getPath() + "): " + exception.getMessage());
    }

    public static void save(File file, YamlConfiguration yaml) throws IOException {
        Path path = file.toPath();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
            path,
            yaml.saveToString(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }
}
