package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class PetStuckSupport {
    private PetStuckSupport() {
    }

    record StuckSnapshot(Location currentLocation, long now, long lastMoveMillis, boolean resetTracking) {
    }

    static StuckSnapshot snapshot(LivingEntity entity, PetType type, Player owner, Location lastEntityLocation, long lastEntityMoveMillis) {
        if (entity == null || entity.isDead()) {
            return new StuckSnapshot(null, lastEntityMoveMillis, lastEntityMoveMillis, false);
        }
        Location current = entity.getLocation();
        long now = System.currentTimeMillis();
        boolean changedPosition = lastEntityLocation != null && lastEntityLocation.getWorld() != null && lastEntityLocation.getWorld().equals(current.getWorld())
            && movedEnoughForProgress(lastEntityLocation, current, type);
        boolean movedTowardOwner = changedPosition && movedTowardOwner(lastEntityLocation, current, owner);
        boolean reset = lastEntityLocation == null
            || lastEntityLocation.getWorld() == null
            || !lastEntityLocation.getWorld().equals(current.getWorld())
            || movedTowardOwner;
        return new StuckSnapshot(current.clone(), now, lastEntityMoveMillis, reset);
    }

    static boolean shouldRecover(StuckSnapshot snapshot, Player owner, BalanceConfig config) {
        return snapshot.now() - snapshot.lastMoveMillis() >= 2_500L
            && snapshot.currentLocation().distanceSquared(owner.getLocation()) > Math.pow(config.followMaxRadius() + 1.5D, 2.0D);
    }

    static boolean shouldSideStep(PetType type, boolean blockedAhead, long now, long lastHopMillis) {
        return !type.flying() && blockedAhead && now - lastHopMillis > 350L;
    }

    static boolean shouldPullCloserWhenBlocked(StuckSnapshot snapshot, Player owner, BalanceConfig config, boolean blockedAhead) {
        return blockedAhead
            && snapshot.now() - snapshot.lastMoveMillis() >= 2_800L
            && horizontalDistanceSquared(snapshot.currentLocation(), owner.getLocation()) > Math.pow(config.followMaxRadius() + 2.0D, 2.0D);
    }

    static boolean shouldTeleport(StuckSnapshot snapshot, Player owner, BalanceConfig config) {
        return snapshot.currentLocation().distanceSquared(owner.getLocation()) > Math.pow(config.returnDistance() + 2.0D, 2.0D);
    }

    static boolean shouldTeleportWhenBlocked(StuckSnapshot snapshot, Player owner, BalanceConfig config, boolean blockedAhead) {
        return blockedAhead
            && snapshot.now() - snapshot.lastMoveMillis() >= 4_500L
            && snapshot.currentLocation().distanceSquared(owner.getLocation()) > Math.pow(config.followMaxRadius() + 1.5D, 2.0D);
    }

    static boolean shouldTeleportWhenBelowOwner(StuckSnapshot snapshot, Player owner, BalanceConfig config) {
        return snapshot.now() - snapshot.lastMoveMillis() >= 3_500L
            && owner.getLocation().getY() - snapshot.currentLocation().getY() > 1.25D
            && horizontalDistanceSquared(snapshot.currentLocation(), owner.getLocation()) > Math.pow(config.followMaxRadius() + 0.75D, 2.0D);
    }

    static boolean shouldPullCloser(StuckSnapshot snapshot, Player owner, BalanceConfig config) {
        return owner.isSprinting()
            && snapshot.currentLocation().distanceSquared(owner.getLocation()) > Math.pow(config.followMaxRadius() + 4.0D, 2.0D);
    }

    private static boolean movedEnoughForProgress(Location previous, Location current, PetType type) {
        double dx = previous.getX() - current.getX();
        double dz = previous.getZ() - current.getZ();
        double horizontalSquared = dx * dx + dz * dz;
        if (!type.flying()) {
            return horizontalSquared > 0.0225D;
        }
        return horizontalSquared + Math.pow(previous.getY() - current.getY(), 2.0D) > 0.035D;
    }

    private static boolean movedTowardOwner(Location previous, Location current, Player owner) {
        if (owner == null || owner.getWorld() == null || previous == null || current == null || !owner.getWorld().equals(current.getWorld())) {
            return false;
        }
        double previousDistance = horizontalDistanceSquared(previous, owner.getLocation());
        double currentDistance = horizontalDistanceSquared(current, owner.getLocation());
        return currentDistance + 0.20D < previousDistance;
    }

    private static double horizontalDistanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }
}
