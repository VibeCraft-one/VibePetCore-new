package dev.li2fox.vibepetcore.pet;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;

final class PetRecallSupport {
    private PetRecallSupport() {
    }

    static RecallState recall(LivingEntity entity, PetType type, Location followLocation) {
        entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation().add(0.0D, 0.7D, 0.0D), 6, 0.2D, 0.2D, 0.2D, 0.02D);
        entity.getWorld().playSound(entity.getLocation(), PetInteractionSupport.responseSound(type), 0.35F, 1.45F);
        return new RecallState(
            followLocation,
            System.currentTimeMillis() + 1_200L,
            AmbientAction.WATCH_OWNER,
            System.currentTimeMillis() + 1_800L,
            "heard",
            1_600L
        );
    }

    record RecallState(
        Location ambientTarget,
        long ambientRetargetMillis,
        AmbientAction ambientAction,
        long ambientActionUntilMillis,
        String actionCaption,
        long actionCaptionDurationMillis
    ) {
    }
}
