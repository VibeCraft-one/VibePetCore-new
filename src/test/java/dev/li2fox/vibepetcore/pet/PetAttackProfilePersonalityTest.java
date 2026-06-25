package dev.li2fox.vibepetcore.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.li2fox.vibepetcore.pet.skill.PetPersonality;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PetAttackProfilePersonalityTest {
    @Test
    void aggressivePersonalityFavorsComboOverPassive() {
        Map<AttackPattern, Integer> aggressive = samplePatterns(PetType.WOLF, PetPersonality.AGGRESSIVE, 2_000);
        Map<AttackPattern, Integer> passive = samplePatterns(PetType.WOLF, PetPersonality.PASSIVE, 2_000);
        assertTrueGreater(aggressive.get(AttackPattern.COMBO), passive.get(AttackPattern.COMBO));
        assertTrueGreater(passive.get(AttackPattern.STRAFE), aggressive.get(AttackPattern.STRAFE));
    }

    @Test
    void burstPersonalityFavorsRangedForGhast() {
        Map<AttackPattern, Integer> burst = samplePatterns(PetType.GHAST, PetPersonality.BURST, 1_000);
        assertTrueGreater(burst.getOrDefault(AttackPattern.RANGED, 0), burst.getOrDefault(AttackPattern.STRAFE, 0));
    }

    private static Map<AttackPattern, Integer> samplePatterns(PetType type, PetPersonality personality, int rolls) {
        Map<AttackPattern, Integer> counts = new EnumMap<>(AttackPattern.class);
        for (int index = 0; index < rolls; index++) {
            AttackPattern pattern = PetAttackProfile.selectPattern(type, 3, personality);
            counts.merge(pattern, 1, Integer::sum);
        }
        return counts;
    }

    private static void assertTrueGreater(int left, int right) {
        assertEquals(true, left > right, () -> "expected " + left + " > " + right);
    }
}
