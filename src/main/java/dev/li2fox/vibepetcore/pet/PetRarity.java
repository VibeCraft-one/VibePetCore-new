package dev.li2fox.vibepetcore.pet;

import java.util.Locale;

public enum PetRarity {
    COMMON("Common"),
    RARE("Rare"),
    EPIC("Epic"),
    LEGENDARY("Legendary");

    private final String displayName;

    PetRarity(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean canUpgrade() {
        return this != LEGENDARY;
    }

    public PetRarity next() {
        return switch (this) {
            case COMMON -> RARE;
            case RARE -> EPIC;
            case EPIC -> LEGENDARY;
            case LEGENDARY -> LEGENDARY;
        };
    }

    public static PetRarity parse(String value) {
        if (value == null || value.isBlank()) {
            return COMMON;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "rare", "редкий" -> RARE;
            case "epic", "эпический" -> EPIC;
            case "legendary", "легендарный" -> LEGENDARY;
            default -> COMMON;
        };
    }
}
