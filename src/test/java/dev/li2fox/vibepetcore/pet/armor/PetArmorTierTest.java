package dev.li2fox.vibepetcore.pet.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PetArmorTierTest {
    @Test
    void armorProgressionIsMonotonicByEvolutionAndProtection() {
        PetArmorTier previous = null;
        for (PetArmorTier tier : PetArmorTier.values()) {
            if (previous != null) {
                assertTrue(tier.minEvolution() > previous.minEvolution());
                assertTrue(tier.petArmorPoints() > previous.petArmorPoints());
            }
            previous = tier;
        }
    }

    @Test
    void goldIsThirdEvolutionTier() {
        assertEquals(3, PetArmorTier.GOLD.minEvolution());
        assertTrue(PetArmorTier.GOLD.petArmorPoints() > PetArmorTier.IRON.petArmorPoints());
    }
}
