package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class PetMotionSupport {
    private PetMotionSupport() {
    }

    static Vector horizontalForward(Player owner) {
        Vector forward = owner.getLocation().getDirection().clone().setY(0.0D);
        if (forward.lengthSquared() < 0.01D) {
            forward = new Vector(0.0D, 0.0D, 1.0D);
        }
        return forward.normalize();
    }

    static Vector restVelocity(PetType type, LivingEntity entity, double hoverBob) {
        return new Vector(0.0D, type.flying() ? hoverBob * 0.55D : entity.getVelocity().getY(), 0.0D);
    }

    static void applyScale(LivingEntity entity, BalanceConfig config, PetType type, int evolutionStage) {
        try {
            var scale = entity.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(config.petScale(type, evolutionStage));
            }
        } catch (RuntimeException ignored) {
        }
    }

    static void teleportNearOwner(
        LivingEntity entity,
        Player owner,
        PetType type,
        OwnedPetData data,
        java.util.function.Function<PetType, Double> followHeight,
        java.util.function.Predicate<Location> canSpawnAt
    ) {
        Location target = PetFollowSupport.safeCompanionLocation(owner, type, data, PetFollowSupport.rawFollowLocation(owner, type, data, followHeight), canSpawnAt, followHeight);
        entity.teleport(target);
    }

    static void teleportCloserToOwner(
        LivingEntity entity,
        Player owner,
        PetType type,
        OwnedPetData data,
        java.util.function.Function<PetType, Double> followHeight,
        java.util.function.Predicate<Location> canSpawnAt
    ) {
        Location ownerLocation = owner.getLocation();
        Location entityLocation = entity.getLocation();
        if (!ownerLocation.getWorld().equals(entityLocation.getWorld())) {
            teleportNearOwner(entity, owner, type, data, followHeight, canSpawnAt);
            return;
        }
        Vector fromOwnerToPet = entityLocation.toVector().subtract(ownerLocation.toVector());
        if (fromOwnerToPet.lengthSquared() < 9.0D) {
            return;
        }
        double trailingDistance = Math.max(3.0D, Math.min(6.0D, data.followDistanceBand() + 1.0D));
        Location preferred = ownerLocation.clone().add(fromOwnerToPet.normalize().multiply(trailingDistance));
        if (type.flying()) {
            preferred.add(0.0D, followHeight.apply(type), 0.0D);
        }
        Location target = PetFollowSupport.safeCompanionLocation(owner, type, data, preferred, canSpawnAt, followHeight);
        entity.teleport(target);
    }

    static Location sideStepLocation(
        Player owner,
        LivingEntity entity,
        OwnedPetData data,
        java.util.function.Function<Player, Location> fallback,
        java.util.function.Predicate<Location> canSpawnAt
    ) {
        Vector forward = horizontalForward(owner);
        Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
        double sideSign = Math.floorMod((int) (entity.getTicksLived() + data.petId().hashCode()), 2) == 0 ? 1.0D : -1.0D;
        Location candidate = entity.getLocation().clone()
            .add(side.multiply(sideSign * 1.2D))
            .add(forward.multiply(0.45D));
        return canSpawnAt.test(candidate) ? candidate : fallback.apply(owner);
    }
}
