package dev.li2fox.vibepetcore.box;

import dev.li2fox.vibepetcore.pet.PetRarity;
import dev.li2fox.vibepetcore.pet.PetType;

public record BoxOpenResult(boolean success, String message, PetType petType, PetRarity rarity, boolean pity) {
}
