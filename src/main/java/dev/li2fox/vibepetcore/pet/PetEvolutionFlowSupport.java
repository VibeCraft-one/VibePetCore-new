package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.api.ProgressionAPI;
import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class PetEvolutionFlowSupport {
    private PetEvolutionFlowSupport() {
    }

    static boolean tryEvolveActivePet(
        Player player,
        Optional<RuntimePet> pet,
        ProgressionAPI progressionAPI,
        Function<OwnedPetData, PetEngineManager.EvolutionRequirement> evolutionRequirement,
        Function<OwnedPetData, Integer> evolutionBond,
        BiFunction<Player, OwnedPetData, Boolean> hasEvolutionMaterials,
        Function<OwnedPetData, Integer> requiredEvolutionQuestCompletions,
        BiFunction<Player, OwnedPetData, Integer> completedEvolutionQuestCompletions,
        EvolutionChanceResolver evolutionChanceResolver,
        EvolutionMaterialConsumer consumeEvolutionMaterials,
        Function<RuntimePet, ItemStack[]> snapshotVaultContents,
        BiConsumer<RuntimePet, ItemStack[]> restoreVaultContents,
        EvolutionSaveFunction saveEvolutionState,
        BiConsumer<Player, RuntimePet> playEvolutionEffect,
        BiConsumer<Player, Long> showActionBar
    ) {
        if (pet.isEmpty()) {
            player.sendMessage(GameText.text("pet.evolution.no-active", "Нет активного питомца.", "No active pet."));
            return false;
        }
        if (progressionAPI == null) {
            player.sendMessage(GameText.text("pet.evolution.progression-not-ready", "Система прогресса ещё не готова.", "The progression system is not ready yet."));
            return false;
        }

        RuntimePet runtimePet = pet.get();
        OwnedPetData petSnapshot = snapshotPetState(runtimePet.data());
        PetEngineManager.EvolutionRequirement requirement = evolutionRequirement.apply(runtimePet.data());
        int bond = evolutionBond.apply(runtimePet.data());
        if (runtimePet.data().level() < requirement.requiredLevel()) {
            player.sendMessage(GameText.text(
                "pet.evolution.need-level",
                "Питомец ещё не готов: нужен уровень {level}.",
                "The pet is not ready yet: level {level} is required."
            ).replace("{level}", String.valueOf(requirement.requiredLevel())));
            return false;
        }
        if (bond < requirement.requiredBond()) {
            player.sendMessage(GameText.text(
                "pet.evolution.need-bond",
                "Питомец ещё не готов: нужна связь {bond}/10.",
                "The pet is not ready yet: bond {bond}/10 is required."
            ).replace("{bond}", String.valueOf(requirement.requiredBond())));
            return false;
        }
        if (!hasEvolutionMaterials.apply(player, runtimePet.data())) {
            player.sendMessage(GameText.text("pet.evolution.need-materials", "Не хватает ресурсов для следующей эволюции.", "You do not have enough resources for the next evolution."));
            return false;
        }
        int requiredQuests = requiredEvolutionQuestCompletions.apply(runtimePet.data());
        int completedQuests = completedEvolutionQuestCompletions.apply(player, runtimePet.data());
        if (completedQuests < requiredQuests) {
            player.sendMessage(GameText.text(
                "pet.evolution.need-quests",
                "Для этой стадии нужны квесты: {current}/{required}.",
                "This stage requires quests: {current}/{required}."
            ).replace("{current}", String.valueOf(completedQuests)).replace("{required}", String.valueOf(requiredQuests)));
            return false;
        }

        InventorySnapshot inventorySnapshot = null;
        ItemStack[] vaultSnapshot = null;
        if (player.getGameMode() != GameMode.CREATIVE) {
            inventorySnapshot = snapshotInventory(player);
            vaultSnapshot = snapshotVaultContents.apply(runtimePet);
            consumeEvolutionMaterials.accept(player, runtimePet, requirement.materials());
        }

        double chance = evolutionChanceResolver.chance(player, runtimePet);
        var result = progressionAPI.tryEvolve(runtimePet.data(), chance);
        runtimePet.refreshName();
        if (!saveEvolutionState.save(player, runtimePet)) {
            rollbackPetState(runtimePet.data(), petSnapshot);
            runtimePet.refreshName();
            if (inventorySnapshot != null) {
                restoreInventory(player, inventorySnapshot);
            }
            if (vaultSnapshot != null) {
                restoreVaultContents.accept(runtimePet, vaultSnapshot);
            }
            player.sendMessage(GameText.text(
                "pet.evolution.save-failed",
                "Не удалось сохранить эволюцию. Ресурсы и состояние питомца восстановлены, попробуйте ещё раз через пару секунд.",
                "Could not save the evolution. Resources and pet state were restored. Try again in a few seconds."
            ));
            showActionBar.accept(player, 2_000L);
            return false;
        }
        if (result.success()) {
            playEvolutionEffect.accept(player, runtimePet);
            showActionBar.accept(player, 2_500L);
            return true;
        }
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.35D, 0.35D, 0.35D, 0.02D);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7F, 0.9F);
        player.sendMessage(GameText.text(
            "pet.evolution.failed",
            "Не повезло, эволюция неудачная: Источник силы слишком далеко. Ресурсы потрачены! Шанс был {chance}%.",
            "Bad luck, evolution failed: the Source of power is too far away. Resources were spent. Chance was {chance}%."
        ).replace("{chance}", String.valueOf(Math.round(result.chance() * 100.0D))));
        showActionBar.accept(player, 2_000L);
        return true;
    }

    @FunctionalInterface
    interface EvolutionMaterialConsumer {
        void accept(Player player, RuntimePet pet, Map<Material, Integer> materials);
    }

    @FunctionalInterface
    interface EvolutionChanceResolver {
        double chance(Player player, RuntimePet pet);
    }

    @FunctionalInterface
    interface EvolutionSaveFunction {
        boolean save(Player player, RuntimePet pet);
    }

    private static OwnedPetData snapshotPetState(OwnedPetData source) {
        OwnedPetData snapshot = new OwnedPetData(source.petId(), source.ownerId(), source.petType(), source.rarity());
        restorePetState(snapshot, source);
        return snapshot;
    }

    private static void rollbackPetState(OwnedPetData target, OwnedPetData snapshot) {
        restorePetState(target, snapshot);
    }

    private static void restorePetState(OwnedPetData target, OwnedPetData source) {
        target.copyProgressionFrom(source);
        target.setOwnerId(source.ownerId());
        target.setState(source.state());
    }

    private static InventorySnapshot snapshotInventory(Player player) {
        return new InventorySnapshot(cloneContents(player.getInventory().getContents()));
    }

    private static void restoreInventory(Player player, InventorySnapshot snapshot) {
        player.getInventory().setContents(cloneContents(snapshot.contents()));
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            clone[index] = source[index] == null ? null : source[index].clone();
        }
        return clone;
    }

    private record InventorySnapshot(ItemStack[] contents) {
    }
}
