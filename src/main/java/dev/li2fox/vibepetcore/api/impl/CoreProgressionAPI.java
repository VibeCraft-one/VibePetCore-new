package dev.li2fox.vibepetcore.api.impl;

import dev.li2fox.vibepetcore.api.ProgressionAPI;
import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.progression.EvolutionResult;
import dev.li2fox.vibepetcore.progression.FeedResult;
import dev.li2fox.vibepetcore.progression.FeedType;
import dev.li2fox.vibepetcore.progression.ProgressionResult;
import dev.li2fox.vibepetcore.pet.PetType;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;

public final class CoreProgressionAPI implements ProgressionAPI {
    private static final String FEEDING_XP_NEXT_SECONDS_KEY = "feeding_xp_next_seconds";
    private static final String FEEDING_XP_CREDITS_KEY = "feeding_xp_credits";
    private static final double FALLBACK_EVOLUTION_CHANCE = 0.25D;

    @SuppressWarnings("unused")
    private final PlayerDataManager playerDataManager;
    private final BalanceConfig balanceConfig;

    public CoreProgressionAPI(PlayerDataManager playerDataManager, BalanceConfig balanceConfig) {
        this.playerDataManager = playerDataManager;
        this.balanceConfig = balanceConfig;
    }

    @Override
    public long xpRequiredForSubLevel(OwnedPetData pet) {
        int safeLevel = Math.max(1, pet.level());
        double rarityMultiplier = balanceConfig.rarityXpMultiplier(pet.rarity());
        return Math.round(balanceConfig.baseXp()
            * Math.pow(balanceConfig.xpMultiplier(), safeLevel - 1)
            * (1.0D + (pet.evolutionStage() - 1) * 0.35D)
            * rarityMultiplier);
    }

    @Override
    public ProgressionResult addXp(OwnedPetData pet, long amount) {
        return addXpInternal(pet, boostedAmount(pet, amount));
    }

    private ProgressionResult addFeedingCareXp(OwnedPetData pet) {
        long nowSeconds = System.currentTimeMillis() / 1_000L;
        int nextAllowedSeconds = pet.progress().getOrDefault(FEEDING_XP_NEXT_SECONDS_KEY, 0);
        if (nowSeconds < Integer.toUnsignedLong(nextAllowedSeconds)) {
            return ProgressionResult.none();
        }

        int credits = Math.max(0, pet.progress().getOrDefault(FEEDING_XP_CREDITS_KEY, 0)) + 1;
        int feedsPerReward = balanceConfig.feedingXpFeedsPerReward();
        pet.progress().put(FEEDING_XP_NEXT_SECONDS_KEY, safeEpochSecond(nowSeconds + balanceConfig.feedingXpCooldownSeconds()));
        pet.progress().put(FEEDING_XP_CREDITS_KEY, credits % feedsPerReward);
        if (credits < feedsPerReward) {
            return ProgressionResult.none();
        }

        long amount = Math.max(1L, Math.round(xpRequiredForSubLevel(pet) * (balanceConfig.feedingXpRewardPercent() / 100.0D)));
        return addXpInternal(pet, amount);
    }

    private ProgressionResult addXpInternal(OwnedPetData pet, long effectiveAmount) {
        if (pet.evolutionStage() >= 5) {
            pet.setLevel(balanceConfig.maxLevel());
            pet.setSubLevel(balanceConfig.maxSubLevel());
            pet.setXp(0L);
            return new ProgressionResult(0L, 0, 0, true);
        }
        int maxLevel = balanceConfig.maxLevel();
        if (effectiveAmount <= 0L || isCapped(pet, maxLevel)) {
            return new ProgressionResult(0L, 0, 0, true);
        }

        int levelsGained = 0;
        pet.setXp(pet.xp() + effectiveAmount);

        while (canGainSubLevel(pet)) {
            long required = xpRequiredForSubLevel(pet);
            pet.setXp(pet.xp() - required);

            if (pet.level() < maxLevel) {
                pet.setLevel(pet.level() + 1);
                pet.setSubLevel(1);
                levelsGained++;
                continue;
            }

            pet.setXp(required);
            break;
        }

        boolean capped = isCapped(pet, maxLevel);
        if (capped) {
            pet.setXp(Math.min(pet.xp(), xpRequiredForSubLevel(pet)));
        }

        return new ProgressionResult(effectiveAmount, 0, levelsGained, capped);
    }

    @Override
    public boolean canGainSubLevel(OwnedPetData pet) {
        return pet.xp() >= xpRequiredForSubLevel(pet);
    }

    @Override
    public boolean canEvolve(OwnedPetData pet) {
        if (pet.evolutionStage() >= 5) {
            return false;
        }
        int nextStage = Math.min(5, pet.evolutionStage() + 1);
        if (pet.level() < balanceConfig.evolutionRequiredLevel(nextStage)) {
            return false;
        }
        return evolutionBond(pet) >= balanceConfig.evolutionRequiredBond(nextStage);
    }

    @Override
    public EvolutionResult tryEvolve(OwnedPetData pet) {
        return tryEvolve(pet, FALLBACK_EVOLUTION_CHANCE);
    }

    @Override
    public EvolutionResult tryEvolve(OwnedPetData pet, double chanceOverride) {
        if (!canEvolve(pet)) {
            return EvolutionResult.notAttempted();
        }

        double chance = Math.max(0.0D, Math.min(1.0D, chanceOverride));
        boolean success = ThreadLocalRandom.current().nextDouble() <= chance;
        if (success) {
            pet.setEvolutionStage(pet.evolutionStage() + 1);
            if (pet.evolutionStage() >= 5) {
                pet.setLevel(balanceConfig.maxLevel());
                pet.setSubLevel(balanceConfig.maxSubLevel());
            } else {
                pet.setLevel(1);
                pet.setSubLevel(1);
            }
            pet.setBond(0);
            pet.setXp(0L);
        }
        return new EvolutionResult(true, success, pet.evolutionStage(), chance);
    }

    @Override
    public FeedResult feed(OwnedPetData pet, Material material) {
        if (material == null) {
            return FeedResult.rejected(msg("feed.reject.invalid", "This item cannot be used to feed the pet."));
        }

        if (balanceConfig.isEvolutionItem(material)) {
            return FeedResult.rejected(msg("feed.evolution.source-only", "Evolution opens through /pet or the Pet Source."));
        }

        PetType type = PetType.parse(pet.petType()).orElse(PetType.WOLF);
        if (balanceConfig.isPetRareResource(type, material)) {
            boolean satietyChanged = fillSatiety(pet, 1.0D);
            boolean growthBoostApplied = applyGrowthBoost(pet);
            if (!satietyChanged && !growthBoostApplied) {
                return FeedResult.rejected(msg("feed.reject.full", "The pet is already full."));
            }
            ProgressionResult progression = addFeedingCareXp(pet);
            return new FeedResult(true, FeedType.RARE_RESOURCE, progression, EvolutionResult.notAttempted(), msg("feed.rare-resource", "The pet gained a growth boost."));
        }

        if (balanceConfig.isPetFood(type, material)) {
            if (!fillSatiety(pet, 1.0D)) {
                return FeedResult.rejected(msg("feed.reject.full", "The pet is already full."));
            }
            ProgressionResult progression = addFeedingCareXp(pet);
            return new FeedResult(true, FeedType.FOOD, progression, EvolutionResult.notAttempted(), msg("feed.food.satiety", "The pet has eaten and feels better."));
        }

        if (!balanceConfig.hasConfiguredPetFood(type) && balanceConfig.isCommonFood(material)) {
            if (!fillSatiety(pet, 1.0D)) {
                return FeedResult.rejected(msg("feed.reject.full", "The pet is already full."));
            }
            ProgressionResult progression = addFeedingCareXp(pet);
            return new FeedResult(true, FeedType.FOOD, progression, EvolutionResult.notAttempted(), msg("feed.common-food", "The pet has eaten."));
        }

        return FeedResult.rejected(msg("feed.reject.unsuitable", "This item does not suit the pet."));
    }

    private String msg(String key, String fallback) {
        return balanceConfig.message(key, fallback);
    }

    private long boostedAmount(OwnedPetData pet, long amount) {
        long scaled = amount;
        if (Bukkit.getServer() != null && pet.growthBoostUntil() > Bukkit.getCurrentTick()) {
            scaled = Math.max(1L, Math.round(scaled * balanceConfig.growthBoostMultiplier()));
        }
        double satietyMultiplier = balanceConfig.satietyXpMultiplier(pet.satiety());
        if (satietyMultiplier <= 0.0D) {
            return 0L;
        }
        return Math.max(0L, Math.round(scaled * satietyMultiplier));
    }

    private boolean isCapped(OwnedPetData pet, int maxLevel) {
        return pet.level() >= maxLevel;
    }

    private boolean fillSatiety(OwnedPetData pet, double amount) {
        double maxSatiety = Math.max(1.0D, balanceConfig.eggMaxSatiety());
        double before = Math.min(pet.satiety(), maxSatiety);
        double after = Math.min(maxSatiety, before + Math.max(0.0D, amount));
        if (after <= before) {
            if (pet.satiety() > maxSatiety) {
                pet.setSatiety(maxSatiety);
            }
            return false;
        }
        pet.setSatiety(after);
        return pet.satiety() > before;
    }

    private boolean applyGrowthBoost(OwnedPetData pet) {
        int durationTicks = balanceConfig.growthBoostDurationTicks();
        if (durationTicks <= 0) {
            return false;
        }
        long before = pet.growthBoostUntil();
        long boostUntil = (long) Bukkit.getCurrentTick() + durationTicks;
        if (boostUntil <= before) {
            return false;
        }
        pet.setGrowthBoostUntil(boostUntil);
        return pet.growthBoostUntil() > before;
    }

    private int safeEpochSecond(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private int evolutionBond(OwnedPetData pet) {
        return Math.max(0, Math.min(balanceConfig.bondMax(), pet.bond()));
    }

    public long legacyXpRequiredForLevel(int level) {
        int safeLevel = Math.max(1, level);
        return Math.round(balanceConfig.baseXp() * Math.pow(balanceConfig.xpMultiplier(), safeLevel - 1));
    }
}
