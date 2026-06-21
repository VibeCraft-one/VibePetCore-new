package dev.li2fox.vibepetcore.pet;

import org.bukkit.Material;

final class PetEnvironmentSupport {
    private PetEnvironmentSupport() {
    }

    static boolean isSoftInterest(Material material) {
        String name = material.name();
        return name.endsWith("FLOWER")
            || name.endsWith("SAPLING")
            || name.contains("MUSHROOM")
            || material == Material.SHORT_GRASS
            || material == Material.TALL_GRASS
            || material == Material.FERN
            || material == Material.SWEET_BERRY_BUSH;
    }

    static boolean isHazard(Material material) {
        return material == Material.LAVA
            || material == Material.FIRE
            || material == Material.SOUL_FIRE
            || material == Material.MAGMA_BLOCK
            || material == Material.CACTUS
            || material == Material.CAMPFIRE
            || material == Material.SOUL_CAMPFIRE;
    }

    static int restPhaseModulo(PetType type) {
        return switch (type) {
            case CAT, FOX, RABBIT -> 4;
            case WOLF -> 5;
            default -> 6;
        };
    }

    static boolean shouldRestAtPhase(PetType type, long phase) {
        return switch (type) {
            case WOLF, BLAZE, GHAST -> phase == 0;
            case CAT, FOX, RABBIT, FROG, PANDA, ARMADILLO -> phase <= 1;
            default -> phase == 0;
        };
    }
}
