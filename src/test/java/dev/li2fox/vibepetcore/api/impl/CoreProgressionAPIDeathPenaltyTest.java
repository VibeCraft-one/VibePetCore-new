package dev.li2fox.vibepetcore.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.progression.DeathPenaltyResult;
import java.lang.reflect.Field;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class CoreProgressionAPIDeathPenaltyTest {
    @Test
    void deathPenaltyRemovesPercentOfCurrentXp() throws Exception {
        CoreProgressionAPI progressionAPI = progressionApi(15.0D, false, 0.0D);
        OwnedPetData pet = pet();
        pet.setXp(100L);

        DeathPenaltyResult result = progressionAPI.applyDeathXpPenalty(pet);

        assertEquals(15L, result.xpLost());
        assertEquals(85L, pet.xp());
        assertFalse(result.levelRollbackApplied());
    }

    @Test
    void deathPenaltyCanRollbackOneLevel() throws Exception {
        CoreProgressionAPI progressionAPI = progressionApi(15.0D, true, 1.0D);
        OwnedPetData pet = pet();
        pet.setLevel(4);
        pet.setSubLevel(3);
        pet.setXp(42L);

        DeathPenaltyResult result = progressionAPI.applyDeathXpPenalty(pet);

        assertTrue(result.levelRollbackApplied());
        assertEquals(1, result.levelsLost());
        assertEquals(3, pet.level());
        assertEquals(10, pet.subLevel());
        assertEquals(0L, pet.xp());
    }

    private static CoreProgressionAPI progressionApi(double xpLossPercent, boolean rollbackEnabled, double rollbackChance) throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
            progression:
              base-xp: 100
              xp-multiplier: 1.5
              max-level: 10
              max-sub-level: 10
            egg-core:
              death-xp-loss-percent: %s
              death-level-rollback-enabled: %s
              death-level-rollback-chance: %s
            """.formatted(xpLossPercent, rollbackEnabled, rollbackChance)));
        BalanceConfig balanceConfig = new BalanceConfig(null);
        Field configField = BalanceConfig.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(balanceConfig, config);
        return new CoreProgressionAPI(null, balanceConfig);
    }

    private static OwnedPetData pet() {
        return new OwnedPetData(UUID.randomUUID(), UUID.randomUUID(), "WOLF", "common");
    }
}
