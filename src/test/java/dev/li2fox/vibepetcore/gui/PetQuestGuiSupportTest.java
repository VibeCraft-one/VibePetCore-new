package dev.li2fox.vibepetcore.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PetQuestGuiSupportTest {
    @Test
    void questGuiPrefersOffhandWhenNoRuntimePetIsSelected() {
        OwnedPetData mainHandPet = pet();
        OwnedPetData offhandPet = pet();

        Optional<OwnedPetData> selected = PetQuestGuiSupport.selectQuestGuiHeldPet(
            Optional.empty(),
            Optional.of(mainHandPet),
            Optional.of(offhandPet)
        );

        assertTrue(selected.isPresent());
        assertEquals(offhandPet, selected.get());
    }

    @Test
    void questGuiPrefersMatchingOffhandForActivePet() {
        OwnedPetData activePet = pet();
        OwnedPetData mainHandPet = pet();

        Optional<OwnedPetData> selected = PetQuestGuiSupport.selectQuestGuiHeldPet(
            Optional.of(activePet.petId()),
            Optional.of(mainHandPet),
            Optional.of(activePet)
        );

        assertTrue(selected.isPresent());
        assertEquals(activePet, selected.get());
    }

    @Test
    void questGuiReturnsEmptyWhenNoCoreExists() {
        Optional<OwnedPetData> selected = PetQuestGuiSupport.selectQuestGuiHeldPet(
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertFalse(selected.isPresent());
    }

    private OwnedPetData pet() {
        return new OwnedPetData(UUID.randomUUID(), UUID.randomUUID(), "wolf", "common");
    }
}
