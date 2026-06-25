package dev.li2fox.vibepetcore.core;

import dev.li2fox.vibepetcore.api.EconomyAPI;
import dev.li2fox.vibepetcore.api.PetAPI;
import dev.li2fox.vibepetcore.api.ProgressionAPI;
import dev.li2fox.vibepetcore.api.impl.CoreEconomyAPI;
import dev.li2fox.vibepetcore.api.impl.CorePetAPI;
import dev.li2fox.vibepetcore.api.impl.CoreProgressionAPI;
import dev.li2fox.vibepetcore.box.BoxManager;
import dev.li2fox.vibepetcore.box.LootBoxManager;
import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.egg.PetEggController;
import dev.li2fox.vibepetcore.egg.PetEggService;
import dev.li2fox.vibepetcore.economy.EconomyManager;
import dev.li2fox.vibepetcore.economy.EconomyQuestListener;
import dev.li2fox.vibepetcore.gui.PetGuiService;
import dev.li2fox.vibepetcore.master.PetMasterManager;
import dev.li2fox.vibepetcore.player.PlayerDataListener;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.pet.PetEngineListener;
import dev.li2fox.vibepetcore.pet.PetEngineManager;
import dev.li2fox.vibepetcore.pet.PetSpawnGuardListener;
import dev.li2fox.vibepetcore.pet.PetType;
import dev.li2fox.vibepetcore.pet.armor.PetArmorService;
import dev.li2fox.vibepetcore.pet.inventory.PetVaultService;
import dev.li2fox.vibepetcore.quest.QuestManager;
import dev.li2fox.vibepetcore.task.TaskManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class VibePetCorePlugin extends JavaPlugin {
    private ModuleManager moduleManager;
    private BalanceConfig balanceConfig;
    private PlayerDataManager playerDataManager;
    private PetEngineManager petEngineManager;
    private EconomyManager economyManager;
    private QuestManager questManager;
    private BoxManager boxManager;
    private LootBoxManager lootBoxManager;
    private PetEggService petEggService;
    private PetEggController petEggController;
    private PetArmorService petArmorService;
    private PetVaultService petVaultService;
    private PetGuiService petGuiService;
    private PetMasterManager petMasterManager;
    private TaskManager taskManager;
    private PetDebugLogger debugLogger;
    private VibePetCommandHandler commandHandler;
    private PetAPI petAPI;
    private ProgressionAPI progressionAPI;
    private EconomyAPI economyAPI;

    @Override
    public void onEnable() {
        ensureVersionedConfig();
        saveBundledConfigs();

        balanceConfig = new BalanceConfig(this);
        GameText.bind(balanceConfig);
        playerDataManager = new PlayerDataManager(this);
        petArmorService = new PetArmorService(this);
        petVaultService = new PetVaultService(this, balanceConfig, petArmorService);
        debugLogger = new PetDebugLogger(this, balanceConfig);
        petEngineManager = new PetEngineManager(playerDataManager, balanceConfig, petVaultService, petArmorService, debugLogger);
        economyManager = new EconomyManager(playerDataManager, balanceConfig);
        questManager = new QuestManager(balanceConfig, playerDataManager, economyManager);
        petEngineManager.setQuestManager(questManager);
        petEggService = new PetEggService(this, balanceConfig);
        petEngineManager.setPetEggService(petEggService);
        boxManager = new BoxManager(balanceConfig, economyManager, petEggService);
        petMasterManager = new PetMasterManager(this, debugLogger);
        petEngineManager.setPetMasterManager(petMasterManager);
        lootBoxManager = new LootBoxManager(this, boxManager, petMasterManager, debugLogger);
        petGuiService = new PetGuiService(playerDataManager, petEngineManager, questManager, boxManager, lootBoxManager, petMasterManager, petEggService, petArmorService, balanceConfig, debugLogger);
        petMasterManager.setGuiService(petGuiService);
        petEggController = new PetEggController(this, balanceConfig, petEngineManager, petEggService, debugLogger);
        taskManager = new TaskManager(this, balanceConfig, playerDataManager, petEngineManager, debugLogger);
        petAPI = new CorePetAPI(playerDataManager, petEngineManager);
        progressionAPI = new CoreProgressionAPI(playerDataManager, balanceConfig);
        petEngineManager.setProgressionAPI(progressionAPI);
        questManager.setProgressionAPI(progressionAPI);
        economyAPI = new CoreEconomyAPI(economyManager);

        moduleManager = new ModuleManager(debugLogger);
        moduleManager.register(balanceConfig);
        moduleManager.register(playerDataManager);
        moduleManager.register(economyManager);
        moduleManager.register(questManager);
        moduleManager.register(petArmorService);
        moduleManager.register(petEngineManager);
        moduleManager.register(lootBoxManager);
        moduleManager.register(petEggController);
        moduleManager.register(petMasterManager);
        moduleManager.register(taskManager);
        moduleManager.enableAll();

        registerListeners();
        registerCommands();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new VibePetPlaceholderExpansion(this, playerDataManager, petEngineManager).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        getLogger().info("VibePetCore enabled.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerDataListener(playerDataManager), this);
        getServer().getPluginManager().registerEvents(new PetEngineListener(petEngineManager, debugLogger), this);
        getServer().getPluginManager().registerEvents(new PetSpawnGuardListener(), this);
        getServer().getPluginManager().registerEvents(petArmorService, this);
        getServer().getPluginManager().registerEvents(new EconomyQuestListener(balanceConfig, playerDataManager, economyManager, questManager, petEggService), this);
        getServer().getPluginManager().registerEvents(petEggController, this);
        getServer().getPluginManager().registerEvents(petVaultService, this);
        getServer().getPluginManager().registerEvents(lootBoxManager, this);
        getServer().getPluginManager().registerEvents(petGuiService, this);
        getServer().getPluginManager().registerEvents(petMasterManager, this);
    }

    private void registerCommands() {
        commandHandler = new VibePetCommandHandler(
            this,
            moduleManager,
            balanceConfig,
            playerDataManager,
            petEngineManager,
            questManager,
            lootBoxManager,
            petEggService,
            petGuiService,
            petMasterManager,
            debugLogger,
            petAPI,
            economyAPI
        );

        var tabCompleter = new VibePetCoreTabCompleter();
        var vibeCommand = Objects.requireNonNull(getCommand("vibepetcore"));
        vibeCommand.setExecutor(commandHandler);
        vibeCommand.setTabCompleter(tabCompleter);
        var petCommand = Objects.requireNonNull(getCommand("pet"));
        petCommand.setExecutor(commandHandler);
        petCommand.setTabCompleter(tabCompleter);
    }

    private void saveBundledConfigs() {
        List<String> fixedResources = List.of(
            "messages/ru.yml",
            "messages/en.yml",
            "quests.yml",
            "pet-master.yml"
        );
        fixedResources.forEach(this::saveBundledResourceIfMissing);

        for (PetType type : PetType.values()) {
            saveBundledResourceIfMissing("pets/" + type.name().toLowerCase(java.util.Locale.ROOT) + ".yml");
        }
    }

    private void saveBundledResourceIfMissing(String resource) {
        if (getResource(resource) == null) {
            return;
        }
        if (!getDataFolder().toPath().resolve(resource).toFile().exists()) {
            saveResource(resource, false);
        }
    }

    private void ensureVersionedConfig() {
        Path configPath = getDataFolder().toPath().resolve("config.yml");
        if (!Files.exists(configPath)) {
            saveDefaultConfig();
            reloadConfig();
            return;
        }

        saveDefaultConfig();
        reloadConfig();
        String expectedVersion = getPluginMeta().getVersion();
        String currentVersion = getConfig().getString("config-version", "");
        if (expectedVersion.equals(currentVersion)) {
            return;
        }

        try {
            Path backupFolder = getDataFolder().toPath().resolve("config-backups");
            Files.createDirectories(backupFolder);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String fromVersion = sanitizeBackupPart(currentVersion == null || currentVersion.isBlank() ? "unknown" : currentVersion);
            String toVersion = sanitizeBackupPart(expectedVersion);
            Path backupPath = backupFolder.resolve("config-" + fromVersion + "-to-" + toVersion + "-" + timestamp + ".yml");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            saveResource("config.yml", true);
            reloadConfig();
            getLogger().warning("Config version mismatch. Old config was backed up to " + backupPath.getFileName() + "; bundled config.yml was installed.");
        } catch (IllegalArgumentException | IOException exception) {
            getLogger().warning("Could not refresh versioned config.yml: " + exception.getMessage());
        }
    }

    private String sanitizeBackupPart(String value) {
        String normalized = value == null ? "unknown" : value.toLowerCase(Locale.ROOT).trim();
        if (normalized.isBlank()) {
            return "unknown";
        }
        return normalized.replaceAll("[^a-z0-9._-]", "_");
    }

    @Override
    public void onDisable() {
        if (commandHandler != null) {
            commandHandler.cancelPendingDangerDeletes();
        }
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        getLogger().info("VibePetCore disabled.");
    }

    public PetAPI petAPI() {
        return petAPI;
    }

    public ProgressionAPI progressionAPI() {
        return progressionAPI;
    }

    public EconomyAPI economyAPI() {
        return economyAPI;
    }

    public BalanceConfig balanceConfig() {
        return balanceConfig;
    }
}
