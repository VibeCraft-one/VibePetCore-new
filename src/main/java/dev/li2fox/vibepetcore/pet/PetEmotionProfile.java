package dev.li2fox.vibepetcore.pet;

import org.bukkit.Particle;
import org.bukkit.Sound;

final class PetEmotionProfile {
    private PetEmotionProfile() {
    }

    static Particle profileParticle(PetType type) {
        return switch (type) {
            case BLAZE, GHAST -> Particle.FLAME;
            case ALLAY, VEX, BREEZE, AXOLOTL -> Particle.ENCHANT;
            case BAT, PHANTOM -> Particle.SMOKE;
            default -> Particle.HAPPY_VILLAGER;
        };
    }

    static int profileParticleCount(PetType type) {
        return switch (type) {
            case BLAZE, VEX -> 3;
            case ALLAY, BEE, PARROT, BAT -> 2;
            default -> 1;
        };
    }

    static double emotionHeight(PetType type) {
        return switch (type) {
            case BAT, BEE, PARROT, ALLAY -> 0.55D;
            case BLAZE, VEX -> 1.2D;
            default -> 0.85D;
        };
    }

    static Sound responseSound(PetType type) {
        return switch (type) {
            case WOLF -> Sound.ENTITY_WOLF_AMBIENT;
            case CAT -> Sound.ENTITY_CAT_AMBIENT;
            case FOX -> Sound.ENTITY_FOX_AMBIENT;
            case RABBIT -> Sound.ENTITY_RABBIT_AMBIENT;
            case BAT -> Sound.ENTITY_BAT_AMBIENT;
            case BLAZE -> Sound.ENTITY_BLAZE_AMBIENT;
            case BREEZE -> Sound.ENTITY_BREEZE_IDLE_GROUND;
            case FROG -> Sound.ENTITY_FROG_AMBIENT;
            case GHAST -> Sound.ENTITY_GHAST_AMBIENT;
            case PANDA -> Sound.ENTITY_PANDA_AMBIENT;
            case PARROT -> Sound.ENTITY_PARROT_AMBIENT;
            case PHANTOM -> Sound.ENTITY_PHANTOM_AMBIENT;
            case ALLAY, VEX, AXOLOTL -> Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM;
            case ARMADILLO -> Sound.ENTITY_ARMADILLO_AMBIENT;
            case BEE -> Sound.ENTITY_BEE_LOOP;
        };
    }

    static float responsePitch(PetType type) {
        return switch (type) {
            case BAT, RABBIT -> 1.55F;
            case BLAZE -> 0.85F;
            case WOLF, FOX -> 1.05F;
            default -> 1.25F;
        };
    }
}
