package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class PetFollowSupport {
    private PetFollowSupport() {
    }

    static Location spawnLocation(
        Player owner,
        PetType type,
        OwnedPetData data,
        Location preferredLocation,
        Predicate<Location> canSpawnAt,
        Function<PetType, Double> followHeight,
        Function<PetType, Double> spawnDistance
    ) {
        if (preferredLocation != null && preferredLocation.getWorld() != null && preferredLocation.getWorld().equals(owner.getWorld())) {
            Location anchored = preferredLocation.clone();
            if (type.flying()) {
                anchored.add(0.0D, followHeight.apply(type), 0.0D);
            }
            if (canSpawnAt.test(anchored)) {
                return anchored;
            }
        }

        Vector forward = horizontalForward(owner);
        Location front = owner.getLocation().clone().add(forward.multiply(spawnDistance.apply(type)));
        if (type.flying()) {
            front.add(0.0D, followHeight.apply(type), 0.0D);
        }
        if (canSpawnAt.test(front)) {
            return front;
        }
        return safeCompanionLocation(owner, type, data, owner.getLocation(), canSpawnAt, followHeight);
    }

    static Location followLocation(
        Player owner,
        PetType type,
        OwnedPetData data,
        Predicate<Location> canSpawnAt,
        Function<PetType, Double> followHeight
    ) {
        return safeCompanionLocation(owner, type, data, rawFollowLocation(owner, type, data, followHeight), canSpawnAt, followHeight);
    }

    static Location rawFollowLocation(Player owner, PetType type, OwnedPetData data, Function<PetType, Double> followHeight) {
        Vector forward = horizontalForward(owner);
        Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
        double distance = Math.max(2.0D, Math.min(6.0D, data.followDistanceBand()));
        double angleRadians = switch (data.followPosition()) {
            case 0 -> 0.0D;
            case 1 -> Math.toRadians(45.0D);
            case 2 -> Math.toRadians(90.0D);
            case 3 -> Math.toRadians(135.0D);
            case 4 -> Math.toRadians(180.0D);
            case 5 -> Math.toRadians(225.0D);
            case 6 -> Math.toRadians(270.0D);
            case 7 -> Math.toRadians(315.0D);
            default -> 0.0D;
        };
        Vector offset = forward.clone().multiply(Math.cos(angleRadians) * distance)
            .add(side.multiply(Math.sin(angleRadians) * distance));
        Location location = owner.getLocation().clone().add(offset);
        if (type.flying()) {
            location.add(0.0D, followHeight.apply(type), 0.0D);
        }
        return location;
    }

    static Location safeCompanionLocation(
        Player owner,
        PetType type,
        OwnedPetData data,
        Location preferred,
        Predicate<Location> canSpawnAt,
        Function<PetType, Double> followHeight
    ) {
        if (canSpawnAt.test(preferred)) {
            return preferred;
        }

        Vector forward = horizontalForward(owner);
        Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
        double sideSign = Math.floorMod(data.petId().hashCode(), 2) == 0 ? 1.0D : -1.0D;
        double height = type.flying() ? followHeight.apply(type) : 0.0D;
        double[] sideDistances = {1.35D, 1.85D, 2.35D, 0.75D};
        double[] forwardDistances = {-0.45D, 0.35D, -1.0D, 0.9D};
        double[] yOffsets = type.flying() ? new double[] {0.0D, 0.5D, -0.35D} : new double[] {0.0D, 1.0D, -1.0D};

        for (double yOffset : yOffsets) {
            for (double sideDistance : sideDistances) {
                for (double forwardDistance : forwardDistances) {
                    Location candidate = owner.getLocation().clone()
                        .add(side.clone().multiply(sideSign * sideDistance))
                        .add(forward.clone().multiply(forwardDistance))
                        .add(0.0D, height + yOffset, 0.0D);
                    if (canSpawnAt.test(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return owner.getLocation();
    }

    static Location playfulCircleLocation(
        Player owner,
        PetType type,
        Function<Player, Location> fallback,
        Function<PetType, Double> followHeight,
        Predicate<Location> canSpawnAt
    ) {
        double phase = (System.currentTimeMillis() % 3_600L) / 3_600.0D * Math.PI * 2.0D;
        double radius = type.flying() ? 2.0D : 1.45D;
        Location location = owner.getLocation().clone().add(Math.cos(phase) * radius, type.flying() ? followHeight.apply(type) : 0.0D, Math.sin(phase) * radius);
        return canSpawnAt.test(location) ? location : fallback.apply(owner);
    }

    private static Vector horizontalForward(Player owner) {
        Vector forward = owner.getLocation().getDirection().clone().setY(0.0D);
        if (forward.lengthSquared() < 0.01D) {
            forward = new Vector(0.0D, 0.0D, 1.0D);
        }
        return forward.normalize();
    }
}
