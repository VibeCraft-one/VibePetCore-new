package dev.li2fox.vibepetcore.egg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

final class PetCoreSlotSupportTest {
    @Test
    void activeRuntimeRequiresMatchingOffhandSlot() {
        assertTrue(PetCoreSlotSupport.keepsActiveRuntime(PetCoreSlotSupport.OFFHAND_SLOT));
        assertFalse(PetCoreSlotSupport.keepsActiveRuntime(0));
        assertFalse(PetCoreSlotSupport.keepsActiveRuntime(8));
        assertFalse(PetCoreSlotSupport.keepsActiveRuntime(39));
        assertFalse(PetCoreSlotSupport.keepsActiveRuntime(-1));
    }

    @Test
    void offhandActivationBlocksMainHandSummon() {
        assertTrue(PetCoreSlotSupport.canSummonFromHand(true, EquipmentSlot.OFF_HAND));
        assertFalse(PetCoreSlotSupport.canSummonFromHand(true, EquipmentSlot.HAND));
        assertTrue(PetCoreSlotSupport.canSummonFromHand(false, EquipmentSlot.HAND));
    }
}
