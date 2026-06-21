package dev.li2fox.vibepetcore.core;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.master.PetMasterManager;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class PetSourceCommandSupport {
    private final BalanceConfig balanceConfig;
    private final PetMasterManager petMasterManager;
    private final Predicate<CommandSender> requireAdmin;
    private final BiConsumer<CommandSender, String> sendMessage;

    PetSourceCommandSupport(
        BalanceConfig balanceConfig,
        PetMasterManager petMasterManager,
        Predicate<CommandSender> requireAdmin,
        BiConsumer<CommandSender, String> sendMessage
    ) {
        this.balanceConfig = balanceConfig;
        this.petMasterManager = petMasterManager;
        this.requireAdmin = requireAdmin;
        this.sendMessage = sendMessage;
    }

    boolean handleSourceCommand(CommandSender sender, String label, String[] args) {
        if (!this.requireAdmin.test(sender)) {
            return true;
        }
        if (args.length < 2) {
            this.sendMessage.accept(sender, msg("source.usage", "Usage: /{label} source <set|remove|info>", "label", label));
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "set": {
                if (!(sender instanceof Player)) {
                    this.sendMessage.accept(sender, msg("source.player-only", "This command can only be used by a player."));
                    return true;
                }
                Player player = (Player) sender;
                this.petMasterManager.place(player.getLocation());
                this.petMasterManager.setTeleportLocation(player.getLocation());
                this.sendMessage.accept(sender, msg("source.set", "Pet Source placed: {location}", "location", this.locationText(player.getLocation())));
                return true;
            }
            case "remove": {
                this.sendMessage.accept(sender, this.petMasterManager.remove()
                    ? msg("source.removed", "Pet Source removed.")
                    : msg("source.not-set", "Pet Source is not configured."));
                return true;
            }
            case "info": {
                if (!this.petMasterManager.configured()) {
                    this.sendMessage.accept(sender, msg("source.not-set", "Pet Source is not configured."));
                    return true;
                }
                Location location = this.petMasterManager.location();
                World world = location == null ? null : location.getWorld();
                if (world == null) {
                    this.sendMessage.accept(sender, msg("source.not-set", "Pet Source is not configured."));
                    return true;
                }
                this.sendMessage.accept(sender, msg("source.info", "Pet Source: {world} {x} {y} {z} style={style} mode={mode}",
                    "world", world.getName(),
                    "x", location.getBlockX(),
                    "y", location.getBlockY(),
                    "z", location.getBlockZ(),
                    "style", this.petMasterManager.style().id(),
                    "mode", this.petMasterManager.visualMode().id()));
                return true;
            }
            default:
                return false;
        }
    }

    boolean handleTpPointCommand(CommandSender sender, String label, String[] args) {
        if (!this.requireAdmin.test(sender)) {
            return true;
        }
        if (args.length < 2) {
            this.sendMessage.accept(sender, msg("tppoint.usage", "Usage: /{label} tppoint <set|clear|info>", "label", label));
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "set": {
                if (!(sender instanceof Player)) {
                    this.sendMessage.accept(sender, msg("tppoint.player-only", "This command can only be used by a player."));
                    return true;
                }
                Player player = (Player) sender;
                this.petMasterManager.setTeleportLocation(player.getLocation());
                this.sendMessage.accept(sender, msg("tppoint.set", "Teleport point set: {location}", "location", this.locationText(player.getLocation())));
                return true;
            }
            case "clear": {
                this.sendMessage.accept(sender, this.petMasterManager.clearTeleportLocation()
                    ? msg("tppoint.cleared", "Teleport point cleared.")
                    : msg("tppoint.not-set", "Teleport point is not set."));
                return true;
            }
            case "info": {
                Location location = this.petMasterManager.teleportLocation();
                if (location == null || location.getWorld() == null) {
                    this.sendMessage.accept(sender, msg("tppoint.not-set", "Teleport point is not set."));
                    return true;
                }
                this.sendMessage.accept(sender, msg("tppoint.info", "Teleport point: {location}", "location", this.locationText(location)));
                return true;
            }
            default:
                return false;
        }
    }

    private String msg(String key, String fallback, Object... replacements) {
        return this.balanceConfig.message(key, fallback, replacements);
    }

    private String locationText(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName()
            + " " + location.getBlockX()
            + " " + location.getBlockY()
            + " " + location.getBlockZ();
    }
}
