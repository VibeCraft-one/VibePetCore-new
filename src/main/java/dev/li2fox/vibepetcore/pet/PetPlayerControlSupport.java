package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.GameText;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

final class PetPlayerControlSupport {
    private PetPlayerControlSupport() {
    }

    static boolean toggleWaitMode(Player player, Entity entity, Optional<RuntimePet> pet) {
        pet.ifPresent(runtimePet -> {
            if (runtimePet.state() == PetState.IDLE) {
                runtimePet.setState(PetState.FOLLOW);
                runtimePet.recall(player);
                player.sendMessage(GameText.text("pet.control.returning", "Питомец возвращается к вам.", "The pet is returning to you."));
            } else {
                runtimePet.setState(PetState.IDLE);
                player.sendMessage(GameText.text("pet.control.wait-nearby", "Питомец будет ждать рядом и гулять вокруг.", "The pet will wait nearby and wander around."));
            }
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation().add(0.0D, 0.7D, 0.0D), 8, 0.2D, 0.2D, 0.2D, 0.02D);
            player.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.45F, 1.4F);
        });
        return pet.isPresent();
    }

    static boolean callPet(Player player, Optional<RuntimePet> pet, BiConsumer<Player, Long> showActionBar) {
        pet.ifPresent(runtimePet -> {
            runtimePet.recall(player);
            showActionBar.accept(player, 2_000L);
            player.sendMessage(GameText.text("pet.control.heard-call", "Питомец услышал зов.", "The pet heard you."));
        });
        return pet.isPresent();
    }

    static boolean setWaitMode(Player player, Optional<RuntimePet> pet, boolean waiting, BiConsumer<Player, Long> showActionBar) {
        pet.ifPresent(runtimePet -> {
            runtimePet.setState(waiting ? PetState.IDLE : PetState.FOLLOW);
            if (!waiting) {
                runtimePet.recall(player);
            }
            showActionBar.accept(player, 2_000L);
            player.sendMessage(waiting
                ? GameText.text("pet.control.waiting", "Питомец будет ждать рядом.", "The pet will wait nearby.")
                : GameText.text("pet.control.following", "Питомец снова следует за вами.", "The pet follows you again."));
        });
        return pet.isPresent();
    }

    static boolean toggleAutoloot(Player player, Optional<RuntimePet> pet, BalanceConfig balanceConfig, BiConsumer<Player, Long> showActionBar) {
        if (pet.isEmpty()) {
            player.sendMessage(GameText.text("pet.control.no-active", "Нет активного питомца.", "No active pet."));
            return false;
        }
        RuntimePet runtimePet = pet.get();
        if (!balanceConfig.petAutoLootEnabled(runtimePet.type())) {
            player.sendMessage(GameText.text("pet.control.autoloot-unsupported", "Этот питомец не поддерживает автолут.", "This pet does not support auto-loot."));
            return false;
        }
        boolean enabled = !runtimePet.data().autoLootEnabled();
        runtimePet.data().setAutoLootEnabled(enabled);
        showActionBar.accept(player, 2_000L);
        player.sendMessage(enabled
            ? GameText.text("pet.control.autoloot-enabled", "Автолут включён.", "Auto-loot enabled.")
            : GameText.text("pet.control.autoloot-disabled", "Автолут выключен.", "Auto-loot disabled."));
        return true;
    }

    static boolean toggleDefense(Player player, Optional<RuntimePet> pet, BiConsumer<Player, Long> showActionBar) {
        if (pet.isEmpty()) {
            player.sendMessage(GameText.text("pet.control.no-active", "Нет активного питомца.", "No active pet."));
            return false;
        }
        RuntimePet runtimePet = pet.get();
        boolean enabled = !runtimePet.data().defenseEnabled();
        runtimePet.data().setDefenseEnabled(enabled);
        showActionBar.accept(player, 2_000L);
        player.sendMessage(enabled
            ? GameText.text("pet.control.defense-enabled", "Защита владельца включена.", "Player defense enabled.")
            : GameText.text("pet.control.defense-disabled", "Защита владельца выключена.", "Player defense disabled."));
        return true;
    }

    static boolean cycleFollowPosition(Player player, Optional<RuntimePet> pet, BiConsumer<Player, Long> showActionBar) {
        if (pet.isEmpty()) {
            player.sendMessage(GameText.text("pet.control.no-active", "Нет активного питомца.", "No active pet."));
            return false;
        }
        RuntimePet runtimePet = pet.get();
        return setFollowPosition(player, pet, runtimePet.data().followPosition() + 1, showActionBar);
    }

    static boolean previousFollowPosition(Player player, Optional<RuntimePet> pet, BiConsumer<Player, Long> showActionBar) {
        if (pet.isEmpty()) {
            player.sendMessage(GameText.text("pet.control.no-active", "Нет активного питомца.", "No active pet."));
            return false;
        }
        RuntimePet runtimePet = pet.get();
        return setFollowPosition(player, pet, runtimePet.data().followPosition() - 1, showActionBar);
    }

    static boolean setFollowPosition(Player player, Optional<RuntimePet> pet, int position, BiConsumer<Player, Long> showActionBar) {
        if (pet.isEmpty()) {
            player.sendMessage(GameText.text("pet.control.no-active", "Нет активного питомца.", "No active pet."));
            return false;
        }
        RuntimePet runtimePet = pet.get();
        runtimePet.data().setFollowPosition(position);
        runtimePet.recall(player);
        showActionBar.accept(player, 2_000L);
        player.sendMessage(GameText.text(
            "pet.control.position",
            "Позиция питомца: {position}.",
            "Pet position: {position}."
        ).replace("{position}", runtimePet.data().followPositionTitle()));
        return true;
    }

    static boolean increaseFollowDistance(Player player, Optional<RuntimePet> pet, BiConsumer<Player, Long> showActionBar) {
        if (pet.isEmpty()) {
            player.sendMessage(GameText.text("pet.control.no-active", "Нет активного питомца.", "No active pet."));
            return false;
        }
        RuntimePet runtimePet = pet.get();
        runtimePet.data().increaseFollowDistance();
        runtimePet.recall(player);
        showActionBar.accept(player, 2_000L);
        player.sendMessage(GameText.text(
            "pet.control.distance",
            "Дистанция питомца: {distance}.",
            "Pet distance: {distance}."
        ).replace("{distance}", runtimePet.data().followDistanceTitle()));
        return true;
    }

    static boolean decreaseFollowDistance(Player player, Optional<RuntimePet> pet, BiConsumer<Player, Long> showActionBar) {
        if (pet.isEmpty()) {
            player.sendMessage(GameText.text("pet.control.no-active", "Нет активного питомца.", "No active pet."));
            return false;
        }
        RuntimePet runtimePet = pet.get();
        runtimePet.data().decreaseFollowDistance();
        runtimePet.recall(player);
        showActionBar.accept(player, 2_000L);
        player.sendMessage(GameText.text(
            "pet.control.distance",
            "Дистанция питомца: {distance}.",
            "Pet distance: {distance}."
        ).replace("{distance}", runtimePet.data().followDistanceTitle()));
        return true;
    }
}
