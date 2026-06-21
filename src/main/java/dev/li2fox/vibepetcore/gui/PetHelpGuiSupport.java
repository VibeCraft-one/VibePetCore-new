package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.pet.PetType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PetHelpGuiSupport {
    private final BalanceConfig balanceConfig;

    PetHelpGuiSupport(BalanceConfig balanceConfig) {
        this.balanceConfig = balanceConfig;
    }

    List<String> helpCardLore(PetType type) {
        List<String> lore = new ArrayList<>(PetGuiText.petTypeLore(type));
        lore.add(GameText.helpFoodLine(foodSummary(type)));
        lore.add(GameText.helpUseCaseLine(PetGuiText.shortUseCase(type)));
        lore.add(GameText.helpBuffPlanHeader());
        lore.addAll(PetGuiText.buffPlanLore(type));
        lore.add(GameText.text("gui.help.open-pet-info", "&eКлик: открыть карточку питомца", "&eClick: open the pet card"));
        return lore;
    }

    private String foodSummary(PetType type) {
        if (balanceConfig.petFoodMaterials(type).isEmpty()) {
            return GameText.text("gui.help.food-config-hint", "смотри pets/{type}.yml", "see pets/{type}.yml")
                .replace("{type}", type.name().toLowerCase(Locale.ROOT));
        }
        return GameText.materialList(balanceConfig.petFoodMaterials(type), 4);
    }
}
