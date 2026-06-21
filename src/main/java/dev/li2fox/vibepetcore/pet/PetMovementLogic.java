package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class PetMovementLogic {
    private PetMovementLogic() {
    }

    static MoveResult moveToward(
        LivingEntity entity,
        PetType type,
        OwnedPetData data,
        Vector smoothedVelocity,
        Location target,
        double speed,
        int idleTicks,
        long lastHopMillis,
        Supplier<Vector> restVelocity,
        DoubleSupplier hoverBob,
        BiFunction<Vector, Double, Boolean> blockedAhead
    ) {
        Vector direction = target.toVector().subtract(entity.getLocation().toVector());
        if (direction.lengthSquared() < 0.09D) {
            return setSmoothedVelocity(entity, type, smoothedVelocity, type.flying() ? new Vector(0.0D, hoverBob.getAsDouble(), 0.0D) : restVelocity.get(), 0.35D, lastHopMillis);
        }

        if (type.flying()) {
            Vector velocity = direction.normalize().multiply(Math.min(speed, 0.72D));
            velocity.setY(velocity.getY() + Math.sin(idleTicks * 0.23D + data.petId().hashCode()) * hoverBob.getAsDouble());
            face(entity, direction);
            return setSmoothedVelocity(entity, type, smoothedVelocity, velocity, 0.42D, lastHopMillis);
        }

        Vector flat = new Vector(direction.getX(), 0.0D, direction.getZ());
        if (flat.lengthSquared() < 0.04D) {
            return setSmoothedVelocity(entity, type, smoothedVelocity, restVelocity.get(), 0.35D, lastHopMillis);
        }

        Vector flatDirection = flat.clone().normalize();
        Vector velocity = flatDirection.clone().multiply(Math.min(speed, 0.55D));
        HopResult hop = groundYVelocity(entity, type, data, idleTicks, lastHopMillis, direction.getY(), flat.length(), flatDirection, blockedAhead);
        velocity.setY(hop.yVelocity());
        face(entity, flat);
        return setSmoothedVelocity(entity, type, smoothedVelocity, velocity, 0.48D, hop.lastHopMillis());
    }

    static MoveResult moveFollow(
        Player owner,
        LivingEntity entity,
        PetType type,
        OwnedPetData data,
        BalanceConfig config,
        Vector smoothedVelocity,
        int idleTicks,
        long lastHopMillis,
        Supplier<Location> followLocation,
        Supplier<Vector> restVelocity,
        DoubleSupplier idlePull,
        DoubleSupplier hoverBob,
        BiFunction<Vector, Double, Boolean> blockedAhead
    ) {
        Location target = followLocation.get();
        Vector offset = target.toVector().subtract(entity.getLocation().toVector());
        double distance = offset.length();
        if (distance < config.followMinRadius()) {
            Vector push = offset.lengthSquared() < 0.01D
                ? owner.getLocation().getDirection().setY(0.0D).multiply(-0.08D)
                : offset.normalize().multiply(-0.12D);
            if (type.flying()) {
                push.setY(0.02D);
            } else {
                push.setY(restVelocity.get().getY());
            }
            return setSmoothedVelocity(entity, type, smoothedVelocity, push, 0.25D, lastHopMillis);
        }

        boolean ownerSprinting = owner.isSprinting();
        double sprintCatchUpSpeed = ownerSprinting ? config.petSprintSpeed() + 0.08D : config.petSprintSpeed();
        double speed = PetMovementProfile.movementSpeed(type, ownerSprinting ? sprintCatchUpSpeed : config.petBaseSpeed());
        if (distance <= config.followMaxRadius()) {
            Vector idle = target.toVector().subtract(entity.getLocation().toVector()).multiply(idlePull.getAsDouble());
            if (type.flying()) {
                idle.setY(Math.sin(idleTicks * 0.2D) * hoverBob.getAsDouble());
            } else {
                idle.setY(restVelocity.get().getY());
            }
            return setSmoothedVelocity(entity, type, smoothedVelocity, idle, 0.18D, lastHopMillis);
        }

        return moveToward(
            entity,
            type,
            data,
            smoothedVelocity,
            target,
            Math.min(speed + (distance - config.followMaxRadius()) * 0.025D, sprintCatchUpSpeed),
            idleTicks,
            lastHopMillis,
            restVelocity,
            hoverBob,
            blockedAhead
        );
    }

    static void face(LivingEntity entity, Location target) {
        if (entity == null || entity.isDead() || !entity.getWorld().equals(target.getWorld())) {
            return;
        }
        face(entity, target.toVector().subtract(entity.getEyeLocation().toVector()));
    }

    private static MoveResult setSmoothedVelocity(
        LivingEntity entity,
        PetType type,
        Vector smoothedVelocity,
        Vector desired,
        double blend,
        long lastHopMillis
    ) {
        double desiredY = desired.getY();
        Vector nextVelocity = smoothedVelocity.multiply(1.0D - blend).add(desired.clone().multiply(blend));
        if (!type.flying()) {
            if (desiredY > 0.35D) {
                nextVelocity.setY(desiredY);
            }
            nextVelocity.setY(Math.max(-0.65D, Math.min(0.50D, nextVelocity.getY())));
        }
        if (type.flying() && nextVelocity.length() > 0.8D) {
            nextVelocity.normalize().multiply(0.8D);
        } else if (!type.flying()) {
            nextVelocity = capGroundHorizontalSpeed(nextVelocity, 0.8D);
        }
        entity.setVelocity(nextVelocity);
        return new MoveResult(nextVelocity, lastHopMillis);
    }

    static MoveResult smoothVelocity(
        LivingEntity entity,
        PetType type,
        Vector smoothedVelocity,
        Vector desired,
        double blend,
        long lastHopMillis
    ) {
        return setSmoothedVelocity(entity, type, smoothedVelocity, desired, blend, lastHopMillis);
    }

    private static Vector capGroundHorizontalSpeed(Vector velocity, double maxSpeed) {
        Vector horizontal = velocity.clone().setY(0.0D);
        if (horizontal.length() <= maxSpeed) {
            return velocity;
        }
        horizontal.normalize().multiply(maxSpeed);
        velocity.setX(horizontal.getX());
        velocity.setZ(horizontal.getZ());
        return velocity;
    }

    private static void face(LivingEntity entity, Vector direction) {
        if (direction.lengthSquared() < 0.01D) {
            return;
        }
        double yaw = Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        entity.setRotation((float) yaw, 0.0F);
    }

    private static HopResult groundYVelocity(
        LivingEntity entity,
        PetType type,
        OwnedPetData data,
        int idleTicks,
        long lastHopMillis,
        double verticalGap,
        double horizontalDistance,
        Vector horizontalDirection,
        BiFunction<Vector, Double, Boolean> blockedAhead
    ) {
        double currentY = entity.getVelocity().getY();
        if (!entity.isOnGround()) {
            return new HopResult(Math.max(-0.65D, Math.min(0.28D, currentY)), lastHopMillis);
        }
        long now = System.currentTimeMillis();
        boolean smallStep = verticalGap > 0.42D && verticalGap < 1.55D && horizontalDistance < 3.2D;
        boolean rabbitHop = type == PetType.RABBIT && horizontalDistance > 0.55D;
        boolean livelyHop = (type == PetType.FOX || type == PetType.CAT) && horizontalDistance > 1.2D && idleTicks % 10 == 0;
        boolean blockedHop = horizontalDistance > 0.25D && blockedAhead.apply(horizontalDirection, 1.15D);
        if ((smallStep || rabbitHop || livelyHop || blockedHop) && now - lastHopMillis > PetMovementProfile.hopCooldownMillis(type)) {
            long nextHopMillis = now;
            double yVelocity = (smallStep || blockedHop)
                ? PetMovementProfile.obstacleJumpStrength(type, data.level())
                : PetMovementProfile.playfulJumpStrength(type);
            return new HopResult(yVelocity, nextHopMillis);
        }
        return new HopResult(0.0D, lastHopMillis);
    }

    record MoveResult(Vector smoothedVelocity, long lastHopMillis) {
    }

    private record HopResult(double yVelocity, long lastHopMillis) {
    }
}
