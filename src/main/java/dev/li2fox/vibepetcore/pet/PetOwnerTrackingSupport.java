package dev.li2fox.vibepetcore.pet;

import org.bukkit.Location;

final class PetOwnerTrackingSupport {
    private PetOwnerTrackingSupport() {
    }

    record UpdateResult(Location location, long moveMillis, boolean moved) {
    }

    static UpdateResult updateOwnerMovement(Location lastOwnerLocation, long lastOwnerMoveMillis, Location current, long now) {
        if (lastOwnerLocation == null
            || lastOwnerLocation.getWorld() == null
            || !lastOwnerLocation.getWorld().equals(current.getWorld())
            || lastOwnerLocation.distanceSquared(current) > 0.0064D) {
            return new UpdateResult(current.clone(), now, true);
        }
        return new UpdateResult(lastOwnerLocation, lastOwnerMoveMillis, false);
    }

    static boolean ownerResting(long lastOwnerMoveMillis) {
        return System.currentTimeMillis() - lastOwnerMoveMillis >= 3_000L;
    }
}
