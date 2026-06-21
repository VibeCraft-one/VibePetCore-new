package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class PetInteractionSupport {
    private PetInteractionSupport() {
    }

    static long emitMood(
        Player owner,
        LivingEntity entity,
        BalanceConfig config,
        PetType type,
        OwnedPetData data,
        PetState state,
        long nextEmotionMillis,
        boolean ownerResting
    ) {
        return PetInteractionEffects.emitMood(
            owner,
            entity,
            config,
            type,
            data,
            state,
            nextEmotionMillis,
            ownerResting,
            PetEmotionProfile.emotionHeight(type),
            PetEmotionProfile.profileParticle(type),
            PetEmotionProfile.profileParticleCount(type)
        );
    }

    static long playAmbientSound(LivingEntity entity, long nextAmbientSoundMillis, PetType type) {
        return PetInteractionEffects.playAmbientSound(
            entity,
            nextAmbientSoundMillis,
            PetEmotionProfile.responseSound(type),
            PetEmotionProfile.responsePitch(type)
        );
    }

    static void playSparEffect(LivingEntity entity, Location targetLocation, PetType type) {
        PetInteractionEffects.playSparEffect(entity, targetLocation, PetEmotionProfile.responsePitch(type));
    }

    static SocialState startSocialAction(
        Player owner,
        LivingEntity entity,
        RuntimePet other,
        AmbientAction action,
        long now,
        long nextAmbientSoundMillis,
        PetType type,
        Function<Player, Location> followLocation,
        Predicate<Location> canSpawnAt
    ) {
        long nextSocialActionMillis = PetSocialStateSupport.nextSocialActionMillis(now);
        long ambientActionUntilMillis = now + PetSocialProfile.durationMillis(action);
        Location ambientTarget = PetSocialGeometry.socialTarget(
            owner,
            entity,
            other.entity().orElse(null),
            action,
            type,
            ignored -> PetMovementProfile.followHeight(type),
            PetMotionSupport::horizontalForward,
            location -> canSpawnAt.test(location),
            followLocation::apply
        );
        PetInteractionEffects.playSocialEffect(
            entity,
            action,
            PetSocialProfile.particle(action, PetEmotionProfile.profileParticle(type)),
            PetSocialProfile.particleCount(action),
            PetEmotionProfile.emotionHeight(type)
        );
        long nextSoundMillis = PetInteractionEffects.playAmbientSound(
            entity,
            nextAmbientSoundMillis,
            PetEmotionProfile.responseSound(type),
            PetEmotionProfile.responsePitch(type)
        );
        return new SocialState(
            nextSocialActionMillis,
            action,
            ambientActionUntilMillis,
            ambientTarget,
            nextSoundMillis,
            PetAmbientSupport.actionCaption(action, type),
            2_400L
        );
    }

    static AmbientAction socialActionWith(PetType type, int evolutionStage, RuntimePet other) {
        return PetSocialProfile.actionWith(
            type == other.type(),
            evolutionStage,
            other.data().evolutionStage(),
            Math.random()
        );
    }

    static AmbientAction companionSocialAction(AmbientAction action) {
        return PetSocialProfile.companionAction(action);
    }

    static Sound responseSound(PetType type) {
        return PetEmotionProfile.responseSound(type);
    }

    record SocialState(
        long nextSocialActionMillis,
        AmbientAction ambientAction,
        long ambientActionUntilMillis,
        Location ambientTarget,
        long nextAmbientSoundMillis,
        String actionCaption,
        long actionCaptionDurationMillis
    ) {
    }
}
