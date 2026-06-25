package dev.li2fox.vibepetcore.config;

import dev.li2fox.vibepetcore.core.CoreModule;
import dev.li2fox.vibepetcore.core.YamlUtf8IO;
import dev.li2fox.vibepetcore.pet.PetType;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class BalanceConfig implements CoreModule {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private YamlConfiguration questsConfig;
    private final Map<String, YamlConfiguration> petConfigs = new HashMap<>();
    private YamlConfiguration englishMessages;
    private YamlConfiguration russianMessages;

    public BalanceConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        mergeMissingConfigDefaults();
        config = plugin.getConfig();
        loadMessages();
        loadPetConfigs();
        loadQuestConfig();
    }

    @Override
    public void disable() {
    }

    @Override
    public void reload() {
        plugin.reloadConfig();
        mergeMissingConfigDefaults();
        config = plugin.getConfig();
        loadMessages();
        loadPetConfigs();
        loadQuestConfig();
    }

    private void mergeMissingConfigDefaults() {
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        plugin.reloadConfig();
    }

    public String message(String key, String fallback, Object... replacements) {
        YamlConfiguration active = activeMessages();
        String raw = active.getString(key);
        if (raw == null || raw.isBlank()) {
            raw = englishMessages == null ? null : englishMessages.getString(key);
        }
        if (raw == null || raw.isBlank()) {
            raw = fallback == null || fallback.isBlank() ? key : fallback;
        }
        String resolved = raw;
        if (replacements != null) {
            for (int index = 0; index + 1 < replacements.length; index += 2) {
                String token = String.valueOf(replacements[index]);
                String value = String.valueOf(replacements[index + 1]);
                resolved = resolved.replace("{" + token + "}", value);
            }
        }
        return translateLegacyColors(repairLegacyMojibake(resolved));
    }

    public String message(String key, String fallback) {
        return message(key, fallback, new Object[0]);
    }

    private void loadMessages() {
        englishMessages = loadMessageFile("messages/en.yml", false);
        russianMessages = loadMessageFile("messages/ru.yml", true);
    }

    private String translateLegacyColors(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '&' && index + 1 < text.length() && isLegacyColorCode(text.charAt(index + 1))) {
                out.append('\u00A7');
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private String repairLegacyMojibake(String text) {
        if (text == null || text.isEmpty() || mojibakeScore(text) <= 0) {
            return text;
        }
        try {
            String repaired = new String(text.getBytes(java.nio.charset.Charset.forName("windows-1251")), StandardCharsets.UTF_8);
            return mojibakeScore(repaired) < mojibakeScore(text) && cyrillicScore(repaired) >= cyrillicScore(text)
                ? repaired
                : text;
        } catch (Exception exception) {
            return text;
        }
    }

    private int mojibakeScore(String text) {
        int score = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '\uFFFD' || ch == '\u00C2' || ch == '\u00D0' || ch == '\u00D1' || ch == '\u201A'
                || ch == '\u0453' || ch == '\u201E' || ch == '\u2026' || ch == '\u2020' || ch == '\u2021'
                || ch == '\u2030' || ch == '\u2039' || ch == '\u0409' || ch == '\u040A' || ch == '\u040C'
                || ch == '\u040B' || ch == '\u040F' || ch == '\u2122') {
                score += 2;
                continue;
            }
            if (ch >= '\u0080' && ch <= '\u009F') {
                score += 3;
            }
        }
        return score;
    }

    private int cyrillicScore(String text) {
        int score = 0;
        for (int index = 0; index < text.length(); index++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(index));
            if (block == Character.UnicodeBlock.CYRILLIC) {
                score++;
            }
        }
        return score;
    }

    private boolean isLegacyColorCode(char code) {
        char normalized = Character.toLowerCase(code);
        return (normalized >= '0' && normalized <= '9')
            || (normalized >= 'a' && normalized <= 'f')
            || (normalized >= 'k' && normalized <= 'o')
            || normalized == 'r'
            || normalized == 'x';
    }

    private YamlConfiguration loadMessageFile(String relativePath, boolean protectRussianFromEnglishOverrides) {
        File file = new File(plugin.getDataFolder(), relativePath);
        YamlConfiguration bundled = loadBundledMessage(relativePath);
        YamlConfiguration loaded = YamlUtf8IO.load(file, plugin.getLogger(), relativePath);
        if (loaded.getKeys(false).isEmpty()) {
            if (file.exists()) {
                plugin.getLogger().warning("Message file " + relativePath + " is unreadable or empty, falling back to bundled copy.");
            }
            return bundled;
        }
        mergeYaml(bundled, loaded, protectRussianFromEnglishOverrides ? englishMessages : null);
        saveMergedMessageFile(relativePath, file, bundled);
        return bundled;
    }

    private YamlConfiguration loadBundledMessage(String resourcePath) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return new YamlConfiguration();
            }
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(content);
            return yaml;
        } catch (Exception exception) {
            return new YamlConfiguration();
        }
    }

    private YamlConfiguration activeMessages() {
        YamlConfiguration selected = useRussianLanguage() ? russianMessages : englishMessages;
        return selected == null ? new YamlConfiguration() : selected;
    }

    private void mergeYaml(YamlConfiguration target, YamlConfiguration override, YamlConfiguration englishReference) {
        if (target == null || override == null) {
            return;
        }
        override.getValues(true).forEach((key, value) -> {
            if (isDeprecatedMessageKey(key)) {
                return;
            }
            if (englishReference != null && value instanceof String overrideText) {
                String englishText = englishReference.getString(key);
                if (englishText != null && englishText.equals(overrideText)) {
                    return;
                }
            }
            target.set(key, value);
        });
    }

    private boolean isDeprecatedMessageKey(String key) {
        return key != null && key.startsWith("master.");
    }

    private void saveMergedMessageFile(String relativePath, File file, YamlConfiguration messages) {
        try {
            YamlUtf8IO.save(file, messages);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not refresh message file " + relativePath + ": " + exception.getMessage());
        }
    }

    public double rarityChance(String rarity) {
        return config.getDouble("rarity.chances." + rarity, 0.0D);
    }

    public double defaultPetDamage() {
        return config.getDouble("pets.defaults.damage", 2.0D);
    }

    public double defaultPetSpeed() {
        return config.getDouble("pets.defaults.speed", 0.24D);
    }

    public double defaultPetRadius() {
        return config.getDouble("pets.defaults.radius", 8.0D);
    }

    public int baseXp() {
        return config.getInt("progression.base-xp", 100);
    }

    public double xpMultiplier() {
        return config.getDouble("progression.xp-multiplier", 1.35D);
    }

    public int maxLevel() {
        return config.getInt("progression.max-level", 10);
    }

    public int maxSubLevel() {
        return config.getInt("progression.max-sub-level", 10);
    }

    public String language() {
        String language = config.getString("language", "ru");
        String normalized = language.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "ru", "rus", "russian" -> "ru";
            case "en", "eng", "english" -> "en";
            default -> "ru";
        };
    }

    public boolean useRussianLanguage() {
        return "ru".equals(language());
    }

    public boolean resourcePackEnabled() {
        return config.getBoolean("resource-pack.enabled", false);
    }

    public String resourcePackUrl() {
        return config.getString("resource-pack.url", "");
    }

    public String resourcePackSha1() {
        return config.getString("resource-pack.sha1", "");
    }

    public boolean resourcePackRequired() {
        return config.getBoolean("resource-pack.required", false);
    }

    public String resourcePackPrompt() {
        return config.getString("resource-pack.prompt", "VibePetCore resource pack is required for pet egg miniatures.");
    }

    public boolean resourcePackAutoHostEnabled() {
        return config.getBoolean("resource-pack.auto-host.enabled", true);
    }

    public String resourcePackAutoHostBindHost() {
        return config.getString("resource-pack.auto-host.bind-host", "0.0.0.0");
    }

    public int resourcePackAutoHostPort() {
        return Math.max(1, Math.min(65535, config.getInt("resource-pack.auto-host.port", 25512)));
    }

    public String resourcePackAutoHostPublicHost() {
        return config.getString("resource-pack.auto-host.public-host", "");
    }

    public String resourcePackAutoHostPublicUrl() {
        return config.getString("resource-pack.auto-host.public-url", "");
    }

    public boolean eggOffhandActivation() {
        return config.getBoolean("egg-core.offhand-activation", true);
    }

    public boolean eggEnchantedGlint() {
        return config.getBoolean("egg-core.enchanted-glint", true);
    }

    public int eggCustomModelData(PetType type, boolean activeButton) {
        String group = activeButton ? "active-button" : "egg";
        return eggCustomModelData(type, group);
    }

    public int eggCustomModelData(PetType type, String group) {
        String typeKey = type.name().toLowerCase(java.util.Locale.ROOT);
        int fallback = Math.max(0, config.getInt("egg-core.custom-model-data." + group + ".default", 0));
        return Math.max(0, config.getInt("egg-core.custom-model-data." + group + "." + typeKey, fallback));
    }

    public int eggPurgeEpoch() {
        return Math.max(0, config.getInt("egg-core.purge-epoch", 0));
    }

    public Material activeButtonMaterial() {
        Material material = Material.matchMaterial(config.getString("egg-core.active-button-material", "WARPED_BUTTON"));
        return material == null ? Material.WARPED_BUTTON : material;
    }

    public int eggMaxDurability() {
        return config.getInt("egg-core.max-durability", 7);
    }

    public int eggMaxSatiety() {
        return config.getInt("egg-core.max-satiety", 5);
    }

    public int eggRarityUpgradeCost() {
        return Math.max(1, config.getInt("egg-core.rarity-upgrade.required-sacrifices", 2));
    }

    public double eggRarityUpgradeChance(String rarity) {
        return config.getDouble("egg-core.rarity-upgrade.chances." + rarity.toLowerCase(), 0.0D);
    }

    public int deathPenaltyMinutes(int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return config.getInt("egg-core.death-penalty-minutes." + safeEvolution, 3);
    }

    public double equippedKeepChance(int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return config.getDouble("egg-core.equipped-keep-chance." + safeEvolution, 0.75D);
    }

    public double deathDurabilityLossChance(int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return config.getDouble("egg-core.death-durability-loss-chance." + safeEvolution, 0.10D);
    }

    public long activityXp() {
        return config.getLong("progression.activity-xp", 1L);
    }

    public long activityXpCooldownMillis() {
        return Math.max(0L, config.getLong("progression.activity-xp-cooldown-seconds", 120L)) * 1_000L;
    }

    public long combatXp() {
        return config.getLong("progression.combat-xp", 3L);
    }

    public long nearbyTimeXp() {
        return config.getLong("progression.nearby-time-xp", 1L);
    }

    public long nearbyTimeXpCooldownMillis() {
        return Math.max(0L, config.getLong("progression.nearby-time-xp-cooldown-seconds", 300L)) * 1_000L;
    }

    public long trainingXp() {
        return Math.max(0L, config.getLong("progression.training-xp", 2L));
    }

    public long trainingCooldownMillis() {
        return Math.max(0L, config.getLong("progression.training-cooldown-seconds", 180L)) * 1_000L;
    }

    public double trainingMaxDistance() {
        return Math.max(1.0D, config.getDouble("progression.training-max-distance", 6.0D));
    }

    public double questEvolutionPetXpPercent() {
        return Math.max(0.0D, config.getDouble("progression.quest.evolution-pet-xp-percent", 3.0D));
    }

    public double satietyPassiveDrainAmount() {
        return Math.max(0.0D, config.getDouble("progression.satiety.passive-drain-amount", 0.04D));
    }

    public long satietyPassiveDrainCooldownMillis() {
        return Math.max(0L, config.getLong("progression.satiety.passive-drain-cooldown-seconds", 180L)) * 1_000L;
    }

    public double satietyXpMultiplier(double satiety) {
        if (satiety <= 1.0D) {
            return Math.max(0.0D, config.getDouble("progression.satiety.xp-multiplier.starving", 0.0D));
        }
        if (satiety <= 2.0D) {
            return Math.max(0.0D, config.getDouble("progression.satiety.xp-multiplier.hungry", 0.5D));
        }
        if (satiety <= 3.0D) {
            return Math.max(0.0D, config.getDouble("progression.satiety.xp-multiplier.peckish", 0.85D));
        }
        return 1.0D;
    }

    public int bondMax() {
        return Math.max(1, config.getInt("progression.bond.max", 10));
    }

    public int bondNearbyGain() {
        return Math.max(0, config.getInt("progression.bond.nearby-gain", 1));
    }

    public long bondNearbyCooldownMillis() {
        return Math.max(0L, config.getLong("progression.bond.nearby-cooldown-seconds", 600L)) * 1_000L;
    }

    public int bondFoodGain() {
        return Math.max(0, config.getInt("progression.bond.food-gain", 1));
    }

    public long bondFoodCooldownMillis() {
        return Math.max(0L, config.getLong("progression.bond.food-cooldown-seconds", 300L)) * 1_000L;
    }

    public int bondRareFoodGain() {
        return Math.max(0, config.getInt("progression.bond.rare-food-gain", 1));
    }

    public long bondRareFoodCooldownMillis() {
        return Math.max(0L, config.getLong("progression.bond.rare-food-cooldown-seconds", 600L)) * 1_000L;
    }

    public int bondCombatGain() {
        return Math.max(0, config.getInt("progression.bond.combat-gain", 1));
    }

    public long bondCombatCooldownMillis() {
        return Math.max(0L, config.getLong("progression.bond.combat-cooldown-seconds", 180L)) * 1_000L;
    }

    public int bondKillGain() {
        return Math.max(0, config.getInt("progression.bond.kill-gain", 1));
    }

    public long bondKillCooldownMillis() {
        return Math.max(0L, config.getLong("progression.bond.kill-cooldown-seconds", 300L)) * 1_000L;
    }

    public int bondDeathLoss() {
        return Math.max(0, config.getInt("progression.bond.death-loss", 1));
    }

    public double rarityXpMultiplier(String rarity) {
        return config.getDouble("progression.rarity-xp-multiplier." + rarity.toLowerCase(), 1.0D);
    }

    public int evolutionRequiredLevel() {
        return config.getInt("progression.evolution.required-level", 5);
    }

    public int evolutionRequiredLevel(int nextStage) {
        int safeStage = safeEvolutionStage(nextStage);
        return Math.max(1, Math.min(maxLevel(), evolutionStageInt(safeStage, "required-level", defaultEvolutionRequiredLevel(safeStage))));
    }

    public int evolutionRequiredSubLevel() {
        return config.getInt("progression.evolution.required-sub-level", 10);
    }

    public int evolutionRequiredBond(int nextStage) {
        int safeStage = safeEvolutionStage(nextStage);
        return Math.max(0, Math.min(10, evolutionStageInt(safeStage, "required-bond", defaultEvolutionRequiredBond(safeStage))));
    }

    public int evolutionRequiredQuests(int nextStage) {
        int safeStage = safeEvolutionStage(nextStage);
        return Math.max(0, evolutionStageInt(safeStage, "required-quests", defaultEvolutionRequiredQuests(safeStage)));
    }

    public double evolutionChance(String rarity) {
        return config.getDouble("progression.evolution.chance." + rarity.toLowerCase(), 0.0D);
    }

    public long feedingXpCooldownSeconds() {
        return Math.max(0L, config.getLong("progression.feeding.xp-cooldown-seconds", 300L));
    }

    public int feedingXpFeedsPerReward() {
        return Math.max(1, config.getInt("progression.feeding.xp-feeds-per-reward", 5));
    }

    public double feedingXpRewardPercent() {
        return Math.max(0.0D, config.getDouble("progression.feeding.xp-reward-percent", 1.0D));
    }

    public int growthBoostDurationTicks() {
        return config.getInt("progression.feeding.growth-boost-duration-ticks", 1200);
    }

    public double growthBoostMultiplier() {
        return config.getDouble("progression.feeding.growth-boost-multiplier", 1.5D);
    }

    public boolean isCommonFood(Material material) {
        return materialList("progression.feeding.common-foods").contains(material);
    }

    public boolean isRareResource(Material material) {
        return materialList("progression.feeding.rare-resources").contains(material);
    }

    public boolean isEvolutionItem(Material material) {
        return materialList("progression.feeding.evolution-items").contains(material);
    }

    public List<Material> evolutionItems() {
        return materialList("progression.feeding.evolution-items");
    }

    public boolean isPetFood(PetType type, Material material) {
        return isPetHealFood(type, material) || isPetSatietyFood(type, material);
    }

    public boolean hasConfiguredPetFood(PetType type) {
        return !materialList(petConfig(type), "food.heal").isEmpty()
            || !materialList(petConfig(type), "food.satiety").isEmpty();
    }

    public boolean isPetHealFood(PetType type, Material material) {
        List<Material> configured = materialList(petConfig(type), "food.heal");
        return configured.isEmpty() ? isCommonFood(material) : configured.contains(material);
    }

    public boolean isPetSatietyFood(PetType type, Material material) {
        List<Material> configured = materialList(petConfig(type), "food.satiety");
        return configured.isEmpty() ? isCommonFood(material) : configured.contains(material);
    }

    public List<Material> petFoodMaterials(PetType type) {
        java.util.LinkedHashSet<Material> materials = new java.util.LinkedHashSet<>();
        materials.addAll(materialList(petConfig(type), "food.heal"));
        materials.addAll(materialList(petConfig(type), "food.satiety"));
        if (materials.isEmpty()) {
            materials.addAll(materialList("progression.feeding.common-foods"));
        }
        return materials.stream().toList();
    }

    public boolean isPetRareResource(PetType type, Material material) {
        if (!isRareResource(material)) {
            return false;
        }
        if (isPetFood(type, material)) {
            return true;
        }
        for (PetType candidate : PetType.values()) {
            if (isPetFood(candidate, material)) {
                return false;
            }
        }
        return true;
    }

    public long questCompletePoints() {
        return config.getLong("rewards.quest-complete-points", 25L);
    }

    public long activityPoints() {
        return config.getLong("rewards.activity-points", 5L);
    }

    public long killPoints() {
        return config.getLong("rewards.kill-points", 2L);
    }

    public long minutePointCap() {
        return config.getLong("economy.minute-point-cap", 120L);
    }

    public int repeatedActionWindowTicks() {
        return config.getInt("economy.repeated-action-window-ticks", 1200);
    }

    public int repeatedActionSoftLimit() {
        return config.getInt("economy.repeated-action-soft-limit", 8);
    }

    public double repeatedActionMultiplier() {
        return config.getDouble("economy.repeated-action-multiplier", 0.35D);
    }

    public double afkDistanceThreshold() {
        return config.getDouble("economy.afk-distance-threshold", 7.5D);
    }

    public int activityRewardIntervalTicks() {
        return config.getInt("economy.activity-reward-interval-ticks", 1200);
    }

    public long explorationPoints() {
        return config.getLong("economy.exploration-points", 4L);
    }

    public long defaultKillPoints() {
        return config.getLong("economy.kill-points.default", 2L);
    }

    public long bossKillPoints() {
        return config.getLong("economy.kill-points.boss", 80L);
    }

    public long boxCost(String boxId) {
        return config.getLong("economy.boxes." + boxId + ".cost", 100L);
    }

    public long boxFreeCooldownMillis(String boxId) {
        return Math.max(1L, config.getLong("economy.boxes." + boxId + ".free-cooldown-minutes", 120L)) * 60_000L;
    }

    public int boxPityThreshold(String boxId) {
        return config.getInt("economy.boxes." + boxId + ".pity-threshold", 20);
    }

    public double boxRarityChance(String boxId, String rarity) {
        return config.getDouble("economy.boxes." + boxId + ".rarity." + rarity.toLowerCase(), rarityChance(rarity));
    }

    public boolean boxGuaranteeOnlyRare(String boxId) {
        return config.getBoolean("economy.boxes." + boxId + ".guarantee-rare-only", true);
    }

    public Set<String> questIds() {
        if (questsConfig != null && questsConfig.isConfigurationSection("quests")) {
            ConfigurationSection quests = questsConfig.getConfigurationSection("quests");
            return quests == null ? Set.of() : quests.getKeys(false);
        }
        if (!config.isConfigurationSection("economy.quests")) {
            return Set.of();
        }
        ConfigurationSection quests = config.getConfigurationSection("economy.quests");
        return quests == null ? Set.of() : quests.getKeys(false);
    }

    public String questTitle(String questId) {
        if (questsConfig != null && questsConfig.isSet("quests." + questId + ".title")) {
            return questsConfig.getString("quests." + questId + ".title", questId);
        }
        return config.getString("economy.quests." + questId + ".title", questId);
    }

    public String questCategory(String questId) {
        if (questsConfig != null && questsConfig.isSet("quests." + questId + ".category")) {
            return questsConfig.getString("quests." + questId + ".category", "daily");
        }
        return config.getString("economy.quests." + questId + ".category", "daily");
    }

    public String questDescription(String questId) {
        if (questsConfig != null && questsConfig.isSet("quests." + questId + ".description")) {
            return questsConfig.getString("quests." + questId + ".description", "");
        }
        return config.getString("economy.quests." + questId + ".description", "");
    }

    public String questType(String questId) {
        if (questsConfig != null && questsConfig.isSet("quests." + questId + ".type")) {
            return questsConfig.getString("quests." + questId + ".type", "KILL_MOB");
        }
        return config.getString("economy.quests." + questId + ".type", "KILL_MOB");
    }

    public String questTarget(String questId) {
        if (questsConfig != null && questsConfig.isSet("quests." + questId + ".target")) {
            return questsConfig.getString("quests." + questId + ".target", "");
        }
        return config.getString("economy.quests." + questId + ".target", "");
    }

    public int questAmount(String questId) {
        if (questsConfig != null && questsConfig.isSet("quests." + questId + ".amount")) {
            return questsConfig.getInt("quests." + questId + ".amount", 1);
        }
        return config.getInt("economy.quests." + questId + ".amount", 1);
    }

    public long questRewardPoints(String questId) {
        if (questsConfig != null && questsConfig.isSet("quests." + questId + ".reward-points")) {
            return questsConfig.getLong("quests." + questId + ".reward-points", questCompletePoints());
        }
        return config.getLong("economy.quests." + questId + ".reward-points", questCompletePoints());
    }

    public long questRewardPetXp(String questId) {
        if (questsConfig != null && questsConfig.isSet("quests." + questId + ".reward-pet-xp")) {
            return questsConfig.getLong("quests." + questId + ".reward-pet-xp", 0L);
        }
        return config.getLong("economy.quests." + questId + ".reward-pet-xp", 0L);
    }

    public long questRepeatCooldownMinutes(String questId) {
        if (questsConfig != null && questsConfig.isSet("quests." + questId + ".repeat-cooldown-minutes")) {
            return questsConfig.getLong("quests." + questId + ".repeat-cooldown-minutes", -1L);
        }
        return config.getLong("economy.quests." + questId + ".repeat-cooldown-minutes", -1L);
    }

    public Material questIcon(String questId, Material fallback) {
        String value = null;
        if (questsConfig != null && questsConfig.isSet("quests." + questId + ".icon")) {
            value = questsConfig.getString("quests." + questId + ".icon");
        }
        if (value == null) {
            value = config.getString("economy.quests." + questId + ".icon", fallback.name());
        }
        Material material = Material.matchMaterial(value == null ? "" : value);
        return material == null ? fallback : material;
    }

    public long petUpdateIntervalTicks() {
        return config.getLong("tasks.pet-update-interval-ticks", 20L);
    }

    public long triggerCheckIntervalTicks() {
        return config.getLong("tasks.trigger-check-interval-ticks", 100L);
    }

    public long saveIntervalTicks() {
        return config.getLong("tasks.save-interval-ticks", 6000L);
    }

    public double followMinRadius() {
        return config.getDouble("pet-engine.follow-min-radius", 1.0D);
    }

    public double followMaxRadius() {
        return config.getDouble("pet-engine.follow-max-radius", 3.0D);
    }

    public double returnDistance() {
        return config.getDouble("pet-engine.return-distance", 10.0D);
    }

    public double teleportDistance() {
        return config.getDouble("pet-engine.teleport-distance", 28.0D);
    }

    public double petBaseSpeed() {
        return config.getDouble("pet-engine.base-speed", 0.32D);
    }

    public double petSprintSpeed() {
        return config.getDouble("pet-engine.sprint-speed", 0.55D);
    }

    public double attackRange() {
        return config.getDouble("pet-engine.attack-range", 2.2D);
    }

    public int attackCooldownTicks() {
        return config.getInt("pet-engine.attack-cooldown-ticks", 30);
    }

    public double aggroRadius() {
        return config.getDouble("pet-engine.aggro-radius", 8.0D);
    }

    public double petIncomingDamageMultiplier(int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return Math.max(0.05D, config.getDouble(
            "pet-engine.incoming-damage-multiplier-by-evolution." + safeEvolution,
            defaultPetIncomingDamageMultiplier(safeEvolution)
        ));
    }

    public double combatBaseDamage() {
        return config.getDouble("abilities.combat.base-damage", 1.5D);
    }

    public double combatDamagePerLevel() {
        return config.getDouble("abilities.combat.damage-per-level", 0.45D);
    }

    public double combatDamagePerSubLevel() {
        return config.getDouble("abilities.combat.damage-per-sub-level", 0.08D);
    }

    public double combatDamagePerEvolution() {
        return config.getDouble("abilities.combat.damage-per-evolution", 0.75D);
    }

    public double combatAttackRatingBaseline() {
        return Math.max(0.5D, config.getDouble("abilities.combat.attack-rating-baseline", 1.8D));
    }

    public double combatEvolutionMultiplier(int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return Math.max(0.25D, config.getDouble(
            "abilities.combat.evolution-multiplier." + safeEvolution,
            defaultCombatEvolutionMultiplier(safeEvolution)
        ));
    }

    public double petAttackRating(PetType type) {
        return Math.max(0.5D, petConfig(type).getDouble("combat.attack-rating", defaultPetAttackRating(type)));
    }

    public double petAttackMultiplier(PetType type) {
        return Math.max(0.45D, Math.min(1.85D, petAttackRating(type) / combatAttackRatingBaseline()));
    }

    public double petAttackTempo(PetType type) {
        return Math.max(0.75D, Math.min(1.35D, petConfig(type).getDouble("combat.attack-tempo", defaultPetAttackTempo(type))));
    }

    public boolean legendaryAllayVexEnabled() {
        return "VEX_STRIKE".equalsIgnoreCase(petConfig(PetType.ALLAY).getString("legendary-trait.id", ""));
    }

    public double legendaryAllayVexChance() {
        return Math.max(0.0D, Math.min(1.0D, petConfig(PetType.ALLAY).getDouble("legendary-trait.chance", 0.5D)));
    }

    public int legendaryAllayVexDurationSeconds() {
        return Math.max(1, petConfig(PetType.ALLAY).getInt("legendary-trait.duration-seconds", 10));
    }

    public int legendaryAllayVexCooldownSeconds() {
        return Math.max(1, petConfig(PetType.ALLAY).getInt("legendary-trait.cooldown-seconds", 300));
    }

    public double combatRarityMultiplier(String rarity) {
        return config.getDouble("abilities.combat.rarity-multiplier." + rarity.toLowerCase(), 1.0D);
    }

    public double combatTypeMultiplier(PetType type) {
        String key = type.name().toLowerCase();
        return petAttackMultiplier(type) * Math.max(0.1D, config.getDouble("abilities.combat.type-multiplier." + key, 1.0D));
    }

    public double combatPatternWeight(String pattern, int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return Math.max(0.0D, config.getDouble(
            "abilities.combat.patterns." + pattern + ".weight-by-evolution." + safeEvolution,
            defaultCombatPatternWeight(pattern, safeEvolution)
        ));
    }

    public double combatPatternDamageMultiplier(String pattern) {
        return Math.max(0.1D, config.getDouble(
            "abilities.combat.patterns." + pattern + ".damage-multiplier",
            defaultCombatPatternDamageMultiplier(pattern)
        ));
    }

    public double combatPatternAccuracy(String pattern, int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return Math.max(0.05D, Math.min(1.0D, config.getDouble(
            "abilities.combat.patterns." + pattern + ".accuracy-by-evolution." + safeEvolution,
            defaultCombatPatternAccuracy(pattern, safeEvolution)
        )));
    }

    public double combatPvpTypeMultiplier(PetType type) {
        String key = type.name().toLowerCase();
        return defaultCombatPvpTypeMultiplier(type) * Math.max(0.1D, config.getDouble("abilities.combat.pvp.type-multiplier." + key, 1.0D));
    }

    public double combatPvpEvolutionMultiplier(int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return config.getDouble(
            "abilities.combat.pvp.evolution-multiplier." + safeEvolution,
            defaultCombatPvpEvolutionMultiplier(safeEvolution)
        );
    }

    public double defenseDamageReductionPercent() {
        return config.getDouble("abilities.defense.damage-reduction-percent", 0.06D);
    }

    public double defenseReductionPerLevel() {
        return config.getDouble("abilities.defense.reduction-per-level", 0.01D);
    }

    public int defenseReactionCooldownTicks() {
        return config.getInt("abilities.defense.reaction-cooldown-ticks", 180);
    }

    public double rescueHealthPercent() {
        return config.getDouble("abilities.defense.rescue-health-percent", 0.35D);
    }

    public int rescueCooldownTicks() {
        return config.getInt("abilities.defense.rescue-cooldown-ticks", 1200);
    }

    public int rescueShieldDurationTicks() {
        return config.getInt("abilities.defense.rescue-shield-duration-ticks", 100);
    }

    public int rescueRegenerationDurationTicks() {
        return config.getInt("abilities.defense.rescue-regeneration-duration-ticks", 80);
    }

    public int passiveDurationTicks() {
        return config.getInt("abilities.passive.duration-ticks", 140);
    }

    public int passiveDurationTicks(int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return config.getInt("abilities.passive.duration-ticks-by-evolution." + safeEvolution, defaultPassiveDurationTicks(safeEvolution));
    }

    public int passiveCastCooldownTicks(int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return config.getInt("abilities.passive.cast-cooldown-ticks-by-evolution." + safeEvolution, defaultPassiveCastCooldownTicks(safeEvolution));
    }

    public int passiveMaxEffects(int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return config.getInt("abilities.passive.max-effects-by-evolution." + safeEvolution, defaultPassiveMaxEffects(safeEvolution));
    }

    public int passiveAmplifierCap(int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        return config.getInt("abilities.passive.amplifier-cap-by-evolution." + safeEvolution, defaultPassiveAmplifierCap(safeEvolution));
    }

    public int passiveAmplifierPerLevels() {
        return config.getInt("abilities.passive.amplifier-per-levels", 3);
    }

    public double autoPickupRadius() {
        return config.getDouble("abilities.utility.auto-pickup-radius", 5.0D);
    }

    public boolean isAutoPickupBlacklisted(Material material) {
        return materialList("abilities.utility.auto-pickup-blacklist").contains(material);
    }

    public String petVaultTitle() {
        return config.getString("pet-vault.title", "PetVault");
    }

    public int petVaultSize(int evolution, String petType) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        int size = config.getInt("pet-vault.size-by-evolution." + safeEvolution, 9);
        if ("ALLAY".equalsIgnoreCase(petType)) {
            size += config.getInt("pet-vault.allay-bonus-slots", 9);
        }
        return Math.max(9, Math.min(54, ((size + 8) / 9) * 9));
    }

    public double foxExtraLootChance() {
        return config.getDouble("abilities.utility.fox-extra-loot-chance", 0.05D);
    }

    public int foxExtraLootCooldownTicks() {
        return config.getInt("abilities.utility.fox-extra-loot-cooldown-ticks", 200);
    }

    public double miningProcChance() {
        return config.getDouble("abilities.utility.mining-proc-chance", 0.08D);
    }

    public int miningProcCooldownTicks() {
        return config.getInt("abilities.utility.mining-proc-cooldown-ticks", 100);
    }

    public boolean petEnabled(PetType type) {
        return petConfig(type).getBoolean("enabled", true);
    }

    public String petPersonality(PetType type) {
        return petConfig(type).getString("personality", "BALANCED");
    }

    public double petScale(PetType type, int evolution) {
        int safeEvolution = Math.max(1, Math.min(5, evolution));
        double configured = petConfig(type).getDouble("scale-by-evolution." + safeEvolution, 1.0D);
        return Math.max(petScaleFloor(type), Math.min(petScaleCeiling(type), configured));
    }

    private double petScaleFloor(PetType type) {
        return switch (type) {
            case GHAST -> 0.12D;
            case PHANTOM, BREEZE -> 0.44D;
            case BAT -> 0.40D;
            case ALLAY -> 0.50D;
            case FROG -> 0.50D;
            case BEE, PARROT, VEX, AXOLOTL -> 0.58D;
            case RABBIT -> 0.86D;
            case CAT, PANDA, ARMADILLO -> 0.72D;
            case FOX, WOLF -> 0.72D;
            case BLAZE -> 0.44D;
        };
    }

    private double petScaleCeiling(PetType type) {
        return switch (type) {
            case GHAST -> 0.50D;
            case PHANTOM -> 0.96D;
            case BREEZE -> 0.98D;
            case BAT -> 0.90D;
            case ALLAY -> 1.13D;
            case AXOLOTL -> 1.23D;
            case FROG -> 1.15D;
            case PARROT -> 1.06D;
            case BEE -> 1.38D;
            case RABBIT -> 1.69D;
            case CAT -> 2.03D;
            case VEX -> 1.08D;
            case ARMADILLO -> 1.25D;
            case FOX -> 1.54D;
            case WOLF -> 1.65D;
            case PANDA -> 1.60D;
            case BLAZE -> 0.90D;
        };
    }

    public double petRoamRadius(PetType type, boolean manualIdle) {
        String path = manualIdle ? "behavior.manual-roam-radius" : "behavior.roam-radius";
        double fallback = switch (type) {
            case WOLF, FOX -> 7.5D;
            case CAT, RABBIT, PANDA -> 5.0D;
            case ALLAY, BEE, PARROT, BAT, AXOLOTL, PHANTOM -> 8.0D;
            case BLAZE, VEX, BREEZE, GHAST -> 6.5D;
            case FROG, ARMADILLO -> 7.0D;
        };
        return Math.max(2.0D, Math.min(9.0D, petConfig(type).getDouble(path, fallback)));
    }

    public double petAmbientSpeedMultiplier(PetType type) {
        double fallback = switch (type) {
            case CAT -> 0.42D;
            case PANDA -> 0.46D;
            case RABBIT, FROG -> 0.55D;
            case ALLAY, BEE, BAT, AXOLOTL, ARMADILLO -> 0.60D;
            case PARROT, BREEZE -> 0.66D;
            case FOX -> 0.72D;
            case WOLF -> 0.78D;
            case BLAZE, VEX, GHAST -> 0.58D;
            case PHANTOM -> 0.68D;
        };
        return Math.max(0.15D, Math.min(1.4D, petConfig(type).getDouble("behavior.ambient-speed-multiplier", fallback)));
    }

    public int petStayMaxSeconds(PetType type) {
        return Math.max(10, petConfig(type).getInt("behavior.max-stay-seconds", 90));
    }

    public int petSpawnRetrySeconds(PetType type) {
        return Math.max(1, petConfig(type).getInt("behavior.spawn-retry-seconds", 3));
    }

    public boolean petEmotionEnabled(PetType type) {
        return petConfig(type).getBoolean("emotion.enabled", true);
    }

    public int petEmotionMinTicks(PetType type) {
        return Math.max(10, petConfig(type).getInt("emotion.interval-min-ticks", 30));
    }

    public int petEmotionMaxTicks(PetType type) {
        return Math.max(petEmotionMinTicks(type), petConfig(type).getInt("emotion.interval-max-ticks", 60));
    }

    public boolean petAutoLootEnabled(PetType type) {
        return petConfig(type).getBoolean("auto-loot.enabled",
            type == PetType.ALLAY || type == PetType.FOX || type == PetType.BEE || type == PetType.BAT || type == PetType.PARROT);
    }

    public boolean petAutoLootSampleMode(PetType type) {
        return petConfig(type).getBoolean("auto-loot.sample-mode", true);
    }

    public double petAutoLootRadius(PetType type) {
        return Math.max(1.0D, Math.min(8.0D, petConfig(type).getDouble("auto-loot.radius", autoPickupRadius())));
    }

    public boolean isPetAutoLootBlacklisted(PetType type, Material material) {
        return materialList(petConfig(type), "auto-loot.blacklist").contains(material) || isAutoPickupBlacklisted(material);
    }

    public boolean debugEnabled() {
        return config.getBoolean("debug.enabled", false);
    }

    public boolean debugFileEnabled() {
        return config.getBoolean("debug.file", true);
    }

    public boolean debugPetRuntimeEnabled() {
        return config.getBoolean("debug.pet.runtime", false);
    }

    public boolean debugPetStuckEnabled() {
        return config.getBoolean("debug.pet.stuck", false);
    }

    public boolean debugPetDamageEnabled() {
        return config.getBoolean("debug.pet.damage", false);
    }

    public boolean worldPetsEnabled(String worldName) {
        return worldBoolean(worldName, "pets-enabled", true);
    }

    public boolean worldPetAttacksEnabled(String worldName) {
        return worldBoolean(worldName, "attacks-enabled", true);
    }

    public boolean worldPetBuffsEnabled(String worldName) {
        return worldBoolean(worldName, "buffs-enabled", true);
    }

    public boolean worldPetAutolootEnabled(String worldName) {
        return worldBoolean(worldName, "autoloot-enabled", true);
    }

    public boolean worldDecorativeOnly(String worldName) {
        return worldBoolean(worldName, "decorative-only", false);
    }

    private List<Material> materialList(String path) {
        return config.getStringList(path).stream()
            .map(String::toUpperCase)
            .map(Material::matchMaterial)
            .filter(material -> material != null)
            .toList();
    }

    private List<Material> materialList(YamlConfiguration yaml, String path) {
        return yaml.getStringList(path).stream()
            .map(String::toUpperCase)
            .map(Material::matchMaterial)
            .filter(material -> material != null)
            .toList();
    }

    private void loadPetConfigs() {
        petConfigs.clear();
        File folder = new File(plugin.getDataFolder(), "pets");
        for (PetType type : PetType.values()) {
            File file = new File(folder, type.name().toLowerCase() + ".yml");
            petConfigs.put(type.name(), YamlUtf8IO.load(file, plugin.getLogger(), "pets/" + file.getName()));
        }
    }

    private void loadQuestConfig() {
        File file = new File(plugin.getDataFolder(), "quests.yml");
        questsConfig = YamlUtf8IO.load(file, plugin.getLogger(), "quests.yml");
    }

    private YamlConfiguration petConfig(PetType type) {
        return petConfigs.getOrDefault(type.name(), new YamlConfiguration());
    }

    private boolean worldBoolean(String worldName, String key, boolean fallback) {
        String safeWorld = worldName == null ? "" : worldName;
        if (config.isSet("worlds.rules." + safeWorld + "." + key)) {
            return config.getBoolean("worlds.rules." + safeWorld + "." + key, fallback);
        }
        return config.getBoolean("worlds.default." + key, fallback);
    }

    private double defaultCombatPatternWeight(String pattern, int evolution) {
        return switch (pattern.toLowerCase()) {
            case "combo" -> switch (evolution) {
                case 1 -> 0.70D;
                case 2 -> 0.56D;
                case 3 -> 0.44D;
                case 4 -> 0.34D;
                default -> 0.28D;
            };
            case "pounce" -> switch (evolution) {
                case 1 -> 0.22D;
                case 2 -> 0.27D;
                case 3 -> 0.31D;
                case 4 -> 0.35D;
                default -> 0.36D;
            };
            case "strafe" -> switch (evolution) {
                case 1 -> 0.08D;
                case 2 -> 0.17D;
                case 3 -> 0.25D;
                case 4 -> 0.31D;
                default -> 0.36D;
            };
            case "ranged" -> switch (evolution) {
                case 1 -> 0.12D;
                case 2 -> 0.20D;
                case 3 -> 0.28D;
                case 4 -> 0.34D;
                default -> 0.40D;
            };
            default -> 0.0D;
        };
    }

    private double defaultCombatEvolutionMultiplier(int evolution) {
        return switch (evolution) {
            case 1 -> 0.82D;
            case 2 -> 0.94D;
            case 3 -> 1.02D;
            case 4 -> 1.10D;
            default -> 1.18D;
        };
    }

    private double defaultCombatPatternDamageMultiplier(String pattern) {
        return switch (pattern.toLowerCase()) {
            case "combo" -> 0.72D;
            case "pounce" -> 1.18D;
            case "strafe" -> 0.64D;
            case "ranged" -> 0.86D;
            default -> 1.0D;
        };
    }

    private double defaultCombatPatternAccuracy(String pattern, int evolution) {
        return switch (pattern.toLowerCase()) {
            case "combo" -> switch (evolution) {
                case 1 -> 0.72D;
                case 2 -> 0.77D;
                case 3 -> 0.82D;
                case 4 -> 0.88D;
                default -> 0.92D;
            };
            case "pounce" -> switch (evolution) {
                case 1 -> 0.44D;
                case 2 -> 0.55D;
                case 3 -> 0.67D;
                case 4 -> 0.78D;
                default -> 0.86D;
            };
            case "strafe" -> switch (evolution) {
                case 1 -> 0.26D;
                case 2 -> 0.40D;
                case 3 -> 0.55D;
                case 4 -> 0.69D;
                default -> 0.82D;
            };
            case "ranged" -> switch (evolution) {
                case 1 -> 0.62D;
                case 2 -> 0.72D;
                case 3 -> 0.80D;
                case 4 -> 0.88D;
                default -> 0.93D;
            };
            default -> 1.0D;
        };
    }

    private double defaultCombatPvpTypeMultiplier(PetType type) {
        double normalized = petAttackMultiplier(type);
        return Math.max(0.62D, Math.min(1.05D, 0.58D + normalized * 0.30D));
    }

    private double defaultPetAttackRating(PetType type) {
        return switch (type) {
            case AXOLOTL -> 1.64D;
            case ALLAY -> 1.52D;
            case ARMADILLO -> 1.72D;
            case BAT -> 1.50D;
            case BEE -> 1.78D;
            case BLAZE -> 2.00D;
            case BREEZE -> 1.94D;
            case CAT -> 1.56D;
            case FOX -> 1.72D;
            case FROG -> 1.62D;
            case GHAST -> 2.04D;
            case PANDA -> 1.68D;
            case PARROT -> 1.54D;
            case PHANTOM -> 1.88D;
            case RABBIT -> 1.60D;
            case VEX -> 1.96D;
            case WOLF -> 2.10D;
        };
    }

    private double defaultPetAttackTempo(PetType type) {
        return switch (type) {
            case BAT -> 1.16D;
            case ALLAY -> 1.14D;
            case PHANTOM -> 1.14D;
            case PARROT -> 1.12D;
            case VEX, BREEZE -> 1.10D;
            case AXOLOTL, GHAST -> 1.08D;
            case BEE -> 1.08D;
            case RABBIT, BLAZE, FROG -> 1.06D;
            case FOX, CAT -> 1.04D;
            case ARMADILLO, WOLF -> 1.00D;
            case PANDA -> 0.98D;
        };
    }

    private double defaultPetIncomingDamageMultiplier(int evolution) {
        return switch (evolution) {
            case 1 -> 0.30D;
            case 2 -> 0.24D;
            case 3 -> 0.18D;
            case 4 -> 0.15D;
            default -> 0.12D;
        };
    }

    private double defaultCombatPvpEvolutionMultiplier(int evolution) {
        return switch (evolution) {
            case 1 -> 0.72D;
            case 2 -> 0.82D;
            case 3 -> 0.92D;
            case 4 -> 1.00D;
            default -> 1.08D;
        };
    }

    private int defaultPassiveDurationTicks(int evolution) {
        return switch (evolution) {
            case 1 -> 100;
            case 2 -> 120;
            case 3 -> 140;
            case 4 -> 260;
            default -> 320;
        };
    }

    private int defaultPassiveCastCooldownTicks(int evolution) {
        return switch (evolution) {
            case 1, 2 -> 200;
            case 3 -> 180;
            case 4 -> 120;
            default -> 100;
        };
    }

    private int defaultPassiveMaxEffects(int evolution) {
        return switch (evolution) {
            case 1 -> 1;
            case 2, 3 -> 2;
            default -> 3;
        };
    }

    private int defaultPassiveAmplifierCap(int evolution) {
        return evolution >= 5 ? 1 : 0;
    }

    private int safeEvolutionStage(int nextStage) {
        return Math.max(2, Math.min(5, nextStage));
    }

    private int evolutionStageInt(int nextStage, String key, int fallback) {
        return config.getInt("progression.evolution.stages." + nextStage + "." + key, fallback);
    }

    private int defaultEvolutionRequiredLevel(int nextStage) {
        return switch (nextStage) {
            case 2 -> 3;
            case 3 -> 5;
            case 4 -> 7;
            default -> 10;
        };
    }

    private int defaultEvolutionRequiredBond(int nextStage) {
        return switch (nextStage) {
            case 2 -> 4;
            case 3 -> 6;
            case 4 -> 8;
            default -> 10;
        };
    }

    private int defaultEvolutionRequiredQuests(int nextStage) {
        return switch (nextStage) {
            case 2, 3 -> 1;
            case 4 -> 2;
            case 5 -> 3;
            default -> 0;
        };
    }
}
