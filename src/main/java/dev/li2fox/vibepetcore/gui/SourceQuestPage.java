package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.QuestProgressData;
import dev.li2fox.vibepetcore.quest.QuestDefinition;
import dev.li2fox.vibepetcore.quest.QuestManager;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class SourceQuestPage implements PetGuiPage {
    private static final int[] QUEST_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final PetGuiService gui;

    SourceQuestPage(PetGuiService gui) {
        this.gui = gui;
    }

    @Override
    public GuiPageId id() {
        return GuiPageId.SOURCE_QUESTS;
    }

    @Override
    public void open(Player player) {
        open(player, "all", "master", 0);
    }

    void openFromMenu(Player player, String menuId) {
        open(player, questCategoryFromMenu(menuId), gui.sourceFromMenu(menuId), questPageFromMenu(menuId));
    }

    void open(Player player, String category, String source, int page) {
        String normalizedCategory = gui.normalizeCategory(category);
        String normalizedSource = gui.normalizeSource(source);
        List<QuestDefinition> quests = gui.visibleQuests(player, normalizedCategory);
        int maxPage = Math.max(0, (quests.size() - 1) / QUEST_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));
        Inventory inventory = Bukkit.createInventory(new PetGuiHolder("quests:" + normalizedSource + ":" + normalizedCategory + ":" + safePage), 54, gui.title(gui.questGuiSupport().menuTitle()));
        gui.fillFrame(inventory);

        inventory.setItem(1, categoryItem("all", normalizedCategory, Material.NETHER_STAR, GameText.questCategoryName("all")));
        inventory.setItem(2, categoryItem("daily", normalizedCategory, Material.CLOCK, GameText.questCategoryName("daily")));
        inventory.setItem(3, categoryItem("weekly", normalizedCategory, Material.BOOK, GameText.questCategoryName("weekly")));
        inventory.setItem(4, categoryItem("evolution", normalizedCategory, Material.AMETHYST_SHARD, GameText.questCategoryName("evolution")));
        inventory.setItem(5, categoryItem("gather", normalizedCategory, Material.HOPPER, GameText.questCategoryName("gather")));
        inventory.setItem(6, categoryItem("combat", normalizedCategory, Material.IRON_SWORD, GameText.questCategoryName("combat")));
        inventory.setItem(7, categoryItem("explore", normalizedCategory, Material.COMPASS, GameText.questCategoryName("explore")));

        int startIndex = safePage * QUEST_SLOTS.length;
        for (int index = 0; index < QUEST_SLOTS.length && startIndex + index < quests.size(); index++) {
            int questIndex = startIndex + index;
            QuestDefinition quest = quests.get(questIndex);
            QuestProgressData progress = gui.questManager().progress(player.getUniqueId(), quest.id());
            java.util.UUID selectedPetId = gui.selectedQuestPetId(player).orElse(null);
            int visibleProgress = gui.questManager().displayProgress(player, quest, selectedPetId);
            inventory.setItem(
                QUEST_SLOTS[index],
                gui.item(quest.icon(), gui.questGuiSupport().questName(player, quest, progress, selectedPetId), gui.questGuiSupport().questLore(player, quest, progress, visibleProgress))
            );
        }

        if (quests.isEmpty()) {
            inventory.setItem(22, gui.item(Material.BARRIER, GameText.questEmptyTitle(), List.of(GameText.questEmptyHint(gui.questGuiSupport().categoryLabel(normalizedCategory)))));
        }

        if (safePage > 0) {
            inventory.setItem(45, questPageItem(Material.SPECTRAL_ARROW, safePage, maxPage, -1));
        }
        if (safePage < maxPage) {
            inventory.setItem(53, questPageItem(Material.SPECTRAL_ARROW, safePage, maxPage, 1));
        }
        inventory.setItem(49, gui.back());
        gui.playMenuOpen(player, Sound.BLOCK_CHISELED_BOOKSHELF_INSERT_ENCHANTED, 0.7F, 1.15F);
        player.openInventory(inventory);
    }

    @Override
    public boolean handleClick(Player player, String menuId, int slot) {
        String category = questCategoryFromMenu(menuId);
        String source = gui.sourceFromMenu(menuId);
        int page = questPageFromMenu(menuId);
        if (slot == 49) {
            gui.openSourceRoot(player, source);
            return true;
        }
        if (slot == 45 && page > 0) {
            open(player, category, source, page - 1);
            return true;
        }
        if (slot == 53) {
            List<QuestDefinition> quests = gui.visibleQuests(player, category);
            int maxPage = Math.max(0, (quests.size() - 1) / QUEST_SLOTS.length);
            if (page < maxPage) {
                open(player, category, source, page + 1);
            }
            return true;
        }
        if (slot >= 1 && slot <= 7) {
            open(player, categoryBySlot(slot), source, 0);
            return true;
        }
        List<QuestDefinition> quests = gui.visibleQuests(player, category);
        int index = slotToQuestIndex(slot);
        if (index >= 0) {
            index += page * QUEST_SLOTS.length;
        }
        if (index < 0 || index >= quests.size()) {
            return true;
        }
        QuestDefinition quest = quests.get(index);
        QuestProgressData progress = gui.questManager().progress(player.getUniqueId(), quest.id());
        if (progress.completed()) {
            QuestManager.AcceptResult acceptedAgain = gui.questManager().accept(player.getUniqueId(), quest.id(), gui.selectedQuestPetId(player).orElse(null));
            player.sendMessage(gui.questGuiSupport().acceptedAgainMessage(acceptedAgain));
            if (acceptedAgain.accepted()) {
                gui.playQuestFeedback(player, true, false);
            }
            open(player, category, source, page);
            return true;
        }
        if (!progress.accepted()) {
            Optional<String> blockReason = gui.questGuiSupport().acceptanceBlockReason(player, quest);
            if (blockReason.isPresent()) {
                player.sendMessage(gui.questGuiSupport().blockedMessage(blockReason.get()));
                gui.playQuestFeedback(player, false, false);
                open(player, category, source, page);
                return true;
            }
            QuestManager.AcceptResult accepted = gui.questManager().accept(player.getUniqueId(), quest.id(), gui.selectedQuestPetId(player).orElse(null));
            player.sendMessage(gui.questGuiSupport().acceptedMessage(accepted));
            gui.playQuestFeedback(player, accepted.accepted(), false);
            open(player, category, source, page);
            return true;
        }
        boolean turnedIn = gui.questManager().turnIn(player, quest.id(), gui.selectedQuestPetId(player).orElse(null));
        player.sendMessage(gui.questGuiSupport().turnedInMessage(turnedIn));
        gui.playQuestFeedback(player, turnedIn, turnedIn);
        open(player, category, source, page);
        return true;
    }

    private ItemStack categoryItem(String category, String activeCategory, Material material, String title) {
        boolean active = gui.normalizeCategory(activeCategory).equals(gui.normalizeCategory(category));
        return gui.item(material, (active ? "&a" : "&e") + title, List.of(
            GameText.questCategoryTabLine(title),
            active ? GameText.questCategoryActiveHint() : GameText.questCategoryOpenHint()
        ));
    }

    private ItemStack questPageItem(Material material, int page, int maxPage, int direction) {
        boolean next = direction > 0;
        String title = next
            ? gui.msg("gui.quest.page.next", "&eNext page")
            : gui.msg("gui.quest.page.previous", "&ePrevious page");
        return gui.item(material, title, List.of(
            gui.msg(
                "gui.quest.page.current",
                "&7Page {page}/{pages}",
                "page", page + 1,
                "pages", maxPage + 1
            )
        ));
    }

    private String questCategoryFromMenu(String menuId) {
        String[] parts = menuId.split(":");
        if (parts.length >= 3) {
            return gui.normalizeCategory(parts[2]);
        }
        return parts.length >= 2 ? gui.normalizeCategory(parts[1]) : "daily";
    }

    private int questPageFromMenu(String menuId) {
        String[] parts = menuId.split(":");
        if (parts.length < 4 || !"quests".equalsIgnoreCase(parts[0])) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(parts[3]));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String categoryBySlot(int slot) {
        return switch (slot) {
            case 1 -> "all";
            case 2 -> "daily";
            case 3 -> "weekly";
            case 4 -> "evolution";
            case 5 -> "gather";
            case 6 -> "combat";
            case 7 -> "explore";
            default -> "daily";
        };
    }

    private int slotToQuestIndex(int slot) {
        for (int i = 0; i < QUEST_SLOTS.length; i++) {
            if (QUEST_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
