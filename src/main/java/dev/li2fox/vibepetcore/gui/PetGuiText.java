package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.pet.PetType;
import java.util.List;
import java.util.Locale;

final class PetGuiText {
    private PetGuiText() {
    }

    static String evolutionReward(PetType type, int stage) {
        return switch (stage) {
            case 2 -> switch (type) {
                case WOLF, BLAZE, GHAST, BREEZE -> t("reward.combat-stronger", "becomes noticeably stronger in combat");
                case FOX, ALLAY, AXOLOTL -> t("reward.farming-nearby", "farms better and stays nearby");
                case CAT, BEE, PANDA, ARMADILLO -> t("reward.support-reliable", "supports the player more reliably");
                case RABBIT, PARROT, BAT, FROG, PHANTOM -> t("reward.movement", "moves and maneuvers more easily");
                case VEX -> t("reward.burst", "hits harder in sharp burst attacks");
            };
            case 3 -> t("reward.stage3", "keeps pace, buffs, and pet role better");
            case 4 -> t("reward.stage4", "unlocks late boosts and a higher power ceiling");
            case 5 -> t("reward.stage5", "MAX form with maximum value");
            default -> t("reward.default", "stat growth");
        };
    }

    static String shortUseCase(PetType type) {
        return switch (type) {
            case WOLF -> t("use.wolf", "combat and defense");
            case CAT -> t("use.cat", "night, mines, safe retreat");
            case ALLAY -> t("use.allay", "mining, resources, backpack");
            case FOX -> t("use.fox", "loot, speed, scouting");
            case RABBIT -> t("use.rabbit", "movement and farms");
            case BEE -> t("use.bee", "healing and survival");
            case PARROT -> t("use.parrot", "scouting and mobility");
            case BAT -> t("use.bat", "night, mines, scouting");
            case BLAZE -> t("use.blaze", "Nether, fire, combat");
            case AXOLOTL -> t("use.axolotl", "water, support, outings");
            case FROG -> t("use.frog", "swamps, maneuvers, control");
            case ARMADILLO -> t("use.armadillo", "armor and protection");
            case PANDA -> t("use.panda", "survival and resilience");
            case GHAST -> t("use.ghast", "air, pressure, burst");
            case PHANTOM -> t("use.phantom", "night, dives, pursuit");
            case BREEZE -> t("use.breeze", "wind, dashes, disruption");
            case VEX -> t("use.vex", "bursts and sudden damage");
        };
    }

    static List<String> supportUseLore(PetType type) {
        return switch (type) {
            case WOLF -> lines("support.wolf.1", "&7Role: &fcombat and defense.", "support.wolf.2", "&7Reliable in fights and more likely to answer threats.");
            case CAT -> lines("support.cat.1", "&7Role: &fnight and mines.", "support.cat.2", "&7Feels better in the dark and supports careful play.");
            case ALLAY -> lines("support.allay.1", "&7Role: &fresources and backpack.", "support.allay.2", "&7Simplifies gathering, carrying, and calm routine play.");
            case FOX -> lines("support.fox.1", "&7Role: &floot and scouting.", "support.fox.2", "&7Good for movement, farming, and routes with frequent drops.");
            case RABBIT -> lines("support.rabbit.1", "&7Role: &fmobility.", "support.rabbit.2", "&7Useful for movement, height, and quick pace changes.");
            case BEE -> lines("support.bee.1", "&7Role: &fsupport.", "support.bee.2", "&7Adds survivability and helps in longer fights.");
            case PARROT -> lines("support.parrot.1", "&7Role: &fscouting.", "support.parrot.2", "&7Keeps track of surroundings and helps maintain tempo.");
            case BAT -> lines("support.bat.1", "&7Role: &fnight scouting.", "support.bat.2", "&7A calm companion for mines and dark areas.");
            case BLAZE -> lines("support.blaze.1", "&7Role: &fcombat and the Nether.", "support.blaze.2", "&7Shows its strength in dangerous areas and aggressive play.");
            case AXOLOTL -> lines("support.axolotl.1", "&7Role: &fwater support.", "support.axolotl.2", "&7Stabilizes water trips with breathing and recovery.");
            case BREEZE -> lines("support.breeze.1", "&7Role: &fwind combat.", "support.breeze.2", "&7Adds speed, jumps, and safer vertical movement.");
            case FROG -> lines("support.frog.1", "&7Role: &fswamp mobility.", "support.frog.2", "&7Helps with jumps, water, and flexible movement.");
            case GHAST -> lines("support.ghast.1", "&7Role: &fair pressure.", "support.ghast.2", "&7Keeps falls safer and grows into strong burst support.");
            case PANDA -> lines("support.panda.1", "&7Role: &fsteady survival.", "support.panda.2", "&7Plays slower, but gives reliable sustain.");
            case PHANTOM -> lines("support.phantom.1", "&7Role: &fnight pursuit.", "support.phantom.2", "&7Supports night movement, dives, and chase tempo.");
            case ARMADILLO -> lines("support.armadillo.1", "&7Role: &farmor and stability.", "support.armadillo.2", "&7Protects steadily and softens dangerous movement.");
            case VEX -> lines("support.vex.1", "&7Role: &fburst damage.", "support.vex.2", "&7Temporary aggressive form with sharp attacks.");
        };
    }

    static List<String> buffPlanLore(PetType type) {
        return switch (type) {
            case WOLF -> effects("E1: Resistance I", "E2: +Strength I", "E4: +Absorption I", "E5: Resistance II");
            case CAT -> effects("E1: Night Vision I", "E2: +Speed I", "E4: +Invisibility I");
            case ALLAY -> effects("E1: Haste I", "E2: +Speed I", "E4: +Saturation I", "E5: Haste II");
            case FOX -> effects("E1: Haste I", "E2: +Speed I", "E3: +Night Vision I", "E5: Haste II");
            case RABBIT -> effects("E1: Jump Boost I", "E2: +Speed I", "E4: +Slow Falling I", "E5: Jump Boost II");
            case BEE -> effects("E1: Regeneration I", "E2: +Haste I", "E4: +Absorption I");
            case PARROT -> effects("E1: Speed I", "E3: +Night Vision I", "E5: +Slow Falling I", "E5: Speed II");
            case BAT -> effects("E1: Night Vision I", "E3: +Speed I", "E5: +Slow Falling I");
            case BLAZE -> effects("E1: Fire Resistance I", "E2: +Resistance I", "E4: +Strength I");
            case AXOLOTL -> effects("E1: Water Breathing I", "E2: +Regeneration I", "E4: +Absorption I");
            case BREEZE -> effects("E1: Speed I", "E2: +Jump Boost I", "E4: +Slow Falling I");
            case FROG -> effects("E1: Jump Boost I", "E2: +Speed I", "E4: +Water Breathing I");
            case GHAST -> effects("E1: Slow Falling I", "E2: +Fire Resistance I", "E4: +Strength I");
            case PANDA -> effects("E1: Resistance I", "E2: +Regeneration I", "E4: +Absorption I");
            case PHANTOM -> effects("E1: Slow Falling I", "E3: +Night Vision I", "E5: +Speed I");
            case ARMADILLO -> effects("E1: Resistance I", "E2: +Absorption I", "E4: +Slow Falling I");
            case VEX -> effects("E1: Speed I", "E4: +Strength I", "Dashes and burst damage");
        };
    }

    static String compactBuffSummary(PetType type) {
        return switch (type) {
            case WOLF -> t("buff.wolf", "resistance, strength, absorption");
            case CAT -> t("buff.cat", "night vision, speed, invisibility");
            case ALLAY -> t("buff.allay", "haste, speed, saturation");
            case FOX -> t("buff.fox", "haste, speed, night vision");
            case RABBIT -> t("buff.rabbit", "jump boost, speed, slow falling");
            case BEE -> t("buff.bee", "regeneration, haste, absorption");
            case PARROT -> t("buff.parrot", "speed, night vision, slow falling");
            case BAT -> t("buff.bat", "night vision, speed, slow falling");
            case BLAZE -> t("buff.blaze", "fire resistance, resistance, strength");
            case AXOLOTL -> t("buff.axolotl", "water breathing, regeneration, absorption");
            case BREEZE -> t("buff.breeze", "speed, jump boost, slow falling");
            case FROG -> t("buff.frog", "jump boost, speed, water breathing");
            case GHAST -> t("buff.ghast", "slow falling, fire resistance, strength");
            case PANDA -> t("buff.panda", "resistance, regeneration, absorption");
            case PHANTOM -> t("buff.phantom", "slow falling, night vision, speed");
            case ARMADILLO -> t("buff.armadillo", "resistance, absorption, slow falling");
            case VEX -> t("buff.vex", "speed, strength, burst");
        };
    }

    static List<String> petTypeLore(PetType type) {
        return switch (type) {
            case WOLF -> lines("lore.wolf.1", "&7Stays close to combat.", "lore.wolf.2", "&7Feels better when staying near the owner.");
            case CAT -> lines("lore.cat.1", "&7Likes careful movement.", "lore.cat.2", "&7Useful at night and in mines.");
            case ALLAY -> lines("lore.allay.1", "&7Good for calm gathering.", "lore.allay.2", "&7Helps with resources and collection.");
            case FOX -> lines("lore.fox.1", "&7Fast and nimble companion.", "lore.fox.2", "&7Often useful for farming and routes.");
            case RABBIT -> lines("lore.rabbit.1", "&7A mobile and light pet.", "lore.rabbit.2", "&7Good for pace and height.");
            case BEE -> lines("lore.bee.1", "&7Supports the owner in survival.", "lore.bee.2", "&7Reliable in longer skirmishes.");
            case PARROT -> lines("lore.parrot.1", "&7Adds awareness and mobility.", "lore.parrot.2", "&7Useful for exploring the world.");
            case BAT -> lines("lore.bat.1", "&7A calm companion for darkness.", "lore.bat.2", "&7Useful in mines and at night.");
            case BLAZE -> lines("lore.blaze.1", "&7More aggressive and hotter than normal forms.", "lore.blaze.2", "&7Shines in dangerous areas.");
            case AXOLOTL -> lines("lore.axolotl.1", "&7Comfortable around water and long trips.", "lore.axolotl.2", "&7Grows into a soft defensive support pet.");
            case BREEZE -> lines("lore.breeze.1", "&7Fast, sharp, and movement-focused.", "lore.breeze.2", "&7Good for players who like tempo and vertical fights.");
            case FROG -> lines("lore.frog.1", "&7A mobile pet for water, swamps, and jumps.", "lore.frog.2", "&7Helpful when terrain keeps changing underfoot.");
            case GHAST -> lines("lore.ghast.1", "&7A rare air pet with high burst potential.", "lore.ghast.2", "&7Best when falls, fire, and pressure matter.");
            case PANDA -> lines("lore.panda.1", "&7Slow, steady, and built around sustain.", "lore.panda.2", "&7Good for safer long sessions.");
            case PHANTOM -> lines("lore.phantom.1", "&7A night hunter with aerial pressure.", "lore.phantom.2", "&7Good for chase tempo and risky movement.");
            case ARMADILLO -> lines("lore.armadillo.1", "&7A compact defensive companion.", "lore.armadillo.2", "&7Useful when stability matters more than speed.");
            case VEX -> lines("lore.vex.1", "&7A temporary service form of Allay.", "lore.vex.2", "&7Focused on sudden burst damage.");
        };
    }

    static String defenseChanceText(PetType type) {
        return switch (type) {
            case WOLF -> t("defense.very-high", "very high");
            case BLAZE, GHAST, BREEZE, VEX -> t("defense.high", "high");
            case BEE, FOX, ARMADILLO, AXOLOTL -> t("defense.solid", "solid");
            case RABBIT, CAT, FROG, PANDA -> t("defense.medium", "medium");
            default -> t("defense.moderate", "moderate");
        };
    }

    static String usefulEffectsText(PetType type) {
        return switch (type) {
            case WOLF -> t("effects.wolf", "resistance, strength, protection");
            case CAT -> t("effects.cat", "night vision, speed, invisibility");
            case ALLAY -> t("effects.allay", "haste, speed, resource support");
            case FOX -> t("effects.fox", "haste, speed, loot");
            case RABBIT -> t("effects.rabbit", "jump boost, speed, tempo");
            case BEE -> t("effects.bee", "regeneration, haste, survival");
            case PARROT -> t("effects.parrot", "speed, night vision, mobility");
            case BAT -> t("effects.bat", "night vision, speed, light scouting");
            case BLAZE -> t("effects.blaze", "fire resistance, resistance, strength");
            case AXOLOTL -> t("effects.axolotl", "water breathing, regeneration, absorption");
            case BREEZE -> t("effects.breeze", "speed, jump boost, slow falling");
            case FROG -> t("effects.frog", "jump boost, speed, water breathing");
            case GHAST -> t("effects.ghast", "slow falling, fire resistance, strength");
            case PANDA -> t("effects.panda", "resistance, regeneration, absorption");
            case PHANTOM -> t("effects.phantom", "slow falling, night vision, speed");
            case ARMADILLO -> t("effects.armadillo", "resistance, absorption, slow falling");
            case VEX -> t("effects.vex", "speed, strength, burst");
        };
    }

    private static List<String> lines(String firstKey, String firstFallback, String secondKey, String secondFallback) {
        return List.of(t(firstKey, firstFallback), t(secondKey, secondFallback));
    }

    private static List<String> effects(String... values) {
        return java.util.Arrays.stream(values).map(PetGuiText::e).toList();
    }

    private static String t(String key, String fallback) {
        return GameText.text("gui.pet.text." + key, fallback, fallback);
    }

    private static String e(String en) {
        String key = en.toLowerCase(Locale.ROOT).replace(":", "").replace(" ", "-").replace("+", "plus");
        return "&f" + GameText.text("gui.pet.effect." + key, en, en);
    }
}
