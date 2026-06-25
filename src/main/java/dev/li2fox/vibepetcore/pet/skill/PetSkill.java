package dev.li2fox.vibepetcore.pet.skill;

import dev.li2fox.vibepetcore.pet.AttackPattern;

public record PetSkill(
    PetSkillKind kind,
    String nameKey,
    String descriptionKey,
    AttackPattern attackPattern
) {
    public PetSkill(PetSkillKind kind, String nameKey, String descriptionKey) {
        this(kind, nameKey, descriptionKey, AttackPattern.NONE);
    }
}
