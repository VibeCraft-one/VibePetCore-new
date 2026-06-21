package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.pet.armor.PetArmorTier;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

final class PetArmorHelpPage implements PetGuiPage {
    private final PetGuiService gui;

    PetArmorHelpPage(PetGuiService gui) {
        this.gui = gui;
    }

    @Override
    public GuiPageId id() {
        return GuiPageId.PET_ARMOR_HELP;
    }

    @Override
    public void open(Player player) {
        open(player, "master");
    }

    void open(Player player, String source) {
        Inventory inventory = Bukkit.createInventory(
            new PetGuiHolder("petarmor:" + gui.normalizeSource(source)),
            54,
            gui.title(GameText.text("pet.armor.gui.title", "VibePet - Броня", "VibePet - Armor"))
        );
        gui.fillFrame(inventory);
        inventory.setItem(4, gui.item(Material.HEART_OF_THE_SEA, "&b" + GameText.text("pet.armor.gui.craft-title", "Крафт кольчуги питомца", "Pet chainmail crafting"), List.of(
            GameText.text("pet.armor.gui.craft.1", "&7Сердце моря ставится в центр.", "&7Heart of the sea goes in the center."),
            GameText.text("pet.armor.gui.craft.2", "&7Вокруг него 8 блоков материала.", "&7Place 8 material blocks around it."),
            GameText.text("pet.armor.gui.craft.3", "&7На выходе: кастомная броня наутилуса.", "&7Output: custom nautilus armor.")
        )));
        int[] slots = {20, 21, 22, 23, 24};
        PetArmorTier[] tiers = PetArmorTier.values();
        for (int index = 0; index < slots.length && index < tiers.length; index++) {
            inventory.setItem(slots[index], gui.createPetArmor(tiers[index]));
        }
        inventory.setItem(31, gui.item(Material.ENCHANTED_BOOK, "&d" + GameText.text("pet.armor.gui.enchant-title", "Зачарование", "Enchanting"), List.of(
            GameText.text("pet.armor.gui.enchant.1", "&7Кольчугу можно усилить на наковальне.", "&7The chainmail can be upgraded on an anvil."),
            GameText.text("pet.armor.gui.enchant.2", "&7Подходят защитные книги для нагрудника.", "&7Chestplate protection books are supported."),
            GameText.text("pet.armor.gui.enchant.3", "&7Лимит: &fдо 4 &7защитных чар.", "&7Limit: &fup to 4 &7protection enchants."),
            GameText.text("pet.armor.gui.enchant.4", "&7Эффект чар на питомце делится на 2.", "&7Enchant effects on the pet are halved.")
        )));
        inventory.setItem(40, gui.item(Material.CHEST, "&e" + GameText.text("pet.armor.gui.activate-title", "Активация", "Activation"), List.of(
            GameText.text("pet.armor.gui.activate.1", "&7Положите кольчугу в рюкзак питомца.", "&7Place the chainmail in the pet vault."),
            GameText.text("pet.armor.gui.activate.2", "&7Одновременно работает только одна.", "&7Only one works at the same time."),
            GameText.text("pet.armor.gui.activate.3", "&7Если качество выше эволюции, предмет вернётся игроку.", "&7If the tier is too high, it is returned to the player.")
        )));
        inventory.setItem(49, gui.back());
        gui.playMenuOpen(player, Sound.UI_BUTTON_CLICK, 0.6F, 1.1F);
        player.openInventory(inventory);
    }
}
