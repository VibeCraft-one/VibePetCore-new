package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.PetEngineManager;
import dev.li2fox.vibepetcore.pet.PetType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;

final class PetEvolutionPreviewGuiSupport {
    private final PetEngineManager petEngineManager;

    PetEvolutionPreviewGuiSupport(PetEngineManager petEngineManager) {
        this.petEngineManager = petEngineManager;
    }

    List<String> evolutionStageLore(Player player, PetType type, int currentStage, Optional<OwnedPetData> petData) {
        List<String> lore = new ArrayList<>();
        int stage = Math.max(1, Math.min(5, currentStage));
        if (stage >= 5) {
            lore.add(GameText.petEvolutionPreviewMaxStage(stageName(stage)));
            lore.add(GameText.petEvolutionPreviewCurrentStageHint(stageName(stage)));
            return lore;
        }

        OwnedPetData preview = petData
            .map(this::copyPetData)
            .orElseGet(() -> new OwnedPetData(UUID.randomUUID(), player.getUniqueId(), type.name(), "COMMON"));
        preview.setEvolutionStage(stage);

        PetEngineManager.EvolutionRequirement requirement = petEngineManager.evolutionRequirement(preview);
        int level = preview.level();
        int bond = petEngineManager.evolutionBond(preview);
        int quests = petData.map(value -> petEngineManager.completedEvolutionQuestCompletions(player, value)).orElse(0);

        lore.add(GameText.petEvolutionPreviewTransition(stageName(stage), stageName(requirement.nextStage())));
        lore.add(GameText.petEvolutionPreviewLevelLine(level, requirement.requiredLevel()));
        lore.add(GameText.petEvolutionPreviewBondLine(Math.min(bond, requirement.requiredBond()), requirement.requiredBond()));
        if (requirement.requiredQuests() > 0) {
            lore.add(GameText.petEvolutionPreviewQuestLine(quests, requirement.requiredQuests()));
        }

        if (!requirement.materials().isEmpty()) {
            lore.add("");
            lore.add(GameText.petEvolutionPreviewMaterialsHeader());
            for (Map.Entry<Material, Integer> entry : requirement.materials().entrySet()) {
                int current = petEngineManager.countEvolutionMaterial(player, preview, entry.getKey());
                lore.add(GameText.petEvolutionPreviewMaterialLine(
                    GameText.materialName(entry.getKey()),
                    current,
                    entry.getValue()
                ));
            }
        }

        lore.add("");
        lore.add(isReady(player, petData, preview, requirement)
            ? GameText.petEvolutionPreviewReady()
            : GameText.petEvolutionPreviewNotReady());
        return lore;
    }

    OwnedPetData copyPetData(OwnedPetData source) {
        OwnedPetData copy = new OwnedPetData(source.petId(), source.ownerId(), source.petType(), source.rarity());
        copy.copyProgressionFrom(source);
        copy.setHealth(source.health());
        copy.setState(source.state());
        return copy;
    }

    String stageName(int stage) {
        return GameText.evolutionStageName(stage);
    }

    private boolean isReady(Player player, Optional<OwnedPetData> petData, OwnedPetData preview, PetEngineManager.EvolutionRequirement requirement) {
        if (preview.level() < requirement.requiredLevel()) {
            return false;
        }
        if (petEngineManager.evolutionBond(preview) < requirement.requiredBond()) {
            return false;
        }
        int completedQuests = petData.map(value -> petEngineManager.completedEvolutionQuestCompletions(player, value)).orElse(0);
        if (completedQuests < requirement.requiredQuests()) {
            return false;
        }
        for (Map.Entry<Material, Integer> entry : requirement.materials().entrySet()) {
            if (petEngineManager.countEvolutionMaterial(player, preview, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }
}
