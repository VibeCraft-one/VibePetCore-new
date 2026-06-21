package dev.li2fox.vibepetcore.pet;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.entity.EntityType;

public enum PetType {
    AXOLOTL(EntityType.AXOLOTL, "Axolotl", true),
    BAT(EntityType.BAT, "Bat", true),
    BEE(EntityType.BEE, "Bee", true),
    BLAZE(EntityType.BLAZE, "Blaze", true),
    BREEZE(EntityType.BREEZE, "Breeze", true),
    CAT(EntityType.CAT, "Cat", false),
    FOX(EntityType.FOX, "Fox", false),
    FROG(EntityType.FROG, "Frog", false),
    GHAST(EntityType.GHAST, "Mini Ghast", true),
    PANDA(EntityType.PANDA, "Panda", false),
    PARROT(EntityType.PARROT, "Parrot", true),
    PHANTOM(EntityType.PHANTOM, "Phantom", true),
    RABBIT(EntityType.RABBIT, "Rabbit", false),
    ALLAY(EntityType.ALLAY, "Allay", true),
    ARMADILLO(EntityType.ARMADILLO, "Armadillo", false),
    VEX(EntityType.VEX, "Vex", true),
    WOLF(EntityType.WOLF, "Wolf", false);

    private final EntityType entityType;
    private final String displayName;
    private final boolean flying;

    PetType(EntityType entityType, String displayName, boolean flying) {
        this.entityType = entityType;
        this.displayName = displayName;
        this.flying = flying;
    }

    public EntityType entityType() {
        return entityType;
    }

    public String displayName() {
        return displayName;
    }

    public boolean flying() {
        return flying;
    }

    public static Optional<PetType> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "AXOLOTL", "АКСОЛОТЛЬ", "АКСОЛОТЛ" -> Optional.of(AXOLOTL);
            case "BAT", "ЛЕТУЧАЯ_МЫШЬ", "МЫШЬ" -> Optional.of(BAT);
            case "BEE", "ПЧЕЛА" -> Optional.of(BEE);
            case "BLAZE", "БЛЕЙЗ" -> Optional.of(BLAZE);
            case "BREEZE", "БРИЗ" -> Optional.of(BREEZE);
            case "CAT", "КОТ", "КОШКА" -> Optional.of(CAT);
            case "FOX", "ЛИСА" -> Optional.of(FOX);
            case "FROG", "ЖАБА", "ЛЯГУШКА" -> Optional.of(FROG);
            case "GHAST", "GAST", "ГАСТ", "MINI_GHAST", "МИНИ_ГАСТ" -> Optional.of(GHAST);
            case "PANDA", "ПАНДА", "ПАНДОЧКА" -> Optional.of(PANDA);
            case "PARROT", "ПОПУГАЙ" -> Optional.of(PARROT);
            case "PHANTOM", "ФАНТОМ" -> Optional.of(PHANTOM);
            case "RABBIT", "КРОЛИК" -> Optional.of(RABBIT);
            case "ALLAY", "ЭЛЛЕЙ", "ЭЛЕЙ" -> Optional.of(ALLAY);
            case "ARMADILLO", "БРОНЕНОСЕЦ" -> Optional.of(ARMADILLO);
            case "VEX", "ВЕКС" -> Optional.of(VEX);
            case "WOLF", "ВОЛК" -> Optional.of(WOLF);
            default -> {
                try {
                    yield Optional.of(PetType.valueOf(normalized));
                } catch (IllegalArgumentException exception) {
                    yield Optional.empty();
                }
            }
        };
    }
}
