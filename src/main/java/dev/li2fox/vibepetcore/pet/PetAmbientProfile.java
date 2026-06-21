package dev.li2fox.vibepetcore.pet;

final class PetAmbientProfile {
    private PetAmbientProfile() {
    }

    static String actionCaption(AmbientAction action, PetType type) {
        return switch (action) {
            case REST_NEAR_OWNER -> type == PetType.WOLF || type == PetType.CAT || type == PetType.FOX ? "sits nearby" : "rests nearby";
            case WATCH_OWNER -> "watches";
            case INSPECT_HAND -> "inspects item";
            case INSPECT_GROUND -> groundInterestCaption(type);
            case CURIOUS_STEP -> "curious";
            case PLAYFUL_CIRCLE -> type.flying() ? "circles nearby" : "plays nearby";
            case ALERT -> "alert";
            case SOCIAL_GREET -> "greets";
            case SOCIAL_PLAY -> "plays nearby";
            case SOCIAL_CALM -> "chats";
            case NONE -> "nearby";
        };
    }

    static AmbientAction randomAction(int evolutionStage, double roll) {
        if (evolutionStage <= 2) {
            if (roll < 0.34D) return AmbientAction.PLAYFUL_CIRCLE;
            if (roll < 0.58D) return AmbientAction.CURIOUS_STEP;
            if (roll < 0.78D) return AmbientAction.WATCH_OWNER;
            return AmbientAction.REST_NEAR_OWNER;
        }
        if (evolutionStage >= 4) {
            if (roll < 0.26D) return AmbientAction.WATCH_OWNER;
            if (roll < 0.48D) return AmbientAction.ALERT;
            if (roll < 0.70D) return AmbientAction.REST_NEAR_OWNER;
            if (roll < 0.86D) return AmbientAction.CURIOUS_STEP;
            return AmbientAction.PLAYFUL_CIRCLE;
        }
        if (roll < 0.28D) return AmbientAction.CURIOUS_STEP;
        if (roll < 0.54D) return AmbientAction.WATCH_OWNER;
        if (roll < 0.76D) return AmbientAction.REST_NEAR_OWNER;
        return AmbientAction.PLAYFUL_CIRCLE;
    }

    static String groundInterestCaption(PetType type) {
        return switch (type) {
            case FOX, RABBIT, BEE, FROG -> "explores the ground";
            case WOLF, CAT, PANDA, ARMADILLO -> "sniffs around";
            case ALLAY, PARROT, BAT, AXOLOTL, PHANTOM -> "looks around";
            case BLAZE, VEX, BREEZE, GHAST -> "searches for a target";
        };
    }

    static long actionDurationMillis(AmbientAction action, int evolutionStage) {
        return switch (action) {
            case PLAYFUL_CIRCLE -> evolutionStage <= 2 ? 4_800L : 3_200L;
            case REST_NEAR_OWNER, WATCH_OWNER -> 4_000L;
            case ALERT -> 2_800L;
            default -> 3_600L;
        };
    }

    static long minDelayMillis(int evolutionStage) {
        return evolutionStage <= 2 ? 7_000L : 10_000L;
    }

    static long maxDelayMillis(int evolutionStage) {
        return evolutionStage >= 4 ? 24_000L : 18_000L;
    }

    static double actionChance(int evolutionStage) {
        return switch (evolutionStage) {
            case 1 -> 0.62D;
            case 2 -> 0.55D;
            case 3 -> 0.46D;
            case 4 -> 0.42D;
            default -> 0.48D;
        };
    }
}
