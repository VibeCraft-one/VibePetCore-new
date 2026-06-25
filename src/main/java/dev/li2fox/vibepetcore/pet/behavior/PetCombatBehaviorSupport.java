package dev.li2fox.vibepetcore.pet.behavior;

import dev.li2fox.vibepetcore.pet.PetType;

public final class PetCombatBehaviorSupport {
    private PetCombatBehaviorSupport() {
    }

    public static PetCombatBehavior forType(PetType type) {
        return type.flying() ? FlyingCombatBehavior.INSTANCE : GroundCombatBehavior.INSTANCE;
    }
}
