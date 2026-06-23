package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.PetRarity;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class PetForgeFlowSupport {
    private PetForgeFlowSupport() {
    }

    static ForgeStateSnapshot snapshotState(Player player, OwnedPetData petData) {
        return new ForgeStateSnapshot(
            snapshotPet(petData),
            new InventorySnapshot(
                cloneContents(player.getInventory().getStorageContents()),
                cloneItem(player.getInventory().getItemInMainHand()),
                cloneItem(player.getInventory().getItemInOffHand())
            )
        );
    }

    static ForgeAttemptResult attemptRarityUpgrade(
        Player player,
        OwnedPetData petData,
        PetRarity rarity,
        List<Integer> donorSlots,
        ForgeStateSnapshot snapshot,
        double successChance,
        DoubleSupplier rollSupplier,
        Consumer<OwnedPetData> applyPetState,
        BooleanSupplier saveAction
    ) {
        for (int slot : donorSlots) {
            consumeOneInventorySlot(player, slot);
        }

        boolean upgraded = rollSupplier.getAsDouble() < successChance;
        return finishAttempt(player, petData, rarity, snapshot, upgraded, applyPetState, saveAction);
    }

    private static ForgeAttemptResult finishAttempt(
        Player player,
        OwnedPetData petData,
        PetRarity rarity,
        ForgeStateSnapshot snapshot,
        boolean upgraded,
        Consumer<OwnedPetData> applyPetState,
        BooleanSupplier saveAction
    ) {
        if (upgraded) {
            petData.setRarity(rarity.next().name());
            applyPetState.accept(petData);
        }
        if (saveAction.getAsBoolean()) {
            return new ForgeAttemptResult(upgraded, false);
        }

        restorePet(petData, snapshot.petSnapshot());
        applyPetState.accept(snapshot.petSnapshot());
        restoreInventory(player, snapshot.inventorySnapshot());
        return new ForgeAttemptResult(false, true);
    }

    private static void consumeOneInventorySlot(Player player, int slot) {
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || isAir(item.getType())) {
            return;
        }
        if (item.getAmount() <= 1) {
            player.getInventory().setItem(slot, null);
            return;
        }
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItem(slot, item);
    }

    private static void restoreInventory(Player player, InventorySnapshot snapshot) {
        player.getInventory().setStorageContents(cloneContents(snapshot.storageContents()));
        player.getInventory().setItemInMainHand(cloneItem(snapshot.mainHand()));
        player.getInventory().setItemInOffHand(cloneItem(snapshot.offHand()));
    }

    private static OwnedPetData snapshotPet(OwnedPetData source) {
        OwnedPetData snapshot = new OwnedPetData(source.petId(), source.ownerId(), source.petType(), source.rarity());
        restorePet(snapshot, source);
        return snapshot;
    }

    private static void restorePet(OwnedPetData target, OwnedPetData source) {
        target.copyProgressionFrom(source);
        target.setOwnerId(source.ownerId());
        target.setState(source.state());
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            clone[index] = cloneItem(source[index]);
        }
        return clone;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static boolean isAir(Material material) {
        return material == null || material == Material.AIR || material.name().endsWith("_AIR");
    }

    record ForgeStateSnapshot(OwnedPetData petSnapshot, InventorySnapshot inventorySnapshot) {
    }

    record InventorySnapshot(ItemStack[] storageContents, ItemStack mainHand, ItemStack offHand) {
    }

    record ForgeAttemptResult(boolean upgraded, boolean saveFailed) {
    }
}
