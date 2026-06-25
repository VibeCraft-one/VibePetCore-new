package dev.li2fox.vibepetcore.pet.behavior;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.pet.PetType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

final class GroundCombatBehavior implements PetCombatBehavior {
    static final GroundCombatBehavior INSTANCE = new GroundCombatBehavior();

    private GroundCombatBehavior() {
    }

    @Override
    public boolean canReachTarget(PetType type, LivingEntity entity, Location targetLocation, BalanceConfig config) {
        double verticalGap = targetLocation.getY() - entity.getLocation().getY();
        double distance = entity.getLocation().distance(targetLocation);
        return !(verticalGap > 1.8D && distance > config.attackRange());
    }

    @Override
    public double approachYOffset(PetType type) {
        return 0.0D;
    }

    @Override
    public double attackReachBonus(PetType type) {
        return switch (type) {
            case FROG, RABBIT -> 0.1D;
            case ARMADILLO, PANDA -> 0.05D;
            default -> 0.0D;
        };
    }
}
