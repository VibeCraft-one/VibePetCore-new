package dev.li2fox.vibepetcore.pet;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;

final class PetEvolutionMaterialSupport {
    private PetEvolutionMaterialSupport() {
    }

    static Map<Material, Integer> materialsForNextStage(PetType type, int nextStage) {
        LinkedHashMap<Material, Integer> materials = new LinkedHashMap<>();
        switch (nextStage) {
            case 2 -> fillEvolutionStageTwo(type, materials);
            case 3 -> fillEvolutionStageThree(type, materials);
            case 4 -> fillEvolutionStageFour(type, materials);
            case 5 -> fillEvolutionStageFive(type, materials);
            default -> {
            }
        }
        return materials;
    }

    private static void fillEvolutionStageTwo(PetType type, Map<Material, Integer> materials) {
        switch (type) {
            case WOLF -> {
                materials.put(Material.BONE, 24);
                materials.put(Material.LEATHER, 16);
            }
            case CAT -> {
                materials.put(Material.COD, 16);
                materials.put(Material.SALMON, 12);
            }
            case ALLAY -> {
                materials.put(Material.AMETHYST_SHARD, 20);
                materials.put(Material.GLOW_BERRIES, 16);
            }
            case FOX -> {
                materials.put(Material.SWEET_BERRIES, 24);
                materials.put(Material.CHICKEN, 12);
            }
            case RABBIT -> {
                materials.put(Material.CARROT, 24);
                materials.put(Material.RABBIT_HIDE, 6);
            }
            case BEE -> {
                materials.put(Material.HONEYCOMB, 16);
                materials.put(Material.POPPY, 16);
            }
            case PARROT -> {
                materials.put(Material.WHEAT_SEEDS, 32);
                materials.put(Material.FEATHER, 12);
            }
            case BAT -> {
                materials.put(Material.GLOW_BERRIES, 20);
                materials.put(Material.FERMENTED_SPIDER_EYE, 8);
            }
            case BLAZE -> {
                materials.put(Material.BLAZE_POWDER, 16);
                materials.put(Material.GOLD_INGOT, 10);
            }
            case AXOLOTL -> {
                materials.put(Material.TROPICAL_FISH, 18);
                materials.put(Material.PRISMARINE_CRYSTALS, 10);
            }
            case BREEZE -> {
                materials.put(Material.WIND_CHARGE, 12);
                materials.put(Material.COPPER_INGOT, 16);
            }
            case FROG -> {
                materials.put(Material.SLIME_BALL, 20);
                materials.put(Material.MANGROVE_ROOTS, 12);
            }
            case GHAST -> {
                materials.put(Material.GUNPOWDER, 18);
                materials.put(Material.QUARTZ, 16);
            }
            case PANDA -> {
                materials.put(Material.BAMBOO, 32);
                materials.put(Material.APPLE, 12);
            }
            case PHANTOM -> {
                materials.put(Material.PHANTOM_MEMBRANE, 4);
                materials.put(Material.FEATHER, 18);
            }
            case ARMADILLO -> {
                materials.put(Material.ARMADILLO_SCUTE, 8);
                materials.put(Material.COPPER_INGOT, 16);
            }
            case VEX -> {
                materials.put(Material.AMETHYST_SHARD, 16);
                materials.put(Material.GLOW_INK_SAC, 6);
            }
            default -> {
            }
        }
    }

    private static void fillEvolutionStageThree(PetType type, Map<Material, Integer> materials) {
        switch (type) {
            case WOLF -> {
                materials.put(Material.IRON_INGOT, 20);
                materials.put(Material.BONE_BLOCK, 6);
                materials.put(Material.DIAMOND, 2);
            }
            case CAT -> {
                materials.put(Material.COD, 24);
                materials.put(Material.LAPIS_LAZULI, 24);
                materials.put(Material.EMERALD, 4);
            }
            case ALLAY -> {
                materials.put(Material.AMETHYST_SHARD, 32);
                materials.put(Material.EMERALD, 6);
                materials.put(Material.GLOW_INK_SAC, 4);
            }
            case FOX -> {
                materials.put(Material.GOLD_INGOT, 12);
                materials.put(Material.RABBIT_HIDE, 8);
                materials.put(Material.EMERALD, 4);
            }
            case RABBIT -> {
                materials.put(Material.GOLDEN_CARROT, 8);
                materials.put(Material.EMERALD, 6);
                materials.put(Material.RABBIT_FOOT, 4);
            }
            case BEE -> {
                materials.put(Material.HONEY_BOTTLE, 8);
                materials.put(Material.AMETHYST_SHARD, 16);
                materials.put(Material.EMERALD, 4);
            }
            case PARROT -> {
                materials.put(Material.LAPIS_LAZULI, 24);
                materials.put(Material.GOLD_INGOT, 12);
                materials.put(Material.MELON_SEEDS, 24);
            }
            case BAT -> {
                materials.put(Material.COAL, 24);
                materials.put(Material.AMETHYST_SHARD, 16);
                materials.put(Material.PHANTOM_MEMBRANE, 2);
            }
            case BLAZE -> {
                materials.put(Material.BLAZE_ROD, 8);
                materials.put(Material.MAGMA_CREAM, 8);
                materials.put(Material.DIAMOND, 2);
            }
            case AXOLOTL -> {
                materials.put(Material.TROPICAL_FISH_BUCKET, 2);
                materials.put(Material.PRISMARINE_SHARD, 24);
                materials.put(Material.EMERALD, 4);
            }
            case BREEZE -> {
                materials.put(Material.BREEZE_ROD, 6);
                materials.put(Material.WIND_CHARGE, 18);
                materials.put(Material.EMERALD, 4);
            }
            case FROG -> {
                materials.put(Material.SLIME_BLOCK, 4);
                materials.put(Material.OCHRE_FROGLIGHT, 6);
                materials.put(Material.EMERALD, 4);
            }
            case GHAST -> {
                materials.put(Material.GHAST_TEAR, 4);
                materials.put(Material.SOUL_SAND, 16);
                materials.put(Material.EMERALD, 4);
            }
            case PANDA -> {
                materials.put(Material.BAMBOO_BLOCK, 10);
                materials.put(Material.HONEY_BOTTLE, 6);
                materials.put(Material.EMERALD, 4);
            }
            case PHANTOM -> {
                materials.put(Material.PHANTOM_MEMBRANE, 8);
                materials.put(Material.ENDER_PEARL, 6);
                materials.put(Material.EMERALD, 4);
            }
            case ARMADILLO -> {
                materials.put(Material.ARMADILLO_SCUTE, 14);
                materials.put(Material.IRON_INGOT, 16);
                materials.put(Material.EMERALD, 4);
            }
            case VEX -> {
                materials.put(Material.AMETHYST_SHARD, 28);
                materials.put(Material.ENDER_PEARL, 8);
                materials.put(Material.EMERALD, 4);
            }
            default -> {
            }
        }
    }

    private static void fillEvolutionStageFour(PetType type, Map<Material, Integer> materials) {
        switch (type) {
            case WOLF -> {
                materials.put(Material.DIAMOND, 6);
                materials.put(Material.ECHO_SHARD, 2);
                materials.put(Material.BLAZE_ROD, 8);
            }
            case CAT -> {
                materials.put(Material.PUFFERFISH, 4);
                materials.put(Material.AMETHYST_SHARD, 20);
                materials.put(Material.ECHO_SHARD, 2);
            }
            case ALLAY -> {
                materials.put(Material.ECHO_SHARD, 3);
                materials.put(Material.DIAMOND, 4);
                materials.put(Material.ENDER_PEARL, 8);
            }
            case FOX -> {
                materials.put(Material.DIAMOND, 4);
                materials.put(Material.ECHO_SHARD, 2);
                materials.put(Material.GOLD_INGOT, 24);
            }
            case RABBIT -> {
                materials.put(Material.DIAMOND, 4);
                materials.put(Material.AMETHYST_SHARD, 16);
                materials.put(Material.ECHO_SHARD, 2);
            }
            case BEE -> {
                materials.put(Material.AMETHYST_SHARD, 24);
                materials.put(Material.BLAZE_POWDER, 8);
                materials.put(Material.SUNFLOWER, 12);
            }
            case PARROT -> {
                materials.put(Material.DIAMOND, 4);
                materials.put(Material.ECHO_SHARD, 2);
                materials.put(Material.FEATHER, 24);
            }
            case BAT -> {
                materials.put(Material.ECHO_SHARD, 3);
                materials.put(Material.DIAMOND, 4);
                materials.put(Material.SPIDER_EYE, 16);
            }
            case BLAZE -> {
                materials.put(Material.BLAZE_ROD, 16);
                materials.put(Material.ECHO_SHARD, 2);
                materials.put(Material.DIAMOND, 6);
            }
            case AXOLOTL -> {
                materials.put(Material.HEART_OF_THE_SEA, 1);
                materials.put(Material.PRISMARINE_CRYSTALS, 32);
                materials.put(Material.DIAMOND, 4);
            }
            case BREEZE -> {
                materials.put(Material.BREEZE_ROD, 12);
                materials.put(Material.ECHO_SHARD, 2);
                materials.put(Material.DIAMOND, 4);
            }
            case FROG -> {
                materials.put(Material.VERDANT_FROGLIGHT, 8);
                materials.put(Material.AMETHYST_SHARD, 18);
                materials.put(Material.DIAMOND, 4);
            }
            case GHAST -> {
                materials.put(Material.GHAST_TEAR, 8);
                materials.put(Material.ECHO_SHARD, 2);
                materials.put(Material.DIAMOND, 4);
            }
            case PANDA -> {
                materials.put(Material.CAKE, 2);
                materials.put(Material.HONEY_BLOCK, 3);
                materials.put(Material.DIAMOND, 4);
            }
            case PHANTOM -> {
                materials.put(Material.PHANTOM_MEMBRANE, 12);
                materials.put(Material.ECHO_SHARD, 2);
                materials.put(Material.DIAMOND, 4);
            }
            case ARMADILLO -> {
                materials.put(Material.ARMADILLO_SCUTE, 20);
                materials.put(Material.ECHO_SHARD, 2);
                materials.put(Material.DIAMOND, 4);
            }
            case VEX -> {
                materials.put(Material.ECHO_SHARD, 4);
                materials.put(Material.AMETHYST_SHARD, 24);
                materials.put(Material.DIAMOND, 4);
            }
            default -> {
            }
        }
    }

    private static void fillEvolutionStageFive(PetType type, Map<Material, Integer> materials) {
        switch (type) {
            case WOLF -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.EMERALD, 24);
                materials.put(Material.DIAMOND, 10);
            }
            case CAT -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.LAPIS_LAZULI, 64);
                materials.put(Material.EMERALD, 20);
            }
            case ALLAY -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.ECHO_SHARD, 6);
                materials.put(Material.EMERALD, 24);
            }
            case FOX -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.EMERALD, 24);
                materials.put(Material.RABBIT_HIDE, 16);
            }
            case RABBIT -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.EMERALD, 20);
                materials.put(Material.RABBIT_FOOT, 8);
            }
            case BEE -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.HONEY_BLOCK, 4);
                materials.put(Material.EMERALD, 20);
            }
            case PARROT -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.EMERALD, 20);
                materials.put(Material.LAPIS_LAZULI, 48);
            }
            case BAT -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.PHANTOM_MEMBRANE, 6);
                materials.put(Material.EMERALD, 18);
            }
            case BLAZE -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.BLAZE_ROD, 24);
                materials.put(Material.EMERALD, 24);
            }
            case AXOLOTL -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.HEART_OF_THE_SEA, 1);
                materials.put(Material.EMERALD, 22);
            }
            case BREEZE -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.BREEZE_ROD, 20);
                materials.put(Material.EMERALD, 22);
            }
            case FROG -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.PEARLESCENT_FROGLIGHT, 8);
                materials.put(Material.EMERALD, 20);
            }
            case GHAST -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.GHAST_TEAR, 12);
                materials.put(Material.EMERALD, 22);
            }
            case PANDA -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.BAMBOO_BLOCK, 24);
                materials.put(Material.EMERALD, 20);
            }
            case PHANTOM -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.PHANTOM_MEMBRANE, 16);
                materials.put(Material.EMERALD, 20);
            }
            case ARMADILLO -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.ARMADILLO_SCUTE, 28);
                materials.put(Material.EMERALD, 20);
            }
            case VEX -> {
                materials.put(Material.NETHER_STAR, 1);
                materials.put(Material.ECHO_SHARD, 6);
                materials.put(Material.AMETHYST_SHARD, 32);
            }
            default -> {
            }
        }
    }
}
