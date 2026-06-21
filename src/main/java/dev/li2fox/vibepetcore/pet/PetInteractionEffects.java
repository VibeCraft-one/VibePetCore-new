package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class PetInteractionEffects {
    private PetInteractionEffects() {
    }

    static long emitMood(
        Player owner,
        LivingEntity entity,
        BalanceConfig config,
        PetType type,
        OwnedPetData data,
        PetState state,
        long nextEmotionMillis,
        boolean ownerResting,
        double emotionHeight,
        Particle profileParticle,
        int profileParticleCount
    ) {
        if (!config.petEmotionEnabled(type)) {
            return nextEmotionMillis;
        }
        long now = System.currentTimeMillis();
        if (now < nextEmotionMillis) {
            return nextEmotionMillis;
        }
        long next = now + java.util.concurrent.ThreadLocalRandom.current().nextLong(config.petEmotionMinTicks(type) * 50L, config.petEmotionMaxTicks(type) * 50L + 1L);

        Location location = entity.getLocation().add(0.0D, emotionHeight, 0.0D);
        if (data.satiety() <= 1) {
            entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, location, 2, 0.16D, 0.1D, 0.16D, 0.0D);
            return next;
        }
        if (data.health() < data.maxHealth() * 0.35D) {
            entity.getWorld().spawnParticle(Particle.SMOKE, location, 4, 0.18D, 0.14D, 0.18D, 0.01D);
            return next;
        }
        if (state == PetState.ATTACK) {
            entity.getWorld().spawnParticle(Particle.CRIT, location, 4, 0.18D, 0.16D, 0.18D, 0.03D);
            return next;
        }
        if (ownerResting && entity.getLocation().distanceSquared(owner.getLocation()) <= 81.0D) {
            entity.getWorld().spawnParticle(profileParticle, location, profileParticleCount, 0.16D, 0.14D, 0.16D, 0.01D);
        }
        return next;
    }

    static long playAmbientSound(LivingEntity entity, long nextAmbientSoundMillis, Sound sound, float pitch) {
        long now = System.currentTimeMillis();
        if (now < nextAmbientSoundMillis) {
            return nextAmbientSoundMillis;
        }
        long next = now + java.util.concurrent.ThreadLocalRandom.current().nextLong(7_000L, 14_000L);
        entity.getWorld().playSound(entity.getLocation(), sound, 0.22F, pitch);
        return next;
    }

    static void playSparEffect(LivingEntity entity, Location targetLocation, float pitch) {
        Location center = targetLocation.clone().add(0.0D, 0.75D, 0.0D);
        entity.getWorld().spawnParticle(Particle.CRIT, center, 6, 0.22D, 0.22D, 0.22D, 0.04D);
        entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        entity.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_WEAK, 0.32F, pitch);
    }

    static void playSocialEffect(LivingEntity entity, AmbientAction action, Particle particle, int count, double emotionHeight) {
        entity.getWorld().spawnParticle(particle, entity.getLocation().add(0.0D, emotionHeight, 0.0D), count, 0.24D, 0.18D, 0.24D, 0.015D);
    }
}
