package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.RuntimePet;
import dev.li2fox.vibepetcore.pet.PetType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

final class PetInfoPage implements PetGuiPage {
    private final PetGuiService gui;

    PetInfoPage(PetGuiService gui) {
        this.gui = gui;
    }

    @Override
    public GuiPageId id() {
        return GuiPageId.PET_INFO;
    }

    @Override
    public void open(Player player) {
        open(player, "wolf", "master");
    }

    void open(Player player, String rawType, String source) {
        PetType type = PetType.parse(rawType).orElse(PetType.WOLF);
        String normalizedSource = gui.normalizeSource(source);
        Optional<OwnedPetData> petData = gui.selectedPetForType(player, type);
        boolean activePetForType = activePetForType(player, type);
        int currentStage = petData.map(OwnedPetData::evolutionStage).orElse(1);

        Inventory inventory = Bukkit.createInventory(
            new PetGuiHolder("petinfo:" + normalizedSource + ":" + type.name().toLowerCase(Locale.ROOT)),
            54,
            gui.title(GameText.petTypeName(type))
        );
        gui.fillFrame(inventory);
        inventory.setItem(4, gui.item(gui.eggMaterial(type), "&e" + GameText.petTypeName(type), gui.petInfoLore(type, petData)));
        inventory.setItem(10, gui.item(Material.COMPASS, "&e" + GameText.petInfoRoleTitle(), gui.roleDetails(type)));
        inventory.setItem(16, gui.item(Material.BOOK, "&e" + GameText.petInfoGuideTitle(), gui.helpCardLore(type)));
        if (petData.isPresent()) {
            List<String> evolveLore = new ArrayList<>(gui.evolutionStageLore(player, type, currentStage, petData));
            evolveLore.add("");
            evolveLore.add(activePetForType ? GameText.petInfoEvolutionActionHint() : needActiveViewedPetLine());
            inventory.setItem(22, gui.item(activePetForType ? Material.SCULK_SHRIEKER : Material.BARRIER, (activePetForType ? "&d" : "&7") + GameText.petOverviewEvolution(), evolveLore));
        } else {
            inventory.setItem(22, gui.item(Material.BARRIER, "&c" + GameText.petOverviewEvolution(), List.of(
                GameText.petInfoNeedCoreHint(),
                GameText.guiUnavailable()
            )));
        }
        int[] stageSlots = {29, 30, 31, 32, 33};
        for (int stage = 1; stage <= 5; stage++) {
            String title = stage == currentStage
                ? "&a" + GameText.evolutionStageName(stage)
                : "&e" + GameText.evolutionStageName(stage);
            inventory.setItem(stageSlots[stage - 1], gui.item(gui.stageMaterial(stage), title, gui.evolutionStageLore(player, type, stage, petData)));
        }

        inventory.setItem(49, gui.back());
        gui.playMenuOpen(player, Sound.UI_BUTTON_CLICK, 0.6F, 1.1F);
        player.openInventory(inventory);
    }

    @Override
    public boolean handleClick(Player player, String menuId, int slot) {
        if (slot == 22 && gui.allowGuiAction(player)) {
            PetType viewedType = PetType.parse(gui.petInfoTypeFromMenu(menuId)).orElse(PetType.WOLF);
            if (!activePetForType(player, viewedType)) {
                player.sendMessage(needActiveViewedPetLine());
                open(player, gui.petInfoTypeFromMenu(menuId), gui.petInfoSourceFromMenu(menuId));
                return true;
            }
            gui.tryEvolveActivePet(player);
            gui.syncOffhandEgg(player);
            open(player, gui.petInfoTypeFromMenu(menuId), gui.petInfoSourceFromMenu(menuId));
        }
        return true;
    }

    private boolean activePetForType(Player player, PetType type) {
        return gui.runtimePet(player)
            .map(RuntimePet::type)
            .filter(type::equals)
            .isPresent();
    }

    private String needActiveViewedPetLine() {
        return GameText.text(
            "gui.pet.info.evolution.need-active-viewed",
            "&7Сначала призовите именно этого питомца.",
            "&7Summon this pet first."
        );
    }
}
