package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.RuntimePet;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.potion.PotionEffectType;

final class PetOverviewPage implements PetGuiPage {
    private final PetGuiService gui;

    PetOverviewPage(PetGuiService gui) {
        this.gui = gui;
    }

    @Override
    public GuiPageId id() {
        return GuiPageId.PET_OVERVIEW;
    }

    @Override
    public void open(Player player) {
        Optional<RuntimePet> runtimePet = gui.runtimePet(player);
        Optional<OwnedPetData> offhandPet = gui.heldPetData(player);
        boolean summoned = runtimePet.isPresent();

        Inventory inventory = Bukkit.createInventory(new PetGuiHolder(id().menuId()), 54, gui.title(GameText.guiTitlePetOverview()));
        gui.fillFrame(inventory);
        inventory.setItem(13, gui.evolutionEntryButton(player, runtimePet, offhandPet));
        inventory.setItem(17, gui.item(Material.BOOK, "&e" + GameText.petOverviewHelpTitle(), List.of(GameText.petOverviewHelpHint())));
        inventory.setItem(9, gui.followPositionButton(runtimePet, offhandPet, 7));
        inventory.setItem(10, gui.followPositionButton(runtimePet, offhandPet, 0));
        inventory.setItem(11, gui.followPositionButton(runtimePet, offhandPet, 1));
        inventory.setItem(18, gui.followPositionButton(runtimePet, offhandPet, 6));
        inventory.setItem(19, gui.followControllerCenter(runtimePet, offhandPet));
        inventory.setItem(20, gui.followPositionButton(runtimePet, offhandPet, 2));
        inventory.setItem(24, gui.repairCoreButton(player, runtimePet, offhandPet));
        inventory.setItem(26, gui.aggressiveStyleButton(runtimePet));
        inventory.setItem(27, gui.followPositionButton(runtimePet, offhandPet, 5));
        inventory.setItem(28, gui.followPositionButton(runtimePet, offhandPet, 4));
        inventory.setItem(29, gui.followPositionButton(runtimePet, offhandPet, 3));
        inventory.setItem(31, gui.item(Material.CHEST, "&e" + GameText.petOverviewVault(), List.of(GameText.petOverviewVaultHint())));
        inventory.setItem(32, gui.passiveEffectButton(runtimePet, offhandPet, PotionEffectType.NIGHT_VISION, Material.ENDER_EYE));
        inventory.setItem(33, gui.passiveEffectButton(runtimePet, offhandPet, PotionEffectType.SLOW_FALLING, Material.FEATHER));
        inventory.setItem(34, gui.passiveEffectButton(runtimePet, offhandPet, PotionEffectType.INVISIBILITY, Material.GLASS));
        inventory.setItem(35, gui.autolootToggle(player));
        inventory.setItem(36, gui.followDistanceDownButton(runtimePet));
        inventory.setItem(37, gui.followDistanceCard(runtimePet, offhandPet));
        inventory.setItem(38, gui.followDistanceUpButton(runtimePet));
        inventory.setItem(49, gui.petCoreUsageInfo(summoned));
        inventory.setItem(51, gui.renamePetButton(runtimePet, offhandPet));
        inventory.setItem(52, gui.exitButton());
        inventory.setItem(53, gui.item(Material.ENDER_PEARL, gui.msg("gui.pet.master.title", "&dTo the Pet Source"), List.of(
            gui.msg("gui.pet.master.line.one", "&7Teleport to the Pet Source."),
            gui.msg("gui.pet.master.line.two", "&7Preparation: 5 seconds.")
        )));
        gui.playMenuOpen(player, Sound.UI_BUTTON_CLICK, 0.6F, 1.15F);
        player.openInventory(inventory);
    }
}
