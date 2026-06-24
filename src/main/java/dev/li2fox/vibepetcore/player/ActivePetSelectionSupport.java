package dev.li2fox.vibepetcore.player;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class ActivePetSelectionSupport {
    private ActivePetSelectionSupport() {
    }

    public static <T> Optional<T> selectPreferred(
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

    public static Optional<UUID> selectQuestPetId(
        Optional<UUID> activePetId,
        Optional<UUID> mainHandPetId,
        Optional<UUID> offhandPetId
    ) {
        if (activePetId.isPresent()) {
            UUID activeId = activePetId.get();
            if (offhandPetId.filter(activeId::equals).isPresent()) {
                return offhandPetId;
            }
            if (mainHandPetId.filter(activeId::equals).isPresent()) {
                return mainHandPetId;
            }
            return activePetId;
        }
        return offhandPetId.isPresent() ? offhandPetId : mainHandPetId;
    }
}
