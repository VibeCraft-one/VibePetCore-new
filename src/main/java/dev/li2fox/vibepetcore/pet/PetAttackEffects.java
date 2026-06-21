package dev.li2fox.vibepetcore.pet;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;

final class PetAttackEffects {
    private PetAttackEffects() {
    }

    static void playImpact(Location targetLocation, AttackPattern pattern, PetType type, LivingEntity source) {
        Particle particle = impactParticle(pattern, type);
        Sound sound = impactSound(pattern, type);
        if (source != null && pattern == AttackPattern.RANGED) {
            drawTrail(source.getLocation().clone().add(0.0D, 0.8D, 0.0D), targetLocation.clone().add(0.0D, 0.8D, 0.0D), particle);
        }
        targetLocation.getWorld().spawnParticle(particle, targetLocation.clone().add(0.0D, 0.8D, 0.0D), 8, 0.25D, 0.25D, 0.25D, 0.02D);
        targetLocation.getWorld().playSound(targetLocation, sound, 0.45F, 1.15F);
    }

    static void playMiss(Location targetLocation, AttackPattern pattern, PetType type, LivingEntity source) {
        Particle particle = pattern == AttackPattern.RANGED ? impactParticle(pattern, type) : (pattern == AttackPattern.STRAFE ? Particle.SMOKE : Particle.CLOUD);
        if (source != null && pattern == AttackPattern.RANGED) {
            drawTrail(source.getLocation().clone().add(0.0D, 0.8D, 0.0D), targetLocation.clone().add(0.0D, 0.8D, 0.0D), particle);
        }
        targetLocation.getWorld().spawnParticle(particle, targetLocation.clone().add(0.0D, 0.8D, 0.0D), 6, 0.22D, 0.22D, 0.22D, 0.01D);
        targetLocation.getWorld().playSound(targetLocation, pattern == AttackPattern.RANGED ? impactSound(pattern, type) : Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 0.35F, 1.3F);
    }

    private static Particle impactParticle(AttackPattern pattern, PetType type) {
        return switch (pattern) {
            case COMBO -> Particle.CRIT;
            case POUNCE -> Particle.SWEEP_ATTACK;
            case STRAFE -> Particle.ENCHANT;
            case RANGED -> switch (type) {
                case BLAZE -> Particle.FLAME;
                case BREEZE -> Particle.CLOUD;
                case GHAST -> Particle.SMOKE;
                default -> Particle.ENCHANT;
            };
            default -> Particle.CRIT;
        };
    }

    private static Sound impactSound(AttackPattern pattern, PetType type) {
        return switch (pattern) {
            case COMBO -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
            case POUNCE -> Sound.ENTITY_PLAYER_ATTACK_STRONG;
            case STRAFE -> Sound.ENTITY_PLAYER_ATTACK_WEAK;
            case RANGED -> switch (type) {
                case BLAZE -> Sound.ENTITY_BLAZE_SHOOT;
                case BREEZE -> Sound.ENTITY_BREEZE_SHOOT;
                case GHAST -> Sound.ENTITY_GHAST_SHOOT;
                default -> Sound.ENTITY_PLAYER_ATTACK_WEAK;
            };
            default -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
        };
    }

    private static void drawTrail(Location from, Location to, Particle particle) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        double distance = Math.max(0.1D, from.distance(to));
        int steps = Math.max(4, Math.min(18, (int) Math.round(distance * 2.2D)));
        double stepX = (to.getX() - from.getX()) / steps;
        double stepY = (to.getY() - from.getY()) / steps;
        double stepZ = (to.getZ() - from.getZ()) / steps;
        for (int index = 0; index <= steps; index++) {
            Location point = from.clone().add(stepX * index, stepY * index, stepZ * index);
            from.getWorld().spawnParticle(particle, point, 1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }
}
