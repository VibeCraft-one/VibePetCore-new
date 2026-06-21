package dev.li2fox.vibepetcore.egg;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.PetEngineManager;
import dev.li2fox.vibepetcore.pet.PetType;
import java.util.Optional;
import org.bukkit.entity.Player;

final class PetCoreRecoverySupport {
    private PetCoreRecoverySupport() {
    }

    static RecoveryResult recoverInactiveCore(Player player, PetEngineManager petEngineManager, Optional<OwnedPetData> coreData) {
        if (player == null || petEngineManager == null || coreData.isEmpty()) {
            return RecoveryResult.notRecovered();
        }

        OwnedPetData data = coreData.get();
        if (!isRecoverableCoreData(data)) {
            return RecoveryResult.notRecovered();
        }

        Optional<OwnedPetData> storedPet = petEngineManager.storedPetData(player, data.petId());
        if (storedPet.isPresent()) {
            return storedPet.get().durability() > 0
                ? RecoveryResult.recovered(storedPet.get(), false)
                : RecoveryResult.notRecovered();
        }

        OwnedPetData restored = petEngineManager.restoreInactivePetData(player, data);
        return restored.durability() > 0
            ? RecoveryResult.recovered(restored, true)
            : RecoveryResult.notRecovered();
    }

    private static boolean isRecoverableCoreData(OwnedPetData data) {
        if (data == null || data.petId() == null || data.durability() <= 0) {
            return false;
        }
        Optional<PetType> type = PetType.parse(data.petType());
        return type.isPresent() && type.get() != PetType.VEX;
    }

    record RecoveryResult(boolean recovered, OwnedPetData pet, boolean recreatedStorage) {
        static RecoveryResult recovered(OwnedPetData pet, boolean recreatedStorage) {
            return new RecoveryResult(true, pet, recreatedStorage);
        }

        static RecoveryResult notRecovered() {
            return new RecoveryResult(false, null, false);
        }
    }
}
