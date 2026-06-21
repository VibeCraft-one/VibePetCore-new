package dev.li2fox.vibepetcore.pet;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class PetOwnerViewSupport {
    private PetOwnerViewSupport() {
    }

    static boolean ownerLooksAtPet(Player owner, LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.getWorld().equals(owner.getWorld())) {
            return false;
        }
        Location eye = owner.getEyeLocation();
        Vector toPet = entity.getEyeLocation().toVector().subtract(eye.toVector());
        double distance = toPet.length();
        if (distance < 0.6D || distance > 10.0D || !owner.hasLineOfSight(entity)) {
            return false;
        }
        return eye.getDirection().normalize().dot(toPet.normalize()) > 0.965D;
    }
}
