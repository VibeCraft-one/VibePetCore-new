package dev.li2fox.vibepetcore.pet;

import org.bukkit.Particle;

final class PetSocialProfile {
    private PetSocialProfile() {
    }

    static AmbientAction actionWith(boolean sameType, int stage, int otherStage, double roll) {
        if (stage <= 2 || otherStage <= 2) {
            return roll < (sameType ? 0.72D : 0.55D) ? AmbientAction.SOCIAL_PLAY : AmbientAction.SOCIAL_GREET;
        }
        if (stage >= 4 && otherStage >= 4) {
            return roll < 0.58D ? AmbientAction.SOCIAL_CALM : AmbientAction.SOCIAL_GREET;
        }
        return roll < 0.45D ? AmbientAction.SOCIAL_GREET : AmbientAction.SOCIAL_CALM;
    }

    static AmbientAction companionAction(AmbientAction action) {
        return switch (action) {
            case SOCIAL_PLAY -> AmbientAction.SOCIAL_PLAY;
            case SOCIAL_CALM -> AmbientAction.SOCIAL_CALM;
            default -> AmbientAction.SOCIAL_GREET;
        };
    }

    static long durationMillis(AmbientAction action) {
        return switch (action) {
            case SOCIAL_PLAY -> 5_000L;
            case SOCIAL_CALM -> 4_500L;
            default -> 3_600L;
        };
    }

    static Particle particle(AmbientAction action, Particle fallback) {
        return switch (action) {
            case SOCIAL_PLAY -> Particle.HAPPY_VILLAGER;
            case SOCIAL_CALM -> Particle.ENCHANT;
            default -> fallback;
        };
    }

    static int particleCount(AmbientAction action) {
        return action == AmbientAction.SOCIAL_PLAY ? 5 : 3;
    }
}
