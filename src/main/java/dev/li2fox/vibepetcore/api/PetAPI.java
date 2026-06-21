package dev.li2fox.vibepetcore.api;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.PetType;
import dev.li2fox.vibepetcore.pet.RuntimePet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

public interface PetAPI {
    List<OwnedPetData> pets(UUID playerId);

    Optional<OwnedPetData> activePet(UUID playerId);

    void addPet(UUID playerId, OwnedPetData pet);

    boolean setActivePet(UUID playerId, UUID petId);

    RuntimePet spawnPet(Player player, PetType type);

    void removePet(Player player);

    Optional<RuntimePet> getPet(Player player);

    void updatePet(RuntimePet pet);
}
