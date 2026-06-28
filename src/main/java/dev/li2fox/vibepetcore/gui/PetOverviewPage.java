package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.RuntimePet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
        Optional<OwnedPetData> offhandPet = gui.petMenuHeldPetData(player);
        boolean summoned = runtimePet.isPresent();

        Inventory inventory = Bukkit.createInventory(new PetGuiHolder(id().menuId()), 54, gui.title(GameText.guiTitlePetOverview()));
        gui.fillFrame(inventory);
        inventory.setItem(13, evolutionEntryButton(runtimePet, offhandPet));
        inventory.setItem(17, gui.item(Material.BOOK, "&e" + GameText.petOverviewHelpTitle(), List.of(GameText.petOverviewHelpHint())));
        inventory.setItem(9, followPositionButton(runtimePet, offhandPet, 7));
        inventory.setItem(10, followPositionButton(runtimePet, offhandPet, 0));
        inventory.setItem(11, followPositionButton(runtimePet, offhandPet, 1));
        inventory.setItem(18, followPositionButton(runtimePet, offhandPet, 6));
        inventory.setItem(19, followControllerCenter(runtimePet, offhandPet));
        inventory.setItem(20, followPositionButton(runtimePet, offhandPet, 2));
        inventory.setItem(22, trainButton(runtimePet, offhandPet));
        inventory.setItem(24, repairCoreButton(player, runtimePet, offhandPet));
        inventory.setItem(26, aggressiveStyleButton(runtimePet, offhandPet));
        inventory.setItem(27, followPositionButton(runtimePet, offhandPet, 5));
        inventory.setItem(28, followPositionButton(runtimePet, offhandPet, 4));
        inventory.setItem(29, followPositionButton(runtimePet, offhandPet, 3));
        inventory.setItem(31, vaultButton(runtimePet, offhandPet));
        inventory.setItem(32, passiveEffectButton(runtimePet, offhandPet, PotionEffectType.NIGHT_VISION, Material.ENDER_EYE));
        inventory.setItem(33, passiveEffectButton(runtimePet, offhandPet, PotionEffectType.SLOW_FALLING, Material.FEATHER));
        inventory.setItem(34, passiveEffectButton(runtimePet, offhandPet, PotionEffectType.INVISIBILITY, Material.GLASS));
        inventory.setItem(35, autolootToggle(player, offhandPet));
        inventory.setItem(36, followDistanceDownButton(runtimePet, offhandPet));
        inventory.setItem(37, followDistanceCard(runtimePet, offhandPet));
        inventory.setItem(38, followDistanceUpButton(runtimePet, offhandPet));
        inventory.setItem(49, petCoreUsageInfo(summoned));
        inventory.setItem(51, renamePetButton(runtimePet, offhandPet));
        inventory.setItem(52, gui.exitButton());
        inventory.setItem(53, gui.item(Material.ENDER_PEARL, gui.msg("gui.pet.master.title", "&dTo the Pet Source"), List.of(
            gui.msg("gui.pet.master.line.one", "&7Teleport to the Pet Source."),
            gui.msg("gui.pet.master.line.two", "&7Preparation: 5 seconds.")
        )));
        gui.playMenuOpen(player, Sound.UI_BUTTON_CLICK, 0.6F, 1.15F);
        player.openInventory(inventory);
    }

    @Override
    public boolean handleClick(Player player, int slot) {
        if (!gui.allowPetMenuClick(player)) {
            return true;
        }
        if (slot == 22) {
            gui.trainPet(player);
            return true;
        }
        if (slot == 24) {
            if (gui.repairCore(player)) {
                open(player);
            }
            return true;
        }
        if (slot == 13) {
            gui.openCurrentPetGrowth(player, "pet");
            return true;
        }
        if (slot == 9) {
            gui.selectFollowPosition(player, 7);
            return true;
        }
        if (slot == 10) {
            gui.selectFollowPosition(player, 0);
            return true;
        }
        if (slot == 11) {
            gui.selectFollowPosition(player, 1);
            return true;
        }
        if (slot == 18) {
            gui.selectFollowPosition(player, 6);
            return true;
        }
        if (slot == 20) {
            gui.selectFollowPosition(player, 2);
            return true;
        }
        if (slot == 27) {
            gui.selectFollowPosition(player, 5);
            return true;
        }
        if (slot == 28) {
            gui.selectFollowPosition(player, 4);
            return true;
        }
        if (slot == 29) {
            gui.selectFollowPosition(player, 3);
            return true;
        }
        if (slot == 31) {
            gui.openActivePetVault(player);
            return true;
        }
        if (slot == 32) {
            gui.togglePassiveEffect(player, PotionEffectType.NIGHT_VISION);
            return true;
        }
        if (slot == 33) {
            gui.togglePassiveEffect(player, PotionEffectType.SLOW_FALLING);
            return true;
        }
        if (slot == 34) {
            gui.togglePassiveEffect(player, PotionEffectType.INVISIBILITY);
            return true;
        }
        if (slot == 26) {
            gui.toggleDefense(player);
            return true;
        }
        if (slot == 35) {
            gui.toggleAutoloot(player);
            return true;
        }
        if (slot == 36) {
            gui.decreaseFollowDistance(player);
            return true;
        }
        if (slot == 38) {
            gui.increaseFollowDistance(player);
            return true;
        }
        if (slot == 17) {
            gui.openHelpOverview(player, "pet");
            return true;
        }
        if (slot == 52) {
            player.closeInventory();
            return true;
        }
        if (slot == 53) {
            gui.startSourceTeleport(player);
            return true;
        }
        return true;
    }

    private ItemStack followControllerCenter(Optional<RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        if (runtimePet.isEmpty()) {
            return gui.item(Material.COMPASS, "&b" + GameText.petOverviewControllerTitle(), List.of(
                activePetRequiredLine(offhandPet),
                GameText.guiUnavailable()
            ));
        }
        OwnedPetData pet = runtimePet.get().data();
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petOverviewControllerCurrent(positionLabel(pet.followPosition()), pet.followDistanceTitle()));
        lore.add(GameText.petOverviewFollowDistanceHint());
        return gui.item(Material.COMPASS, "&b" + GameText.petOverviewControllerTitle(), lore);
    }

    private ItemStack followPositionButton(Optional<RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet, int position) {
        if (runtimePet.isEmpty()) {
            return gui.item(Material.RED_STAINED_GLASS_PANE, gui.msg("gui.pet.position.title", "&7Position"), activePetRequiredLore(offhandPet));
        }
        OwnedPetData pet = runtimePet.get().data();
        String label = positionLabel(position);
        boolean active = pet.followPosition() == position;
        String title = active ? "&a" + label : "&c" + label;
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petOverviewControllerCurrent(label, pet.followDistanceTitle()));
        lore.add(GameText.petOverviewFollowDistanceHint());
        return gui.item(active ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE, title, lore);
    }

    private ItemStack followDistanceCard(Optional<RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        if (runtimePet.isEmpty()) {
            return gui.item(Material.LEAD, gui.msg("gui.pet.distance.title", "&dDistance"), activePetRequiredLore(offhandPet));
        }
        OwnedPetData pet = runtimePet.get().data();
        return gui.item(Material.LEAD, gui.msg("gui.pet.distance.current.title", "&dDistance &f") + pet.followDistanceTitle(), List.of(
            GameText.petOverviewFollowDistanceHint(),
            gui.msg("gui.pet.distance.current.line", "&7Current level: &f") + pet.followDistanceTitle()
        ));
    }

    private ItemStack followDistanceDownButton(Optional<RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        boolean enabled = runtimePet.isPresent();
        return gui.item(
            Material.REDSTONE_TORCH,
            GameText.petOverviewFollowBackTitle(),
            enabled ? List.of(gui.msg("gui.pet.follow.closer", "&7Make the pet follow closer."), GameText.petOverviewFollowDistanceHint()) : activePetRequiredLore(offhandPet)
        );
    }

    private ItemStack followDistanceUpButton(Optional<RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        boolean enabled = runtimePet.isPresent();
        return gui.item(
            Material.SOUL_TORCH,
            GameText.petOverviewFollowForwardTitle(),
            enabled ? List.of(gui.msg("gui.pet.follow.further", "&7Make the pet follow farther."), GameText.petOverviewFollowDistanceHint()) : activePetRequiredLore(offhandPet)
        );
    }

    private ItemStack evolutionEntryButton(Optional<RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        Optional<OwnedPetData> petData = runtimePet.map(RuntimePet::data).or(() -> offhandPet);
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petOverviewInfoLineOne());
        lore.add(GameText.petOverviewInfoLineTwo());
        petData.ifPresentOrElse(pet -> {
            lore.add("");
            lore.add(GameText.petInfoEvolutionLine(GameText.evolutionStageName(pet.evolutionStage()), pet.evolutionStage()));
            if (pet.evolutionStage() >= 5) {
                lore.add(GameText.petEvolutionPreviewMaxStage(GameText.evolutionStageName(5)));
            } else {
                lore.add(shortStageRequirement(pet.evolutionStage() + 1));
            }
            lore.add("");
            lore.add(gui.msg("gui.pet.overview.info.click", "&eClick: open evolution, quests, and materials."));
        }, () -> lore.add(GameText.petOverviewNeedCoreHint()));
        return gui.item(Material.CALIBRATED_SCULK_SENSOR, "&d" + GameText.petOverviewInfoTitle(), lore);
    }

    private ItemStack trainButton(Optional<RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        if (runtimePet.isEmpty()) {
            return gui.item(Material.TARGET, "&e" + GameText.petOverviewTrainTitle(), List.of(
                activePetRequiredLine(offhandPet),
                GameText.guiUnavailable()
            ));
        }
        long cooldownSeconds = gui.trainingCooldownSeconds(runtimePet.get().data().petId());
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petOverviewTrainHint());
        lore.add(cooldownSeconds > 0L
            ? GameText.petOverviewTrainCooldown(cooldownSeconds)
            : GameText.petOverviewTrainReady());
        return gui.item(
            cooldownSeconds > 0L ? Material.CROSSBOW : Material.TARGET,
            "&e" + GameText.petOverviewTrainTitle(),
            lore
        );
    }

    private ItemStack repairCoreButton(Player player, Optional<RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        Optional<OwnedPetData> petData = runtimePet.map(RuntimePet::data).or(() -> offhandPet);
        if (petData.isEmpty()) {
            return gui.item(Material.BARRIER, "&c" + GameText.petOverviewRepairCore(), List.of(
                GameText.petOverviewNeedCoreHint(),
                GameText.coreRepairMissing()
            ));
        }

        OwnedPetData pet = petData.get();
        int maxDurability = gui.balanceConfig().eggMaxDurability();
        int durability = pet.durability();
        int totems = countMaterial(player, Material.TOTEM_OF_UNDYING);
        boolean damaged = durability < maxDurability;
        List<String> lore = new ArrayList<>();
        lore.add(gui.msg("gui.pet.durability", "&7Durability: &f{current}&8/&f{max}")
            .replace("{current}", String.valueOf(durability))
            .replace("{max}", String.valueOf(maxDurability)));
        if (!damaged) {
            lore.add("&a" + GameText.coreRepairAlreadyFull());
            lore.add(gui.msg("gui.pet.repair.no-cost", "&7Totems are not consumed."));
        } else {
            lore.add(gui.msg("gui.pet.repair.totems", "&7Totems in inventory: &f{count}", "count", totems));
            lore.add(totems > 0
                ? gui.msg("gui.pet.repair.consume", "&7Click consumes 1 totem and restores 1 durability.")
                : "&c" + GameText.coreRepairNoTotems());
        }
        String title = damaged ? "&e" + GameText.petOverviewRepairCore() : "&a" + GameText.coreRepairAlreadyFull();
        return gui.item(damaged ? Material.ANVIL : Material.ENCHANTED_BOOK, title, lore);
    }

    private ItemStack passiveEffectButton(Optional<RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet, PotionEffectType effectType, Material material) {
        String effectKey = effectType.getKey().getKey().toLowerCase(java.util.Locale.ROOT);
        String effectName = GameText.effectName(effectKey);
        if (runtimePet.isEmpty()) {
            return gui.item(Material.GRAY_DYE, gui.msg("gui.pet.buff-toggle.title", "&7Auto-buff: {effect}", "effect", effectName), List.of(
                activePetRequiredLine(offhandPet),
                GameText.guiUnavailable()
            ));
        }
        boolean enabled = runtimePet.get().data().passiveEffectEnabled(effectKey);
        List<String> lore = new ArrayList<>();
        lore.add(enabled
            ? gui.msg("gui.pet.buff-toggle.enabled", "&aEnabled")
            : gui.msg("gui.pet.buff-toggle.disabled", "&cDisabled"));
        lore.add(gui.msg("gui.pet.buff-toggle.hint", "&7Click to toggle this automatic pet buff."));
        lore.add(gui.msg("gui.pet.buff-toggle.scope", "&8Affects only passive pet casts."));
        return gui.item(
            enabled ? material : Material.GRAY_DYE,
            (enabled ? "&a" : "&c") + gui.msg("gui.pet.buff-toggle.title", "Auto-buff: {effect}", "effect", effectName),
            lore
        );
    }

    private ItemStack autolootToggle(Player player, Optional<OwnedPetData> offhandPet) {
        Optional<RuntimePet> runtimePet = gui.runtimePet(player);
        if (runtimePet.isEmpty()) {
            return gui.item(Material.HOPPER, "&e" + GameText.petOverviewAutoLoot(false), List.of(
                activePetRequiredLine(offhandPet),
                GameText.guiUnavailable()
            ));
        }
        if (!gui.balanceConfig().petAutoLootEnabled(runtimePet.get().type())) {
            return gui.item(Material.HOPPER, "&e" + GameText.petOverviewAutoLoot(false), List.of(
                GameText.petOverviewAutoLootHint(),
                GameText.guiUnavailable()
            ));
        }
        boolean enabled = runtimePet.get().data().autoLootEnabled();
        return gui.item(Material.HOPPER, "&e" + GameText.petOverviewAutoLoot(enabled), List.of(GameText.petOverviewAutoLootHint()));
    }

    private ItemStack petCoreUsageInfo(boolean summoned) {
        return gui.item(Material.BELL, gui.msg("gui.pet.summon.title", "&ePet core"), List.of(
            gui.msg("gui.pet.summon.line.one", "&7Summon: hold the filled core in offhand and right-click."),
            gui.msg("gui.pet.summon.line.two", "&7Return: hold the empty matching core and right-click."),
            gui.msg("gui.pet.summon.line.three", "&7Active runtime requires the matching offhand core."),
            summoned ? gui.msg("gui.pet.summon.active", "&aPet is active now.") : gui.msg("gui.pet.summon.prompt", "&8Information card")
        ));
    }

    private ItemStack renamePetButton(Optional<RuntimePet> runtimePet, Optional<OwnedPetData> heldPet) {
        Optional<OwnedPetData> pet = runtimePet.map(RuntimePet::data).or(() -> heldPet);
        if (pet.isEmpty()) {
            return gui.item(Material.NAME_TAG, GameText.text("gui.pet.rename.button", "&eИмя питомца", "&ePet name"), List.of(
                GameText.text("gui.pet.rename.need-core", "&7Сначала выберите активное ядро.", "&7Select an active core first.")
            ));
        }
        return gui.item(Material.NAME_TAG, GameText.text("gui.pet.rename.button", "&eИмя питомца", "&ePet name"), List.of(
            GameText.text("gui.pet.rename.current", "&7Сейчас: &f{name}", "&7Current: &f{name}").replace("{name}", pet.get().petName()),
            GameText.text("gui.pet.rename.hint", "&7Напишите команду: &f/pet name имя", "&7Use: &f/pet name name"),
            GameText.text("gui.pet.rename.cooldown-hint", "&8Переименование: раз в час.", "&8Rename: once per hour.")
        ));
    }

    private ItemStack vaultButton(Optional<RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        if (runtimePet.isEmpty()) {
            return gui.item(Material.CHEST, "&7" + GameText.petOverviewVault(), activePetRequiredLore(offhandPet));
        }
        return gui.item(Material.CHEST, "&e" + GameText.petOverviewVault(), List.of(GameText.petOverviewVaultHint()));
    }

    private ItemStack aggressiveStyleButton(Optional<RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        if (runtimePet.isEmpty()) {
            return gui.item(Material.SHIELD, GameText.petOverviewAggressiveTitle(), List.of(
                activePetRequiredLine(offhandPet),
                GameText.guiUnavailable()
            ));
        }
        boolean enabled = runtimePet.get().data().defenseEnabled();
        return gui.item(enabled ? Material.IRON_SWORD : Material.SHIELD, GameText.petOverviewAggressiveTitle(), List.of(
            GameText.petOverviewAggressiveHint(enabled)
        ));
    }

    private String shortStageRequirement(int nextStage) {
        int level = gui.balanceConfig().evolutionRequiredLevel(nextStage);
        int quests = gui.balanceConfig().evolutionRequiredQuests(nextStage);
        if (quests > 0) {
            return gui.msg(
                "gui.pet.requirements.summary-quests",
                "&7Required: &fLv. {level} &8| &7Stage quests: &f{quests}",
                "level", level,
                "quests", quests
            );
        }
        return gui.msg(
            "gui.pet.requirements.summary",
            "&7Required: &fLv. {level}",
            "level", level
        );
    }

    private String positionLabel(int position) {
        return switch (Math.floorMod(position, 8)) {
            case 0 -> GameText.text("gui.pet.position.front", "спереди", "front");
            case 1 -> GameText.text("gui.pet.position.front-right", "спереди справа", "front-right");
            case 2 -> GameText.text("gui.pet.position.right", "справа", "right");
            case 3 -> GameText.text("gui.pet.position.back-right", "сзади справа", "back-right");
            case 4 -> GameText.text("gui.pet.position.back", "сзади", "back");
            case 5 -> GameText.text("gui.pet.position.back-left", "сзади слева", "back-left");
            case 6 -> GameText.text("gui.pet.position.left", "слева", "left");
            case 7 -> GameText.text("gui.pet.position.front-left", "спереди слева", "front-left");
            default -> GameText.text("gui.pet.position.front", "спереди", "front");
        };
    }

    private List<String> activePetRequiredLore(Optional<OwnedPetData> offhandPet) {
        return List.of(activePetRequiredLine(offhandPet), GameText.guiUnavailable());
    }

    private String activePetRequiredLine(Optional<OwnedPetData> offhandPet) {
        return offhandPet.isPresent()
            ? GameText.text("gui.pet.need-active-pet", "&7Сначала призовите питомца.", "&7Summon the pet first.")
            : GameText.petOverviewNeedCoreHint();
    }

    private int countMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }
}
