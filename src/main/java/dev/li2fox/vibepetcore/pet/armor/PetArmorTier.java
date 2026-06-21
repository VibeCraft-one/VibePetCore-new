package dev.li2fox.vibepetcore.pet.armor;

import org.bukkit.Material;

public enum PetArmorTier {
    COPPER("copper", "Медная кольчуга питомца", Material.COPPER_NAUTILUS_ARMOR, Material.COPPER_BLOCK, 1, 3.0D),
    IRON("iron", "Железная кольчуга питомца", Material.IRON_NAUTILUS_ARMOR, Material.IRON_BLOCK, 2, 6.0D),
    GOLD("gold", "Золотая кольчуга питомца", Material.GOLDEN_NAUTILUS_ARMOR, Material.GOLD_BLOCK, 2, 5.0D),
    DIAMOND("diamond", "Алмазная кольчуга питомца", Material.DIAMOND_NAUTILUS_ARMOR, Material.DIAMOND_BLOCK, 4, 8.0D),
    NETHERITE("netherite", "Незеритовая кольчуга питомца", Material.NETHERITE_NAUTILUS_ARMOR, Material.NETHERITE_BLOCK, 5, 8.0D);

    private final String id;
    private final String displayName;
    private final Material itemMaterial;
    private final Material recipeMaterial;
    private final int minEvolution;
    private final double nautilusArmorPoints;

    PetArmorTier(String id, String displayName, Material itemMaterial, Material recipeMaterial, int minEvolution, double nautilusArmorPoints) {
        this.id = id;
        this.displayName = displayName;
        this.itemMaterial = itemMaterial;
        this.recipeMaterial = recipeMaterial;
        this.minEvolution = minEvolution;
        this.nautilusArmorPoints = nautilusArmorPoints;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material itemMaterial() {
        return itemMaterial;
    }

    public Material recipeMaterial() {
        return recipeMaterial;
    }

    public int minEvolution() {
        return minEvolution;
    }

    public double petArmorPoints() {
        return nautilusArmorPoints;
    }
}
