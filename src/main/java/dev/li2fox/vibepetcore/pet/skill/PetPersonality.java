package dev.li2fox.vibepetcore.pet.skill;

import java.util.Locale;

public enum PetPersonality {
    AGGRESSIVE,
    PASSIVE,
    DEFENSIVE,
    BURST,
    SCOUT,
    MOBILITY,
    SUPPORT,
    UTILITY,
    BALANCED;

    public static PetPersonality parse(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        try {
            return PetPersonality.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BALANCED;
        }
    }
}
