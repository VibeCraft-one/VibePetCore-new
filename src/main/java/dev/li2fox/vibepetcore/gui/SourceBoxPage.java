package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.PlayerData;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

final class SourceBoxPage implements PetGuiPage {
    private final PetGuiService gui;

    SourceBoxPage(PetGuiService gui) {
        this.gui = gui;
    }

    @Override
    public GuiPageId id() {
        return GuiPageId.SOURCE_BOX;
    }

    @Override
    public void open(Player player) {
        open(player, "master");
    }

    void open(Player player, String source) {
        PlayerData data = gui.playerData(player);
        long now = System.currentTimeMillis();
        long minutes = data.freeBoxNextAtMillis() <= now ? 0L : Math.max(1L, (data.freeBoxNextAtMillis() - now) / 60_000L);
        int pityThreshold = gui.balanceConfig().boxPityThreshold("basic");
        int pityProgress = Math.min(pityThreshold, Math.max(0, data.boxPity().getOrDefault("basic", 0)));

        Inventory inventory = Bukkit.createInventory(new PetGuiHolder("box:" + gui.normalizeSource(source)), 54, gui.title(GameText.guiTitleBox()));
        gui.fillFrame(inventory);
        inventory.setItem(20, gui.item(Material.CLOCK, GameText.boxStatusTitle(), List.of(
            minutes <= 0L ? GameText.boxStatusFreeReady() : GameText.boxStatusFreeCooldown(minutes)
        )));
        long boxCost = Math.max(1L, gui.balanceConfig().boxCost("basic"));
        long pointAttempts = Math.max(0L, gui.economyPoints(player) / boxCost);
        inventory.setItem(13, gui.item(Material.NETHER_STAR, GameText.boxPointsTitle(), List.of(
            GameText.boxPointsBalance(gui.economyPoints(player)),
            GameText.boxPointsCost(boxCost),
            GameText.boxPointsAvailable(pointAttempts)
        )));
        inventory.setItem(22, gui.item(Material.ENDER_CHEST, GameText.boxOpenBasicTitle(), List.of(
            GameText.boxOpenBasicHint(),
            GameText.boxOpenBasicAttemptsHint()
        )));
        inventory.setItem(24, gui.item(Material.AMETHYST_SHARD, GameText.boxPityTitle(), List.of(
            GameText.boxPityProgress(pityProgress, pityThreshold),
            GameText.boxPityHint()
        )));
        inventory.setItem(31, gui.item(Material.BOOK, GameText.boxInfoTitle(), List.of(
            GameText.boxInfoLineOne(),
            GameText.boxInfoLineTwo(),
            GameText.boxInfoLineThree()
        )));
        inventory.setItem(49, gui.back());
        gui.playMenuOpen(player, Sound.BLOCK_ENDER_CHEST_OPEN, 0.7F, 1.1F);
        player.openInventory(inventory);
    }

    @Override
    public boolean handleClick(Player player, String menuId, int slot) {
        if (slot == 22 && gui.allowGuiAction(player)) {
            gui.openBoxForPlayer(player, gui.sourceFromMenu(menuId));
        }
        return true;
    }
}
