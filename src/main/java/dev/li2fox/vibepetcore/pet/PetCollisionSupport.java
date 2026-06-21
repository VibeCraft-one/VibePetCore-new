package dev.li2fox.vibepetcore.pet;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class PetCollisionSupport {
    private PetCollisionSupport() {
    }

    static void applyOwnerExemption(Player owner, LivingEntity petEntity) {
        if (owner == null || petEntity == null || petEntity.isDead()) {
            return;
        }
        petEntity.setCollidable(true);
        petEntity.getCollidableExemptions().add(owner.getUniqueId());
    }
}
