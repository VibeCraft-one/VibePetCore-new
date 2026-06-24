package dev.li2fox.vibepetcore.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ActivePetSelectionSupportTest {
    @Test
    void genericSelectionPrefersMatchingOffhandForActivePet() {
        UUID activePetId = UUID.randomUUID();
        Candidate mainHand = new Candidate(UUID.randomUUID());
        Candidate offhand = new Candidate(activePetId);

        Optional<Candidate> selected = ActivePetSelectionSupport.selectPreferred(
            Optional.of(activePetId),
            Optional.of(mainHand),
            Optional.of(offhand),
            Candidate::petId
        );

        assertTrue(selected.isPresent());
        assertEquals(offhand, selected.get());
    }

    @Test
    void genericSelectionPrefersOffhandWhenNoActivePetExists() {
        Candidate mainHand = new Candidate(UUID.randomUUID());
        Candidate offhand = new Candidate(UUID.randomUUID());

        Optional<Candidate> selected = ActivePetSelectionSupport.selectPreferred(
            Optional.empty(),
            Optional.of(mainHand),
            Optional.of(offhand),
            Candidate::petId
        );

        assertTrue(selected.isPresent());
        assertEquals(offhand, selected.get());
    }

    @Test
    void questPetIdFallsBackToStoredActivePetWhenHandsDoNotMatch() {
        UUID activePetId = UUID.randomUUID();

        Optional<UUID> selected = ActivePetSelectionSupport.selectQuestPetId(
            Optional.of(activePetId),
            Optional.of(UUID.randomUUID()),
            Optional.of(UUID.randomUUID())
        );

        assertTrue(selected.isPresent());
        assertEquals(activePetId, selected.get());
    }

    @Test
    void questPetIdPrefersOffhandWhenNoActivePetExists() {
        UUID mainHandPetId = UUID.randomUUID();
        UUID offhandPetId = UUID.randomUUID();

        Optional<UUID> selected = ActivePetSelectionSupport.selectQuestPetId(
            Optional.empty(),
            Optional.of(mainHandPetId),
            Optional.of(offhandPetId)
        );

        assertTrue(selected.isPresent());
        assertEquals(offhandPetId, selected.get());
    }

    @Test
    void questPetIdReturnsEmptyWhenNothingExists() {
        Optional<UUID> selected = ActivePetSelectionSupport.selectQuestPetId(
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertFalse(selected.isPresent());
    }

    private record Candidate(UUID petId) {
    }
}
