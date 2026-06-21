package dev.li2fox.vibepetcore.pet;

enum AttackPattern {
    NONE("combo"),
    COMBO("combo"),
    POUNCE("pounce"),
    STRAFE("strafe"),
    RANGED("ranged");

    private final String configKey;

    AttackPattern(String configKey) {
        this.configKey = configKey;
    }

    String configKey() {
        return configKey;
    }
}
