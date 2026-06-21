package dev.li2fox.vibepetcore.pet;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class PetSocialRuntimeSupport {
    private PetSocialRuntimeSupport() {
    }

    static OwnerTrackState updateOwnerMovement(Location lastOwnerLocation, long lastOwnerMoveMillis, Player owner) {
        PetOwnerTrackingSupport.UpdateResult result = PetOwnerTrackingSupport.updateOwnerMovement(
            lastOwnerLocation,
            lastOwnerMoveMillis,
            owner.getLocation(),
            System.currentTimeMillis()
        );
        return new OwnerTrackState(result.moved(), result.location(), result.moveMillis());
    }

    static boolean ownerResting(long lastOwnerMoveMillis) {
        return PetOwnerTrackingSupport.ownerResting(lastOwnerMoveMillis);
    }

    static boolean canSocialize(LivingEntity entity, PetState state, long nextSocialActionMillis) {
        return PetSocialStateSupport.canSocialize(entity, state, nextSocialActionMillis);
    }

    static boolean shouldLookBackAtOwner(Player owner, LivingEntity entity) {
        return PetOwnerViewSupport.ownerLooksAtPet(owner, entity);
    }

    static boolean canStartSocial(Player owner, RuntimePet self, RuntimePet other) {
        if (other == null || other == self) {
            return false;
        }
        Optional<LivingEntity> selfEntity = self.entity();
        Optional<LivingEntity> otherEntity = other.entity();
        if (selfEntity.isEmpty() || otherEntity.isEmpty()) {
            return false;
        }
        LivingEntity left = selfEntity.get();
        LivingEntity right = otherEntity.get();
        return left.getWorld().equals(right.getWorld()) && left.getLocation().distanceSquared(right.getLocation()) <= 36.0D;
    }

    record OwnerTrackState(boolean moved, Location location, long moveMillis) {
    }
}
