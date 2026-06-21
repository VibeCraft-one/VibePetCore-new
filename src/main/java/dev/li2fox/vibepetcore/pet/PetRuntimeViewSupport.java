package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.ability.PetAbilityService;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class PetRuntimeViewSupport {
    private PetRuntimeViewSupport() {
    }

    static String debugLine(
        BalanceConfig config,
        Player owner,
        OwnedPetData data,
        PetType type,
        PetState state,
        LivingEntity entity,
        AmbientAction ambientAction,
        boolean ownerResting,
        long nextSpawnAttemptMillis
    ) {
        String entityInfo = entity == null
            ? config.message("debugpet.entity.null", "entity=null")
            : config.message(
                "debugpet.entity",
                "entity={entity}/{uuid} dead={dead} world={world} dist={distance} ownerResting={ownerResting}",
                "entity", entity.getType(),
                "uuid", entity.getUniqueId(),
                "dead", entity.isDead(),
                "world", entity.getWorld().getName(),
                "distance", String.format(Locale.ROOT, "%.2f", entity.getLocation().distance(owner.getLocation())),
                "ownerResting", ownerResting
            );
        return config.message(
            "debugpet.runtime",
            "pet={petId} type={type} state={state} {entityInfo} bond={bond} ambient={ambient} spawnRetrySec={spawnRetrySec}",
            "petId", data.petId(),
            "type", type.name(),
            "state", state,
            "entityInfo", entityInfo,
            "bond", data.bond(),
            "ambient", ambientAction,
            "spawnRetrySec", spawnRetrySecondsRemaining(nextSpawnAttemptMillis)
        );
    }

    static String actionBarLine(BalanceConfig config, OwnedPetData data, PetType type, RuntimePet pet, LivingEntity entity, PetAbilityService abilityService, String actionBarHint, long actionBarHintUntilMillis, long nextSpawnAttemptMillis) {
        if (entity == null || entity.isDead()) {
            return PetHudSupport.spawnStatusLine(spawnRetrySecondsRemaining(nextSpawnAttemptMillis));
        }
        return hudLine(config, data, type, pet, entity, abilityService, actionBarHint, actionBarHintUntilMillis, nextSpawnAttemptMillis);
    }

    static String compactHudLine(BalanceConfig config, OwnedPetData data, PetType type, RuntimePet pet, LivingEntity entity, PetAbilityService abilityService, long nextSpawnAttemptMillis) {
        if (entity == null || entity.isDead()) {
            return PetHudSupport.compactMissingLine(data, type, abilityService, pet, spawnRetrySecondsRemaining(nextSpawnAttemptMillis));
        }
        return PetHudSupport.compactLine(data, type, abilityService, pet);
    }

    static Optional<String> overlayHint(String actionBarHint, long actionBarHintUntilMillis) {
        if (!PetSocialStateSupport.hasActionHint(actionBarHint, actionBarHintUntilMillis)) {
            return Optional.empty();
        }
        return Optional.of(PetHudSupport.localizedActionHint(actionBarHint));
    }

    static String hudLine(BalanceConfig config, OwnedPetData data, PetType type, RuntimePet pet, LivingEntity entity, PetAbilityService abilityService, String actionBarHint, long actionBarHintUntilMillis, long nextSpawnAttemptMillis) {
        String base = compactHudLine(config, data, type, pet, entity, abilityService, nextSpawnAttemptMillis);
        Optional<String> critical = PetHudSupport.criticalNote(data);
        if (critical.isPresent()) {
            return base + " | " + critical.get();
        }
        Optional<String> hint = overlayHint(actionBarHint, actionBarHintUntilMillis);
        return hint.map(message -> base + " | " + message).orElse(base);
    }

    static long spawnRetrySecondsRemaining(long nextSpawnAttemptMillis) {
        long remaining = nextSpawnAttemptMillis - System.currentTimeMillis();
        return remaining <= 0L ? 0L : Math.max(1L, remaining / 1000L);
    }

    static String rarityColor(OwnedPetData data) {
        return PetDisplaySupport.rarityColor(data);
    }

    static long actionHintUntilMillis(long durationMillis) {
        return PetSocialStateSupport.actionHintUntilMillis(durationMillis);
    }

    static String displayLabel(OwnedPetData data, PetType type) {
        return PetDisplaySupport.displayLabel(data, type);
    }
}
