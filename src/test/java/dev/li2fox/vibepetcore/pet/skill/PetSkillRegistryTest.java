package dev.li2fox.vibepetcore.pet.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.li2fox.vibepetcore.pet.PetType;
import org.junit.jupiter.api.Test;

final class PetSkillRegistryTest {
    @Test
    void everyPetTypeHasThreeNamedSkills() {
        for (PetType type : PetType.values()) {
            PetSkillSet skills = PetSkillRegistry.skills(type);
            assertNotNull(skills.basic(), type + " basic skill");
            assertNotNull(skills.passive(), type + " passive skill");
            assertNotNull(skills.ultimate(), type + " ultimate skill");
            assertNotNull(skills.basic().nameKey(), type + " basic name key");
            assertNotNull(skills.passive().descriptionKey(), type + " passive description key");
            assertNotNull(skills.ultimate().nameKey(), type + " ultimate name key");
        }
    }

    @Test
    void primaryAttackPatternMatchesBasicSkill() {
        assertEquals(PetSkillRegistry.basicSkill(PetType.WOLF).attackPattern(), PetSkillRegistry.primaryAttackPattern(PetType.WOLF));
        assertEquals(PetSkillRegistry.basicSkill(PetType.GHAST).attackPattern(), PetSkillRegistry.primaryAttackPattern(PetType.GHAST));
    }

    @Test
    void registryContainsAllSeventeenTypes() {
        assertEquals(17, PetType.values().length);
        for (PetType type : PetType.values()) {
            assertNotNull(PetSkillRegistry.skills(type).ultimate());
        }
    }
}
