package dev.li2fox.vibepetcore.pet;

final class PetMovementProfile {
    private PetMovementProfile() {
    }

    static double movementSpeed(PetType type, double baseSpeed) {
        return switch (type) {
            case VEX -> baseSpeed * 1.25D;
            case ALLAY, BEE -> baseSpeed * 0.85D;
            case BAT, PARROT -> baseSpeed * 0.95D;
            case WOLF, FOX -> baseSpeed * 1.05D;
            default -> baseSpeed;
        };
    }

    static double spawnDistance(PetType type) {
        return switch (type) {
            case BAT, BEE, ALLAY, PARROT -> 1.7D;
            case BLAZE, VEX -> 2.4D;
            default -> 2.1D;
        };
    }

    static double followHeight(PetType type) {
        return switch (type) {
            case ALLAY -> 1.15D;
            case BEE, PARROT, BAT -> 1.35D;
            case GHAST -> 3.55D;
            case PHANTOM -> 3.0D;
            case VEX, BLAZE -> 1.55D;
            default -> 0.0D;
        };
    }

    static double hoverBob(PetType type) {
        return switch (type) {
            case ALLAY -> 0.012D;
            case BEE, BAT -> 0.018D;
            case VEX -> 0.035D;
            default -> 0.022D;
        };
    }

    static double idlePull(PetType type) {
        return switch (type) {
            case ALLAY, CAT -> 0.022D;
            case VEX -> 0.055D;
            case WOLF -> 0.042D;
            default -> 0.035D;
        };
    }

    static double obstacleJumpStrength(PetType type, int level) {
        double base = switch (type) {
            case RABBIT -> 0.46D;
            case FOX -> 0.45D;
            case WOLF -> 0.43D;
            case CAT -> 0.42D;
            default -> 0.42D;
        };
        if (level <= 1) {
            base -= 0.04D;
        }
        return Math.max(0.38D, base);
    }

    static double playfulJumpStrength(PetType type) {
        return switch (type) {
            case RABBIT -> 0.32D;
            case FOX -> 0.28D;
            case CAT -> 0.25D;
            default -> 0.24D;
        };
    }

    static long hopCooldownMillis(PetType type) {
        return switch (type) {
            case RABBIT -> 450L;
            case FOX -> 900L;
            case CAT -> 1_200L;
            default -> 800L;
        };
    }
}
