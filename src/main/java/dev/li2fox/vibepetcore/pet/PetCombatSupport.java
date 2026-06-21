package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.pet.ability.PetAbilityService;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class PetCombatSupport {
    private PetCombatSupport() {
    }

    static CombatState updateAttack(
        Player owner,
        LivingEntity entity,
        RuntimePet pet,
        PetType type,
        int evolutionStage,
        BalanceConfig config,
        PetAbilityService abilityService,
        UUID attackTargetId,
        boolean sparringAttack,
        AttackPattern attackPattern,
        int attackStepsRemaining,
        long nextAttackActionMillis,
        boolean attackOrbitClockwise,
        Movement movement,
        Consumer<Location> face,
        Consumer<Boolean> finishAttackSequence,
        Runnable resetAttackSequence,
        Consumer<Location> playSparEffect,
        ScaledAttackDelay scaledAttackDelay,
        long lastAttackMillis
    ) {
        if (!config.worldPetAttacksEnabled(owner.getWorld().getName())) {
            finishAttackSequence.accept(true);
            return new CombatState(attackTargetId, sparringAttack, attackPattern, attackStepsRemaining, nextAttackActionMillis, attackOrbitClockwise, lastAttackMillis);
        }
        Entity target = attackTarget(owner, attackTargetId);
        if (!(target instanceof Damageable damageable) || target.isDead()) {
            finishAttackSequence.accept(true);
            return new CombatState(attackTargetId, sparringAttack, attackPattern, attackStepsRemaining, nextAttackActionMillis, attackOrbitClockwise, lastAttackMillis);
        }

        if (!target.getWorld().equals(entity.getWorld())) {
            finishAttackSequence.accept(true);
            return new CombatState(attackTargetId, sparringAttack, attackPattern, attackStepsRemaining, nextAttackActionMillis, attackOrbitClockwise, lastAttackMillis);
        }

        double verticalGap = target.getLocation().getY() - entity.getLocation().getY();
        double distance = entity.getLocation().distance(target.getLocation());
        if (!type.flying() && verticalGap > 1.8D && distance > config.attackRange()) {
            finishAttackSequence.accept(true);
            return new CombatState(attackTargetId, sparringAttack, attackPattern, attackStepsRemaining, nextAttackActionMillis, attackOrbitClockwise, lastAttackMillis);
        }

        if (distance > config.teleportDistance()) {
            finishAttackSequence.accept(true);
            return new CombatState(attackTargetId, sparringAttack, attackPattern, attackStepsRemaining, nextAttackActionMillis, attackOrbitClockwise, lastAttackMillis);
        }

        CombatState state = new CombatState(attackTargetId, sparringAttack, attackPattern, attackStepsRemaining, nextAttackActionMillis, attackOrbitClockwise, lastAttackMillis);
        if (abilityService.prefersLegendaryRanged(pet) && state.attackPattern() != AttackPattern.RANGED) {
            state = new CombatState(attackTargetId, sparringAttack, AttackPattern.NONE, 0, 0L, attackOrbitClockwise, lastAttackMillis);
        }
        if (state.attackPattern() == AttackPattern.NONE || state.attackStepsRemaining() <= 0) {
            state = prepareAttackSequence(type, evolutionStage, config, abilityService, pet, scaledAttackDelay, state);
        }

        long now = System.currentTimeMillis();
        Location targetLocation = target.getLocation().clone();
        return switch (state.attackPattern()) {
            case COMBO -> updateComboAttack(owner, entity, pet, damageable, targetLocation, type, evolutionStage, config, abilityService, distance, now, state, movement, face, finishAttackSequence, playSparEffect, scaledAttackDelay);
            case POUNCE -> updatePounceAttack(owner, entity, pet, damageable, targetLocation, type, evolutionStage, config, abilityService, distance, now, state, movement, face, finishAttackSequence, playSparEffect, scaledAttackDelay);
            case STRAFE -> updateStrafeAttack(owner, entity, pet, damageable, targetLocation, type, evolutionStage, config, abilityService, distance, now, state, movement, face, finishAttackSequence, playSparEffect, scaledAttackDelay);
            case RANGED -> updateRangedAttack(owner, entity, pet, damageable, targetLocation, type, evolutionStage, config, abilityService, distance, now, state, movement, face, finishAttackSequence, playSparEffect, scaledAttackDelay);
            default -> {
                finishAttackSequence.accept(true);
                yield state;
            }
        };
    }

    static CombatState beginCombat(
        PetType type,
        int evolutionStage,
        boolean sparringAttack,
        UUID attackTargetId,
        BalanceConfig config,
        ScaledAttackDelay scaledAttackDelay
    ) {
        return prepareAttackSequence(
            type,
            evolutionStage,
            config,
            null,
            null,
            scaledAttackDelay,
            new CombatState(attackTargetId, sparringAttack, AttackPattern.NONE, 0, 0L, false, 0L)
        );
    }

    private static Entity attackTarget(Player owner, UUID attackTargetId) {
        if (attackTargetId == null) {
            return null;
        }
        return owner.getWorld().getEntity(attackTargetId);
    }

    private static CombatState updateComboAttack(
        Player owner,
        LivingEntity entity,
        RuntimePet pet,
        Damageable damageable,
        Location targetLocation,
        PetType type,
        int evolutionStage,
        BalanceConfig config,
        PetAbilityService abilityService,
        double distance,
        long now,
        CombatState state,
        Movement movement,
        Consumer<Location> face,
        Consumer<Boolean> finishAttackSequence,
        Consumer<Location> playSparEffect,
        ScaledAttackDelay scaledAttackDelay
    ) {
        double reach = config.attackRange() + 0.2D;
        if (distance > reach) {
            movement.moveToward(targetLocation.add(0.0D, type.flying() ? 0.45D : 0.0D, 0.0D), config.petSprintSpeed());
            return state;
        }
        if (now < state.nextAttackActionMillis()) {
            movement.moveToward(
                PetAttackGeometry.offsetLocation(entity.getLocation(), targetLocation, 1.15D, 0.55D, type.flying() ? 0.35D : 0.0D, state.attackOrbitClockwise(), type.flying()),
                Math.min(config.petSprintSpeed(), config.petBaseSpeed() * 1.8D)
            );
            return state;
        }
        return executeAttackStep(owner, entity, pet, damageable, targetLocation, config, abilityService, evolutionStage, AttackPattern.COMBO, 9, 13, state, face, finishAttackSequence, playSparEffect, scaledAttackDelay);
    }

    private static CombatState updatePounceAttack(
        Player owner,
        LivingEntity entity,
        RuntimePet pet,
        Damageable damageable,
        Location targetLocation,
        PetType type,
        int evolutionStage,
        BalanceConfig config,
        PetAbilityService abilityService,
        double distance,
        long now,
        CombatState state,
        Movement movement,
        Consumer<Location> face,
        Consumer<Boolean> finishAttackSequence,
        Consumer<Location> playSparEffect,
        ScaledAttackDelay scaledAttackDelay
    ) {
        if (distance > config.attackRange() + 0.75D) {
            movement.moveToward(targetLocation.add(0.0D, type.flying() ? 0.55D : 0.0D, 0.0D), config.petSprintSpeed());
            return state;
        }
        if (now < state.nextAttackActionMillis()) {
            movement.moveToward(PetAttackGeometry.passThroughLocation(entity.getLocation(), targetLocation, 1.5D, type.flying()), config.petSprintSpeed());
            return state;
        }
        return executeAttackStep(owner, entity, pet, damageable, targetLocation, config, abilityService, evolutionStage, AttackPattern.POUNCE, 12, 18, state, face, finishAttackSequence, playSparEffect, scaledAttackDelay);
    }

    private static CombatState updateStrafeAttack(
        Player owner,
        LivingEntity entity,
        RuntimePet pet,
        Damageable damageable,
        Location targetLocation,
        PetType type,
        int evolutionStage,
        BalanceConfig config,
        PetAbilityService abilityService,
        double distance,
        long now,
        CombatState state,
        Movement movement,
        Consumer<Location> face,
        Consumer<Boolean> finishAttackSequence,
        Consumer<Location> playSparEffect,
        ScaledAttackDelay scaledAttackDelay
    ) {
        double attackWindow = config.attackRange() + 0.45D;
        Location strafePoint = PetAttackGeometry.offsetLocation(
            entity.getLocation(),
            targetLocation,
            Math.max(1.5D, config.attackRange() - 0.15D),
            1.0D,
            type.flying() ? 0.55D : 0.0D,
            state.attackOrbitClockwise(),
            type.flying()
        );
        if (distance > attackWindow + 0.85D) {
            movement.moveToward(strafePoint, config.petSprintSpeed());
            return state;
        }
        movement.moveToward(strafePoint, Math.min(config.petSprintSpeed() * 0.88D, config.petBaseSpeed() * 2.0D));
        if (distance <= attackWindow && now >= state.nextAttackActionMillis()) {
            return executeAttackStep(owner, entity, pet, damageable, targetLocation, config, abilityService, evolutionStage, AttackPattern.STRAFE, 10, 16, state, face, finishAttackSequence, playSparEffect, scaledAttackDelay);
        }
        return state;
    }

    private static CombatState updateRangedAttack(
        Player owner,
        LivingEntity entity,
        RuntimePet pet,
        Damageable damageable,
        Location targetLocation,
        PetType type,
        int evolutionStage,
        BalanceConfig config,
        PetAbilityService abilityService,
        double distance,
        long now,
        CombatState state,
        Movement movement,
        Consumer<Location> face,
        Consumer<Boolean> finishAttackSequence,
        Consumer<Location> playSparEffect,
        ScaledAttackDelay scaledAttackDelay
    ) {
        double preferredDistance = switch (type) {
            case GHAST -> 6.8D;
            case BREEZE -> 5.7D;
            default -> 4.9D;
        };
        double minWindow = Math.max(2.8D, preferredDistance - 1.4D);
        double maxWindow = preferredDistance + 2.0D;
        if (!entity.hasLineOfSight(damageable)) {
            movement.moveToward(targetLocation.add(0.0D, type.flying() ? 0.65D : 0.0D, 0.0D), config.petSprintSpeed());
            return state;
        }
        if (distance > maxWindow) {
            movement.moveToward(targetLocation.add(0.0D, type.flying() ? 0.65D : 0.0D, 0.0D), config.petSprintSpeed());
            return state;
        }
        if (distance < minWindow) {
            movement.moveToward(PetAttackGeometry.passThroughLocation(targetLocation, entity.getLocation(), preferredDistance - 0.6D, type.flying()), config.petSprintSpeed());
            return state;
        }
        Location strafePoint = PetAttackGeometry.offsetLocation(
            entity.getLocation(),
            targetLocation,
            preferredDistance,
            1.25D,
            type.flying() ? 0.75D : 0.0D,
            state.attackOrbitClockwise(),
            type.flying()
        );
        movement.moveToward(strafePoint, Math.min(config.petSprintSpeed() * 0.82D, config.petBaseSpeed() * 1.8D));
        if (now < state.nextAttackActionMillis()) {
            return state;
        }
        return executeAttackStep(owner, entity, pet, damageable, targetLocation, config, abilityService, evolutionStage, AttackPattern.RANGED, 12, 18, state, face, finishAttackSequence, playSparEffect, scaledAttackDelay);
    }

    private static CombatState executeAttackStep(
        Player owner,
        LivingEntity entity,
        RuntimePet pet,
        Damageable damageable,
        Location targetLocation,
        BalanceConfig config,
        PetAbilityService abilityService,
        int evolutionStage,
        AttackPattern pattern,
        int minDelayTicks,
        int maxDelayTicks,
        CombatState state,
        Consumer<Location> face,
        Consumer<Boolean> finishAttackSequence,
        Consumer<Location> playSparEffect,
        ScaledAttackDelay scaledAttackDelay
    ) {
        face.accept(targetLocation);
        boolean landed = state.sparringAttack()
            || ThreadLocalRandom.current().nextDouble() <= config.combatPatternAccuracy(pattern.configKey(), evolutionStage);
        if (landed) {
            if (state.sparringAttack()) {
                double damage = abilityService == null
                    ? 1.0D
                    : abilityService.attackDamage(pet, damageable, config.combatPatternDamageMultiplier(pattern.configKey()));
                damageable.damage(damage, owner);
                playSparEffect.accept(targetLocation);
            } else {
                double damage = abilityService.attackDamage(pet, damageable, config.combatPatternDamageMultiplier(pattern.configKey()));
                damageable.damage(damage, owner);
                abilityService.applyLegendaryHitEffects(owner, pet, damageable);
                PetAttackEffects.playImpact(targetLocation, pattern, pet.type(), entity);
            }
        } else {
            PetAttackEffects.playMiss(targetLocation, pattern, pet.type(), entity);
        }

        long newLastAttackMillis = System.currentTimeMillis();
        int remaining = Math.max(0, state.attackStepsRemaining() - 1);
        if (remaining <= 0) {
            finishAttackSequence.accept(true);
            return new CombatState(state.attackTargetId(), state.sparringAttack(), state.attackPattern(), remaining, state.nextAttackActionMillis(), state.attackOrbitClockwise(), newLastAttackMillis);
        }
        long newNextAttackActionMillis = System.currentTimeMillis() + scaledAttackDelay.scale(config, minDelayTicks, maxDelayTicks);
        boolean newAttackOrbitClockwise = pattern != AttackPattern.COMBO ? !state.attackOrbitClockwise() : state.attackOrbitClockwise();
        return new CombatState(state.attackTargetId(), state.sparringAttack(), state.attackPattern(), remaining, newNextAttackActionMillis, newAttackOrbitClockwise, newLastAttackMillis);
    }

    private static CombatState prepareAttackSequence(
        PetType type,
        int evolutionStage,
        BalanceConfig config,
        PetAbilityService abilityService,
        RuntimePet pet,
        ScaledAttackDelay scaledAttackDelay,
        CombatState state
    ) {
        AttackPattern pattern = abilityService != null && abilityService.prefersLegendaryRanged(pet)
            ? AttackPattern.RANGED
            : PetAttackProfile.selectPattern(type, evolutionStage);
        int steps = PetAttackProfile.stepsFor(
            pattern,
            evolutionStage,
            ThreadLocalRandom.current().nextInt(3),
            ThreadLocalRandom.current().nextDouble()
        );
        long nextAttackActionMillis = System.currentTimeMillis() + scaledAttackDelay.scale(config, 3, 5);
        boolean orbitClockwise = ThreadLocalRandom.current().nextBoolean();
        return new CombatState(state.attackTargetId(), state.sparringAttack(), pattern, steps, nextAttackActionMillis, orbitClockwise, state.lastAttackMillis());
    }

    record CombatState(
        UUID attackTargetId,
        boolean sparringAttack,
        AttackPattern attackPattern,
        int attackStepsRemaining,
        long nextAttackActionMillis,
        boolean attackOrbitClockwise,
        long lastAttackMillis
    ) {
    }

    interface Movement {
        void moveToward(Location target, double speed);
    }

    interface ScaledAttackDelay {
        long scale(BalanceConfig config, int minDelayTicks, int maxDelayTicks);
    }
}
