package dev.li2fox.vibepetcore.gui;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

final class PetGuiCoreSelectionSupport {
    private PetGuiCoreSelectionSupport() {
    }

    static <T> Optional<T> selectPersonalMenuCore(
        Optional<UUID> activePetId,
        Optional<T> mainHand,
        Optional<T> offhand,
        Function<T, UUID> petId
    ) {
        if (activePetId.isPresent()) {
            UUID activeId = activePetId.get();
            Optional<T> matchingOffhand = offhand.filter(core -> activeId.equals(petId.apply(core)));
            if (matchingOffhand.isPresent()) {
                return matchingOffhand;
            }
            Optional<T> matchingMainHand = mainHand.filter(core -> activeId.equals(petId.apply(core)));
            if (matchingMainHand.isPresent()) {
                return matchingMainHand;
            }
        }
        return offhand.isPresent() ? offhand : mainHand;
    }
}
