package dev.li2fox.vibepetcore.core;

import dev.li2fox.vibepetcore.api.EconomyAPI;
import dev.li2fox.vibepetcore.api.PetAPI;
import dev.li2fox.vibepetcore.box.BoxOpenResult;
import dev.li2fox.vibepetcore.box.LootBoxManager;
import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.egg.PetEggService;
import dev.li2fox.vibepetcore.gui.PetGuiService;
import dev.li2fox.vibepetcore.master.PetMasterManager;
import dev.li2fox.vibepetcore.player.ActivePetSelectionSupport;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.player.PlayerData;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.player.QuestProgressData;
import dev.li2fox.vibepetcore.pet.PetEngineManager;
import dev.li2fox.vibepetcore.pet.PetRarity;
import dev.li2fox.vibepetcore.pet.PetType;
import dev.li2fox.vibepetcore.pet.RuntimePet;
import dev.li2fox.vibepetcore.quest.QuestDefinition;
import dev.li2fox.vibepetcore.quest.QuestManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

final class VibePetCommandHandler implements CommandExecutor {
    private final VibePetCorePlugin plugin;
    private final ModuleManager moduleManager;
    private final BalanceConfig balanceConfig;
    private final PlayerDataManager playerDataManager;
    private final PetEngineManager petEngineManager;
    private final QuestManager questManager;
    private final LootBoxManager lootBoxManager;
    private final PetEggService petEggService;
    private final PetGuiService petGuiService;
    private final PetMasterManager petMasterManager;
    private final PetDebugLogger debugLogger;
    private final PetAPI petAPI;
    private final EconomyAPI economyAPI;
    private final VibePetHelpSupport helpSupport;
    private final PetSourceCommandSupport petSourceCommandSupport;
    private final Map<UUID, PendingDangerDelete> pendingDangerDeletes = new ConcurrentHashMap<>();

    private record PendingDangerDelete(String scope, UUID targetPlayerId, String targetName, BukkitTask timeoutTask) {
    }

    VibePetCommandHandler(
        VibePetCorePlugin plugin,
        ModuleManager moduleManager,
        BalanceConfig balanceConfig,
        PlayerDataManager playerDataManager,
        PetEngineManager petEngineManager,
        QuestManager questManager,
        LootBoxManager lootBoxManager,
        PetEggService petEggService,
        PetGuiService petGuiService,
        PetMasterManager petMasterManager,
        PetDebugLogger debugLogger,
        PetAPI petAPI,
        EconomyAPI economyAPI
    ) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;
        this.balanceConfig = balanceConfig;
        this.helpSupport = new VibePetHelpSupport(balanceConfig);
        this.playerDataManager = playerDataManager;
        this.petEngineManager = petEngineManager;
        this.questManager = questManager;
        this.lootBoxManager = lootBoxManager;
        this.petEggService = petEggService;
        this.petGuiService = petGuiService;
        this.petMasterManager = petMasterManager;
        this.debugLogger = debugLogger;
        this.petAPI = petAPI;
        this.economyAPI = economyAPI;
        this.petSourceCommandSupport = new PetSourceCommandSupport(
            balanceConfig,
            petMasterManager,
            this::requireAdmin,
            this::sendNormalizedMessage
        );
    }

    void cancelPendingDangerDeletes() {
        this.pendingDangerDeletes.values().forEach(pending -> pending.timeoutTask().cancel());
        this.pendingDangerDeletes.clear();
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        try {
            boolean playerCommand = command.getName().equalsIgnoreCase("pet");
            if (args.length == 0) {
                if (playerCommand && sender instanceof Player) {
                    Player player = (Player)sender;
                    this.petGuiService.open(player, "pet");
                } else if (playerCommand) {
                    sendNormalizedMessage(sender, msg("command.pet.open-menu", "Use /pet to open the pet menu."));
                } else {
                    sendNormalizedMessage(sender, msg("command.vpc.use-status-help", "Use /vpc status or /vpc help."));
                }
                return true;
            }
            String root = args[0].toLowerCase();
            if (playerCommand && this.isAdminRoot(root)) {
                sendNormalizedMessage(sender, msg("command.pet.admin-root", "This command is only available under /vpc. Use /vpc {root}.", "root", root));
                return true;
            }
            if (!playerCommand && this.isPlayerRoot(root) && !root.equals("help")) {
                sendNormalizedMessage(sender, msg("command.vpc.player-root", "This command is only available under /pet. Use /pet {root}.", "root", root));
                return true;
            }
            return this.handleCommandRoot(sender, label, args, root, playerCommand);
        }
        catch (RuntimeException exception) {
            String commandPath = args.length == 0 ? label : label + " " + String.join((CharSequence)" ", args);
            this.debugLogger.errorRateLimited("command:" + commandPath, "command", "Command failed sender=" + sender.getName() + " command=/" + commandPath, exception, 10000L);
            sendNormalizedMessage(sender, msg("command.error.runtime", "Something went wrong while handling the command. Check runtime-notes.log."));
            return true;
        }
    }

    private void sendNormalizedMessage(CommandSender sender, String message) {
        if (sender == null || message == null) {
            return;
        }
        sender.sendMessage(normalizeLegacyMessage(message));
    }

    private String msg(String key, String fallback, Object... replacements) {
        return this.balanceConfig.message(key, fallback, replacements);
    }

    private String normalizeLegacyMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        if (!looksLikeLegacyMojibake(message)) {
            return message;
        }
        try {
            byte[] legacyBytes = message.getBytes(java.nio.charset.Charset.forName("windows-1251"));
            return new String(legacyBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return message;
        }
    }

    private boolean looksLikeLegacyMojibake(String message) {
        for (int index = 0; index < message.length(); index++) {
            char ch = message.charAt(index);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
            if (block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
                || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_A
                || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_B
                || ch == '\u00A0'
                || ch == 'Ё'
                || ch == 'ё'
                || ch == 'Ў'
                || ch == 'ў'
                || ch == 'Ћ'
                || ch == 'ћ'
                || ch == 'Ќ'
                || ch == 'ќ'
                || ch == 'Ї'
                || ch == 'ї'
                || ch == 'Є'
                || ch == 'є') {
                return true;
            }
        }
        return false;
    }

    private void handleDangerDeleteCommand(CommandSender sender, String label, String[] args) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player)) {
            sendNormalizedMessage(sender, msg("danger-delete.player-only", "Only players can use DANGER-DELETE."));
            return;
        }
        Player player = (Player)sender;
        if (args.length < 2) {
            sendNormalizedMessage(sender, msg("danger-delete.usage", "Usage: /{label} DANGER-DELETE <All|player>", "label", label));
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            this.startDangerDeleteConfirmation(player, "all", null, "All");
            return;
        }
        Player target = Bukkit.getPlayerExact((String)args[1]);
        if (target == null) {
            sendNormalizedMessage(sender, msg("danger-delete.no-target", "Target player is not online for DANGER-DELETE."));
            return;
        }
        this.startDangerDeleteConfirmation(player, "player", target.getUniqueId(), target.getName());
    }

    private void handleConfirmCommand(CommandSender sender) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player)) {
            sendNormalizedMessage(sender, msg("confirm.player-only", "Only players can use /vpc confirm."));
            return;
        }
        Player player = (Player)sender;
        PendingDangerDelete pending = this.pendingDangerDeletes.remove(player.getUniqueId());
        if (pending == null) {
            sendNormalizedMessage(sender, msg("confirm.none", "No pending DANGER-DELETE confirmation."));
            return;
        }
        pending.timeoutTask().cancel();
        this.executeDangerDelete(player, pending);
    }

    private boolean handleAdminCommand(CommandSender sender, String label, String[] args) {
        if (!this.requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sendNormalizedMessage(sender, msg("admin.usage.header", "VibePet admin help:"));
            sendNormalizedMessage(sender, msg("admin.usage.giveegg", "- /{label} admin giveegg <player> <type> <rarity> - give a pet core.", "label", label));
            sendNormalizedMessage(sender, msg("admin.usage.addattempts", "- /{label} admin addattempts <player> <amount> - add Source attempts.", "label", label));
            sendNormalizedMessage(sender, msg("admin.usage.addpoints", "- /{label} admin addpoints <player> <amount> - add points.", "label", label));
            sendNormalizedMessage(sender, msg("admin.usage.takepoints", "- /{label} admin takepoints <player> <amount> - remove points.", "label", label));
            sendNormalizedMessage(sender, msg("admin.usage.tools", "- /{label} admin inspect | audit | fixpet | repaircore - diagnostics and recovery.", "label", label));
            sendNormalizedMessage(sender, msg("admin.usage.help", "- /vpc help admin - show this help."));
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "debugtest": {
                this.debugLogger.debug("admin", "Debug test by " + sender.getName());
                sendNormalizedMessage(sender, msg("admin.debugtest", "Debug test sent. debug.enabled=true. File: {file}", "file", this.debugLogger.file().getAbsolutePath()));
                sendNormalizedMessage(sender, msg("admin.runtime-notes", "runtime-notes={file}", "file", this.debugLogger.notesFile().getAbsolutePath()));
                return true;
            }
            case "dumpconfig": {
                String language = this.balanceConfig.language();
                boolean debug = this.balanceConfig.debugEnabled();
                String world = sender instanceof Player ? ((Player)sender).getWorld().getName() : "console";
                String attacks = sender instanceof Player ? Boolean.toString(this.balanceConfig.worldPetAttacksEnabled(((Player)sender).getWorld().getName())) : "n/a";
                String combat = sender instanceof Player ? Boolean.toString(this.petEngineManager.isPetCombatSuppressed((Player)sender)) : "n/a";
                sendNormalizedMessage(sender, msg("admin.dumpconfig", "language={language} debug={debug} world={world} attacks={attacks} combatSuppressed={combat}", "language", language, "debug", debug, "world", world, "attacks", attacks, "combat", combat));
                sendNormalizedMessage(sender, msg("admin.debuglog", "debug.log={file}", "file", this.debugLogger.file().getAbsolutePath()));
                sendNormalizedMessage(sender, msg("admin.runtime-notes", "runtime-notes={file}", "file", this.debugLogger.notesFile().getAbsolutePath()));
                return true;
            }
            case "inspect": {
                if (!(sender instanceof Player)) {
                    sendNormalizedMessage(sender, msg("admin.inspect.player-only", "Only players can use /vpc admin inspect."));
                    return true;
                }
                Player player = (Player)sender;
                sendNormalizedMessage(sender, this.petEngineManager.debugPet(player));
                this.sendRuntimePetInfo(sender, player);
                return true;
            }
            case "audit": {
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) {
                        sendNormalizedMessage(sender, msg("admin.audit.not-online", "That player is not online."));
                        return true;
                    }
                } else if (sender instanceof Player) {
                    target = (Player)sender;
                } else {
                    sendNormalizedMessage(sender, msg("admin.audit.usage", "Usage: /{label} admin audit <player>", "label", label));
                    return true;
                }
                this.sendPetAudit(sender, target);
                return true;
            }
            case "fixpet": {
                if (!(sender instanceof Player)) {
                    sendNormalizedMessage(sender, msg("admin.fixpet.player-only", "Only players can use /vpc admin fixpet."));
                    return true;
                }
                Player player = (Player)sender;
                this.petEngineManager.callPet(player);
                sendNormalizedMessage(sender, msg("admin.fixpet.success", "Runtime pet recovery triggered."));
                return true;
            }
            case "addattempts": {
                if (args.length < 4) {
                    sendNormalizedMessage(sender, msg("admin.addattempts.usage", "Usage: /{label} admin addattempts <player> <amount>", "label", label));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sendNormalizedMessage(sender, msg("admin.player-not-found", "Player not found."));
                    return true;
                }
                OptionalInt amountValue = this.parsePositiveInt(args[3]);
                if (amountValue.isEmpty()) {
                    sendNormalizedMessage(sender, msg("admin.amount.positive", "Amount must be a positive whole number."));
                    return true;
                }
                int amount = amountValue.getAsInt();
                PlayerData data = this.playerDataManager.getOrLoad(target.getUniqueId());
                for (int index = 0; index < amount; ++index) {
                    data.addExtraBoxAttempt();
                }
                sendNormalizedMessage(sender, msg("admin.addattempts.success", "Added {amount} Source attempts to {player}.", "amount", amount, "player", target.getName()));
                return true;
            }
            case "addpoints":
            case "takepoints": {
                if (args.length < 4) {
                    sendNormalizedMessage(sender, msg("admin.points.usage", "Usage: /{label} admin {sub} <player> <amount>", "label", label, "sub", args[1].toLowerCase()));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sendNormalizedMessage(sender, msg("admin.player-not-found", "Player not found."));
                    return true;
                }
                OptionalLong amountValue = this.parsePositiveLong(args[3]);
                if (amountValue.isEmpty()) {
                    sendNormalizedMessage(sender, msg("admin.amount.positive", "Amount must be a positive whole number."));
                    return true;
                }
                long amount = amountValue.getAsLong();
                if (args[1].equalsIgnoreCase("addpoints")) {
                    this.playerDataManager.getOrLoad(target.getUniqueId()).addPoints(amount);
                    sendNormalizedMessage(sender, msg("admin.addpoints.success", "Added {amount} points to {player}.", "amount", amount, "player", target.getName()));
                } else {
                    sendNormalizedMessage(sender, this.playerDataManager.getOrLoad(target.getUniqueId()).takePoints(amount)
                        ? msg("admin.takepoints.success", "Removed {amount} points from {player}.", "amount", amount, "player", target.getName())
                        : msg("admin.takepoints.failure", "Not enough points to remove."));
                }
                return true;
            }
            case "giveegg": {
                if (args.length < 5) {
                    sendNormalizedMessage(sender, msg("admin.giveegg.usage", "Usage: /{label} admin giveegg <player> <type> <common|rare|epic|legendary>", "label", label));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sendNormalizedMessage(sender, msg("admin.player-not-found", "Player not found."));
                    return true;
                }
                PetRarity rarity = PetRarity.parse(args[4]);
                if (args[3].equalsIgnoreCase("all")) {
                    int given = 0;
                    for (PetType petType : PetType.values()) {
                        if (petType == PetType.VEX) {
                            continue;
                        }
                        ItemStack egg = this.petEggService.createEgg(petType, rarity, GameText.petTypeName(petType));
                        target.getInventory().addItem(new ItemStack[]{egg}).values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
                        given++;
                    }
                    sendNormalizedMessage(sender, msg("admin.giveegg.all-success", "Given {amount} pet cores to {player}.", "amount", given, "player", target.getName()));
                    return true;
                }
                PetType type = PetType.parse(args[3]).orElse(null);
                if (type == null) {
                    sendNormalizedMessage(sender, msg("admin.invalid-pet-type", "Unknown pet type."));
                    return true;
                }
                if (type == PetType.VEX) {
                    sendNormalizedMessage(sender, msg("admin.giveegg.vex-blocked", "VEX cannot be spawned here."));
                    return true;
                }
                ItemStack egg = this.petEggService.createEgg(type, rarity, GameText.petTypeName(type));
                target.getInventory().addItem(new ItemStack[]{egg}).values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
                sendNormalizedMessage(sender, msg("admin.giveegg.success", "Pet core given to {player}.", "player", target.getName()));
                return true;
            }
            case "setevolution":
            case "setlevel":
            case "setrarity":
            case "setsatiety":
            case "repaircore":
            case "fixegg": {
                if (!(sender instanceof Player)) {
                    sendNormalizedMessage(sender, msg("admin.core.player-only", "Only players can use this part of admin commands."));
                    return true;
                }
                Player player = (Player)sender;
                Optional<HeldPetCore> heldCore = heldPetCore(player);
                if (heldCore.isEmpty()) {
                    sendNormalizedMessage(sender, msg("admin.core.missing", "Hold a pet core in your hand first."));
                    return true;
                }
                HeldPetCore core = heldCore.get();
                OwnedPetData data = core.data();
                boolean refreshRuntime = false;
                if (args[1].equalsIgnoreCase("setevolution") && args.length >= 3) {
                    OptionalInt nextStageValue = this.parseNonNegativeInt(args[2]);
                    if (nextStageValue.isEmpty()) {
                        sendNormalizedMessage(sender, msg("admin.core.evolution-number", "Evolution stage must be a whole number (0+)."));
                        return true;
                    }
                    int nextStage = nextStageValue.getAsInt();
                    if (nextStage > 5) {
                        sendNormalizedMessage(sender, msg("admin.core.evolution-range", "Evolution stage must be between 0 and 5."));
                        return true;
                    }
                    refreshRuntime = data.evolutionStage() != nextStage;
                    data.setEvolutionStage(nextStage);
                } else if (args[1].equalsIgnoreCase("setlevel") && args.length >= 3) {
                    OptionalInt levelValue = this.parseNonNegativeInt(args[2]);
                    if (levelValue.isEmpty()) {
                        sendNormalizedMessage(sender, msg("admin.core.level-number", "Level must be a whole number (0+)."));
                        return true;
                    }
                    int level = levelValue.getAsInt();
                    if (level > this.balanceConfig.maxLevel()) {
                        sendNormalizedMessage(sender, msg("admin.core.level-range", "Level must be between 0 and {max}.", "max", this.balanceConfig.maxLevel()));
                        return true;
                    }
                    data.setLevel(level);
                } else if (args[1].equalsIgnoreCase("setrarity") && args.length >= 3) {
                    data.setRarity(PetRarity.parse(args[2]).name());
                } else if (args[1].equalsIgnoreCase("setsatiety") && args.length >= 3) {
                    OptionalInt satietyValue = this.parseNonNegativeInt(args[2]);
                    if (satietyValue.isEmpty()) {
                        sendNormalizedMessage(sender, msg("admin.core.satiety-number", "Satiety must be a whole number (0+)."));
                        return true;
                    }
                    int satiety = satietyValue.getAsInt();
                    if (satiety > this.balanceConfig.eggMaxSatiety()) {
                        sendNormalizedMessage(sender, msg("admin.core.satiety-range", "Satiety must be between 0 and {max}.", "max", this.balanceConfig.eggMaxSatiety()));
                        return true;
                    }
                    data.setSatiety(satiety);
                } else {
                    data.setDurability(this.balanceConfig.eggMaxDurability());
                    data.setInactiveUntilMillis(0L);
                    data.setSatiety(this.balanceConfig.eggMaxSatiety());
                    data.setHealth(data.maxHealth());
                }
                this.petEngineManager.replaceActivePetData(player, data);
                if (refreshRuntime) {
                    this.petEngineManager.refreshActivePet(player, null);
                }
                setHeldPetCore(player, core, writeCoreForState(core.item(), data));
                sendNormalizedMessage(sender, msg("admin.core.updated", "Pet core updated: evolution {evolution}, level {level}, rarity {rarity}.", "evolution", data.evolutionStage(), "level", data.level(), "rarity", data.rarity()));
                return true;
            }
            default:
                return false;
        }
    }

    private boolean handleCommandRoot(CommandSender sender, String label, String[] args, String root, boolean playerCommand) {
        if (root.equals("debug")) {
            this.handleDebugCommand(sender, label, args);
            return true;
        }
        if (root.equals("status")) {
            if (!this.requireAdmin(sender)) {
                return true;
            }
            sendNormalizedMessage(sender, msg("status.loaded", "VibePetCore: loaded players={count}", "count", this.playerDataManager.loadedCount()));
            return true;
        }
        if (root.equals("danger-delete")) {
            this.handleDangerDeleteCommand(sender, label, args);
            return true;
        }
        if (root.equals("confirm")) {
            this.handleConfirmCommand(sender);
            return true;
        }
        if (root.equals("reload")) {
            if (!this.requireAdmin(sender)) {
                return true;
            }
            this.plugin.reloadConfig();
            this.moduleManager.reloadAll();
            sendNormalizedMessage(sender, msg("reload.success", "VibePetCore config and modules reloaded."));
            return true;
        }
        if (root.equals("save")) {
            if (!this.requireAdmin(sender)) {
                return true;
            }
            this.playerDataManager.saveAll();
            sendNormalizedMessage(sender, msg("save.success", "VibePetCore player data saved."));
            return true;
        }
        if (root.equals("spawn")) {
            if (!this.requireAdmin(sender)) {
                return true;
            }
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("spawn.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            if (args.length < 2) {
                sendNormalizedMessage(sender, msg("spawn.usage", "Usage: /{label} spawn <bat|cat|wolf|parrot|rabbit|allay|bee|fox|blaze>", "label", label));
                return true;
            }
            return PetType.parse(args[1]).map(type -> {
                if (type == PetType.VEX) {
                    sendNormalizedMessage(sender, msg("spawn.vex-blocked", "VEX cannot be spawned here."));
                    return true;
                }
                this.petAPI.spawnPet(player, (PetType)((Object)type));
                sendNormalizedMessage(sender, msg("spawn.success", "Pet spawned: {type}", "type", GameText.petTypeName(type)));
                return true;
            }).orElseGet(() -> {
                sendNormalizedMessage(sender, msg("spawn.invalid", "Unknown pet type."));
                return true;
            });
        }
        if (root.equals("remove")) {
            if (!this.requireAdmin(sender)) {
                return true;
            }
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("remove.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            this.petAPI.removePet(player);
            sendNormalizedMessage(sender, msg("remove.success", "Pet removed from runtime state."));
            return true;
        }
        if (root.equals("name")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("pet.rename.player-only", "Only players can rename pets."));
                return true;
            }
            Player player = (Player)sender;
            if (args.length < 2) {
                sendNormalizedMessage(sender, msg(
                    "pet.rename.usage",
                    "Откройте /pet и нажмите бирку имени, или используйте /pet name <имя>."
                ));
                return true;
            }
            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            PetEngineManager.RenameResult result = this.petEngineManager.renameActivePet(player, name);
            sendNormalizedMessage(sender, result.message());
            if (result.success()) {
                this.syncOffhandEgg(player);
            }
            return true;
        }
        if (playerCommand && isHiddenPlayerChatRoot(root)) {
            sendNormalizedMessage(sender, GameText.text(
                "command.pet.gui-only",
                "Эта настройка теперь в меню /pet или у Источника.",
                "This action is now in /pet or at the Source."
            ));
            return true;
        }
        if (root.equals("call")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("call.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            sendNormalizedMessage(sender, this.petEngineManager.callPet(player)
                ? msg("call.success", "Pet called.")
                : msg("call.failure", "The pet is already nearby."));
            return true;
        }
        if (root.equals("stay")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("stay.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            sendNormalizedMessage(sender, this.petEngineManager.setWaitMode(player, true)
                ? msg("stay.success", "Pet will stay here.")
                : msg("stay.failure", "The pet is already following."));
            return true;
        }
        if (root.equals("follow")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("follow.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            sendNormalizedMessage(sender, this.petEngineManager.setWaitMode(player, false)
                ? msg("follow.success", "Pet will follow you.")
                : msg("follow.failure", "The pet is already following."));
            return true;
        }
        if (root.equals("vault")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("vault.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            sendNormalizedMessage(sender, this.petEngineManager.openActivePetVault(player)
                ? msg("vault.success", "Pet vault opened.")
                : msg("vault.failure", "The pet vault is unavailable."));
            return true;
        }
        if (root.equals("autoloot")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("autoloot.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            this.petEngineManager.toggleAutoloot(player);
            this.syncOffhandEgg(player);
            return true;
        }
        if (root.equals("defense")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("defense.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            this.petEngineManager.toggleDefense(player);
            this.syncOffhandEgg(player);
            return true;
        }
        if (root.equals("train")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("train.player-only", "Only players can train a pet."));
                return true;
            }
            Player player = (Player)sender;
            PetEngineManager.TrainResult result = this.petEngineManager.trainPet(player);
            sendNormalizedMessage(sender, result.message());
            if (result.success()) {
                this.syncOffhandEgg(player);
            }
            return true;
        }
        if (root.equals("position")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("position.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            this.petEngineManager.cycleFollowPosition(player);
            this.syncOffhandEgg(player);
            return true;
        }
        if (root.equals("evolve")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("evolve.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            this.petEngineManager.tryEvolveActivePet(player);
            this.syncOffhandEgg(player);
            return true;
        }
        if (root.equals("petinfo") || root.equals("info")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("petinfo.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            this.sendRuntimePetInfo(sender, player);
            return true;
        }
        if (root.equals("debugpet")) {
            if (!this.requireAdmin(sender)) {
                return true;
            }
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("debugpet.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            sendNormalizedMessage(sender, this.petEngineManager.debugPet(player));
            sendNormalizedMessage(sender, heldPetCore(player)
                .map(core -> msg("debugpet.egg", "held={type} empty={empty} pet={pet} type={petType} rarity={rarity} level={level}/10 xp={xp}% evolution={evolution} satiety={satiety} durability={durability}",
                    "type", core.item().getType(),
                    "empty", this.petEggService.isEmptyEgg(core.item()),
                    "pet", core.data().petName(),
                    "petType", core.data().petType(),
                    "rarity", core.data().rarity(),
                    "level", core.data().level(),
                    "xp", Math.round(this.petEggService.xpPercent(core.data())),
                    "evolution", core.data().evolutionStage(),
                    "satiety", core.data().satiety(),
                    "durability", core.data().durability()))
                .orElse(msg("debugpet.no-egg", "No pet core found in either hand.")));
            return true;
        }
        if (root.equals("points")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("points.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            sendNormalizedMessage(sender, msg("points.balance", "Your points balance: {points}.", "points", this.economyAPI.points(player.getUniqueId())));
            sendNormalizedMessage(sender, msg("points.usage-hint", "Points are used for progression and special rewards."));
            return true;
        }
        if (root.equals("quest")) {
            return this.handleQuestCommand(sender, label, args);
        }
        if (root.equals("box")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("box.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            if (args.length < 2 || !args[1].equalsIgnoreCase("open")) {
                sendNormalizedMessage(sender, msg("box.usage", "Usage: /{label} box open", "label", label));
                return true;
            }
            BoxOpenResult result = this.lootBoxManager.openNearby(player);
            sendNormalizedMessage(sender, result.message());
            if (result.success() && result.pity()) {
                sendNormalizedMessage(sender, msg("box.pity", "The pity counter has been restored."));
            }
            return true;
        }
        if (root.equals("leaderboard")) {
            if (!this.requireAdmin(sender)) {
                return true;
            }
            this.sendLeaderboard(sender);
            return true;
        }
        if (root.equals("admin")) {
            return this.handleAdminCommand(sender, label, args);
        }
        if (root.equals("source")) {
            return this.petSourceCommandSupport.handleSourceCommand(sender, label, args);
        }
        if (root.equals("tppoint")) {
            return this.petSourceCommandSupport.handleTpPointCommand(sender, label, args);
        }
        if (root.equals("help")) {
            if (playerCommand) {
                if (!(sender instanceof Player)) {
                    sendNormalizedMessage(sender, msg("help.pet-player-only", "Only players can use /pet help."));
                    return true;
                }
                Player player = (Player)sender;
                if (args.length >= 2) {
                    this.sendPetHelpDetailed(sender, args);
                } else {
                    this.petGuiService.open(player, "help");
                }
                return true;
            }
            this.sendAdminCommandHelp(sender, args);
            return true;
        }
        if (root.equals("menu")) {
            if (!(sender instanceof Player)) {
                sendNormalizedMessage(sender, msg("menu.player-only", "Only players can use this command."));
                return true;
            }
            Player player = (Player)sender;
            this.petGuiService.open(player, args.length >= 2 ? args[1] : "pet");
            return true;
        }
        return false;
    }


    private void handleDebugCommand(CommandSender sender, String label, String[] args) {
        if (!this.requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sendNormalizedMessage(sender, msg("debug.usage", "Usage: /{label} debug <on|off>", "label", label));
            return;
        }
        String mode = args[1].toLowerCase();
        if (!mode.equals("on") && !mode.equals("off")) {
            sendNormalizedMessage(sender, msg("debug.usage", "Usage: /{label} debug <on|off>", "label", label));
            return;
        }
        boolean enabled = mode.equals("on");
        this.plugin.getConfig().set("debug.enabled", enabled);
        this.plugin.getConfig().set("debug.pet.runtime", enabled);
        this.plugin.getConfig().set("debug.pet.stuck", enabled);
        this.plugin.getConfig().set("debug.pet.damage", enabled);
        this.plugin.saveConfig();
        this.plugin.reloadConfig();
        this.moduleManager.reloadAll();
        sendNormalizedMessage(sender, enabled
            ? msg("debug.enabled", "All pet debuggers are enabled.")
            : msg("debug.disabled", "All pet debuggers are disabled."));
    }
    private void sendPetHelpDetailed(CommandSender sender, String[] args) {
        this.helpSupport.sendPetHelpDetailed(sender, args, true);
    }

    private void sendAdminCommandHelp(CommandSender sender, String[] args) {
        this.helpSupport.sendAdminCommandHelp(sender, args, this::requireAdmin);
    }

    private void sendPetHelp(CommandSender sender, String[] args, boolean playerCommand) {
        this.helpSupport.sendPetHelp(sender, args, playerCommand);
    }

    private boolean handleQuestCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sendNormalizedMessage(sender, msg("quest.command.player-only", "Only players can use /pet quest."));
            return true;
        }
        Player player = (Player)sender;
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            String category = args.length >= 3 ? args[2] : "all";
            if (!this.isQuestCategory(category)) {
                sendNormalizedMessage(sender, msg("quest.command.unknown-category", "Unknown quest category: {category}.", "category", category));
                sendNormalizedMessage(sender, msg("quest.command.categories", "Categories: all, daily, weekly, evolution, gather, combat, explore."));
                return true;
            }
            sendNormalizedMessage(sender, msg("quest.command.list-header", "Quests ({category}):", "category", GameText.questCategoryName(category)));
            List<QuestDefinition> quests = visibleQuestList(player, category);
            for (QuestDefinition quest : quests) {
                int visibleProgress = this.questManager.displayProgress(player, quest, selectedQuestPetId(player).orElse(null));
                sendNormalizedMessage(sender, msg(
                    "quest.command.list-line",
                    "- {id} [{progress}/{amount}] {status} reward={reward}",
                    "id", quest.id(),
                    "progress", visibleProgress,
                    "amount", quest.amount(),
                    "status", this.questManager.statusLine(player, quest, selectedQuestPetId(player).orElse(null)),
                    "reward", GameText.rewardPoints(quest.rewardPoints())
                ));
            }
            return true;
        }
        if (args.length < 3) {
            sendNormalizedMessage(sender, msg("quest.command.usage", "Usage: /{label} quest <list|accept|turnin> <id>", "label", label));
            return true;
        }
        if (args[1].equalsIgnoreCase("accept")) {
            QuestDefinition quest = this.questManager.quest(args[2]).orElse(null);
            if (quest == null) {
                sendNormalizedMessage(sender, msg("quest.command.unknown", "Unknown quest: {quest}", "quest", args[2]));
                return true;
            }
            Optional<String> blockReason = this.evolutionQuestBlockReason(player, quest);
            if (blockReason.isPresent()) {
                sendNormalizedMessage(sender, msg("quest.blocked", "You cannot take this quest right now: {reason}.", "reason", blockReason.get()));
                return true;
            }
            var result = this.questManager.accept(player.getUniqueId(), args[2], selectedQuestPetId(player).orElse(null));
            sendNormalizedMessage(sender, result.accepted()
                ? (result.replacedPrevious() ? msg("quest.replaced-previous", "You abandoned the current quest and accepted a new one.") : msg("quest.accepted", "Quest accepted."))
                : msg("quest.accept-failed", "Failed to accept quest."));
            return true;
        }
        if (args[1].equalsIgnoreCase("turnin")) {
            QuestDefinition quest = this.questManager.quest(args[2]).orElse(null);
            if (quest == null) {
                sendNormalizedMessage(sender, msg("quest.command.unknown", "Unknown quest: {quest}", "quest", args[2]));
                return true;
            }
            QuestManager.TurnInResult result = this.questManager.turnInResult(player, args[2], selectedQuestPetId(player).orElse(null));
            sendNormalizedMessage(sender, result.turnedIn()
                ? msg("quest.turned-in", "Quest completed. Pet Points awarded.")
                : (result.saveFailed()
                    ? msg("quest.turn-in-save-failed", "Could not save the quest turn-in. Items and reward were restored. Try again in a few seconds.")
                    : msg("quest.turn-in-blocked", "This quest cannot be turned in yet.")));
            return true;
        }
        sendNormalizedMessage(sender, msg("quest.command.usage", "Usage: /{label} quest <list|accept|turnin> <id>", "label", label));
        return true;
    }

    private void sendRuntimePetInfo(CommandSender sender, Player player) {
        this.petAPI.getPet(player).ifPresentOrElse(pet -> sendNormalizedMessage(sender, msg(
            "admin.inspect.runtime-pet",
            "Runtime pet: {type} | rarity: {rarity} | level: {level}/10 | xp: {xp}% | evolution: E{evolution} | satiety: {satiety}/{maxSatiety} | durability: {durability}/{maxDurability}",
            "type", GameText.petTypeName(pet.type()),
            "rarity", GameText.rarityName(pet.data().rarity()),
            "level", pet.data().level(),
            "xp", Math.round(this.petEggService.xpPercent(pet.data())),
            "evolution", pet.data().evolutionStage(),
            "satiety", pet.data().satiety(),
            "maxSatiety", this.balanceConfig.eggMaxSatiety(),
            "durability", pet.data().durability(),
            "maxDurability", this.balanceConfig.eggMaxDurability()
        )), () -> sendNormalizedMessage(sender, heldPetCore(player).map(core -> msg(
            "admin.inspect.held-core",
            "Held core: {pet} | type: {type} | rarity: {rarity} | level: {level}/10 | xp: {xp}% | evolution: E{evolution} | satiety: {satiety}/{maxSatiety} | durability: {durability}/{maxDurability}",
            "pet", core.data().petName(),
            "type", GameText.petTypeName(PetType.parse(core.data().petType()).orElse(PetType.WOLF)),
            "rarity", GameText.rarityName(core.data().rarity()),
            "level", core.data().level(),
            "xp", Math.round(this.petEggService.xpPercent(core.data())),
            "evolution", core.data().evolutionStage(),
            "satiety", core.data().satiety(),
            "maxSatiety", this.balanceConfig.eggMaxSatiety(),
            "durability", core.data().durability(),
            "maxDurability", this.balanceConfig.eggMaxDurability()
        )).orElse(msg("admin.inspect.no-core", "No pet core in either hand."))));
    }

    private void sendLeaderboard(CommandSender sender) {
        List<PlayerData> top = this.playerDataManager.topByPoints(10);
        sendNormalizedMessage(sender, msg("leaderboard.header", "Top players:"));
        for (int index = 0; index < top.size(); ++index) {
            PlayerData data = top.get(index);
            UUID playerId = data.playerId();
            String name = playerId == null
                ? "unknown-player"
                : Optional.ofNullable(Bukkit.getOfflinePlayer(playerId).getName())
                    .filter(value -> !value.isBlank())
                    .orElse("player-" + playerId.toString().substring(0, 8));
            sendNormalizedMessage(sender, msg(
                "leaderboard.line",
                "{rank}. {player} | points: {points} | pets: {pets} | quests: {quests} | activity: {activity}",
                "rank", index + 1,
                "player", name,
                "points", data.points(),
                "pets", data.pets().size(),
                "quests", data.statistics().questsCompleted(),
                "activity", data.statistics().activityTicks()
            ));
        }
    }

    private void sendPetAudit(CommandSender sender, Player target) {
        Optional<RuntimePet> runtimePet;
        sendNormalizedMessage(sender, msg("admin.audit.header", "Audit for player: {player}", "player", target.getName()));
        ArrayList<String> issues = new ArrayList<>();
        PlayerData data = this.playerDataManager.getOrLoad(target.getUniqueId());
        HashMap<UUID, Integer> itemCounts = new HashMap<UUID, Integer>();
        int legacyActiveButtons = 0;
        for (Inventory inventory : List.of(target.getInventory(), target.getEnderChest())) {
            for (int slot = 0; slot < inventory.getSize(); ++slot) {
                ItemStack item = inventory.getItem(slot);
                Optional<OwnedPetData> pet = this.petEggService.readEgg(item);
                if (pet.isEmpty()) continue;
                itemCounts.merge(pet.get().petId(), 1, Integer::sum);
                if (this.petEggService.isActiveButton(item)) {
                    ++legacyActiveButtons;
                }
            }
        }
        itemCounts.forEach((petId, count) -> {
            if (count > 1) {
                issues.add(msg("admin.audit.issue.duplicate", "Duplicate petId {petId} count={count}", "petId", petId, "count", count));
            }
        });
        if (legacyActiveButtons > 0) {
            issues.add(msg("admin.audit.issue.legacy-buttons", "Legacy active buttons detected: {count} (they will be converted to empty cores on sync).", "count", legacyActiveButtons));
        }
        Optional<UUID> activePetId = data.activePetId();
        if (activePetId.isPresent()) {
            UUID id = activePetId.get();
            if (!itemCounts.containsKey(id)) {
                issues.add(msg("admin.audit.issue.missing-inventory", "PlayerData contains petId {petId} but inventory does not.", "petId", id));
            }
        }
        if ((runtimePet = this.petEngineManager.getPet(target)).isPresent() && activePetId.isEmpty()) {
            issues.add(msg("admin.audit.issue.runtime-without-active", "Runtime pet exists but PlayerData activePetId is empty."));
        } else if (runtimePet.isPresent() && activePetId.isPresent() && !runtimePet.get().data().petId().equals(activePetId.get())) {
            issues.add(msg("admin.audit.issue.runtime-mismatch", "Runtime pet id does not match PlayerData activePetId."));
        }
        boolean combatSuppressed = this.petEngineManager.isPetCombatSuppressed(target);
        sendNormalizedMessage(sender, msg(
            "admin.audit.status",
            "world={world} combatSuppressed={combat} activePetId={activePetId}",
            "world", target.getWorld().getName(),
            "combat", combatSuppressed,
            "activePetId", activePetId.map(UUID::toString).orElse("none")
        ));
        if (issues.isEmpty()) {
            sendNormalizedMessage(sender, msg("admin.audit.no-issues", "No audit issues found."));
            return;
        }
        issues.forEach(issue -> sendNormalizedMessage(sender, msg("admin.audit.issue-line", "- {issue}", "issue", issue)));
    }

    private void removePetItems(Player player) {
        this.clearPetItems((Inventory)player.getInventory());
        this.clearPetItems(player.getEnderChest());
        player.updateInventory();
    }

    private void clearPetItems(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); ++slot) {
            ItemStack item = inventory.getItem(slot);
            if (!this.petEggService.isPetEgg(item)) continue;
            inventory.setItem(slot, null);
        }
    }

    private void startDangerDeleteConfirmation(Player sender, String scope, UUID targetPlayerId, String targetName) {
        PendingDangerDelete existing = this.pendingDangerDeletes.remove(sender.getUniqueId());
        if (existing != null) {
            existing.timeoutTask().cancel();
        }
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            PendingDangerDelete pending = this.pendingDangerDeletes.remove(sender.getUniqueId());
            if (pending != null) {
                sendNormalizedMessage(sender, msg("danger-delete.expired", "Danger-delete confirmation expired."));
            }
        }, 200L);
        this.pendingDangerDeletes.put(sender.getUniqueId(), new PendingDangerDelete(scope, targetPlayerId, targetName, timeoutTask));
        sendNormalizedMessage(sender, msg("danger-delete.confirm", "Confirm danger-delete of {target}? Type /vpc confirm within 10 seconds.", "target", scope.equals("all") ? msg("danger-delete.all-target", "ALL pets") : targetName));
    }

    private void executeDangerDelete(Player sender, PendingDangerDelete pending) {
        if (pending.scope().equals("all")) {
            int nextEpoch = this.balanceConfig.eggPurgeEpoch() + 1;
            this.plugin.getConfig().set("egg-core.purge-epoch", (Object)nextEpoch);
            this.plugin.saveConfig();
            this.moduleManager.reloadAll();
            int clearedPlayers = 0;
            for (UUID playerId : this.playerDataManager.knownPlayerIds()) {
                Player online;
                PlayerData data = this.playerDataManager.getOrLoad(playerId);
                if (!data.pets().isEmpty() || data.activePetId().isPresent()) {
                    data.pets().clear();
                    data.setActivePetId(null);
                    ++clearedPlayers;
                }
                if ((online = Bukkit.getPlayer((UUID)playerId)) != null) {
                    this.petEngineManager.despawnPet(online);
                    this.removePetItems(online);
                }
                this.playerDataManager.save(playerId);
            }
            sendNormalizedMessage(sender, msg("danger-delete.all-complete", "DANGER-DELETE ALL completed. Cleared players: {players}. Epoch -> {epoch}.", "players", clearedPlayers, "epoch", nextEpoch));
            return;
        }
        UUID targetPlayerId = pending.targetPlayerId();
        if (targetPlayerId == null) {
            sendNormalizedMessage(sender, msg("danger-delete.no-stored-target", "No target player was stored for DANGER-DELETE."));
            return;
        }
        Player target = Bukkit.getPlayer((UUID)targetPlayerId);
        if (target == null) {
            sendNormalizedMessage(sender, msg("danger-delete.target-offline", "Target player {player} is not online.", "player", pending.targetName()));
            return;
        }
        PlayerData data = this.playerDataManager.getOrLoad(targetPlayerId);
        data.pets().clear();
        data.setActivePetId(null);
        this.petEngineManager.despawnPet(target);
        this.removePetItems(target);
        this.playerDataManager.save(targetPlayerId);
        sendNormalizedMessage(sender, msg("danger-delete.player-complete", "DANGER-DELETE completed for {player}.", "player", target.getName()));
        if (!target.getUniqueId().equals(sender.getUniqueId())) {
            sendNormalizedMessage(target, msg("danger-delete.target-notify", "Your pet data was deleted by an admin."));
        }
    }

    private OptionalInt parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? OptionalInt.of(parsed) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    private OptionalLong parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0L ? OptionalLong.of(parsed) : OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    private OptionalInt parseNonNegativeInt(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 0 ? OptionalInt.of(parsed) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    private boolean isPlayerRoot(String root) {
        return List.of("menu", "name", "call", "stay", "follow", "vault", "autoloot", "defense", "train", "position", "evolve", "info", "petinfo", "points", "quest", "box").contains(root);
    }

    private boolean isHiddenPlayerChatRoot(String root) {
        return List.of("call", "stay", "follow", "train", "position", "quest", "box").contains(root);
    }

    private boolean isAdminRoot(String root) {
        return List.of("status", "reload", "save", "spawn", "remove", "debug", "debugpet", "source", "tppoint", "leaderboard", "admin", "danger-delete").contains(root);
    }

    private boolean isQuestCategory(String category) {
        return category != null && List.of("all", "daily", "weekly", "evolution", "gather", "combat", "explore").contains(category.toLowerCase(Locale.ROOT));
    }

    private List<QuestDefinition> visibleQuestList(Player player, String category) {
        if (!"evolution".equalsIgnoreCase(category)) {
            return this.questManager.visibleQuests(player.getUniqueId(), category);
        }
        int currentStage = selectedQuestEvolutionStage(player);
        if (currentStage <= 0 || currentStage >= 5) {
            return List.of();
        }
        return this.questManager.evolutionQuestsForStage(currentStage);
    }

    private Optional<String> evolutionQuestBlockReason(Player player, QuestDefinition quest) {
        if (!"evolution".equalsIgnoreCase(quest.category())) {
            return Optional.empty();
        }
        int currentStage = selectedQuestEvolutionStage(player);
        if (currentStage <= 0) {
            return Optional.of(msg("quest.block.need-selected-pet", "select an active pet or hold the core"));
        }
        int requiredStage = quest.requiredEvolutionStage();
        return currentStage == requiredStage ? Optional.empty() : Optional.of(msg("quest.block.wrong-stage", "this quest is for E{required}, but E{current} is selected", "required", requiredStage, "current", currentStage));
    }

    private int selectedQuestEvolutionStage(Player player) {
        return this.petEngineManager.getPet(player)
            .map(pet -> pet.data().evolutionStage())
            .or(() -> heldPetCore(player).map(HeldPetCore::data).map(OwnedPetData::evolutionStage))
            .orElse(0);
    }

    private Optional<UUID> selectedQuestPetId(Player player) {
        return this.petEngineManager.getPet(player)
            .map(pet -> pet.data().petId())
            .or(() -> heldPetCore(player).map(HeldPetCore::data).map(OwnedPetData::petId));
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("vibepetcore.admin")) {
            return true;
        }
        sendNormalizedMessage(sender, msg("command.no-permission", "You do not have permission to use this command."));
        return false;
    }

    private Entity targetEntity(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), 6.0, 0.6, entity -> entity != player);
        if (result != null && result.getHitEntity() != null) {
            return result.getHitEntity();
        }
        return player.getNearbyEntities(3.0, 3.0, 3.0).stream().filter(entity -> entity != player).min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation()))).orElse(null);
    }

    private void syncOffhandEgg(Player player) {
        this.petEngineManager.getPet(player).ifPresent(pet -> {
            heldPetCore(player)
                .filter(core -> core.data().petId().equals(pet.data().petId()))
                .ifPresent(core -> setHeldPetCore(player, core, writeCoreForState(core.item(), pet.data())));
        });
    }

    private Optional<HeldPetCore> heldPetCore(Player player) {
        Optional<UUID> activePetId = this.playerDataManager.getOrLoad(player.getUniqueId()).activePetId();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        Optional<OwnedPetData> mainPet = this.petEggService.readEgg(mainHand);
        ItemStack offhand = player.getInventory().getItemInOffHand();
        Optional<OwnedPetData> offhandPet = this.petEggService.readEgg(offhand);
        return ActivePetSelectionSupport.selectPreferred(
            activePetId,
            mainPet.map(pet -> new HeldPetCore(mainHand, true, pet)),
            offhandPet.map(pet -> new HeldPetCore(offhand, false, pet)),
            core -> core.data().petId()
        );
    }

    private void setHeldPetCore(Player player, HeldPetCore core, ItemStack item) {
        if (core.mainHand()) {
            player.getInventory().setItemInMainHand(item);
        } else {
            player.getInventory().setItemInOffHand(item);
        }
    }

    private ItemStack writeCoreForState(ItemStack item, OwnedPetData data) {
        if (this.petEggService.isEmptyEgg(item)) {
            return this.petEggService.writeEmptyEgg(item, data);
        }
        return this.petEggService.writeEgg(item, data);
    }

    private record HeldPetCore(ItemStack item, boolean mainHand, OwnedPetData data) {
    }
}
