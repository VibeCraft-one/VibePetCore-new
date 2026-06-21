package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.PetDebugLogger;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.ability.PetAbilityService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class RuntimePet {
    public static final String SPAWN_BYPASS_TAG = "vibepet_spawn_bypass";
    private final OwnedPetData data;
    private final PetType type;
    private LivingEntity entity;
    private UUID attackTargetId;
    private long lastAttackMillis;
    private boolean sparringAttack;
    private AttackPattern attackPattern = AttackPattern.NONE;
    private int attackStepsRemaining;
    private long nextAttackActionMillis;
    private boolean attackOrbitClockwise;
    private int idleTicks;
    private Vector smoothedVelocity = new Vector();
    private Location lastOwnerLocation;
    private long lastOwnerMoveMillis;
    private Location ambientTarget;
    private long ambientRetargetMillis;
    private long lastHopMillis;
    private long nextEmotionMillis;
    private long nextAmbientSoundMillis;
    private long stateChangedMillis = System.currentTimeMillis();
    private Location lastEntityLocation;
    private long lastEntityMoveMillis = System.currentTimeMillis();
    private long nextSpawnAttemptMillis;
    private AmbientAction ambientAction = AmbientAction.NONE;
    private long ambientActionUntilMillis;
    private long nextAmbientActionMillis;
    private String actionBarHint;
    private long actionBarHintUntilMillis;
    private long nextSocialActionMillis;
    private PetType temporaryFormType;
    private long temporaryFormUntilMillis;

    public RuntimePet(OwnedPetData data, PetType type) {
        this.data = data;
        this.type = type;
    }

    public OwnedPetData data() {
        return data;
    }

    public PetType type() {
        return type;
    }

    public PetType effectiveType() {
        return temporaryFormActive() ? temporaryFormType : type;
    }

    public PetState state() {
        try {
            return PetState.valueOf(data.state());
        } catch (IllegalArgumentException exception) {
            return PetState.FOLLOW;
        }
    }

    public Optional<LivingEntity> entity() {
        return Optional.ofNullable(entity).filter(livingEntity -> !livingEntity.isDead());
    }

    public boolean isEntity(Entity candidate) {
        return entity != null && candidate != null && entity.getUniqueId().equals(candidate.getUniqueId());
    }

    public void spawn(Player owner) {
        spawn(owner, null);
    }

    public void spawn(Player owner, BalanceConfig config) {
        spawn(owner, config, null);
    }

    public void spawn(Player owner, BalanceConfig config, Location preferredLocation) {
        spawn(owner, config, preferredLocation, true);
    }

    public void spawn(Player owner, BalanceConfig config, Location preferredLocation, boolean playSpawnEffects) {
        long now = System.currentTimeMillis();
        if (now < nextSpawnAttemptMillis) {
            return;
        }
        removeEntity();
        PetType spawnType = effectiveType();
        Location location = spawnLocation(owner, preferredLocation);
        try {
            double scale = config == null ? 1.0D : config.petScale(spawnType, data.evolutionStage());
            entity = PetSpawnSupport.spawnEntity(owner.getWorld(), spawnType, location, SPAWN_BYPASS_TAG, scale);
        } catch (RuntimeException exception) {
            nextSpawnAttemptMillis = now + ((config == null ? 3 : config.petSpawnRetrySeconds(spawnType)) * 1000L);
            return;
        }
        if (entity == null) {
            nextSpawnAttemptMillis = now + ((config == null ? 3 : config.petSpawnRetrySeconds(spawnType)) * 1000L);
            return;
        }
        refreshName();
        PetSpawnSupport.configureSpawnedEntity(entity, spawnType);
        PetCollisionSupport.applyOwnerExemption(owner, entity);
        PetAppearanceSupport.applyPersistentAppearance(entity, data);
        PetAppearanceSupport.applyVisualState(entity, data, false);
        PetAppearanceSupport.clearEntityHands(entity);
        face(owner.getEyeLocation());
        lastOwnerLocation = owner.getLocation().clone();
        lastOwnerMoveMillis = System.currentTimeMillis();
        ambientTarget = null;
        ambientRetargetMillis = 0L;
        ambientAction = AmbientAction.NONE;
        ambientActionUntilMillis = 0L;
        PetSpawnSupport.SpawnState spawnState = PetSpawnSupport.newSpawnState(entity, now);
        nextAmbientActionMillis = spawnState.nextAmbientActionMillis();
        smoothedVelocity = spawnState.smoothedVelocity();
        lastEntityLocation = spawnState.lastEntityLocation();
        lastEntityMoveMillis = spawnState.lastEntityMoveMillis();
        nextSpawnAttemptMillis = 0L;
        sparringAttack = false;
        resetAttackSequence();
        announceAction("spawning", 1_800L);
        if (playSpawnEffects) {
            PetSpawnSupport.playSpawnEffects(owner.getWorld(), entity);
        }
    }

    public void despawn() {
        temporaryFormType = null;
        temporaryFormUntilMillis = 0L;
        removeEntity();
    }

    public boolean temporaryFormActive(PetType formType) {
        return temporaryFormActive() && temporaryFormType == formType;
    }

    public boolean activateTemporaryForm(Player owner, BalanceConfig config, PetType formType, long durationMillis) {
        if (owner == null || !owner.isOnline() || formType == null || formType == type || durationMillis <= 0L) {
            return false;
        }
        if (temporaryFormActive()) {
            return false;
        }
        Location location = entity != null && !entity.isDead() ? entity.getLocation().clone() : owner.getLocation().clone();
        temporaryFormType = formType;
        temporaryFormUntilMillis = System.currentTimeMillis() + durationMillis;
        spawn(owner, config, location, false);
        boolean success = entity != null && !entity.isDead() && effectiveType() == formType;
        if (!success) {
            temporaryFormType = null;
            temporaryFormUntilMillis = 0L;
            nextSpawnAttemptMillis = 0L;
            spawn(owner, config, location, false);
        }
        return success;
    }

    private void removeEntity() {
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
        entity = null;
    }

    public void update(Player owner, BalanceConfig config, PetAbilityService abilityService, PetDebugLogger debugLogger) {
        expireTemporaryFormIfNeeded(owner, config);
        if (PetStateFlowSupport.shouldRespawn(entity, owner)) {
            if (debugLogger != null && config.debugPetRuntimeEnabled()) {
                                debugLogger.debugRateLimited(
                    "pet:runtime:respawn:" + data.petId(),
                    "pet-runtime",
                    "Pet respawn owner=" + owner.getName()
                        + " pet=" + data.petId()
                        + " reason=" + (entity == null ? "entity=null" : entity.isDead() ? "entity-dead" : "different-world")
                        + " state=" + state()
                        + " hp=" + Math.round(data.health())
                        + " food=" + Math.round(data.satiety()),
                    5_000L
                );
            }
            spawn(owner, config);
            return;
        }

        idleTicks++;
        PetType runtimeType = effectiveType();
        updateOwnerMovement(owner);
        PetCollisionSupport.applyOwnerExemption(owner, entity);
        PetAppearanceSupport.clearEntityHands(entity);
        PetAppearanceSupport.applyPersistentAppearance(entity, data);
        PetAppearanceSupport.applyVisualState(entity, data, false);
        PetMotionSupport.applyScale(entity, config, runtimeType, data.evolutionStage());
        boolean comfortResting = PetCompanionComfortSupport.keepResting(owner, entity, runtimeType, data, abilityService);
        if (comfortResting && state() != PetState.IDLE) {
            setState(PetState.IDLE);
        }

        PetStateFlowSupport.UpdatePlan updatePlan = PetStateFlowSupport.planUpdate(
            owner,
            entity,
            state(),
            ownerResting(),
            comfortResting,
            stateChangedMillis,
            config,
            runtimeType
        );
        if (updatePlan.clearAmbientTarget()) {
            ambientTarget = null;
        }
        if (updatePlan.targetState() != state()) {
            setState(updatePlan.targetState());
        }

        if (updatePlan.action() == PetStateFlowSupport.UpdateAction.ATTACK) {
            announceAction("protecting", 1_600L);
            updateAttack(owner, config, abilityService, debugLogger);
            handleStuck(owner, config, debugLogger);
            emitMood(owner, config);
            lookBackAtOwnerIfWatched(owner);
            return;
        }

        if (updatePlan.action() == PetStateFlowSupport.UpdateAction.TELEPORT_RETURN) {
            if (state() == PetState.ATTACK) {
                attackTargetId = null;
                sparringAttack = false;
                resetAttackSequence();
                PetCompanionComfortSupport.clearRest(data);
            }
            setState(PetState.RETURN);
            PetMotionSupport.teleportNearOwner(entity, owner, runtimeType, data, ignored -> PetMovementProfile.followHeight(runtimeType), location -> PetNavigationSupport.canSpawnAt(location, runtimeType));
            if (updatePlan.resetSmoothedVelocity()) {
                smoothedVelocity = new Vector();
            }
            setState(updatePlan.targetState());
            lookBackAtOwnerIfWatched(owner);
            return;
        }

        if (comfortResting) {
            updateComfortRest(owner, config);
            emitMood(owner, config);
            lookBackAtOwnerIfWatched(owner);
            return;
        }

        if (PetCompanionComfortSupport.keepOwnerSpace(owner, entity, runtimeType)) {
            smoothedVelocity = entity.getVelocity().clone();
            emitMood(owner, config);
            lookBackAtOwnerIfWatched(owner);
            return;
        }

        switch (updatePlan.action()) {
            case MANUAL_IDLE -> updateManualIdle(owner, config);
            case AMBIENT -> updateAmbient(owner, config);
            case FOLLOW -> moveFollow(owner, config);
            default -> {
            }
        }
        handleStuck(owner, config, debugLogger);
        emitMood(owner, config);
        lookBackAtOwnerIfWatched(owner);
    }

    public boolean attack(Entity target) {
        if (target == null || target.isDead() || target.getUniqueId().equals(data.ownerId())) {
            return false;
        }
        PetCompanionComfortSupport.clearRest(data);
        PetType runtimeType = effectiveType();
        PetStateFlowSupport.CombatStart combatStart = PetStateFlowSupport.startAttack(
            target,
            data,
            runtimeType,
            PetCombatSupport.beginCombat(runtimeType, data.evolutionStage(), false, target.getUniqueId(), null, this::scaledAttackDelayMillis)
        );
        applyCombatStart(combatStart);
        announceAction(combatStart.actionCaption(), combatStart.actionCaptionDurationMillis());
        setState(PetState.ATTACK);
        return true;
    }

    public void spar(RuntimePet targetPet) {
        if (targetPet == null || targetPet.entity == null || targetPet.entity.isDead() || targetPet.data.ownerId().equals(data.ownerId())) {
            return;
        }
        PetCompanionComfortSupport.clearRest(data);
        PetType runtimeType = effectiveType();
        PetStateFlowSupport.CombatStart combatStart = PetStateFlowSupport.startSpar(
            targetPet,
            data,
            PetCombatSupport.beginCombat(
                runtimeType,
                data.evolutionStage(),
                true,
                targetPet.entity.getUniqueId(),
                null,
                this::scaledAttackDelayMillis
            )
        );
        applyCombatStart(combatStart);
        announceAction(combatStart.actionCaption(), combatStart.actionCaptionDurationMillis());
        setState(PetState.ATTACK);
    }

    public void clearCombat() {
        attackTargetId = null;
        sparringAttack = false;
        resetAttackSequence();
        PetStateFlowSupport.ClearCombatResult clearCombatResult = PetStateFlowSupport.clearCombat(state());
        if (clearCombatResult.targetState() != null) {
            setState(clearCombatResult.targetState());
        }
        announceAction(clearCombatResult.actionCaption(), clearCombatResult.actionCaptionDurationMillis());
    }

    public void setState(PetState state) {
        stateChangedMillis = PetRuntimeStateSupport.setState(data, state, stateChangedMillis);
    }

    public void refreshName() {
        PetRuntimeStateSupport.refreshName(entity, data, type);
    }

    public void recall(Player owner) {
        PetCompanionComfortSupport.clearRest(data);
        if (entity == null || entity.isDead() || !entity.getWorld().equals(owner.getWorld())) {
            spawn(owner);
            return;
        }
        PetType runtimeType = effectiveType();
        PetRecallSupport.RecallState recallState = PetRecallSupport.recall(entity, runtimeType, followLocation(owner));
        setState(PetState.FOLLOW);
        ambientTarget = recallState.ambientTarget();
        ambientRetargetMillis = recallState.ambientRetargetMillis();
        ambientAction = recallState.ambientAction();
        ambientActionUntilMillis = recallState.ambientActionUntilMillis();
        announceAction(recallState.actionCaption(), recallState.actionCaptionDurationMillis());
        PetAppearanceSupport.applyVisualState(entity, data, false);
    }

    private void updateAttack(Player owner, BalanceConfig config, PetAbilityService abilityService, PetDebugLogger debugLogger) {
        PetType runtimeType = effectiveType();
        PetCombatSupport.CombatState state = PetCombatSupport.updateAttack(
            owner,
            entity,
            this,
            runtimeType,
            data.evolutionStage(),
            config,
            abilityService,
            attackTargetId,
            sparringAttack,
            attackPattern,
            attackStepsRemaining,
            nextAttackActionMillis,
            attackOrbitClockwise,
            this::moveToward,
            this::face,
            this::finishAttackSequence,
            this::resetAttackSequence,
            this::playSparEffect,
            this::scaledAttackDelayMillis,
            lastAttackMillis
        );
        applyCombatState(state);
        lastAttackMillis = state.lastAttackMillis();
        if (debugLogger != null && config.debugPetRuntimeEnabled()) {
            debugLogger.debugRateLimited(
                "pet:runtime:attack:" + data.petId(),
                "pet-runtime",
                "Pet attack owner=" + owner.getName()
                    + " pet=" + data.petId()
                    + " target=" + (attackTargetId == null ? "null" : attackTargetId)
                    + " sparring=" + sparringAttack
                    + " pattern=" + attackPattern
                    + " steps=" + attackStepsRemaining
                    + " state=" + state(),
                3_000L
            );
        }
    }

    private void finishAttackSequence(boolean returnToOwner) {
        attackTargetId = null;
        sparringAttack = false;
        resetAttackSequence();
        setState(returnToOwner ? PetState.RETURN : PetState.FOLLOW);
    }

    private void resetAttackSequence() {
        attackPattern = AttackPattern.NONE;
        attackStepsRemaining = 0;
        nextAttackActionMillis = 0L;
        attackOrbitClockwise = false;
    }

    private long scaledAttackDelayMillis(BalanceConfig config, int minDelayTicks, int maxDelayTicks) {
        long base = ThreadLocalRandom.current().nextLong(minDelayTicks * 50L, maxDelayTicks * 50L);
        double tempo = config == null ? 1.0D : Math.max(0.75D, config.petAttackTempo(effectiveType()));
        return Math.max(110L, Math.round(base / tempo));
    }

    private void applyCombatStart(PetStateFlowSupport.CombatStart combatStart) {
        attackTargetId = combatStart.attackTargetId();
        sparringAttack = combatStart.sparringAttack();
        attackPattern = combatStart.attackPattern();
        attackStepsRemaining = combatStart.attackStepsRemaining();
        nextAttackActionMillis = combatStart.nextAttackActionMillis();
        attackOrbitClockwise = combatStart.attackOrbitClockwise();
    }

    private void applyCombatState(PetCombatSupport.CombatState state) {
        attackTargetId = state.attackTargetId();
        sparringAttack = state.sparringAttack();
        attackPattern = state.attackPattern();
        attackStepsRemaining = state.attackStepsRemaining();
        nextAttackActionMillis = state.nextAttackActionMillis();
        attackOrbitClockwise = state.attackOrbitClockwise();
    }

    private boolean temporaryFormActive() {
        return temporaryFormType != null && temporaryFormUntilMillis > System.currentTimeMillis();
    }

    private void expireTemporaryFormIfNeeded(Player owner, BalanceConfig config) {
        if (temporaryFormType == null || temporaryFormUntilMillis > System.currentTimeMillis()) {
            return;
        }
        Location location = entity != null && !entity.isDead() ? entity.getLocation().clone() : owner.getLocation().clone();
        temporaryFormType = null;
        temporaryFormUntilMillis = 0L;
        spawn(owner, config, location, false);
    }

    boolean isAttackTarget(Entity target) {
        return attackTargetId != null && target != null && attackTargetId.equals(target.getUniqueId());
    }

    boolean isSparringAttack() {
        return sparringAttack;
    }

    private void updateManualIdle(Player owner, BalanceConfig config) {
        PetType runtimeType = effectiveType();
        Location target = ambientLocation(owner, config, true);
        PetAmbientRuntimeSupport.AmbientRuntimeDecision decision = PetAmbientRuntimeSupport.manualIdle(
            owner,
            entity,
            runtimeType,
            data,
            config,
            ambientAction,
            target,
            () -> followLocation(owner),
            () -> PetMotionSupport.restVelocity(runtimeType, entity, PetMovementProfile.hoverBob(runtimeType))
        );
        if (decision.returnToOwner()) {
            setState(PetState.RETURN);
            if (decision.clearAmbientTarget()) {
                ambientTarget = null;
            }
            moveToward(decision.moveTarget(), config.petSprintSpeed());
            return;
        }
        if (decision.rest()) {
            PetAppearanceSupport.applyVisualState(entity, data, true);
            setSmoothedVelocity(decision.restVelocity(), decision.velocityBlend());
            if (decision.faceOwner()) {
                face(owner.getEyeLocation());
            }
            if (decision.maybePlayAmbientSound()) {
                maybePlayAmbientSound();
            }
            return;
        }
        moveToward(decision.moveTarget(), decision.velocityBlend());
    }

    private void updateAmbient(Player owner, BalanceConfig config) {
        PetType runtimeType = effectiveType();
        Location target = ambientLocation(owner, config, false);
        PetAmbientRuntimeSupport.AmbientRuntimeDecision decision = PetAmbientRuntimeSupport.ambient(
            owner,
            entity,
            runtimeType,
            data,
            config,
            ambientAction,
            idleTicks,
            target,
            () -> followLocation(owner),
            () -> PetMotionSupport.restVelocity(runtimeType, entity, PetMovementProfile.hoverBob(runtimeType))
        );
        if (decision.returnToOwner()) {
            if (decision.clearAmbientTarget()) {
                ambientTarget = null;
            }
            moveToward(decision.moveTarget(), config.petSprintSpeed());
            return;
        }
        if (decision.rest()) {
            PetAppearanceSupport.applyVisualState(entity, data, true);
            setSmoothedVelocity(decision.restVelocity(), decision.velocityBlend());
            if (PetOwnerViewSupport.ownerLooksAtPet(owner, entity) || decision.faceOwner()) {
                face(owner.getEyeLocation());
            }
            if (decision.maybePlayAmbientSound()) {
                maybePlayAmbientSound();
            }
            return;
        }
        moveToward(decision.moveTarget(), decision.velocityBlend());
    }

    private void updateComfortRest(Player owner, BalanceConfig config) {
        PetType runtimeType = effectiveType();
        Location target = PetCompanionComfortSupport.restLocation(owner, entity, runtimeType, data);
        if (!PetNavigationSupport.canSpawnAt(target, runtimeType)) {
            target = entity.getLocation();
        }
        if (entity.getLocation().distanceSquared(target) > 0.72D) {
            moveToward(target, Math.min(config.petBaseSpeed(), config.petSprintSpeed() * 0.55D));
            return;
        }
        PetCompanionComfortSupport.applyRestPose(entity, data, runtimeType, true);
        setSmoothedVelocity(PetMotionSupport.restVelocity(runtimeType, entity, PetMovementProfile.hoverBob(runtimeType)), 0.42D);
        face(owner.getEyeLocation());
        if (idleTicks % 40 == 0) {
            announceAction(PetCompanionComfortSupport.restHint(runtimeType), 1_400L);
        }
    }

    private void moveToward(Location target, double speed) {
        PetType runtimeType = effectiveType();
        applyMoveResult(PetMovementLogic.moveToward(
            entity,
            runtimeType,
            data,
            smoothedVelocity,
            target,
            speed,
            idleTicks,
            lastHopMillis,
            () -> PetMotionSupport.restVelocity(runtimeType, entity, PetMovementProfile.hoverBob(runtimeType)),
            () -> PetMovementProfile.hoverBob(runtimeType),
            (direction, distance) -> PetNavigationSupport.blockedAhead(entity, runtimeType, direction, distance, smoothedVelocity)
        ));
    }

    private void moveFollow(Player owner, BalanceConfig config) {
        PetType runtimeType = effectiveType();
        applyMoveResult(PetMovementLogic.moveFollow(
            owner,
            entity,
            runtimeType,
            data,
            config,
            smoothedVelocity,
            idleTicks,
            lastHopMillis,
            () -> followLocation(owner),
            () -> PetMotionSupport.restVelocity(runtimeType, entity, PetMovementProfile.hoverBob(runtimeType)),
            () -> PetMovementProfile.idlePull(runtimeType),
            () -> PetMovementProfile.hoverBob(runtimeType),
            (direction, distance) -> PetNavigationSupport.blockedAhead(entity, runtimeType, direction, distance, smoothedVelocity)
        ));
    }

    private void applyMoveResult(PetMovementLogic.MoveResult result) {
        smoothedVelocity = result.smoothedVelocity();
        lastHopMillis = result.lastHopMillis();
    }

    private void setSmoothedVelocity(Vector desired, double blend) {
        applyMoveResult(PetMovementLogic.smoothVelocity(entity, effectiveType(), smoothedVelocity, desired, blend, lastHopMillis));
    }

    private void face(Location target) {
        PetMovementLogic.face(entity, target);
    }

    private Location spawnLocation(Player owner, Location preferredLocation) {
        PetType runtimeType = effectiveType();
        return PetFollowSupport.spawnLocation(
            owner,
            runtimeType,
            data,
            preferredLocation,
            location -> PetNavigationSupport.canSpawnAt(location, runtimeType),
            ignored -> PetMovementProfile.followHeight(runtimeType),
            ignored -> PetMovementProfile.spawnDistance(runtimeType)
        );
    }

    private Location followLocation(Player owner) {
        PetType runtimeType = effectiveType();
        return PetFollowSupport.followLocation(owner, runtimeType, data, location -> PetNavigationSupport.canSpawnAt(location, runtimeType), ignored -> PetMovementProfile.followHeight(runtimeType));
    }

    private Location ambientLocation(Player owner, BalanceConfig config, boolean manualIdle) {
        PetType runtimeType = effectiveType();
        PetAmbientSupport.AmbientDecision decision = PetAmbientSupport.ambientLocation(
            owner,
            entity,
            runtimeType,
            data,
            config,
            manualIdle,
            ownerResting(),
            ambientTarget,
            ambientRetargetMillis,
            ambientAction,
            ambientActionUntilMillis,
            nextAmbientActionMillis,
            player -> PetNavigationSupport.interestingLocation(player, entity, runtimeType, config),
            this::followLocation,
            ownerPlayer -> PetNavigationSupport.playfulCircleLocation(ownerPlayer, runtimeType, this::followLocation, location -> PetNavigationSupport.canSpawnAt(location, runtimeType)),
            PetMotionSupport::horizontalForward,
            location -> PetNavigationSupport.canSpawnAt(location, runtimeType)
        );
        ambientTarget = decision.state().target();
        ambientRetargetMillis = decision.state().retargetMillis();
        ambientAction = decision.state().action();
        ambientActionUntilMillis = decision.state().actionUntilMillis();
        nextAmbientActionMillis = decision.state().nextActionMillis();
        if (decision.hint() != null) {
            announceAction(decision.hint(), decision.hintDurationMillis());
        }
        return decision.location();
    }

    private void updateOwnerMovement(Player owner) {
        PetSocialRuntimeSupport.OwnerTrackState state = PetSocialRuntimeSupport.updateOwnerMovement(lastOwnerLocation, lastOwnerMoveMillis, owner);
        if (state.moved()) {
            lastOwnerLocation = state.location();
            lastOwnerMoveMillis = state.moveMillis();
            ambientTarget = null;
            ambientRetargetMillis = 0L;
        }
    }

    private boolean ownerResting() {
        return PetSocialRuntimeSupport.ownerResting(lastOwnerMoveMillis);
    }

    public boolean isLookedAtBy(Player owner) {
        return PetOwnerViewSupport.ownerLooksAtPet(owner, entity);
    }

    public boolean hasActionHint() {
        return PetSocialStateSupport.hasActionHint(actionBarHint, actionBarHintUntilMillis);
    }

    public void showHint(String action, long durationMillis) {
        announceAction(action, durationMillis);
    }

    public String combatSnapshot() {
        return "state=" + state()
            + " target=" + (attackTargetId == null ? "none" : attackTargetId)
            + " pattern=" + attackPattern
            + " steps=" + attackStepsRemaining
            + " form=" + effectiveType()
            + " entity=" + (entity != null && !entity.isDead());
    }

    public boolean canSocialize() {
        return PetSocialRuntimeSupport.canSocialize(entity, state(), nextSocialActionMillis);
    }

    public boolean trySocializeWith(Player owner, RuntimePet other, Player otherOwner) {
        if (!canSocialize() || !other.canSocialize() || !PetSocialRuntimeSupport.canStartSocial(owner, this, other)) {
            return false;
        }
        long now = System.currentTimeMillis();
        AmbientAction action = socialActionWith(other);
        startSocialAction(owner, other, action, now);
        other.startSocialAction(otherOwner, this, companionSocialAction(action), now);
        return true;
    }

    private void lookBackAtOwnerIfWatched(Player owner) {
        if (PetSocialRuntimeSupport.shouldLookBackAtOwner(owner, entity)) {
            face(owner.getEyeLocation());
            maybePlayAmbientSound();
        }
    }

    private void handleStuck(Player owner, BalanceConfig config, PetDebugLogger debugLogger) {
        PetType runtimeType = effectiveType();
        PetRecoverySupport.RecoveryResult result = PetRecoverySupport.handleStuck(
            owner,
            entity,
            runtimeType,
            config,
            lastEntityLocation,
            lastEntityMoveMillis,
            lastHopMillis,
            PetNavigationSupport.blockedAhead(entity, runtimeType, smoothedVelocity),
            () -> PetMotionSupport.sideStepLocation(owner, entity, data, this::followLocation, location -> PetNavigationSupport.canSpawnAt(location, runtimeType)),
            () -> followLocation(owner)
        );
        if (result.resetTracking()) {
            lastEntityLocation = result.trackedLocation();
            lastEntityMoveMillis = result.trackedMoveMillis();
            if (debugLogger != null && config.debugPetStuckEnabled()) {
                                debugLogger.debugRateLimited(
                    "pet:stuck:reset:" + data.petId(),
                    "pet-stuck",
                    "Reset stuck tracking owner=" + owner.getName()
                        + " pet=" + data.petId()
                        + " pos=" + locationText(result.trackedLocation())
                        + " state=" + state(),
                    3_000L
                );
            }
            return;
        }
        if (!result.recover()) {
            if (debugLogger != null && config.debugPetStuckEnabled()) {
                                debugLogger.debugRateLimited(
                    "pet:stuck:none:" + data.petId(),
                    "pet-stuck",
                    "Recovery not needed owner=" + owner.getName()
                        + " pet=" + data.petId()
                        + " blockedAhead=" + PetNavigationSupport.blockedAhead(entity, runtimeType, smoothedVelocity)
                        + " state=" + state(),
                    5_000L
                );
            }
            return;
        }

        ambientTarget = null;
        setState(PetState.RETURN);
        if (result.sideStep()) {
            lastHopMillis = result.lastHopMillis();
            moveToward(result.moveTarget(), config.petSprintSpeed());
            lastEntityLocation = result.trackedLocation();
            lastEntityMoveMillis = result.trackedMoveMillis();
            if (debugLogger != null && config.debugPetStuckEnabled()) {
                                debugLogger.debugRateLimited(
                    "pet:stuck:side-step:" + data.petId(),
                    "pet-stuck",
                    "Side-step recovery owner=" + owner.getName()
                        + " pet=" + data.petId()
                        + " target=" + locationText(result.moveTarget())
                        + " tracked=" + locationText(result.trackedLocation())
                        + " state=" + state(),
                    3_000L
                );
            }
            return;
        }
        if (result.teleport()) {
            PetMotionSupport.teleportNearOwner(entity, owner, runtimeType, data, ignored -> PetMovementProfile.followHeight(runtimeType), location -> PetNavigationSupport.canSpawnAt(location, runtimeType));
            smoothedVelocity = new Vector();
            if (debugLogger != null && config.debugPetStuckEnabled()) {
                                debugLogger.debugRateLimited(
                    "pet:stuck:teleport:" + data.petId(),
                    "pet-stuck",
                    "Teleport recovery owner=" + owner.getName()
                        + " pet=" + data.petId()
                        + " ownerPos=" + locationText(owner.getLocation())
                        + " state=" + state(),
                    3_000L
                );
            }
        } else if (result.pullCloser()) {
            PetMotionSupport.teleportCloserToOwner(entity, owner, runtimeType, data, ignored -> PetMovementProfile.followHeight(runtimeType), location -> PetNavigationSupport.canSpawnAt(location, runtimeType));
            smoothedVelocity = new Vector();
            if (debugLogger != null && config.debugPetStuckEnabled()) {
                                debugLogger.debugRateLimited(
                    "pet:stuck:pull-closer:" + data.petId(),
                    "pet-stuck",
                    "Pull-closer recovery owner=" + owner.getName()
                        + " pet=" + data.petId()
                        + " ownerPos=" + locationText(owner.getLocation())
                        + " state=" + state(),
                    3_000L
                );
            }
        } else {
            moveToward(result.moveTarget(), config.petSprintSpeed());
            if (debugLogger != null && config.debugPetStuckEnabled()) {
                                debugLogger.debugRateLimited(
                    "pet:stuck:follow:" + data.petId(),
                    "pet-stuck",
                    "Follow recovery owner=" + owner.getName()
                        + " pet=" + data.petId()
                        + " target=" + locationText(result.moveTarget())
                        + " state=" + state(),
                    3_000L
                );
            }
        }
        lastEntityLocation = entity.getLocation().clone();
        lastEntityMoveMillis = result.trackedMoveMillis();
    }

    private void emitMood(Player owner, BalanceConfig config) {
        nextEmotionMillis = PetInteractionSupport.emitMood(owner, entity, config, effectiveType(), data, state(), nextEmotionMillis, ownerResting());
    }

    private void maybePlayAmbientSound() {
        nextAmbientSoundMillis = PetInteractionSupport.playAmbientSound(entity, nextAmbientSoundMillis, effectiveType());
    }

    private void playSparEffect(Location targetLocation) {
        PetInteractionSupport.playSparEffect(entity, targetLocation, effectiveType());
    }

    private void startSocialAction(Player owner, RuntimePet other, AmbientAction action, long now) {
        PetType runtimeType = effectiveType();
        PetInteractionSupport.SocialState socialState = PetInteractionSupport.startSocialAction(
            owner,
            entity,
            other,
            action,
            now,
            nextAmbientSoundMillis,
            runtimeType,
            this::followLocation,
            location -> PetNavigationSupport.canSpawnAt(location, runtimeType)
        );
        nextSocialActionMillis = socialState.nextSocialActionMillis();
        ambientAction = socialState.ambientAction();
        ambientActionUntilMillis = socialState.ambientActionUntilMillis();
        ambientRetargetMillis = 0L;
        ambientTarget = socialState.ambientTarget();
        announceAction(socialState.actionCaption(), socialState.actionCaptionDurationMillis());
        nextAmbientSoundMillis = socialState.nextAmbientSoundMillis();
    }

    private AmbientAction socialActionWith(RuntimePet other) {
        return PetInteractionSupport.socialActionWith(effectiveType(), data.evolutionStage(), other);
    }

    private AmbientAction companionSocialAction(AmbientAction action) {
        return PetInteractionSupport.companionSocialAction(action);
    }

    public String debugLine(Player owner, BalanceConfig config) {
        return PetRuntimeViewSupport.debugLine(Objects.requireNonNull(config, "config"), owner, data, type, state(), entity, ambientAction, ownerResting(), nextSpawnAttemptMillis);
    }

    public String actionBarLine(BalanceConfig config, PetAbilityService abilityService) {
        return PetRuntimeViewSupport.actionBarLine(config, data, type, this, entity, abilityService, actionBarHint, actionBarHintUntilMillis, nextSpawnAttemptMillis);
    }

    public String compactHudLine(BalanceConfig config, PetAbilityService abilityService) {
        return PetRuntimeViewSupport.compactHudLine(config, data, type, this, entity, abilityService, nextSpawnAttemptMillis);
    }

    public Optional<String> overlayHint() {
        return PetRuntimeViewSupport.overlayHint(actionBarHint, actionBarHintUntilMillis);
    }

    public String hudLine(BalanceConfig config, PetAbilityService abilityService) {
        return PetRuntimeViewSupport.hudLine(config, data, type, this, entity, abilityService, actionBarHint, actionBarHintUntilMillis, nextSpawnAttemptMillis);
    }

    private void announceAction(String action, long durationMillis) {
        actionBarHint = action;
        actionBarHintUntilMillis = PetRuntimeStateSupport.actionHintUntilMillis(durationMillis);
    }

    private String locationText(Location location) {
        if (location == null || location.getWorld() == null) {
            return "null";
        }
        return location.getWorld().getName() + "@"
            + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", location.getX(), location.getY(), location.getZ());
    }
}
