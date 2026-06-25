package dev.li2fox.vibepetcore.core;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.pet.PetType;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.command.CommandSender;

final class VibePetHelpSupport {
    private final BalanceConfig balanceConfig;

    VibePetHelpSupport(BalanceConfig balanceConfig) {
        this.balanceConfig = balanceConfig;
    }

    private String msg(String key, String fallback, Object... replacements) {
        return this.balanceConfig.message(key, fallback, replacements);
    }

    private void send(CommandSender sender, String key, String fallback, Object... replacements) {
        sender.sendMessage(msg(key, fallback, replacements));
    }

    void sendPetHelpDetailed(CommandSender sender, String[] args, boolean playerCommand) {
        if (args.length < 2) {
            send(sender, "help.pet.header", "VibePet help:");
            send(sender, "help.pet.summon", "- /pet help summon - how to summon and hide a pet.");
            send(sender, "help.pet.death", "- /pet help death - what happens after death.");
            send(sender, "help.pet.growth", "- /pet help growth - feeding, levels, and evolution.");
            send(sender, "help.pet.backpack", "- /pet help backpack - how the pet backpack works.");
            send(sender, "help.pet.roles", "- /pet help roles - what makes pets different.");
            send(sender, "help.pet.commands", "- /pet help commands - quick player commands.");
            send(sender, "help.pet.topic", "- /pet help <wolf|cat|allay|...> - a short overview of a specific pet.");
            send(sender, "help.pet.quests", "- /pet help quests - tasks and rewards.");
            if (!playerCommand) {
                send(sender, "help.pet.admin", "- /vpc help admin - admin commands.");
                send(sender, "help.pet.source", "- /vpc help source - Pet Source and teleport point.");
                send(sender, "help.pet.storage", "- /vpc help storage - data storage.");
            }
            return;
        }
        switch (args[1].toLowerCase()) {
            case "summon": {
                send(sender, "help.pet.summon.header", "Summoning:");
                send(sender, "help.pet.summon.line1", "- Put the filled core in your offhand and right-click.");
                send(sender, "help.pet.summon.line2", "- The first summon takes 3 seconds, the normal summon takes 2 seconds.");
                send(sender, "help.pet.summon.line3", "- Put the matching empty core in your offhand and right-click to return the pet.");
                send(sender, "help.pet.summon.line4", "- In creative and spectator, the pet is automatically returned to the core.");
                return;
            }
            case "death": {
                send(sender, "help.pet.death.header", "Death and penalty:");
                send(sender, "help.pet.death.line1", "- After pet death, a recovery penalty applies.");
                send(sender, "help.pet.death.line2", "- While the penalty is active, the pet cannot be summoned.");
                send(sender, "help.pet.death.line3", "- The core lore shows the penalty only while it is active.");
                return;
            }
            case "growth": {
                send(sender, "help.pet.growth.header", "Growth and evolution:");
                send(sender, "help.pet.growth.line1", "- Feed the pet suitable food to keep health and satiety up.");
                send(sender, "help.pet.growth.line2", "- Each stage has its own level, bond, resources, and stage quests.");
                send(sender, "help.pet.growth.line3", "- {text}", "text", this.evolutionRequirementSummary(2));
                send(sender, "help.pet.growth.line4", "- {text}", "text", this.evolutionRequirementSummary(3));
                send(sender, "help.pet.growth.line5", "- {text}", "text", this.evolutionRequirementSummary(4));
                send(sender, "help.pet.growth.line6", "- {text}", "text", this.evolutionRequirementSummary(5));
                send(sender, "help.pet.growth.line7", "- Bond grows from staying nearby, feeding, and completing pet quests.");
                send(sender, "help.pet.growth.line8", "- At the first stage pets look small; later they grow more visibly.");
                send(sender, "help.pet.growth.line9", "- /pet train near a summoned pet grants training XP on cooldown.");
                return;
            }
            case "backpack": {
                send(sender, "help.pet.backpack.header", "Pet backpack:");
                send(sender, "help.pet.backpack.line1", "- Open it with /pet vault or from the pet menu.");
                send(sender, "help.pet.backpack.line2", "- For loot pets, the sample item inside the backpack sets the filter.");
                send(sender, "help.pet.backpack.line3", "- Backpack size grows with the pet.");
                return;
            }
            case "roles": {
                send(sender, "help.pet.roles.header", "Pet roles:");
                send(sender, "help.pet.roles.line1", "- Combat pets protect the player and retaliate more often.");
                send(sender, "help.pet.roles.line2", "- Support pets rely more on effects and softer reactions.");
                send(sender, "help.pet.roles.line3", "- Loot pets are more focused on backpacks and pickup behavior.");
                return;
            }
            case "commands": {
                send(sender, "help.pet.commands.header", "Player commands:");
                send(sender, "help.pet.commands.line1", "- /pet, /pet menu, /pet call, /pet stay, /pet follow");
                send(sender, "help.pet.commands.line2", "- /pet menu main - open the Pet Source hub.");
                send(sender, "help.pet.commands.line3", "- /pet vault, /pet autoloot, /pet defense, /pet position, /pet evolve");
                send(sender, "help.pet.commands.line4", "- /pet info, /pet points, /pet quest, /pet box");
                return;
            }
            case "quests": {
                send(sender, "help.pet.quests.header", "Quests:");
                send(sender, "help.pet.quests.line1", "- Accept and turn them in through the Pet Source or /pet menu main -> Quests.");
                send(sender, "help.pet.quests.line2", "- Daily and weekly quests return after cooldown.");
                send(sender, "help.pet.quests.line3", "- Quests reward Pet Points for paid Source openings.");
                return;
            }
            case "admin": {
                if (playerCommand) {
                    send(sender, "help.pet.admin-only", "This topic is available through /vpc help admin.");
                    return;
                }
                send(sender, "help.pet.admin.header", "Admin commands:");
                send(sender, "help.pet.admin.line1", "- /vpc status, /vpc reload, /vpc save");
                send(sender, "help.pet.admin.line2", "- /vpc spawn, /vpc remove, /vpc debugpet");
                send(sender, "help.pet.admin.line3", "- /vpc admin giveegg, addattempts, addpoints, takepoints");
                send(sender, "help.pet.admin.line4", "- /vpc admin setevolution, setlevel, setrarity, setsatiety");
                send(sender, "help.pet.admin.line5", "- /vpc admin repaircore, inspect, audit, fixegg, fixpet, dumpconfig, debugtest");
                send(sender, "help.pet.admin.line6", "- /vpc DANGER-DELETE <All|player> with confirmation via /vpc confirm.");
                return;
            }
            case "source": {
                send(sender, "help.pet.source.header", "Pet Source:");
                send(sender, "help.pet.source.line1", "- /vpc source set - place the Pet Source at your location.");
                send(sender, "help.pet.source.line2", "- /vpc source remove - remove the Pet Source.");
                send(sender, "help.pet.source.line3", "- /vpc source info - inspect the configured Source point.");
                send(sender, "help.pet.source.line4", "- /vpc tppoint set|info|clear - manage teleport destination.");
                return;
            }
            case "storage": {
                send(sender, "help.pet.storage.header", "Data storage:");
                send(sender, "help.pet.storage.line1", "- storage.backend in config.yml chooses sqlite, mysql, or json.");
                send(sender, "help.pet.storage.line2", "- sqlite = players.db, mysql = a MySQL table, json = players/<uuid>.json.");
                send(sender, "help.pet.storage.line3", "- Data is cached and saved on save, quit, and on interval.");
                return;
            }
        }
        PetType.parse(args[1]).ifPresentOrElse(type -> {
            if (type == PetType.VEX) {
                send(sender, "help.pet.vex", "VEX is a temporary service form of the legendary Allay; it has no separate core.");
                return;
            }
            sender.sendMessage(GameText.petTypeName(type) + ":");
            send(sender, "help.pet.type.role", "- Role: {role}.", "role", this.shortRole((PetType)((Object)type)));
            send(sender, "help.pet.type.buffs", "- Buffs:");
            for (String line : this.petBuffHelp((PetType)((Object)type))) {
                sender.sendMessage("  " + line);
            }
            send(sender, "help.pet.type.food", "- Favorite food: {food}", "food", GameText.materialList(this.balanceConfig.petFoodMaterials((PetType)((Object)type)), 6));
            send(sender, "help.pet.type.evolution", "- Evolution requires resources, and later stages also need stage quests.");
            send(sender, "help.pet.type.tip", "- Tip: put the core in your offhand and right-click.");
        }, () -> send(sender, "help.pet.unknown", "Unknown help topic or pet type."));
    }

    void sendAdminCommandHelp(CommandSender sender, String[] args, Predicate<CommandSender> requireAdmin) {
        if (!requireAdmin.test(sender)) {
            return;
        }
        if (args.length >= 2) {
            switch (args[1].toLowerCase()) {
                case "admin": {
                    send(sender, "help.admin.header", "VibePet admin commands:");
                    send(sender, "help.admin.line1", "- /vpc admin giveegg <player> <type> <rarity> - give a new pet core.");
                    send(sender, "help.admin.line2", "- /vpc admin addattempts <player> <amount> - add Source attempts.");
                    send(sender, "help.admin.line3", "- /vpc admin addpoints <player> <amount> - grant pet progress points.");
                    send(sender, "help.admin.line4", "- /vpc admin takepoints <player> <amount> - remove pet progress points.");
                    send(sender, "help.admin.line5", "- /vpc admin setevolution <1-5> - change the held core evolution.");
                    send(sender, "help.admin.line6", "- /vpc admin setlevel <1-10> - change the held core level.");
                    send(sender, "help.admin.line7", "- /vpc admin setrarity <rarity> - change the held core rarity.");
                    send(sender, "help.admin.line8", "- /vpc admin repaircore - repair the held core and remove the penalty.");
                    return;
                }
                case "source": {
                    send(sender, "help.admin.source.header", "Pet Source:");
                    send(sender, "help.admin.source.line1", "- /vpc source set - place the source at the admin location.");
                    send(sender, "help.admin.source.line2", "- /vpc source remove - remove the source.");
                    send(sender, "help.admin.source.line3", "- /vpc source info - show source coordinates and mode.");
                    send(sender, "help.admin.source.line4", "- /vpc tppoint set/info/clear - manage the player teleport point.");
                    return;
                }
                case "danger": {
                    send(sender, "help.admin.danger.header", "Dangerous pet deletion:");
                    send(sender, "help.admin.danger.line1", "- /vpc DANGER-DELETE <player> - delete eggs, data, and the online player's active pet.");
                    send(sender, "help.admin.danger.line2", "- /vpc DANGER-DELETE All - delete the pet generation and invalidate old eggs.");
                    send(sender, "help.admin.danger.line3", "- After the command you must run /vpc confirm within 10 seconds.");
                    send(sender, "help.admin.danger.line4", "- Use only before cleaning dupes or old broken versions.");
                    return;
                }
                case "debug": {
                    send(sender, "help.admin.debug.header", "VibePet diagnostics:");
                    send(sender, "help.admin.debug.line1", "- /vpc debugpet - show runtime state for the current pet and held core.");
                    send(sender, "help.admin.debug.line2", "- /vpc admin inspect - same thing through the admin subcommand.");
                    send(sender, "help.admin.debug.line3", "- /vpc admin audit - search for duplicate petId values, broken buttons, and desync.");
                    send(sender, "help.admin.debug.line4", "- /vpc admin fixpet - try to recreate the runtime pet.");
                    send(sender, "help.admin.debug.line5", "- /vpc admin dumpconfig - print key world and debug settings.");
                    send(sender, "help.admin.debug.line6", "- /vpc admin debugtest - write a test debug line.");
                    return;
                }
                case "storage": {
                    send(sender, "help.admin.storage.header", "Data storage:");
                    send(sender, "help.admin.storage.line1", "- /vpc save - force-save player data.");
                    send(sender, "help.admin.storage.line2", "- storage.backend in config.yml chooses sqlite, mysql, or json.");
                    send(sender, "help.admin.storage.line3", "- sqlite stores data in players.db, mysql stores a MySQL table, json stores players/<uuid>.json.");
                    send(sender, "help.admin.storage.line4", "- If you suspect desync, run /vpc save first and then restart.");
                    return;
                }
            }
        }
        send(sender, "help.admin.default.header", "VibePet admin help:");
        send(sender, "help.admin.default.line1", "- /vpc status - check how many players are loaded in memory.");
        send(sender, "help.admin.default.line2", "- /vpc reload - reread config.yml and modules without a full restart.");
        send(sender, "help.admin.default.line3", "- /vpc save - save player data manually.");
        send(sender, "help.admin.default.line4", "- /vpc spawn <type> - test-spawn a runtime pet near the admin.");
        send(sender, "help.admin.default.line5", "- /vpc remove - remove the admin's active runtime pet.");
        send(sender, "help.admin.default.line6", "- /vpc leaderboard - top players by pet points.");
        send(sender, "help.admin.default.line7", "- /vpc help admin - eggs, points, levels, and core repair.");
        send(sender, "help.admin.default.line8", "- /vpc help source - source and teleport-point commands.");
        send(sender, "help.admin.default.line9", "- /vpc tppoint set/info/clear - teleport destination controls.");
        send(sender, "help.admin.default.line10", "- /vpc help danger - dangerous deletion of old/duped pets.");
        send(sender, "help.admin.default.line11", "- /vpc help debug - runtime, held core, and config diagnostics.");
        send(sender, "help.admin.default.line12", "- /pet help - opens the GUI help for players; /pet help growth gives a focused topic.");
    }

    void sendPetHelp(CommandSender sender, String[] args, boolean playerCommand) {
        if (args.length < 2) {
            send(sender, "help.pet.default.header", "VibePet help:");
            send(sender, "help.pet.default.line1", "- /pet or /pet menu - open the active pet menu.");
            send(sender, "help.pet.default.line2", "- /pet menu main - open the Pet Source hub.");
            send(sender, "help.pet.default.line3", "- /pet help <wolf|cat|allay|...> - food and role for a pet.");
            send(sender, "help.pet.default.line4", "- /pet help quests - how quests and resource turn-ins work.");
            send(sender, "help.pet.default.line5", "- /pet help source - how Pet Source and teleport work.");
            send(sender, "help.pet.default.line6", "- /pet help storage - where player data is stored.");
            return;
        }
        if (args[1].equalsIgnoreCase("quests")) {
            send(sender, "help.pet.quests.short1", "Quests: accept them from the Pet Source or via /pet menu main -> Quests, then complete the objective.");
            send(sender, "help.pet.quests.short2", "Gather quests are turned in through the GUI: the required items are consumed automatically.");
            send(sender, "help.pet.quests.short3", "Daily/weekly quests repeat after cooldown; evolution quests are usually one-time.");
            return;
        }
        if (args[1].equalsIgnoreCase("source")) {
            send(sender, "help.pet.source.short1", "Pet Source is placed by admin with /vpc source set.");
            send(sender, "help.pet.source.short2", "Teleport point is managed with /vpc tppoint set/info/clear.");
            return;
        }
        if (args[1].equalsIgnoreCase("storage")) {
            send(sender, "help.pet.storage.short1", "Storage: storage.backend in config.yml. sqlite = players.db, mysql = MySQL database, json = players/<uuid>.json.");
            send(sender, "help.pet.storage.short2", "Data is cached in memory and writes on save/quit/interval.");
            return;
        }
        PetType.parse(args[1]).ifPresentOrElse(type -> {
            if (type == PetType.VEX) {
                send(sender, "help.pet.vex.short", "VEX is a temporary service form of the legendary Allay, so it has no separate egg.");
                return;
            }
            sender.sendMessage(GameText.petTypeName(type) + ":");
            send(sender, "help.pet.type.short1", "- Put the core in your offhand and right-click to summon the pet.");
            send(sender, "help.pet.type.short2", "- Food: {food}", "food", GameText.materialList(this.balanceConfig.petFoodMaterials((PetType)((Object)type)), 6));
            send(sender, "help.pet.type.short3", "- Evolution requires resources, and later stages also require stage quests.");
            send(sender, "help.pet.type.short4", "- Commands: /pet call, /pet stay, /pet follow, /pet vault, /pet autoloot, /pet defense, /pet position, /pet evolve, /pet");
        }, () -> send(sender, "help.pet.unknown-short", "Unknown pet type."));
    }

    private String evolutionRequirementSummary(int nextStage) {
        int quests = balanceConfig.evolutionRequiredQuests(nextStage);
        String questText = quests > 0
            ? msg("help.pet.evolution.quest-suffix", ", quests: {quests}", "quests", quests)
            : msg("help.pet.evolution.no-quest-suffix", ", no required quests");
        return msg("help.pet.evolution.summary",
            "E{stage}: Lv.{level}, bond {bond}/10{quests}",
            "stage", nextStage,
            "level", balanceConfig.evolutionRequiredLevel(nextStage),
            "bond", balanceConfig.evolutionRequiredBond(nextStage),
            "quests", questText);
    }

    private String shortRole(PetType type) {
        return switch (type) {
            case PetType.WOLF -> "combat and defense";
            case PetType.CAT -> "night, mines, safe retreat";
            case PetType.ALLAY -> "mining, resources, backpack";
            case PetType.FOX -> "loot, speed, scouting";
            case PetType.RABBIT -> "movement and farms";
            case PetType.BEE -> "healing and survival";
            case PetType.PARROT -> "scouting and mobility";
            case PetType.BAT -> "night, mines, scouting";
            case PetType.BLAZE -> "Nether, fire, combat";
            case PetType.AXOLOTL -> "water, support, outings";
            case PetType.BREEZE -> "wind, dashes, maneuvers";
            case PetType.FROG -> "swamps, jumps, control";
            case PetType.GHAST -> "air, pressure, long-range threats";
            case PetType.PANDA -> "survival and resilience";
            case PetType.PHANTOM -> "night, dives, pursuit";
            case PetType.ARMADILLO -> "armor, defense, stability";
            case PetType.VEX -> "dashes and burst damage";
        };
    }

    private List<String> petBuffHelp(PetType type) {
        return switch (type) {
            case PetType.WOLF -> List.of("E1: Resistance I", "E2: +Strength I", "E4: +Absorption I", "E5: Resistance II");
            case PetType.CAT -> List.of("E1: Night Vision I", "E2: +Speed I", "E4: +Invisibility I");
            case PetType.ALLAY -> List.of("E1: Haste I", "E2: +Speed I", "E4: +Saturation I", "E5: Haste II");
            case PetType.FOX -> List.of("E1: Haste I", "E2: +Speed I", "E3: +Night Vision I", "E5: Haste II");
            case PetType.RABBIT -> List.of("E1: Jump Boost I", "E2: +Speed I", "E4: +Slow Falling I", "E5: Jump Boost II");
            case PetType.BEE -> List.of("E1: Regeneration I", "E2: +Haste I", "E4: +Absorption I");
            case PetType.PARROT -> List.of("E1: Speed I", "E3: +Night Vision I", "E5: +Slow Falling I", "E5: Speed II");
            case PetType.BAT -> List.of("E1: Night Vision I", "E3: +Speed I", "E5: +Slow Falling I");
            case PetType.BLAZE -> List.of("E1: Fire Resistance I", "E2: +Resistance I", "E4: +Strength I");
            case PetType.AXOLOTL -> List.of("E1: Water Breathing I", "E2: +Regeneration I", "E4: +Absorption I");
            case PetType.BREEZE -> List.of("E1: Speed I", "E2: +Jump Boost I", "E4: +Slow Falling I");
            case PetType.FROG -> List.of("E1: Jump Boost I", "E2: +Speed I", "E4: +Water Breathing I");
            case PetType.GHAST -> List.of("E1: Slow Falling I", "E2: +Fire Resistance I", "E4: +Strength I");
            case PetType.PANDA -> List.of("E1: Resistance I", "E2: +Regeneration I", "E4: +Absorption I");
            case PetType.PHANTOM -> List.of("E1: Slow Falling I", "E3: +Night Vision I", "E5: +Speed I");
            case PetType.ARMADILLO -> List.of("E1: Resistance I", "E2: +Absorption I", "E4: +Slow Falling I");
            case PetType.VEX -> List.of("E1: Speed I", "E4: +Strength I", "Dashes and burst damage");
        };
    }
}
