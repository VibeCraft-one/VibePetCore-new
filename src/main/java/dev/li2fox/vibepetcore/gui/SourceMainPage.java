package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.RuntimePet;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

final class SourceMainPage implements PetGuiPage {
    private final PetGuiService gui;

    SourceMainPage(PetGuiService gui) {
        this.gui = gui;
    }

    @Override
    public GuiPageId id() {
        return GuiPageId.SOURCE_MAIN;
    }

    @Override
    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new PetGuiHolder(id().menuId()), 54, gui.title(GameText.guiTitleMain()));
        gui.fillFrame(inventory);

        Optional<RuntimePet> runtimePet = gui.runtimePet(player);
        Optional<OwnedPetData> heldPet = gui.heldPetData(player);

        inventory.setItem(10, gui.item(Material.COMPASS, GameText.mainMenuQuestsTitle(), List.of(GameText.mainMenuQuestsHint())));
        inventory.setItem(13, gui.item(Material.CHISELED_BOOKSHELF, GameText.mainMenuGuideTitle(), List.of(GameText.mainMenuGuideHint())));
        inventory.setItem(16, gui.item(Material.CHEST, GameText.mainMenuBoxesTitle(), List.of(GameText.mainMenuBoxesHint())));

        inventory.setItem(22, gui.petStatusCard(player, heldPet, runtimePet));

        inventory.setItem(28, gui.item(Material.CALIBRATED_SCULK_SENSOR, gui.msg("gui.legendary.title", "&dLegendary traits"), List.of(
            gui.msg("gui.legendary.menu.line.one", "&7Special traits for legendary pets only."),
            gui.msg("gui.legendary.menu.line.two", "&7Combat style, triggers, and ultimate effects.")
        )));
        inventory.setItem(31, gui.item(Material.AMETHYST_CLUSTER, GameText.mainMenuGrowthTitle(), List.of(GameText.mainMenuGrowthHint())));
        inventory.setItem(34, gui.item(Material.ANVIL, GameText.mainMenuForgeTitle(), List.of(GameText.mainMenuForgeHint())));

        player.openInventory(inventory);
    }

    @Override
    public boolean handleClick(Player player, int slot) {
        switch (slot) {
            case 10 -> gui.openQuests(player, "all", "master");
            case 13 -> gui.openHelpOverview(player, "master");
            case 16 -> gui.openBox(player, "master");
            case 28 -> gui.openLegendaryFeatures(player, "master");
            case 31 -> gui.openCurrentPetGrowth(player, "master");
            case 34 -> gui.openRarityForge(player, "master");
            default -> {
            }
        }
        return true;
    }
}
