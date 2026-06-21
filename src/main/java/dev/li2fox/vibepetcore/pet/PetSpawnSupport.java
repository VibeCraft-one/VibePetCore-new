package dev.li2fox.vibepetcore.pet;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;

final class PetSpawnSupport {
    private PetSpawnSupport() {
    }

    static LivingEntity spawnEntity(World world, PetType type, Location location, String spawnBypassTag, double scaleValue) {
        Class<? extends Entity> entityClass = type.entityType().getEntityClass();
        if (entityClass != null && LivingEntity.class.isAssignableFrom(entityClass)) {
            @SuppressWarnings("unchecked")
            Class<? extends LivingEntity> livingClass = (Class<? extends LivingEntity>) entityClass;
            return world.spawn(location, livingClass, CreatureSpawnEvent.SpawnReason.CUSTOM, spawned -> {
                spawned.addScoreboardTag(spawnBypassTag);
                applyInitialScale(spawned, scaleValue);
            });
        }
        LivingEntity entity = (LivingEntity) world.spawnEntity(location, type.entityType());
        entity.addScoreboardTag(spawnBypassTag);
        applyInitialScale(entity, scaleValue);
        return entity;
    }

    private static void applyInitialScale(LivingEntity entity, double scaleValue) {
        try {
            AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(scaleValue);
            }
        } catch (RuntimeException ignored) {
        }
    }

    static void configureSpawnedEntity(LivingEntity entity, PetType type) {
        entity.setCustomNameVisible(true);
        entity.setInvisible(false);
        entity.setPersistent(false);
        entity.setSilent(true);
        entity.setRemoveWhenFarAway(false);
        entity.setCollidable(true);
        entity.setCanPickupItems(false);
        entity.setGravity(!type.flying());
        if (entity instanceof Mob mob) {
            mob.setAware(false);
            mob.setTarget(null);
        }
        AttributeInstance movementSpeed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(0.35D);
        }
    }

    static SpawnState newSpawnState(LivingEntity entity, long now) {
        return new SpawnState(
            now + ThreadLocalRandom.current().nextLong(4_000L, 9_000L),
            new Vector(),
            entity.getLocation().clone(),
            now
        );
    }

    static void playSpawnEffects(World world, LivingEntity entity) {
        world.spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation().add(0.0D, 0.6D, 0.0D), 12, 0.25D, 0.25D, 0.25D, 0.02D);
        world.playSound(entity.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.45F, 1.35F);
    }

    record SpawnState(long nextAmbientActionMillis, Vector smoothedVelocity, Location lastEntityLocation, long lastEntityMoveMillis) {
    }
}
