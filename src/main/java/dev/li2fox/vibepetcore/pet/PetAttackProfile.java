package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.pet.skill.PetPersonality;
import dev.li2fox.vibepetcore.pet.skill.PetSkillRegistry;

final class PetAttackProfile {
    private PetAttackProfile() {
    }

    static double patternWeight(AttackPattern pattern, PetType type, int evolutionStage, PetPersonality personality) {
        return defaultPatternWeight(pattern, evolutionStage)
            * typePatternAffinity(pattern, type)
            * personalityPatternAffinity(pattern, personality);
    }

    static AttackPattern selectPattern(PetType type, int evolutionStage, PetPersonality personality) {
        double comboWeight = patternWeight(AttackPattern.COMBO, type, evolutionStage, personality);
        double pounceWeight = patternWeight(AttackPattern.POUNCE, type, evolutionStage, personality);
        double strafeWeight = patternWeight(AttackPattern.STRAFE, type, evolutionStage, personality);
        double rangedWeight = patternWeight(AttackPattern.RANGED, type, evolutionStage, personality);
        double total = comboWeight + pounceWeight + strafeWeight + rangedWeight;
        if (total <= 0.0D) {
            return PetSkillRegistry.primaryAttackPattern(type);
        }
        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble(total);
        if (roll < comboWeight) {
            return AttackPattern.COMBO;
        }
        roll -= comboWeight;
        if (roll < pounceWeight) {
            return AttackPattern.POUNCE;
        }
        roll -= pounceWeight;
        if (roll < strafeWeight) {
            return AttackPattern.STRAFE;
        }
        return AttackPattern.RANGED;
    }

    static int stepsFor(AttackPattern pattern, int evolutionStage, int comboExtraRoll, double chanceRoll) {
        return switch (pattern) {
            case COMBO -> 2 + Math.min(comboExtraRoll, (evolutionStage >= 4 ? 3 : 2) - 1);
            case POUNCE -> 1 + ((evolutionStage >= 3 && chanceRoll < 0.45D) ? 1 : 0);
            case STRAFE -> 2 + ((evolutionStage >= 5 && chanceRoll < 0.50D) ? 1 : 0);
            case RANGED -> 2 + ((evolutionStage >= 3 && chanceRoll < 0.60D) ? 1 : 0);
            default -> 1;
        };
    }

    private static double personalityPatternAffinity(AttackPattern pattern, PetPersonality personality) {
        return switch (personality) {
            case AGGRESSIVE -> switch (pattern) {
                case COMBO, POUNCE -> 1.18D;
                case STRAFE -> 0.90D;
                case RANGED -> 1.05D;
                default -> 1.0D;
            };
            case PASSIVE -> switch (pattern) {
                case COMBO, POUNCE -> 0.82D;
                case STRAFE -> 1.12D;
                case RANGED -> 0.95D;
                default -> 1.0D;
            };
            case DEFENSIVE -> switch (pattern) {
                case COMBO -> 0.92D;
                case POUNCE -> 0.88D;
                case STRAFE -> 1.08D;
                case RANGED -> 1.0D;
                default -> 1.0D;
            };
            case BURST -> switch (pattern) {
                case POUNCE, RANGED -> 1.20D;
                case COMBO -> 1.05D;
                case STRAFE -> 0.95D;
                default -> 1.0D;
            };
            case SCOUT -> switch (pattern) {
                case STRAFE -> 1.22D;
                case POUNCE -> 1.08D;
                case COMBO -> 0.92D;
                case RANGED -> 1.0D;
                default -> 1.0D;
            };
            case MOBILITY -> switch (pattern) {
                case POUNCE, STRAFE -> 1.16D;
                case COMBO -> 0.94D;
                case RANGED -> 1.0D;
                default -> 1.0D;
            };
            case SUPPORT -> switch (pattern) {
                case COMBO -> 0.90D;
                case POUNCE -> 0.92D;
                case STRAFE -> 1.06D;
                case RANGED -> 1.0D;
                default -> 1.0D;
            };
            case UTILITY -> switch (pattern) {
                case STRAFE -> 1.10D;
                case COMBO, POUNCE -> 0.96D;
                case RANGED -> 1.0D;
                default -> 1.0D;
            };
            case BALANCED -> 1.0D;
        };
    }

    private static double defaultPatternWeight(AttackPattern pattern, int evolutionStage) {
        return switch (pattern) {
            case COMBO -> switch (evolutionStage) {
                case 1 -> 0.70D;
                case 2 -> 0.56D;
                case 3 -> 0.44D;
                case 4 -> 0.34D;
                default -> 0.28D;
            };
            case POUNCE -> switch (evolutionStage) {
                case 1 -> 0.22D;
                case 2 -> 0.27D;
                case 3 -> 0.31D;
                case 4 -> 0.35D;
                default -> 0.36D;
            };
            case STRAFE -> switch (evolutionStage) {
                case 1 -> 0.08D;
                case 2 -> 0.17D;
                case 3 -> 0.25D;
                case 4 -> 0.31D;
                default -> 0.36D;
            };
            case RANGED -> switch (evolutionStage) {
                case 1 -> 0.12D;
                case 2 -> 0.20D;
                case 3 -> 0.28D;
                case 4 -> 0.34D;
                default -> 0.40D;
            };
            default -> 0.0D;
        };
    }

    private static double typePatternAffinity(AttackPattern pattern, PetType type) {
        return switch (pattern) {
            case COMBO -> switch (type) {
                case WOLF, FOX, CAT, PANDA, ARMADILLO -> 1.18D;
                case BEE, RABBIT, AXOLOTL, FROG -> 1.05D;
                case ALLAY, BAT, PARROT, PHANTOM -> 0.92D;
                case VEX -> 0.95D;
                case BLAZE, BREEZE, GHAST -> 0.0D;
            };
            case POUNCE -> switch (type) {
                case WOLF, FOX, RABBIT, VEX, PHANTOM -> 1.16D;
                case CAT, BEE, AXOLOTL, FROG -> 1.05D;
                case ALLAY, BAT, PARROT, PANDA, ARMADILLO -> 0.88D;
                case BLAZE, BREEZE, GHAST -> 0.0D;
            };
            case STRAFE -> switch (type) {
                case ALLAY, BAT, PARROT, VEX, PHANTOM, AXOLOTL -> 1.20D;
                case BEE, FROG -> 1.08D;
                case CAT, FOX, RABBIT, PANDA -> 0.94D;
                case WOLF, ARMADILLO -> 0.86D;
                case BLAZE, BREEZE, GHAST -> 0.0D;
            };
            case RANGED -> switch (type) {
                case BLAZE -> 1.34D;
                case BREEZE -> 1.42D;
                case GHAST -> 1.56D;
                case ALLAY, AXOLOTL, ARMADILLO, BAT, BEE, CAT, FOX, FROG, PANDA, PARROT, PHANTOM, RABBIT, VEX, WOLF -> 0.0D;
            };
            default -> 0.0D;
        };
    }
}
