package dev.li2fox.vibepetcore.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.progression.FeedResult;
import dev.li2fox.vibepetcore.progression.FeedType;
import dev.li2fox.vibepetcore.progression.ProgressionResult;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class CoreProgressionAPITest {
    @Test
    void normalFoodRaisesSatietyWithoutDirectHealing() throws Exception {
        CoreProgressionAPI progressionAPI = progressionApi();
        OwnedPetData pet = pet();
        pet.setHealth(10.0D);
        pet.setSatiety(2.0D);

        FeedResult result = progressionAPI.feed(pet, Material.APPLE);

        assertTrue(result.accepted());
        assertEquals(FeedType.FOOD, result.feedType());
        assertEquals(10.0D, pet.health());
        assertEquals(3.0D, pet.satiety());
    }

    @Test
    void normalFoodIsRejectedWhenSatietyIsFull() throws Exception {
        CoreProgressionAPI progressionAPI = progressionApi();
        OwnedPetData pet = pet();
        pet.setHealth(10.0D);
        pet.setSatiety(5.0D);

        FeedResult result = progressionAPI.feed(pet, Material.APPLE);

        assertFalse(result.accepted());
        assertEquals(FeedType.NONE, result.feedType());
        assertEquals(10.0D, pet.health());
        assertEquals(5.0D, pet.satiety());
    }

    @Test
    void evolutionItemFeedingDoesNotAttemptEvolution() throws Exception {
        CoreProgressionAPI progressionAPI = progressionApi();
        OwnedPetData pet = pet();
        pet.setLevel(3);
        pet.setBond(4);
        pet.setSatiety(3.0D);

        FeedResult result = progressionAPI.feed(pet, Material.NETHER_STAR);

        assertFalse(result.accepted());
        assertEquals(FeedType.NONE, result.feedType());
        assertFalse(result.evolutionResult().attempted());
        assertEquals(1, pet.evolutionStage());
        assertEquals(3, pet.level());
        assertEquals(4, pet.bond());
        assertEquals(3.0D, pet.satiety());
    }

    @Test
    void lowSatietyReducesXpGain() throws Exception {
        CoreProgressionAPI progressionAPI = progressionApi();
        OwnedPetData pet = pet();
        pet.setSatiety(1.5D);

        ProgressionResult result = progressionAPI.addXp(pet, 10L);

        assertEquals(5L, result.xpAdded());
        assertEquals(5L, pet.xp());
    }

    @Test
    void starvingPetReceivesNoXp() throws Exception {
        CoreProgressionAPI progressionAPI = progressionApi();
        OwnedPetData pet = pet();
        pet.setSatiety(1.0D);

        ProgressionResult result = progressionAPI.addXp(pet, 10L);

        assertEquals(0L, result.xpAdded());
        assertEquals(0L, pet.xp());
    }

    private CoreProgressionAPI progressionApi() throws Exception {
        BalanceConfig balanceConfig = new BalanceConfig(null);
        setConfig(balanceConfig, config());
        return new CoreProgressionAPI(null, balanceConfig);
    }

    private OwnedPetData pet() {
        return new OwnedPetData(UUID.randomUUID(), UUID.randomUUID(), "WOLF", "common");
    }

    private YamlConfiguration config() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("language", "en");
        yaml.set("egg-core.max-satiety", 5);
        yaml.set("progression.base-xp", 100);
        yaml.set("progression.xp-multiplier", 1.35D);
        yaml.set("progression.max-level", 10);
        yaml.set("progression.max-sub-level", 10);
        yaml.set("progression.rarity-xp-multiplier.common", 1.0D);
        yaml.set("progression.feeding.xp-cooldown-seconds", 300L);
        yaml.set("progression.feeding.xp-feeds-per-reward", 5);
        yaml.set("progression.feeding.xp-reward-percent", 1.0D);
        yaml.set("progression.feeding.growth-boost-duration-ticks", 1200);
        yaml.set("progression.feeding.growth-boost-multiplier", 1.5D);
        yaml.set("progression.evolution.stages.2.required-level", 3);
        yaml.set("progression.evolution.stages.2.required-bond", 4);
        yaml.set("progression.feeding.common-foods", List.of("APPLE"));
        yaml.set("progression.feeding.rare-resources", List.of("EMERALD"));
        yaml.set("progression.feeding.evolution-items", List.of("NETHER_STAR"));
        yaml.set("progression.satiety.xp-multiplier.starving", 0.0D);
        yaml.set("progression.satiety.xp-multiplier.hungry", 0.5D);
        yaml.set("progression.satiety.xp-multiplier.peckish", 0.85D);
        return yaml;
    }

    private void setConfig(BalanceConfig balanceConfig, YamlConfiguration yaml) throws Exception {
        Field configField = BalanceConfig.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(balanceConfig, yaml);
    }
}
