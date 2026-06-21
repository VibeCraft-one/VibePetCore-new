package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class PetStateFlowSupport {
    private PetStateFlowSupport() {
    }

    static boolean shouldRespawn(LivingEntity entity, Player owner) {
        return entity == null || entity.isDead() || !entity.getWorld().equals(owner.getWorld());
    }

    static UpdatePlan planUpdate(
        Player owner,
        LivingEntity entity,
        PetState state,
        boolean ownerResting,
        boolean persistentIdle,
        long stateChangedMillis,
        BalanceConfig config,
        PetType type
    ) {
        PetState nextState = state;
        boolean clearAmbientTarget = false;
        if (!persistentIdle && state == PetState.IDLE && System.currentTimeMillis() - stateChangedMillis >= config.petStayMaxSeconds(type) * 1000L) {
            nextState = PetState.FOLLOW;
            clearAmbientTarget = true;
        }

        double distance = entity.getLocation().distance(owner.getLocation());
        if (state == PetState.ATTACK && shouldAbandonDistantCombat(entity, owner, config)) {
            return new UpdatePlan(UpdateAction.TELEPORT_RETURN, PetState.FOLLOW, true, true);
        }
        if (state == PetState.ATTACK) {
            return new UpdatePlan(UpdateAction.ATTACK, state, false, false);
        }
        if (distance > config.teleportDistance()) {
            return new UpdatePlan(UpdateAction.TELEPORT_RETURN, PetState.FOLLOW, true, true);
        }

        if (distance > config.returnDistance() || (ownerResting && distance > 10.5D)) {
            nextState = PetState.RETURN;
            clearAmbientTarget = true;
        } else if (nextState == PetState.RETURN && distance <= config.followMaxRadius()) {
            nextState = PetState.FOLLOW;
        }

        if (nextState == PetState.IDLE) {
            return new UpdatePlan(UpdateAction.MANUAL_IDLE, nextState, clearAmbientTarget, false);
        }
        if (ownerResting && nextState != PetState.RETURN) {
            return new UpdatePlan(UpdateAction.AMBIENT, nextState, clearAmbientTarget, false);
        }
        return new UpdatePlan(UpdateAction.FOLLOW, nextState, clearAmbientTarget, false);
    }

    private static boolean shouldAbandonDistantCombat(LivingEntity entity, Player owner, BalanceConfig config) {
        double dx = entity.getLocation().getX() - owner.getLocation().getX();
        double dz = entity.getLocation().getZ() - owner.getLocation().getZ();
        double horizontalDistanceSquared = dx * dx + dz * dz;
        double farCombatDistance = Math.max(config.returnDistance() + 2.0D, config.followMaxRadius() + 7.0D);
        if (horizontalDistanceSquared > farCombatDistance * farCombatDistance) {
            return true;
        }
        double verticalGap = Math.abs(entity.getLocation().getY() - owner.getLocation().getY());
        return verticalGap > 5.0D
            && horizontalDistanceSquared > Math.pow(config.followMaxRadius() + 1.5D, 2.0D);
    }

    static CombatStart startAttack(Entity target, OwnedPetData data, PetType type, PetCombatSupport.CombatState state) {
        return new CombatStart(
            target.getUniqueId(),
            false,
            state.attackPattern(),
            state.attackStepsRemaining(),
            state.nextAttackActionMillis(),
            state.attackOrbitClockwise(),
            target instanceof Player ? "defends" : "attacks",
            1_800L
        );
    }

    static CombatStart startSpar(RuntimePet targetPet, OwnedPetData data, PetCombatSupport.CombatState state) {
        return new CombatStart(
            targetPet.entity().orElseThrow().getUniqueId(),
            true,
            state.attackPattern(),
            state.attackStepsRemaining(),
            state.nextAttackActionMillis(),
            state.attackOrbitClockwise(),
            "engages",
            2_000L
        );
    }

    static ClearCombatResult clearCombat(PetState state) {
        return new ClearCombatResult(state == PetState.ATTACK ? PetState.FOLLOW : null, "calms down", 1_600L);
    }

    enum UpdateAction {
        ATTACK,
        TELEPORT_RETURN,
        MANUAL_IDLE,
        AMBIENT,
        FOLLOW
    }

    record UpdatePlan(UpdateAction action, PetState targetState, boolean clearAmbientTarget, boolean resetSmoothedVelocity) {
    }

    record CombatStart(
        UUID attackTargetId,
        boolean sparringAttack,
        AttackPattern attackPattern,
        int attackStepsRemaining,
        long nextAttackActionMillis,
        boolean attackOrbitClockwise,
        String actionCaption,
        long actionCaptionDurationMillis
    ) {
    }

    record ClearCombatResult(PetState targetState, String actionCaption, long actionCaptionDurationMillis) {
    }
}
