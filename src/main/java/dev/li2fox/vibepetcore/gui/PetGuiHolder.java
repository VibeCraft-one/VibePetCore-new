package dev.li2fox.vibepetcore.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class PetGuiHolder implements InventoryHolder {
    private final String menuId;

    public PetGuiHolder(String menuId) {
        this.menuId = menuId;
    }

    public String menuId() {
        return menuId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("PetGuiHolder is a marker holder and does not own an inventory instance.");
    }
}
