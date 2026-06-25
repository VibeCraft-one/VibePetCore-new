package dev.li2fox.vibepetcore.api;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.progression.DeathPenaltyResult;
import dev.li2fox.vibepetcore.progression.EvolutionResult;
import dev.li2fox.vibepetcore.progression.FeedResult;
import dev.li2fox.vibepetcore.progression.ProgressionResult;
import org.bukkit.Material;

public interface ProgressionAPI {
    long xpRequiredForSubLevel(OwnedPetData pet);

    ProgressionResult addXp(OwnedPetData pet, long amount);

    boolean canGainSubLevel(OwnedPetData pet);

    boolean canEvolve(OwnedPetData pet);

    EvolutionResult tryEvolve(OwnedPetData pet);

    EvolutionResult tryEvolve(OwnedPetData pet, double chanceOverride);

    FeedResult feed(OwnedPetData pet, Material material);

    DeathPenaltyResult applyDeathXpPenalty(OwnedPetData pet);
}
