package dev.li2fox.vibepetcore.egg;

import org.bukkit.inventory.EquipmentSlot;

final class PetCoreSlotSupport {
    static final int OFFHAND_SLOT = 40;

    private PetCoreSlotSupport() {
    }

    static boolean keepsActiveRuntime(int matchingPetSlot) {
        return matchingPetSlot == OFFHAND_SLOT;
    }

    static boolean canSummonFromHand(boolean offhandActivation, EquipmentSlot hand) {
        return !offhandActivation || hand == EquipmentSlot.OFF_HAND;
    }
}
