package dev.li2fox.vibepetcore.pet.skill;

import dev.li2fox.vibepetcore.pet.AttackPattern;
import dev.li2fox.vibepetcore.pet.PetType;
import java.util.EnumMap;
import java.util.Map;

public final class PetSkillRegistry {
    private static final Map<PetType, PetSkillSet> SKILLS = new EnumMap<>(PetType.class);

    static {
        register(PetType.WOLF,
            skill(PetSkillKind.BASIC, "wolf", "alpha_bite", AttackPattern.COMBO),
            skill(PetSkillKind.PASSIVE, "wolf", "pack_guard"),
            skill(PetSkillKind.ULTIMATE, "wolf", "blood_alpha"));
        register(PetType.CAT,
            skill(PetSkillKind.BASIC, "cat", "claw_swipe", AttackPattern.COMBO),
            skill(PetSkillKind.PASSIVE, "cat", "night_sight"),
            skill(PetSkillKind.ULTIMATE, "cat", "ninth_life"));
        register(PetType.FOX,
            skill(PetSkillKind.BASIC, "fox", "cunning_snap", AttackPattern.POUNCE),
            skill(PetSkillKind.PASSIVE, "fox", "foragers_haste"),
            skill(PetSkillKind.ULTIMATE, "fox", "sly_wound"));
        register(PetType.RABBIT,
            skill(PetSkillKind.BASIC, "rabbit", "buck_kick", AttackPattern.POUNCE),
            skill(PetSkillKind.PASSIVE, "rabbit", "spring_legs"),
            skill(PetSkillKind.ULTIMATE, "rabbit", "last_hop"));
        register(PetType.FROG,
            skill(PetSkillKind.BASIC, "frog", "tongue_lash", AttackPattern.POUNCE),
            skill(PetSkillKind.PASSIVE, "frog", "pond_leap"),
            skill(PetSkillKind.ULTIMATE, "frog", "tongue_snap"));
        register(PetType.PANDA,
            skill(PetSkillKind.BASIC, "panda", "rolling_slam", AttackPattern.COMBO),
            skill(PetSkillKind.PASSIVE, "panda", "bamboo_guard"),
            skill(PetSkillKind.ULTIMATE, "panda", "heavy_paw"));
        register(PetType.ARMADILLO,
            skill(PetSkillKind.BASIC, "armadillo", "shell_bash", AttackPattern.COMBO),
            skill(PetSkillKind.PASSIVE, "armadillo", "armored_hide"),
            skill(PetSkillKind.ULTIMATE, "armadillo", "shell_ward"));
        register(PetType.BAT,
            skill(PetSkillKind.BASIC, "bat", "sonar_dive", AttackPattern.STRAFE),
            skill(PetSkillKind.PASSIVE, "bat", "echo_vision"),
            skill(PetSkillKind.ULTIMATE, "bat", "venom_night"));
        register(PetType.BEE,
            skill(PetSkillKind.BASIC, "bee", "sting_rush", AttackPattern.STRAFE),
            skill(PetSkillKind.PASSIVE, "bee", "royal_nectar"),
            skill(PetSkillKind.ULTIMATE, "bee", "royal_swarm"));
        register(PetType.PARROT,
            skill(PetSkillKind.BASIC, "parrot", "feather_dash", AttackPattern.STRAFE),
            skill(PetSkillKind.PASSIVE, "parrot", "tailwind"),
            skill(PetSkillKind.ULTIMATE, "parrot", "disrupting_cry"));
        register(PetType.PHANTOM,
            skill(PetSkillKind.BASIC, "phantom", "shadow_glide", AttackPattern.STRAFE),
            skill(PetSkillKind.PASSIVE, "phantom", "moon_drift"),
            skill(PetSkillKind.ULTIMATE, "phantom", "night_terror"));
        register(PetType.AXOLOTL,
            skill(PetSkillKind.BASIC, "axolotl", "ripple_strike", AttackPattern.STRAFE),
            skill(PetSkillKind.PASSIVE, "axolotl", "gill_breath"),
            skill(PetSkillKind.ULTIMATE, "axolotl", "tide_breath"));
        register(PetType.ALLAY,
            skill(PetSkillKind.BASIC, "allay", "gust_slash", AttackPattern.STRAFE),
            skill(PetSkillKind.PASSIVE, "allay", "gathering_haste"),
            skill(PetSkillKind.ULTIMATE, "allay", "vex_echo"));
        register(PetType.VEX,
            skill(PetSkillKind.BASIC, "vex", "phase_strike", AttackPattern.POUNCE),
            skill(PetSkillKind.PASSIVE, "vex", "ethereal_speed"),
            skill(PetSkillKind.ULTIMATE, "vex", "phantom_surge"));
        register(PetType.BLAZE,
            skill(PetSkillKind.BASIC, "blaze", "ember_bolt", AttackPattern.RANGED),
            skill(PetSkillKind.PASSIVE, "blaze", "inferno_skin"),
            skill(PetSkillKind.ULTIMATE, "blaze", "firebrand"));
        register(PetType.BREEZE,
            skill(PetSkillKind.BASIC, "breeze", "gust_shot", AttackPattern.RANGED),
            skill(PetSkillKind.PASSIVE, "breeze", "air_step"),
            skill(PetSkillKind.ULTIMATE, "breeze", "wind_volley"));
        register(PetType.GHAST,
            skill(PetSkillKind.BASIC, "ghast", "soul_fire", AttackPattern.RANGED),
            skill(PetSkillKind.PASSIVE, "ghast", "weightless_float"),
            skill(PetSkillKind.ULTIMATE, "ghast", "soul_howl"));
    }

    private PetSkillRegistry() {
    }

    public static PetSkillSet skills(PetType type) {
        return SKILLS.getOrDefault(type, PetSkillSet.EMPTY);
    }

    public static PetSkill basicSkill(PetType type) {
        return skills(type).basic();
    }

    public static AttackPattern primaryAttackPattern(PetType type) {
        PetSkill basic = basicSkill(type);
        return basic == null || basic.attackPattern() == AttackPattern.NONE
            ? AttackPattern.COMBO
            : basic.attackPattern();
    }

    private static void register(PetType type, PetSkill basic, PetSkill passive, PetSkill ultimate) {
        SKILLS.put(type, new PetSkillSet(basic, passive, ultimate));
    }

    private static PetSkill skill(PetSkillKind kind, String typeKey, String skillKey) {
        return new PetSkill(kind, messageKey(typeKey, kind, skillKey, "name"), messageKey(typeKey, kind, skillKey, "desc"));
    }

    private static PetSkill skill(PetSkillKind kind, String typeKey, String skillKey, AttackPattern pattern) {
        return new PetSkill(kind, messageKey(typeKey, kind, skillKey, "name"), messageKey(typeKey, kind, skillKey, "desc"), pattern);
    }

    private static String messageKey(String typeKey, PetSkillKind kind, String skillKey, String suffix) {
        String kindKey = switch (kind) {
            case BASIC -> "basic";
            case PASSIVE -> "passive";
            case ULTIMATE -> "ultimate";
        };
        return "skills." + typeKey + "." + kindKey + "." + skillKey + "." + suffix;
    }
}
