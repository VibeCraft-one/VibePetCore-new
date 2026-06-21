package dev.li2fox.vibepetcore.core;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;

public final class PetDebugLogger {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private final BalanceConfig config;
    private final File file;
    private final File notesFile;
    private final Map<String, Long> throttleUntil = new ConcurrentHashMap<>();

    public PetDebugLogger(JavaPlugin plugin, BalanceConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "debug.log");
        this.notesFile = new File(plugin.getDataFolder(), "runtime-notes.log");
        touch(notesFile);
    }

    public void debug(String channel, String message) {
        if (!config.debugEnabled()) {
            return;
        }
        String line = line("DEBUG", channel, message);
        plugin.getLogger().info("[debug] " + channel + ": " + message);
        if (config.debugFileEnabled()) {
            append(file, line);
        }
    }

    public void debugRateLimited(String key, String channel, String message, long cooldownMillis) {
        if (!config.debugEnabled() || !shouldLog(key, cooldownMillis)) {
            return;
        }
        debug(channel, message);
    }

    public void warn(String channel, String message) {
        logAlways("WARN", channel, message, null);
    }

    public void warnRateLimited(String key, String channel, String message, long cooldownMillis) {
        if (isDebugOnlyChannel(channel)) {
            debugRateLimited(key, channel, message, cooldownMillis);
            return;
        }
        if (!shouldLog(key, cooldownMillis)) {
            return;
        }
        warn(channel, message);
    }

    public void error(String channel, String message, Throwable throwable) {
        logAlways("ERROR", channel, message, throwable);
    }

    public void errorRateLimited(String key, String channel, String message, Throwable throwable, long cooldownMillis) {
        if (!shouldLog(key, cooldownMillis)) {
            return;
        }
        error(channel, message, throwable);
    }

    public void slowRateLimited(String key, String channel, String message, long cooldownMillis) {
        if (!shouldLog(key, cooldownMillis)) {
            return;
        }
        logAlways("SLOW", channel, message, null);
    }

    public File file() {
        return file;
    }

    public File notesFile() {
        return notesFile;
    }

    private void logAlways(String severity, String channel, String message, Throwable throwable) {
        String line = line(severity, channel, message);
        switch (severity) {
            case "ERROR" -> plugin.getLogger().severe("[" + channel + "] " + message);
            case "WARN", "SLOW" -> plugin.getLogger().warning("[" + channel + "] " + message);
            default -> plugin.getLogger().info("[" + channel + "] " + message);
        }
        append(notesFile, line);
        if (config.debugEnabled() && config.debugFileEnabled()) {
            append(file, line);
        }
        if (throwable != null) {
            String stackTrace = stackTrace(throwable);
            append(notesFile, stackTrace);
            if (config.debugEnabled() && config.debugFileEnabled()) {
                append(file, stackTrace);
            }
        }
    }

    private boolean shouldLog(String key, long cooldownMillis) {
        long now = System.currentTimeMillis();
        long nextAllowed = throttleUntil.getOrDefault(key, 0L);
        if (nextAllowed > now) {
            return false;
        }
        throttleUntil.put(key, now + Math.max(0L, cooldownMillis));
        return true;
    }

    private boolean isDebugOnlyChannel(String channel) {
        return "pet-combat".equals(channel);
    }

    private String line(String severity, String channel, String message) {
        return "[" + LocalDateTime.now().format(FORMAT) + "] [" + severity + "] [" + channel + "] " + message;
    }

    private void append(File target, String content) {
        try {
            File parent = target.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            String line = content.endsWith(System.lineSeparator()) ? content : content + System.lineSeparator();
            Files.writeString(
                target.toPath(),
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not write " + target.getName() + ": " + exception.getMessage());
        }
    }

    private String stackTrace(Throwable throwable) {
        StringWriter buffer = new StringWriter();
        try (PrintWriter writer = new PrintWriter(buffer)) {
            throwable.printStackTrace(writer);
        }
        return buffer.toString();
    }

    private void touch(File target) {
        try {
            File parent = target.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            if (!target.exists()) {
                Files.writeString(
                    target.toPath(),
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                );
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create " + target.getName() + ": " + exception.getMessage());
        }
    }
}
