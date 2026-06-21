package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.inventory.PetVaultService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class PetEvolutionRuntimeSupport {
    private PetEvolutionRuntimeSupport() {
    }

    static int countEvolutionMaterial(Player player, OwnedPetData data, Optional<RuntimePet> activePet, PetVaultService petVaultService, Material material) {
        int amount = countPlayerItem(player, material);
        if (activePet.isPresent() && activePet.get().data().petId().equals(data.petId())) {
            amount += petVaultService.count(activePet.get(), material);
        }
        return amount;
    }

    static Map<Material, Integer> evolutionMaterialCounts(
        Player player,
        OwnedPetData data,
        PetEngineManager.EvolutionRequirement requirement,
        Optional<RuntimePet> activePet,
        PetVaultService petVaultService
    ) {
        Map<Material, Integer> counts = new LinkedHashMap<>();
        for (Material material : requirement.materials().keySet()) {
            counts.put(material, countEvolutionMaterial(player, data, activePet, petVaultService, material));
        }
        return counts;
    }

    static boolean hasEvolutionMaterials(
        Player player,
        OwnedPetData data,
        PetEngineManager.EvolutionRequirement requirement,
        Optional<RuntimePet> activePet,
        PetVaultService petVaultService
    ) {
        for (Map.Entry<Material, Integer> entry : requirement.materials().entrySet()) {
            if (countEvolutionMaterial(player, data, activePet, petVaultService, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    static void consumeEvolutionMaterials(Player player, RuntimePet pet, Map<Material, Integer> requirements, PetVaultService petVaultService) {
        for (Map.Entry<Material, Integer> entry : requirements.entrySet()) {
            consumeMaterial(player, pet, entry.getKey(), entry.getValue(), petVaultService);
        }
    }

    static int evolutionBond(OwnedPetData data, BalanceConfig balanceConfig) {
        return Math.max(0, Math.min(balanceConfig.bondMax(), data.bond()));
    }

    private static int countPlayerItem(Player player, Material material) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    private static void consumeMaterial(Player player, RuntimePet pet, Material material, int amount, PetVaultService petVaultService) {
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material && item.getAmount() > 0 && remaining > 0) {
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
            }
        }
        while (remaining > 0) {
            petVaultService.consumeOne(pet, material);
            remaining--;
        }
    }
}
