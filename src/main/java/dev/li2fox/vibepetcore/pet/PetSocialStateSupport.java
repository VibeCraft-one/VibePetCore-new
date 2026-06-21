package dev.li2fox.vibepetcore.pet;

import org.bukkit.entity.LivingEntity;

final class PetSocialStateSupport {
    private PetSocialStateSupport() {
    }

    static boolean hasActionHint(String actionBarHint, long actionBarHintUntilMillis) {
        return actionBarHint != null && System.currentTimeMillis() < actionBarHintUntilMillis;
    }

    static boolean canSocialize(LivingEntity entity, PetState state, long nextSocialActionMillis) {
        return entity != null
            && !entity.isDead()
            && state != PetState.ATTACK
            && state != PetState.RETURN
            && System.currentTimeMillis() >= nextSocialActionMillis;
    }

    static long nextSocialActionMillis(long now) {
        return now + java.util.concurrent.ThreadLocalRandom.current().nextLong(60_000L, 130_000L);
    }

    static long actionHintUntilMillis(long durationMillis) {
        return System.currentTimeMillis() + Math.max(800L, durationMillis);
    }
}
