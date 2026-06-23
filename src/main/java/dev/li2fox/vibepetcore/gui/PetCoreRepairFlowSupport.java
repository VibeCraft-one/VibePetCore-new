package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class PetCoreRepairFlowSupport {
    private PetCoreRepairFlowSupport() {
    }

    static RepairStateSnapshot snapshotState(Player player, OwnedPetData petData) {
        return new RepairStateSnapshot(
            snapshotPet(petData),
            cloneContents(player.getInventory().getStorageContents())
        );
    }

    static RepairAttemptResult attemptRepair(
        Player player,
        OwnedPetData petData,
        int nextDurability,
        double nextSatiety,
        RepairStateSnapshot snapshot,
        Consumer<OwnedPetData> applyPetState,
        BooleanSupplier saveAction
    ) {
        consumeOneMaterial(player, Material.TOTEM_OF_UNDYING);
        petData.setDurability(nextDurability);
        petData.setInactiveUntilMillis(0L);
        petData.setSatiety(nextSatiety);
        petData.setHealth(petData.maxHealth());
        applyPetState.accept(petData);
        if (saveAction.getAsBoolean()) {
            return new RepairAttemptResult(true, false);
        }

        restorePet(petData, snapshot.petSnapshot());
        applyPetState.accept(snapshot.petSnapshot());
        player.getInventory().setStorageContents(cloneContents(snapshot.storageContents()));
        return new RepairAttemptResult(false, true);
    }

    private static void consumeOneMaterial(Player player, Material material) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length; index++) {
            ItemStack item = contents[index];
            if (item == null || item.getType() != material) {
                continue;
            }
            if (item.getAmount() <= 1) {
                contents[index] = null;
            } else {
                item.setAmount(item.getAmount() - 1);
            }
            player.getInventory().setStorageContents(contents);
            return;
        }
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
            clone[index] = source[index] == null ? null : source[index].clone();
        }
        return clone;
    }

    record RepairStateSnapshot(OwnedPetData petSnapshot, ItemStack[] storageContents) {
    }

    record RepairAttemptResult(boolean repaired, boolean saveFailed) {
    }
}
