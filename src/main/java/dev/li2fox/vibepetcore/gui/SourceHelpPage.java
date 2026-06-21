package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.pet.PetType;
import dev.li2fox.vibepetcore.pet.armor.PetArmorTier;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

final class SourceHelpPage implements PetGuiPage {
    private final PetGuiService gui;

    SourceHelpPage(PetGuiService gui) {
        this.gui = gui;
    }

    @Override
    public GuiPageId id() {
        return GuiPageId.SOURCE_HELP;
    }

    @Override
    public void open(Player player) {
        open(player, "pet");
    }

    void open(Player player, String source) {
        String normalizedSource = gui.normalizeSource(source);
        Inventory inventory = Bukkit.createInventory(new PetGuiHolder("help:" + normalizedSource), 54, gui.title(GameText.guiTitleHelpOverview()));
        gui.fillFrame(inventory);
        inventory.setItem(10, gui.item(Material.BOOK, "&e" + GameText.petOverviewHelpTitle(), List.of(GameText.petOverviewHelpHint())));
        inventory.setItem(13, gui.item(Material.COOKED_BEEF, gui.msg("gui.help.care.title", "&eCare and food"), List.of(
            gui.msg("gui.help.care.line.one", "&7Each pet card lists its food and role."),
            gui.msg("gui.help.care.line.two", "&7Food restores satiety; rare resources help growth."),
            gui.msg("gui.help.care.line.three", "&7Use this as a bestiary, not as core progress.")
        )));
        inventory.setItem(16, gui.createPetArmor(PetArmorTier.COPPER));

        int[] petSlots = gui.petHelpSlots();
        List<PetType> types = gui.playablePetTypes();
        for (int index = 0; index < petSlots.length && index < types.size(); index++) {
            PetType type = types.get(index);
            inventory.setItem(petSlots[index], gui.item(gui.eggMaterial(type), "&e" + GameText.petTypeName(type), gui.helpCardLore(type)));
        }

        inventory.setItem(49, gui.back());
        gui.playMenuOpen(player, Sound.UI_BUTTON_CLICK, 0.6F, 1.1F);
        player.openInventory(inventory);
    }

    @Override
    public boolean handleClick(Player player, String menuId, int slot) {
        if (slot == 16) {
            gui.openPetArmorHelp(player, gui.sourceFromMenu(menuId));
            return true;
        }
        PetType clickedType = gui.petTypeByHelpSlot(slot);
        if (clickedType != null) {
            gui.openPetInfo(player, clickedType.name(), gui.sourceFromMenu(menuId));
        }
        return true;
    }
}
