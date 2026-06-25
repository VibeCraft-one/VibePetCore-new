package dev.li2fox.vibepetcore.pet.behavior;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.pet.PetType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public interface PetCombatBehavior {
    boolean canReachTarget(PetType type, LivingEntity entity, Location targetLocation, BalanceConfig config);

    double approachYOffset(PetType type);

    double attackReachBonus(PetType type);
}
