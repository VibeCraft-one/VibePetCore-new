package dev.li2fox.vibepetcore.gui;

import org.bukkit.entity.Player;

interface PetGuiPage {
    GuiPageId id();

    void open(Player player);

    default boolean handleClick(Player player, int slot) {
        return true;
    }

    default boolean handleClick(Player player, String menuId, int slot) {
        return handleClick(player, slot);
    }
}
