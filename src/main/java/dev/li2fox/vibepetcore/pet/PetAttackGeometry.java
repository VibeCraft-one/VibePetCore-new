package dev.li2fox.vibepetcore.pet;

import org.bukkit.Location;
import org.bukkit.util.Vector;

final class PetAttackGeometry {
    private PetAttackGeometry() {
    }

    static Location offsetLocation(
        Location entityLocation,
        Location targetLocation,
        double radius,
        double sideOffset,
        double yOffset,
        boolean clockwise,
        boolean flying
    ) {
        Vector radial = entityLocation.toVector().subtract(targetLocation.toVector());
        radial.setY(0.0D);
        if (radial.lengthSquared() < 0.01D) {
            radial = new Vector(1.0D, 0.0D, 0.0D);
        } else {
            radial.normalize();
        }
        Vector lateral = new Vector(-radial.getZ(), 0.0D, radial.getX()).multiply(clockwise ? 1.0D : -1.0D);
        Location location = targetLocation.clone()
            .add(radial.multiply(radius))
            .add(lateral.multiply(sideOffset));
        if (flying) {
            location.add(0.0D, Math.max(0.35D, yOffset), 0.0D);
        }
        return location;
    }

    static Location passThroughLocation(Location entityLocation, Location targetLocation, double distanceBehind, boolean flying) {
        Vector direction = targetLocation.toVector().subtract(entityLocation.toVector());
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.01D) {
            direction = new Vector(1.0D, 0.0D, 0.0D);
        } else {
            direction.normalize();
        }
        Location location = targetLocation.clone().add(direction.multiply(distanceBehind));
        if (flying) {
            location.add(0.0D, 0.45D, 0.0D);
        }
        return location;
    }
}
