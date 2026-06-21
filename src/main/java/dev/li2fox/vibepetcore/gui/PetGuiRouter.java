package dev.li2fox.vibepetcore.gui;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.entity.Player;

final class PetGuiRouter {
    private final Map<GuiPageId, PetGuiPage> pages = new EnumMap<>(GuiPageId.class);

    void register(PetGuiPage page) {
        pages.put(page.id(), page);
    }

    boolean open(GuiPageId pageId, Player player) {
        PetGuiPage page = pages.get(pageId);
        if (page == null) {
            return false;
        }
        page.open(player);
        return true;
    }

    boolean handleClick(String menuId, Player player, int slot) {
        return GuiPageId.fromMenuId(menuId)
            .map(pages::get)
            .map(page -> page.handleClick(player, menuId, slot))
            .orElse(false);
    }
}
