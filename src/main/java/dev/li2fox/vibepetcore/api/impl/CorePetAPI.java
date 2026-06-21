package dev.li2fox.vibepetcore.api.impl;

import dev.li2fox.vibepetcore.api.PetAPI;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.player.PlayerData;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.pet.PetEngineManager;
import dev.li2fox.vibepetcore.pet.PetType;
import dev.li2fox.vibepetcore.pet.RuntimePet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class CorePetAPI implements PetAPI {
    private final PlayerDataManager playerDataManager;
    private final PetEngineManager petEngineManager;

    public CorePetAPI(PlayerDataManager playerDataManager, PetEngineManager petEngineManager) {
        this.playerDataManager = playerDataManager;
        this.petEngineManager = petEngineManager;
    }

    @Override
    public List<OwnedPetData> pets(UUID playerId) {
        return List.copyOf(playerDataManager.getOrLoad(playerId).pets());
    }

    @Override
    public Optional<OwnedPetData> activePet(UUID playerId) {
        PlayerData data = playerDataManager.getOrLoad(playerId);
        return data.activePetId()
            .flatMap(activeId -> data.pets().stream().filter(pet -> pet.petId().equals(activeId)).findFirst());
    }

    @Override
    public void addPet(UUID playerId, OwnedPetData pet) {
        PlayerData data = playerDataManager.getOrLoad(playerId);
        data.pets().add(pet);
        if (data.activePetId().isEmpty()) {
            data.setActivePetId(pet.petId());
        }
    }

    @Override
    public boolean setActivePet(UUID playerId, UUID petId) {
        PlayerData data = playerDataManager.getOrLoad(playerId);
        boolean ownsPet = data.pets().stream().anyMatch(pet -> pet.petId().equals(petId));
        if (ownsPet) {
            data.setActivePetId(petId);
        }
        return ownsPet;
    }

    @Override
    public RuntimePet spawnPet(Player player, PetType type) {
        return petEngineManager.spawnPet(player, type);
    }

    @Override
    public void removePet(Player player) {
        petEngineManager.removePet(player);
    }

    @Override
    public Optional<RuntimePet> getPet(Player player) {
        return petEngineManager.getPet(player);
    }

    @Override
    public void updatePet(RuntimePet pet) {
        petEngineManager.updatePet(pet);
    }
}
