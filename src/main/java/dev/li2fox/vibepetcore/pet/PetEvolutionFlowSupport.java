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

        if (player.getGameMode() != GameMode.CREATIVE) {
            consumeEvolutionMaterials.accept(player, runtimePet, requirement.materials());
        }

        double chance = evolutionChanceResolver.chance(player, runtimePet);
        var result = progressionAPI.tryEvolve(runtimePet.data(), chance);
        runtimePet.refreshName();
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
}
