package dev.li2fox.vibepetcore.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PetGuiCoreSelectionSupportTest {
    @Test
    void personalMenuPrefersMatchingOffhandForActivePet() {
        OwnedPetData activePet = pet();
        CoreCandidate mainHand = new CoreCandidate(pet());
        CoreCandidate offhand = new CoreCandidate(activePet);

        Optional<CoreCandidate> selected = PetGuiCoreSelectionSupport.selectPersonalMenuCore(
            Optional.of(activePet.petId()),
            Optional.of(mainHand),
            Optional.of(offhand),
            candidate -> candidate.petData().petId()
        );

        assertTrue(selected.isPresent());
        assertEquals(offhand, selected.get());
    }

    @Test
    void personalMenuFallsBackToMatchingMainHandWhenNeeded() {
        OwnedPetData activePet = pet();
        CoreCandidate mainHand = new CoreCandidate(activePet);
        CoreCandidate offhand = new CoreCandidate(pet());

        Optional<CoreCandidate> selected = PetGuiCoreSelectionSupport.selectPersonalMenuCore(
            Optional.of(activePet.petId()),
            Optional.of(mainHand),
            Optional.of(offhand),
            candidate -> candidate.petData().petId()
        );

        assertTrue(selected.isPresent());
        assertEquals(mainHand, selected.get());
    }

    @Test
    void personalMenuPrefersOffhandWhenNoActivePetIsSelected() {
        CoreCandidate mainHand = new CoreCandidate(pet());
        CoreCandidate offhand = new CoreCandidate(pet());

        Optional<CoreCandidate> selected = PetGuiCoreSelectionSupport.selectPersonalMenuCore(
            Optional.empty(),
            Optional.of(mainHand),
            Optional.of(offhand),
            candidate -> candidate.petData().petId()
        );

        assertTrue(selected.isPresent());
        assertEquals(offhand, selected.get());
    }

    @Test
    void personalMenuReturnsEmptyWhenNoCoreExists() {
        Optional<CoreCandidate> selected = PetGuiCoreSelectionSupport.selectPersonalMenuCore(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            candidate -> candidate.petData().petId()
        );

        assertFalse(selected.isPresent());
    }

    private OwnedPetData pet() {
        return new OwnedPetData(UUID.randomUUID(), UUID.randomUUID(), "wolf", "common");
    }
    private record CoreCandidate(OwnedPetData petData) {
    }
}
