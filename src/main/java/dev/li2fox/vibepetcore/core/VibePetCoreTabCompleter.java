package dev.li2fox.vibepetcore.core;

import dev.li2fox.vibepetcore.pet.PetRarity;
import dev.li2fox.vibepetcore.pet.PetType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class VibePetCoreTabCompleter implements TabCompleter {
    private static final List<String> PLAYER_ROOTS = List.of(
        "menu", "name", "vault", "autoloot", "defense", "train", "evolve", "info", "points", "help"
    );
    private static final List<String> ADMIN_ROOTS = List.of(
        "status", "reload", "save", "spawn", "remove", "debugpet", "source", "tppoint", "leaderboard", "admin", "danger-delete", "confirm", "help"
    );
    private static final List<String> ADMIN_SUBCOMMANDS = List.of(
        "giveegg", "addattempts", "addpoints", "takepoints", "setevolution", "setlevel", "setrarity",
        "setsatiety", "repaircore", "inspect", "audit", "fixegg", "fixpet", "dumpconfig", "debugtest"
    );
    private static final List<String> SOURCE = List.of("set", "remove", "info");
    private static final List<String> TP_POINT = List.of("set", "clear", "info");
    private static final List<String> MENUS = List.of("main", "pet", "help");
    private static final List<String> PLAYER_HELP = List.of("summon", "death", "growth", "backpack", "roles", "commands", "quests", "source", "storage");
    private static final List<String> ADMIN_HELP = List.of("admin", "source", "storage", "danger", "debug");

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        boolean playerCommand = command.getName().equalsIgnoreCase("pet");
        if (args.length == 1) {
            return filter(playerCommand ? PLAYER_ROOTS : ADMIN_ROOTS, args[0]);
        }

        String root = args[0].toLowerCase();
        if (root.equals("admin")) {
            return adminComplete(sender, args);
        }
        if (root.equals("spawn") && args.length == 2) {
            return filter(petTypes(), args[1]);
        }
        if (root.equals("help") && args.length == 2) {
            List<String> help = new ArrayList<>(petTypes());
            help.addAll(PLAYER_HELP);
            if (!playerCommand) {
                help.addAll(ADMIN_HELP);
            }
            return filter(help, args[1]);
        }
        if (root.equals("menu") && args.length == 2) {
            return filter(MENUS, args[1]);
        }
        if (root.equals("source")) {
            if (!sender.hasPermission("vibepetcore.admin")) {
                return List.of();
            }
            if (args.length == 2) {
                return filter(SOURCE, args[1]);
            }
        }
        if (root.equals("tppoint") && args.length == 2) {
            if (!sender.hasPermission("vibepetcore.admin")) {
                return List.of();
            }
            return filter(TP_POINT, args[1]);
        }
        if (root.equals("danger-delete")) {
            if (!sender.hasPermission("vibepetcore.admin")) {
                return List.of();
            }
            if (args.length == 2) {
                List<String> values = new ArrayList<>();
                values.add("All");
                values.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                return filter(values, args[1]);
            }
        }
        return List.of();
    }

    private List<String> adminComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vibepetcore.admin")) {
            return List.of();
        }
        if (args.length == 2) {
            return filter(ADMIN_SUBCOMMANDS, args[1]);
        }
        String sub = args[1].toLowerCase();
        if (sub.equals("giveegg")) {
            if (args.length == 3) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
            }
            if (args.length == 4) {
                return filter(petTypes(), args[3]);
            }
            if (args.length == 5) {
                return filter(rarities(), args[4]);
            }
        }
        if (sub.equals("addattempts") || sub.equals("addpoints") || sub.equals("takepoints")) {
            if (args.length == 3) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
            }
            if (args.length == 4) {
                return filter(sub.equals("addattempts") ? List.of("1", "3", "5", "10", "25") : List.of("10", "50", "100", "250", "1000"), args[3]);
            }
        }
        if (sub.equals("audit") && args.length == 3) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (sub.equals("setrarity") && args.length == 3) {
            return filter(rarities(), args[2]);
        }
        if (sub.equals("setevolution") && args.length == 3) {
            return filter(List.of("1", "2", "3", "4", "5"), args[2]);
        }
        if (sub.equals("setlevel") && args.length == 3) {
            return filter(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), args[2]);
        }
        if (sub.equals("setsatiety") && args.length == 3) {
            return filter(List.of("0", "1", "2", "3", "4", "5"), args[2]);
        }
        return List.of();
    }

    private List<String> petTypes() {
        List<String> values = new ArrayList<>(Arrays.stream(PetType.values())
            .filter(type -> type != PetType.VEX)
            .map(type -> type.name().toLowerCase())
            .toList());
        values.add("all");
        return values;
    }

    private List<String> rarities() {
        return Arrays.stream(PetRarity.values()).map(rarity -> rarity.name().toLowerCase()).toList();
    }

    private List<String> filter(List<String> values, String input) {
        String normalized = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(normalized)) {
                result.add(value);
            }
        }
        return result;
    }
}
