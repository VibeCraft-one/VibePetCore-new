package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class PetAmbientRuntimeSupport {
    private PetAmbientRuntimeSupport() {
    }

    static AmbientRuntimeDecision manualIdle(
        Player owner,
        LivingEntity entity,
        PetType type,
        OwnedPetData data,
        BalanceConfig config,
        AmbientAction ambientAction,
        Location target,
        Supplier<Location> followLocation,
        Supplier<Vector> restVelocity
    ) {
        if (entity.getLocation().distance(owner.getLocation()) > 10.0D) {
            return AmbientRuntimeDecision.returnToOwner(followLocation.get());
        }
        if (entity.getLocation().distance(target) < 0.85D) {
            return AmbientRuntimeDecision.rest(restVelocity.get(), 0.35D, true, ambientAction != AmbientAction.NONE);
        }
        return AmbientRuntimeDecision.move(target, config.petBaseSpeed() * config.petAmbientSpeedMultiplier(type));
    }

    static AmbientRuntimeDecision ambient(
        Player owner,
        LivingEntity entity,
        PetType type,
        OwnedPetData data,
        BalanceConfig config,
        AmbientAction ambientAction,
        int idleTicks,
        Location target,
        Supplier<Location> followLocation,
        Supplier<Vector> restVelocity
    ) {
        if (entity.getLocation().distance(owner.getLocation()) > 9.0D) {
            return AmbientRuntimeDecision.returnToOwner(followLocation.get());
        }
        if (entity.getLocation().distance(target) < 0.75D && shouldRestAtTarget(ambientAction, data, type)) {
            return AmbientRuntimeDecision.rest(restVelocity.get(), 0.32D, idleTicks % 18 == 0, ambientAction != AmbientAction.NONE);
        }
        double speed = Math.min(config.petBaseSpeed() * config.petAmbientSpeedMultiplier(type), config.petSprintSpeed() * 0.75D);
        return AmbientRuntimeDecision.move(target, speed);
    }

    private static boolean shouldRestAtTarget(AmbientAction ambientAction, OwnedPetData data, PetType type) {
        if (ambientAction == AmbientAction.REST_NEAR_OWNER
            || ambientAction == AmbientAction.WATCH_OWNER
            || ambientAction == AmbientAction.SOCIAL_GREET) {
            return true;
        }
        long phase = Math.floorMod(
            (System.currentTimeMillis() / 1_500L) + data.petId().getLeastSignificantBits(),
            PetEnvironmentSupport.restPhaseModulo(type)
        );
        return PetEnvironmentSupport.shouldRestAtPhase(type, phase);
    }

    record AmbientRuntimeDecision(
        boolean returnToOwner,
        boolean clearAmbientTarget,
        Location moveTarget,
        boolean rest,
        Vector restVelocity,
        double velocityBlend,
        boolean faceOwner,
        boolean maybePlayAmbientSound
    ) {
        static AmbientRuntimeDecision returnToOwner(Location moveTarget) {
            return new AmbientRuntimeDecision(true, true, moveTarget, false, null, 0.0D, false, false);
        }

        static AmbientRuntimeDecision rest(Vector restVelocity, double velocityBlend, boolean faceOwner, boolean maybePlayAmbientSound) {
            return new AmbientRuntimeDecision(false, false, null, true, restVelocity, velocityBlend, faceOwner, maybePlayAmbientSound);
        }

        static AmbientRuntimeDecision move(Location moveTarget, double speed) {
            return new AmbientRuntimeDecision(false, false, moveTarget, false, null, speed, false, false);
        }
    }
}
