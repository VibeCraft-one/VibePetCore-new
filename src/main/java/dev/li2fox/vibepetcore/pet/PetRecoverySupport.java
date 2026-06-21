package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class PetRecoverySupport {
    private PetRecoverySupport() {
    }

    static RecoveryResult handleStuck(
        Player owner,
        LivingEntity entity,
        PetType type,
        BalanceConfig config,
        Location lastEntityLocation,
        long lastEntityMoveMillis,
        long lastHopMillis,
        boolean blockedAhead,
        Supplier<Location> sideStepLocation,
        Supplier<Location> followLocation
    ) {
        PetStuckSupport.StuckSnapshot snapshot = PetStuckSupport.snapshot(entity, type, owner, lastEntityLocation, lastEntityMoveMillis);
        if (snapshot.resetTracking()) {
            return RecoveryResult.reset(snapshot.currentLocation(), snapshot.now(), lastHopMillis);
        }
        if (!PetStuckSupport.shouldRecover(snapshot, owner, config)) {
            return RecoveryResult.none();
        }

        if (PetStuckSupport.shouldTeleportWhenBlocked(snapshot, owner, config, blockedAhead)) {
            return RecoveryResult.teleport(snapshot.now());
        }
        if (!type.flying() && PetStuckSupport.shouldTeleportWhenBelowOwner(snapshot, owner, config)) {
            return RecoveryResult.teleport(snapshot.now());
        }
        if (PetStuckSupport.shouldPullCloserWhenBlocked(snapshot, owner, config, blockedAhead)) {
            return RecoveryResult.pullCloser(snapshot.now());
        }
        if (PetStuckSupport.shouldSideStep(type, blockedAhead, snapshot.now(), lastHopMillis)) {
            return RecoveryResult.sideStep(sideStepLocation.get(), snapshot.currentLocation(), snapshot.lastMoveMillis(), snapshot.now());
        }
        if (PetStuckSupport.shouldTeleport(snapshot, owner, config)) {
            return RecoveryResult.teleport(snapshot.now());
        }
        if (PetStuckSupport.shouldPullCloser(snapshot, owner, config)) {
            return RecoveryResult.pullCloser(snapshot.now());
        }
        return RecoveryResult.follow(followLocation.get(), snapshot.now());
    }

    record RecoveryResult(
        boolean resetTracking,
        boolean recover,
        boolean sideStep,
        boolean teleport,
        boolean pullCloser,
        Location moveTarget,
        Location trackedLocation,
        long trackedMoveMillis,
        long lastHopMillis
    ) {
        static RecoveryResult none() {
            return new RecoveryResult(false, false, false, false, false, null, null, 0L, 0L);
        }

        static RecoveryResult reset(Location trackedLocation, long trackedMoveMillis, long lastHopMillis) {
            return new RecoveryResult(true, false, false, false, false, null, trackedLocation, trackedMoveMillis, lastHopMillis);
        }

        static RecoveryResult sideStep(Location moveTarget, Location trackedLocation, long trackedMoveMillis, long lastHopMillis) {
            return new RecoveryResult(false, true, true, false, false, moveTarget, trackedLocation, trackedMoveMillis, lastHopMillis);
        }

        static RecoveryResult teleport(long trackedMoveMillis) {
            return new RecoveryResult(false, true, false, true, false, null, null, trackedMoveMillis, 0L);
        }

        static RecoveryResult pullCloser(long trackedMoveMillis) {
            return new RecoveryResult(false, true, false, false, true, null, null, trackedMoveMillis, 0L);
        }

        static RecoveryResult follow(Location moveTarget, long trackedMoveMillis) {
            return new RecoveryResult(false, true, false, false, false, moveTarget, null, trackedMoveMillis, 0L);
        }
    }
}
