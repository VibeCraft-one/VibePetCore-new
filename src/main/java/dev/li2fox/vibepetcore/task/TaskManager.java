package dev.li2fox.vibepetcore.task;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.CoreModule;
import dev.li2fox.vibepetcore.core.PetDebugLogger;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.pet.PetEngineManager;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class TaskManager implements CoreModule {
    private final JavaPlugin plugin;
    private final BalanceConfig balanceConfig;
    private final PlayerDataManager playerDataManager;
    private final PetEngineManager petEngineManager;
    private final PetDebugLogger debugLogger;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public TaskManager(JavaPlugin plugin, BalanceConfig balanceConfig, PlayerDataManager playerDataManager, PetEngineManager petEngineManager, PetDebugLogger debugLogger) {
        this.plugin = plugin;
        this.balanceConfig = balanceConfig;
        this.playerDataManager = playerDataManager;
        this.petEngineManager = petEngineManager;
        this.debugLogger = debugLogger;
    }

    @Override
    public void enable() {
        tasks.add(Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::runPetUpdateTick,
            balanceConfig.petUpdateIntervalTicks(),
            balanceConfig.petUpdateIntervalTicks()
        ));
        tasks.add(Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::runTriggerCheckTick,
            balanceConfig.triggerCheckIntervalTicks(),
            balanceConfig.triggerCheckIntervalTicks()
        ));
        tasks.add(Bukkit.getScheduler().runTaskTimer(
            plugin,
            () -> runGuarded("save-all", 90L, playerDataManager::saveAll),
            balanceConfig.saveIntervalTicks(),
            balanceConfig.saveIntervalTicks()
        ));
    }

    @Override
    public void disable() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    private void runPetUpdateTick() {
        runGuarded("pet-update", 45L, petEngineManager::updateAll);
    }

    private void runTriggerCheckTick() {
        runGuarded("activity-xp", 35L, petEngineManager::grantActivityXp);
    }

    private void runGuarded(String taskName, long slowThresholdMillis, Runnable action) {
        long startedAt = System.nanoTime();
        try {
            action.run();
        } catch (Throwable throwable) {
            debugLogger.errorRateLimited(
                "task:error:" + taskName,
                "task-loop",
                "Task failed: " + taskName
                    + " activePets=" + petEngineManager.activePetCount()
                    + " loadedPlayers=" + playerDataManager.loadedCount(),
                throwable,
                15_000L
            );
        }
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        if (elapsedMillis >= slowThresholdMillis) {
            debugLogger.slowRateLimited(
                "task:slow:" + taskName,
                "task-loop",
                "Task " + taskName + " took " + elapsedMillis + " ms"
                    + " activePets=" + petEngineManager.activePetCount()
                    + " loadedPlayers=" + playerDataManager.loadedCount(),
                10_000L
            );
        }
    }
}
