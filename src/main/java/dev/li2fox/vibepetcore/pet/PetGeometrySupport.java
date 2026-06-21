package dev.li2fox.vibepetcore.pet;

import org.bukkit.Location;
import org.bukkit.util.Vector;

final class PetGeometrySupport {
    private PetGeometrySupport() {
    }

    static boolean withinNearbyBox(Location center, Location candidate, double xRadius, double yRadius, double zRadius) {
        return Math.abs(candidate.getX() - center.getX()) <= xRadius
            && Math.abs(candidate.getY() - center.getY()) <= yRadius
            && Math.abs(candidate.getZ() - center.getZ()) <= zRadius;
    }

    static Location behindOwner(org.bukkit.entity.Player owner, Vector forward, double height, double distance) {
        return owner.getLocation().clone().add(forward.multiply(-distance)).add(0.0D, height, 0.0D);
    }

    static Location awayFrom(Location danger, Location owner, Vector fallbackForward, double height, double distance) {
        Vector away = owner.toVector().subtract(danger.toVector()).setY(0.0D);
        if (away.lengthSquared() < 0.01D) {
            away = fallbackForward.multiply(-1.0D);
        }
        return owner.clone().add(away.normalize().multiply(distance)).add(0.0D, height, 0.0D);
    }

    static Location between(Location owner, Location enemy, double height) {
        Vector toEnemy = enemy.toVector().subtract(owner.toVector()).setY(0.0D);
        if (toEnemy.lengthSquared() < 0.01D) {
            return owner.clone();
        }
        return owner.clone().add(toEnemy.normalize().multiply(2.2D)).add(0.0D, height, 0.0D);
    }
}
