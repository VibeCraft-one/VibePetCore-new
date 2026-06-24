package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.player.ActivePetSelectionSupport;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

final class PetGuiCoreSelectionSupport {
    private PetGuiCoreSelectionSupport() {
    }

    static <T> Optional<T> selectPreferredGuiCore(
        Optional<UUID> activePetId,
        Optional<T> mainHand,
        Optional<T> offhand,
        Function<T, UUID> petId
    ) {
        return ActivePetSelectionSupport.selectPreferred(activePetId, mainHand, offhand, petId);
    }
}
