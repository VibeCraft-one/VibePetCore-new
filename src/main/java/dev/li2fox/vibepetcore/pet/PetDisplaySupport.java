package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.Locale;

final class PetDisplaySupport {
    private PetDisplaySupport() {
    }

    static String rarityColor(OwnedPetData data) {
        return switch (data.rarity().toUpperCase(Locale.ROOT)) {
            case "LEGENDARY" -> "§d";
            case "EPIC" -> "§b";
            case "RARE" -> "§e";
            default -> "§a";
        };
    }

    static String stageSymbol(OwnedPetData data) {
        return switch (Math.max(1, Math.min(5, data.evolutionStage()))) {
            case 1 -> "✧";
            case 2 -> "✦";
            case 3 -> "✶";
            case 4 -> "✹";
            default -> "✪";
        };
    }

    static String displayLabel(OwnedPetData data, PetType type) {
        String customName = data.petName();
        if (customName.equalsIgnoreCase(type.name()) || customName.equalsIgnoreCase(type.displayName())) {
            return localizedTypeName(type);
        }
        return customName;
    }

    static String shortDisplayLabel(OwnedPetData data, PetType type) {
        String label = displayLabel(data, type);
        return label.length() <= 8 ? label : label.substring(0, 8);
    }

    static String localizedTypeName(PetType type) {
        return GameText.petTypeName(type);
    }
}
