package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.PetEngineManager;
import dev.li2fox.vibepetcore.pet.PetRarity;
import dev.li2fox.vibepetcore.pet.PetState;
import dev.li2fox.vibepetcore.pet.PetType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.entity.Player;

final class PetInfoGuiSupport {
    private final BalanceConfig balanceConfig;
    private final PetEngineManager petEngineManager;

    PetInfoGuiSupport(BalanceConfig balanceConfig, PetEngineManager petEngineManager) {
        this.balanceConfig = balanceConfig;
        this.petEngineManager = petEngineManager;
    }

    List<String> petInfoLore(PetType type, Optional<OwnedPetData> petData) {
        List<String> lore = new ArrayList<>(roleDetails(type));
        lore.add("");
        petData.ifPresentOrElse(pet -> {
            lore.add(GameText.petInfoNameLine(pet.petName()));
            lore.add(GameText.petInfoRarityLine(rarityTitle(PetRarity.parse(pet.rarity()))));
            lore.add(GameText.petInfoEvolutionLine(evolutionStageName(pet), pet.evolutionStage()));
            lore.add(GameText.petInfoLevelLine(pet.level()));
            lore.add(GameText.petInfoBondLine(pet.bond(), balanceConfig.bondMax()));
            lore.add(GameText.petInfoDurabilityLine(pet.durability(), balanceConfig.eggMaxDurability()));
            lore.add(GameText.petInfoSatietyLine((int) Math.round(pet.satiety()), balanceConfig.eggMaxSatiety()));
        }, () -> {
            lore.add(GameText.petInfoNoCoreLine());
            lore.add(GameText.petInfoNeedCoreHint());
        });
        return lore;
    }

    List<String> roleDetails(PetType type) {
        List<String> lore = new ArrayList<>(PetGuiText.supportUseLore(type));
        lore.add("");
        lore.add(GameText.petInfoAttackLine(attackRatingLine(type)));
        lore.add(GameText.petInfoDefenseLine(PetGuiText.defenseChanceText(type)));
        lore.add(GameText.petInfoEffectsLine(PetGuiText.usefulEffectsText(type)));
        lore.add(GameText.petInfoPowerLine(combatPowerText(type, 1, 1)));
        lore.add(GameText.text("gui.pet.info.buff-plan-line", "&7Бафф-план: &f", "&7Buff plan: &f") + PetGuiText.compactBuffSummary(type));
        return lore;
    }

    List<String> detailedEvolutionLore(Player player, OwnedPetData pet) {
        List<String> lore = new ArrayList<>();
        int nextStage = pet.evolutionStage() + 1;
        if (nextStage > 5) {
            lore.add(GameText.petInfoMaxEvolution());
            return lore;
        }

        int bond = petEngineManager.evolutionBond(pet);
        int completedQuests = petEngineManager.completedEvolutionQuestCompletions(player, pet);
        int requiredQuests = balanceConfig.evolutionRequiredQuests(nextStage);
        boolean ready = pet.level() >= balanceConfig.evolutionRequiredLevel(nextStage)
            && bond >= balanceConfig.evolutionRequiredBond(nextStage)
            && completedQuests >= requiredQuests;

        lore.add(GameText.petInfoNextEvolutionLine(nextStage));
        lore.add(GameText.petInfoRewardLine(PetGuiText.evolutionReward(PetType.parse(pet.petType()).orElse(PetType.WOLF), nextStage)));
        lore.add(GameText.petInfoRequirementsLine(
            balanceConfig.evolutionRequiredLevel(nextStage),
            balanceConfig.evolutionRequiredBond(nextStage),
            completedQuests,
            requiredQuests
        ));
        lore.add(GameText.petInfoProgressLine(
            pet.level(),
            bond,
            completedQuests,
            requiredQuests
        ));
        boolean nearSource = petEngineManager.isNearEvolutionSource(player);
        int chancePercent = (int) Math.round(petEngineManager.evolutionAttemptChance(player) * 100.0D);
        lore.add(GameText.text(
            nearSource ? "gui.pet.info.evolution.source-chance.near" : "gui.pet.info.evolution.source-chance",
            nearSource ? "&aШанс у Источника: &f{chance}%" : "&cШанс вдали от Источника: &f{chance}%",
            nearSource ? "&aSource chance: &f{chance}%" : "&cFar from Source chance: &f{chance}%"
        ).replace("{chance}", String.valueOf(chancePercent)));
        lore.add(GameText.text(
            nearSource ? "gui.pet.info.evolution.source-hint.near" : "gui.pet.info.evolution.source-hint",
            nearSource ? "&7Источник рядом: эволюция гарантирована." : "&7Подойдите к Источнику на 10 блоков для 100%.",
            nearSource ? "&7The Source is close: evolution is guaranteed." : "&7Stand within 10 blocks of the Source for 100%."
        ));
        lore.add(ready ? GameText.petInfoReadyToEvolve() : GameText.petInfoNeedMoreProgress());
        return lore;
    }

    String formatState(String state) {
        try {
            return GameText.stateName(PetState.valueOf(state.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return state.toLowerCase(Locale.ROOT);
        }
    }

    String combatPowerText(PetType type, int evolution, int level) {
        double multiplier = balanceConfig.petAttackMultiplier(type);
        String base;
        if (multiplier >= 1.25D) {
            base = GameText.combatPowerVeryHigh();
        } else if (multiplier >= 1.00D) {
            base = GameText.combatPowerHigh();
        } else if (multiplier >= 0.82D) {
            base = GameText.combatPowerMedium();
        } else if (multiplier >= 0.68D) {
            base = GameText.combatPowerLow();
        } else {
            base = GameText.combatPowerVeryLow();
        }
        String growth = evolution >= 4 || level >= 8
            ? GameText.combatGrowthLate()
            : evolution >= 2
                ? GameText.combatGrowthMid()
                : GameText.combatGrowthBase();
        return GameText.petInfoCombatPower(base, growth, attackRatingLine(type));
    }

    String rarityTitle(PetRarity rarity) {
        String raw = GameText.rarityName(rarity);
        return raw.isEmpty() ? raw : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    String evolutionStageName(OwnedPetData pet) {
        return GameText.evolutionStageName(pet.evolutionStage());
    }

    private String attackRatingLine(PetType type) {
        return GameText.petInfoAttackRating(
            balanceConfig.petAttackRating(type),
            balanceConfig.petAttackMultiplier(type)
        );
    }
}
