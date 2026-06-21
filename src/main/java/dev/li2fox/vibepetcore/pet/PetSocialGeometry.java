package dev.li2fox.vibepetcore.pet;

import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class PetSocialGeometry {
    private PetSocialGeometry() {
    }

    static Location socialTarget(
        Player owner,
        LivingEntity entity,
        LivingEntity otherEntity,
        AmbientAction action,
        PetType type,
        Function<PetType, Double> followHeight,
        Function<Player, Vector> horizontalForward,
        Predicate<Location> canSpawnAt,
        Function<Player, Location> followLocation
    ) {
        Location otherLocation = otherEntity.getLocation();
        Vector fromOther = entity.getLocation().toVector().subtract(otherLocation.toVector()).setY(0.0D);
        if (fromOther.lengthSquared() < 0.01D) {
            fromOther = horizontalForward.apply(owner);
        }
        double distance = action == AmbientAction.SOCIAL_PLAY ? 1.55D : 1.05D;
        Location target = otherLocation.clone().add(fromOther.normalize().multiply(distance));
        if (type.flying()) {
            target.add(0.0D, followHeight.apply(type), 0.0D);
        }
        return canSpawnAt.test(target) ? target : followLocation.apply(owner);
    }
}
