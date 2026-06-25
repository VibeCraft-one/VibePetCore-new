package dev.li2fox.vibepetcore.pet.skill;

public record PetSkillSet(PetSkill basic, PetSkill passive, PetSkill ultimate) {
    public static final PetSkillSet EMPTY = new PetSkillSet(null, null, null);
}
