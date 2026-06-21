package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class PetAmbientSupport {
    private PetAmbientSupport() {
    }

    static AmbientDecision ambientLocation(
        Player owner,
        LivingEntity entity,
        PetType type,
        OwnedPetData data,
        BalanceConfig config,
        boolean manualIdle,
        boolean ownerResting,
        Location ambientTarget,
        long ambientRetargetMillis,
        AmbientAction ambientAction,
        long ambientActionUntilMillis,
        long nextAmbientActionMillis,
        Function<Player, Optional<Location>> interestingLocation,
        Function<Player, Location> followLocation,
        Function<Player, Location> playfulCircleLocation,
        Function<Player, Vector> horizontalForward,
        Predicate<Location> canSpawnAt
    ) {
        long now = System.currentTimeMillis();
        AmbientState state = expireAmbientAction(ambientTarget, ambientRetargetMillis, ambientAction, ambientActionUntilMillis, nextAmbientActionMillis, now);
        if (state.action() == AmbientAction.PLAYFUL_CIRCLE) {
            return new AmbientDecision(playfulCircleLocation.apply(owner), state, null, 0L);
        }
        if (state.target() != null
            && state.target().getWorld() != null
            && state.target().getWorld().equals(owner.getWorld())
            && now < state.retargetMillis()
            && entity.getLocation().distanceSquared(state.target()) > 0.36D) {
            return new AmbientDecision(state.target().clone(), state, null, 0L);
        }

        Optional<Location> interest = interestingLocation.apply(owner);
        if (interest.isPresent()) {
            Location target = interest.get();
            AmbientState updated = new AmbientState(
                target,
                now + ThreadLocalRandom.current().nextLong(1_200L, 3_500L),
                state.action(),
                state.actionUntilMillis(),
                state.nextActionMillis()
            );
            return new AmbientDecision(target.clone(), updated, null, 0L);
        }

        AmbientDecision started = maybeStartAmbientAction(
            owner,
            entity,
            type,
            data,
            config,
            manualIdle,
            ownerResting,
            state,
            followLocation,
            horizontalForward,
            canSpawnAt
        );
        state = started.state();

        Optional<Location> actionTarget = ambientActionLocation(
            owner,
            type,
            data,
            state.target(),
            state.action(),
            followLocation,
            horizontalForward,
            canSpawnAt
        );
        if (actionTarget.isPresent()) {
            Location target = actionTarget.get();
            AmbientState updated = new AmbientState(
                target,
                Math.min(state.actionUntilMillis(), now + ThreadLocalRandom.current().nextLong(1_400L, 3_200L)),
                state.action(),
                state.actionUntilMillis(),
                state.nextActionMillis()
            );
            return new AmbientDecision(target.clone(), updated, started.hint(), started.hintDurationMillis());
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double radius = random.nextDouble(manualIdle ? 1.5D : 2.4D, config.petRoamRadius(type, manualIdle));
        double angle = random.nextDouble(Math.PI * 2.0D);
        Vector offset = new Vector(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);

        if (type == PetType.WOLF && !manualIdle) {
            Vector forward = horizontalForward.apply(owner);
            Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
            offset = forward.multiply(random.nextDouble(1.5D, 4.0D))
                .add(side.multiply(random.nextDouble(-2.0D, 2.0D)));
        }

        Location target = owner.getLocation().clone().add(offset);
        if (type.flying()) {
            target.add(0.0D, PetMovementProfile.followHeight(type) + random.nextDouble(-0.35D, 0.65D), 0.0D);
        }
        AmbientState updated = new AmbientState(
            target,
            now + random.nextLong(manualIdle ? 2_500L : 3_500L, manualIdle ? 6_000L : 8_500L),
            state.action(),
            state.actionUntilMillis(),
            state.nextActionMillis()
        );
        return new AmbientDecision(target.clone(), updated, started.hint(), started.hintDurationMillis());
    }

    static String actionCaption(AmbientAction action, PetType type) {
        return PetAmbientProfile.actionCaption(action, type);
    }

    private static AmbientDecision maybeStartAmbientAction(
        Player owner,
        LivingEntity entity,
        PetType type,
        OwnedPetData data,
        BalanceConfig config,
        boolean manualIdle,
        boolean ownerResting,
        AmbientState state,
        Function<Player, Location> followLocation,
        Function<Player, Vector> horizontalForward,
        Predicate<Location> canSpawnAt
    ) {
        long now = System.currentTimeMillis();
        if (!config.petEmotionEnabled(type) || now < state.nextActionMillis() || (!manualIdle && !ownerResting)) {
            return new AmbientDecision(null, state, null, 0L);
        }

        AmbientState updated = new AmbientState(
            state.target(),
            state.retargetMillis(),
            state.action(),
            state.actionUntilMillis(),
            now + ThreadLocalRandom.current().nextLong(
                PetAmbientProfile.minDelayMillis(data.evolutionStage()),
                PetAmbientProfile.maxDelayMillis(data.evolutionStage())
            )
        );
        if (entity.getLocation().distanceSquared(owner.getLocation()) > 100.0D
            || ThreadLocalRandom.current().nextDouble() > PetAmbientProfile.actionChance(data.evolutionStage())) {
            return new AmbientDecision(null, updated, null, 0L);
        }

        Material hand = owner.getInventory().getItemInMainHand().getType();
        if (hand != Material.AIR && (config.isPetFood(type, hand) || config.isPetRareResource(type, hand) || config.isEvolutionItem(hand))) {
            return startAmbientAction(updated, AmbientAction.INSPECT_HAND, now, 3_600L, "inspects item");
        }

        Optional<Location> groundInterest = nearbyGroundInterest(owner, type, canSpawnAt);
        if (groundInterest.isPresent() && ThreadLocalRandom.current().nextDouble() < 0.35D) {
            return startAmbientAction(
                new AmbientState(
                    groundInterest.get(),
                    updated.retargetMillis(),
                    updated.action(),
                    updated.actionUntilMillis(),
                    updated.nextActionMillis()
                ),
                AmbientAction.INSPECT_GROUND,
                now,
                4_200L,
                PetAmbientProfile.groundInterestCaption(type)
            );
        }

        AmbientAction next = PetAmbientProfile.randomAction(data.evolutionStage(), ThreadLocalRandom.current().nextDouble());
        return startAmbientAction(
            updated,
            next,
            now,
            PetAmbientProfile.actionDurationMillis(next, data.evolutionStage()),
            PetAmbientProfile.actionCaption(next, type)
        );
    }

    private static AmbientDecision startAmbientAction(AmbientState state, AmbientAction action, long now, long durationMillis, String caption) {
        AmbientState updated = new AmbientState(
            state.target(),
            0L,
            action,
            now + durationMillis,
            state.nextActionMillis()
        );
        return new AmbientDecision(null, updated, caption, Math.min(2_400L, durationMillis));
    }

    private static AmbientState expireAmbientAction(
        Location ambientTarget,
        long ambientRetargetMillis,
        AmbientAction ambientAction,
        long ambientActionUntilMillis,
        long nextAmbientActionMillis,
        long now
    ) {
        if (ambientAction != AmbientAction.NONE && now >= ambientActionUntilMillis) {
            return new AmbientState(ambientTarget, ambientRetargetMillis, AmbientAction.NONE, 0L, nextAmbientActionMillis);
        }
        return new AmbientState(ambientTarget, ambientRetargetMillis, ambientAction, ambientActionUntilMillis, nextAmbientActionMillis);
    }

    private static Optional<Location> ambientActionLocation(
        Player owner,
        PetType type,
        OwnedPetData data,
        Location ambientTarget,
        AmbientAction ambientAction,
        Function<Player, Location> followLocation,
        Function<Player, Vector> horizontalForward,
        Predicate<Location> canSpawnAt
    ) {
        return switch (ambientAction) {
            case REST_NEAR_OWNER -> Optional.of(relativeOwnerLocation(owner, type, data, -0.45D, ownerSideSign(data) * 0.9D, followLocation, horizontalForward, canSpawnAt));
            case WATCH_OWNER -> Optional.of(relativeOwnerLocation(owner, type, data, 1.25D, ownerSideSign(data) * 0.55D, followLocation, horizontalForward, canSpawnAt));
            case INSPECT_HAND -> Optional.of(relativeOwnerLocation(owner, type, data, 0.95D, ownerSideSign(data) * 0.35D, followLocation, horizontalForward, canSpawnAt));
            case CURIOUS_STEP -> Optional.ofNullable(ambientTarget).or(() -> Optional.of(relativeOwnerLocation(
                owner,
                type,
                data,
                ThreadLocalRandom.current().nextDouble(-1.2D, 1.4D),
                ownerSideSign(data) * ThreadLocalRandom.current().nextDouble(1.0D, 2.2D),
                followLocation,
                horizontalForward,
                canSpawnAt
            )));
            case ALERT -> Optional.of(relativeOwnerLocation(owner, type, data, 1.8D, 0.0D, followLocation, horizontalForward, canSpawnAt));
            case SOCIAL_GREET, SOCIAL_PLAY, SOCIAL_CALM, INSPECT_GROUND -> Optional.ofNullable(ambientTarget);
            case PLAYFUL_CIRCLE, NONE -> Optional.empty();
        };
    }

    private static Location relativeOwnerLocation(
        Player owner,
        PetType type,
        OwnedPetData data,
        double forwardDistance,
        double sideDistance,
        Function<Player, Location> followLocation,
        Function<Player, Vector> horizontalForward,
        Predicate<Location> canSpawnAt
    ) {
        Vector forward = horizontalForward.apply(owner);
        Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
        Location location = owner.getLocation().clone()
            .add(forward.multiply(forwardDistance))
            .add(side.multiply(sideDistance))
            .add(0.0D, type.flying() ? PetMovementProfile.followHeight(type) : 0.0D, 0.0D);
        return canSpawnAt.test(location) ? location : followLocation.apply(owner);
    }

    private static Optional<Location> nearbyGroundInterest(Player owner, PetType type, Predicate<Location> canSpawnAt) {
        Location center = owner.getLocation();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Material material = center.getWorld().getBlockAt(center.getBlockX() + x, center.getBlockY(), center.getBlockZ() + z).getType();
                if (PetEnvironmentSupport.isSoftInterest(material)) {
                    Location target = center.getWorld()
                        .getBlockAt(center.getBlockX() + x, center.getBlockY(), center.getBlockZ() + z)
                        .getLocation()
                        .add(0.5D, type.flying() ? PetMovementProfile.followHeight(type) : 0.0D, 0.5D);
                    if (canSpawnAt.test(target)) {
                        return Optional.of(target);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static double ownerSideSign(OwnedPetData data) {
        return Math.floorMod(data.petId().hashCode(), 2) == 0 ? 1.0D : -1.0D;
    }

    record AmbientDecision(Location location, AmbientState state, String hint, long hintDurationMillis) {
    }

    record AmbientState(Location target, long retargetMillis, AmbientAction action, long actionUntilMillis, long nextActionMillis) {
    }
}
