package dev.li2fox.vibepetcore.pet.behavior;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.pet.PetType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

final class FlyingCombatBehavior implements PetCombatBehavior {
    static final FlyingCombatBehavior INSTANCE = new FlyingCombatBehavior();

    private FlyingCombatBehavior() {
    }

    @Override
    public boolean canReachTarget(PetType type, LivingEntity entity, Location targetLocation, BalanceConfig config) {
        return true;
    }

    @Override
    public double approachYOffset(PetType type) {
        return switch (type) {
            case GHAST -> 0.65D;
            case BLAZE, BREEZE -> 0.55D;
            case VEX, PHANTOM -> 0.50D;
            default -> 0.45D;
        };
    }

    @Override
    public double attackReachBonus(PetType type) {
        return switch (type) {
            case GHAST -> 0.35D;
            case BLAZE, BREEZE -> 0.2D;
            case VEX, PHANTOM -> 0.15D;
            default -> 0.1D;
        };
    }
}
