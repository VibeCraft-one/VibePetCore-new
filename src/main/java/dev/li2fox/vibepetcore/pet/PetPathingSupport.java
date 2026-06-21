package dev.li2fox.vibepetcore.pet;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

final class PetPathingSupport {
    private PetPathingSupport() {
    }

    static boolean canStandAt(Location location, boolean flying) {
        if (location.getWorld() == null) {
            return false;
        }
        if (flying) {
            return location.getBlock().isPassable();
        }
        return location.getBlock().isPassable() && location.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable();
    }

    static boolean blockedAhead(LivingEntity entity, boolean flying, Vector direction, double distance) {
        if (entity == null || flying || direction == null) {
            return false;
        }
        Vector horizontal = direction.clone().setY(0.0D);
        if (horizontal.lengthSquared() < 0.01D) {
            horizontal = entity.getLocation().getDirection().clone().setY(0.0D);
        }
        if (horizontal.lengthSquared() < 0.01D) {
            return false;
        }
        Location feet = entity.getLocation().clone().add(horizontal.normalize().multiply(distance));
        return !feet.getBlock().isPassable() || !feet.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable();
    }
}
