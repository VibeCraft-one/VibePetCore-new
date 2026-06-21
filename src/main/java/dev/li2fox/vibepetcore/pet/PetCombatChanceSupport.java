package dev.li2fox.vibepetcore.pet;

final class PetCombatChanceSupport {
    private PetCombatChanceSupport() {
    }

    static double counterAttackChance(RuntimePet pet) {
        double base = switch (pet.type()) {
            case WOLF -> 0.78D;
            case GHAST, VEX, BLAZE, BREEZE -> 0.74D;
            case BEE, ARMADILLO -> 0.66D;
            case FOX -> 0.54D;
            case CAT, PANDA -> 0.46D;
            case RABBIT, FROG -> 0.44D;
            case PARROT, PHANTOM -> 0.40D;
            case BAT, AXOLOTL -> 0.38D;
            case ALLAY -> 0.36D;
        };
        return Math.min(0.96D, base + combatStageBonus(pet));
    }

    static double assistAttackChance(RuntimePet pet) {
        double base = switch (pet.type()) {
            case WOLF -> 0.72D;
            case GHAST, VEX, BLAZE, BREEZE -> 0.68D;
            case BEE, AXOLOTL, ARMADILLO -> 0.56D;
            case FOX -> 0.52D;
            case RABBIT, FROG -> 0.46D;
            case CAT, PANDA -> 0.40D;
            case PARROT, PHANTOM -> 0.36D;
            case BAT -> 0.34D;
            case ALLAY -> 0.32D;
        };
        return Math.min(0.92D, base + combatStageBonus(pet) * 0.9D);
    }

    static double autoAggroChance(RuntimePet pet, boolean combatSuppressed) {
        if (combatSuppressed) {
            return 0.0D;
        }
        double base = switch (pet.type()) {
            case WOLF -> 0.18D;
            case GHAST, VEX, BLAZE, BREEZE -> 0.15D;
            case BEE, AXOLOTL, ARMADILLO -> 0.12D;
            case FOX -> 0.10D;
            case RABBIT, FROG -> 0.08D;
            case CAT, PANDA -> 0.07D;
            case PARROT, PHANTOM -> 0.06D;
            case BAT -> 0.06D;
            case ALLAY -> 0.05D;
        };
        return Math.min(0.34D, base + combatStageBonus(pet) * 0.45D);
    }

    static double coverTargetChance(RuntimePet pet) {
        double base = switch (pet.type()) {
            case WOLF, ARMADILLO -> 0.28D;
            case PANDA, BEE -> 0.25D;
            case GHAST, VEX, BLAZE, BREEZE -> 0.23D;
            case FOX, AXOLOTL, FROG -> 0.21D;
            case RABBIT, CAT -> 0.18D;
            case PARROT, PHANTOM, BAT -> 0.16D;
            case ALLAY -> 0.14D;
        };
        return Math.min(0.46D, base + combatStageBonus(pet) * 0.55D);
    }

    private static double combatStageBonus(RuntimePet pet) {
        return switch (Math.max(1, Math.min(5, pet.data().evolutionStage()))) {
            case 1 -> 0.00D;
            case 2 -> 0.08D;
            case 3 -> 0.14D;
            case 4 -> 0.20D;
            default -> 0.26D;
        };
    }
}
