package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.PetDebugLogger;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

final class LegendaryAllayVexSupport {
    private static final String LEGENDARY_RARITY = "LEGENDARY";

    private final BalanceConfig config;
    private final Map<UUID, Long> cooldowns;
    private final PetDebugLogger debugLogger;

    LegendaryAllayVexSupport(BalanceConfig config, Map<UUID, Long> cooldowns, PetDebugLogger debugLogger) {
        this.config = config;
        this.cooldowns = cooldowns;
        this.debugLogger = debugLogger;
    }

    boolean tryTrigger(Player owner, RuntimePet pet, Entity target) {
        if (owner == null || pet == null || target == null || target.isDead()) {
            return false;
        }
        if (!config.legendaryAllayVexEnabled()
            || pet.type() != PetType.ALLAY
            || pet.temporaryFormActive(PetType.VEX)
            || !LEGENDARY_RARITY.equalsIgnoreCase(pet.data().rarity())
            || pet.data().evolutionStage() < 3) {
            return false;
        }

        long now = System.currentTimeMillis();
        long readyAt = cooldowns.getOrDefault(pet.data().petId(), 0L);
        if (readyAt > now || ThreadLocalRandom.current().nextDouble() >= config.legendaryAllayVexChance()) {
            return false;
        }

        long durationMillis = config.legendaryAllayVexDurationSeconds() * 1000L;
        if (!pet.activateTemporaryForm(owner, config, PetType.VEX, durationMillis)) {
            return false;
        }
        cooldowns.put(pet.data().petId(), now + config.legendaryAllayVexCooldownSeconds() * 1000L);
        debug(owner, pet, target, durationMillis);
        return true;
    }

    private void debug(Player owner, RuntimePet pet, Entity target, long durationMillis) {
        if (debugLogger == null || !config.debugPetRuntimeEnabled()) {
            return;
        }
        debugLogger.debugRateLimited(
            "pet:vex-strike:" + pet.data().petId(),
            "pet-runtime",
            "Legendary allay vex strike owner=" + owner.getName()
                + " pet=" + pet.data().petId()
                + " target=" + target.getType()
                + " durationMillis=" + durationMillis,
            3_000L
        );
    }
}
