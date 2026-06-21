package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class PetNavigationSupport {
    private PetNavigationSupport() {
    }

    static Location playfulCircleLocation(Player owner, PetType type, Function<Player, Location> followLocation, Predicate<Location> canSpawnAt) {
        return PetFollowSupport.playfulCircleLocation(owner, type, followLocation, ignored -> PetMovementProfile.followHeight(type), canSpawnAt);
    }

    static Optional<Location> interestingLocation(Player owner, LivingEntity entity, PetType type, BalanceConfig config) {
        return PetInterestLocator.interestingLocation(
            owner,
            entity,
            type,
            config,
            PetMotionSupport.horizontalForward(owner),
            PetMovementProfile.spawnDistance(type),
            PetMovementProfile.followHeight(type)
        );
    }

    static boolean canSpawnAt(Location location, PetType type) {
        return PetPathingSupport.canStandAt(location, type.flying());
    }

    static boolean blockedAhead(LivingEntity entity, PetType type, Vector smoothedVelocity) {
        return PetPathingSupport.blockedAhead(entity, type.flying(), smoothedVelocity, 0.6D);
    }

    static boolean blockedAhead(LivingEntity entity, PetType type, Vector direction, double distance, Vector smoothedVelocity) {
        if (entity == null || type.flying() || direction == null) {
            return false;
        }
        Vector horizontal = direction.clone().setY(0.0D);
        if (horizontal.lengthSquared() < 0.01D) {
            return blockedAhead(entity, type, smoothedVelocity);
        }
        double safeDistance = Math.max(0.35D, Math.min(1.35D, distance));
        return PetPathingSupport.blockedAhead(entity, false, horizontal, safeDistance);
    }
}
