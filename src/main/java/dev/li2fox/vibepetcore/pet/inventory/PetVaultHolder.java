package dev.li2fox.vibepetcore.pet.inventory;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class PetVaultHolder implements InventoryHolder {
    private final UUID petId;

    public PetVaultHolder(UUID petId) {
        this.petId = petId;
    }

    public UUID petId() {
        return petId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("PetVaultHolder is a marker holder and does not own an inventory instance.");
    }
}
