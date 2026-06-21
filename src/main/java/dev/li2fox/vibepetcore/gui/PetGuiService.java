package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.box.BoxManager;
import dev.li2fox.vibepetcore.box.LootBoxManager;
import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.core.PetDebugLogger;
import dev.li2fox.vibepetcore.egg.PetEggService;
import dev.li2fox.vibepetcore.master.PetMasterManager;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.player.PlayerData;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.player.QuestProgressData;
import dev.li2fox.vibepetcore.pet.PetEngineManager;
import dev.li2fox.vibepetcore.pet.PetRarity;
import dev.li2fox.vibepetcore.pet.PetState;
import dev.li2fox.vibepetcore.pet.PetType;
import dev.li2fox.vibepetcore.pet.armor.PetArmorService;
import dev.li2fox.vibepetcore.quest.QuestDefinition;
import dev.li2fox.vibepetcore.quest.QuestManager;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

public final class PetGuiService implements Listener {
    private static final int[] QUEST_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private final PlayerDataManager playerDataManager;
    private final PetEngineManager petEngineManager;
    private final QuestManager questManager;
    private final BoxManager boxManager;
    private final LootBoxManager lootBoxManager;
    private final PetMasterManager petMasterManager;
    private final PetEggService petEggService;
    private final PetArmorService petArmorService;
    private final BalanceConfig balanceConfig;
    private final PetDebugLogger debugLogger;
    private final PetQuestGuiSupport questGuiSupport;
    private final PetHelpGuiSupport helpGuiSupport;
    private final PetInfoGuiSupport infoGuiSupport;
    private final PetEvolutionPreviewGuiSupport evolutionPreviewGuiSupport;
    private final Map<UUID, Long> petMenuClickCooldowns = new java.util.HashMap<>();
    private final Map<UUID, Long> guiActionCooldowns = new java.util.HashMap<>();

    public PetGuiService(PlayerDataManager playerDataManager, PetEngineManager petEngineManager, QuestManager questManager, BoxManager boxManager, LootBoxManager lootBoxManager, PetMasterManager petMasterManager, PetEggService petEggService, PetArmorService petArmorService, BalanceConfig balanceConfig, PetDebugLogger debugLogger) {
        this.playerDataManager = playerDataManager;
        this.petEngineManager = petEngineManager;
        this.questManager = questManager;
        this.boxManager = boxManager;
        this.lootBoxManager = lootBoxManager;
        this.petMasterManager = petMasterManager;
        this.petEggService = petEggService;
        this.petArmorService = petArmorService;
        this.balanceConfig = balanceConfig;
        this.debugLogger = debugLogger;
        this.questGuiSupport = new PetQuestGuiSupport(questManager, petEngineManager, petEggService);
        this.helpGuiSupport = new PetHelpGuiSupport(balanceConfig);
        this.infoGuiSupport = new PetInfoGuiSupport(balanceConfig, petEngineManager);
        this.evolutionPreviewGuiSupport = new PetEvolutionPreviewGuiSupport(petEngineManager);
    }

    private String msg(String key, String fallback, Object... replacements) {
        return this.balanceConfig.message(key, fallback, replacements);
    }

    private Component title(String legacyTitle) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacyTitle.replace('§', '&'));
    }

    public void open(Player player, String menuId) {
        try {
            String normalizedMenuId = menuId.toLowerCase(Locale.ROOT);
            if (normalizedMenuId.startsWith("quests")) {
                openQuests(player, questCategoryFromMenu(menuId), sourceFromMenu(menuId));
                return;
            }
            if (normalizedMenuId.startsWith("petinfo:")) {
                openPetInfo(player, petInfoTypeFromMenu(menuId), petInfoSourceFromMenu(menuId));
                return;
            }
            if (normalizedMenuId.startsWith("petarmor")) {
                openPetArmorHelp(player, sourceFromMenu(menuId));
                return;
            }
            switch (normalizedMenuId) {
                case "box" -> openBox(player, "master");
                case "forge" -> openRarityForge(player, "master");
                case "pet" -> openPetOverview(player);
                case "help" -> openHelpOverview(player, "master");
                default -> openMain(player);
            }
        } catch (RuntimeException exception) {
            debugLogger.errorRateLimited(
                "gui:open:" + player.getUniqueId() + ":" + menuId,
                "gui",
                "Failed to open menu=" + menuId + " for " + player.getName(),
                exception,
                15_000L
            );
            player.sendMessage(GameText.guiUnavailable());
        }
    }

    private void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(new PetGuiHolder("master"), 54, title(GameText.guiTitleMain()));
        fillFrame(inventory);

        Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet = petEngineManager.getPet(player);
        Optional<OwnedPetData> heldPet = heldPetData(player);

        inventory.setItem(10, item(Material.COMPASS, GameText.mainMenuQuestsTitle(), List.of(GameText.mainMenuQuestsHint())));
        inventory.setItem(13, item(Material.CHISELED_BOOKSHELF, GameText.mainMenuGuideTitle(), List.of(GameText.mainMenuGuideHint())));
        inventory.setItem(16, item(Material.CHEST, GameText.mainMenuBoxesTitle(), List.of(GameText.mainMenuBoxesHint())));

        inventory.setItem(22, petStatusCard(player, heldPet, runtimePet));

        inventory.setItem(28, item(Material.CALIBRATED_SCULK_SENSOR, msg("gui.legendary.title", "&dLegendary traits"), List.of(
            msg("gui.legendary.menu.line.one", "&7Special traits for legendary pets only."),
            msg("gui.legendary.menu.line.two", "&7Combat style, triggers, and ultimate effects.")
        )));
        inventory.setItem(31, item(Material.AMETHYST_CLUSTER, GameText.mainMenuGrowthTitle(), List.of(GameText.mainMenuGrowthHint())));
        inventory.setItem(34, item(Material.ANVIL, GameText.mainMenuForgeTitle(), List.of(GameText.mainMenuForgeHint())));

        player.openInventory(inventory);
    }

    private void openQuests(Player player, String category) {
        openQuests(player, category, "master", 0);
    }

    private void openQuests(Player player, String category, String source) {
        openQuests(player, category, source, 0);
    }

    private void openQuests(Player player, String category, String source, int page) {
        String normalizedCategory = normalizeCategory(category);
        String normalizedSource = normalizeSource(source);
        List<QuestDefinition> quests = visibleQuests(player, normalizedCategory);
        int maxPage = Math.max(0, (quests.size() - 1) / QUEST_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));
        Inventory inventory = Bukkit.createInventory(new PetGuiHolder("quests:" + normalizedSource + ":" + normalizedCategory + ":" + safePage), 54, title(questGuiSupport.menuTitle()));
        fillFrame(inventory);

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
            QuestProgressData progress = questManager.progress(player.getUniqueId(), quest.id());
            int visibleProgress = questManager.displayProgress(player, quest, selectedQuestPetId(player).orElse(null));
            inventory.setItem(
                QUEST_SLOTS[index],
                item(quest.icon(), questGuiSupport.questName(player, quest, progress), questGuiSupport.questLore(player, quest, progress, visibleProgress))
            );
        }

        if (quests.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER, GameText.questEmptyTitle(), List.of(GameText.questEmptyHint(questGuiSupport.categoryLabel(normalizedCategory)))));
        }

        if (safePage > 0) {
            inventory.setItem(45, questPageItem(Material.SPECTRAL_ARROW, safePage, maxPage, -1));
        }
        if (safePage < maxPage) {
            inventory.setItem(53, questPageItem(Material.SPECTRAL_ARROW, safePage, maxPage, 1));
        }
        inventory.setItem(49, back());
        playMenuOpen(player, Sound.BLOCK_CHISELED_BOOKSHELF_INSERT_ENCHANTED, 0.7F, 1.15F);
        player.openInventory(inventory);
    }

    private void openBox(Player player) {
        openBox(player, "master");
    }

    private void openBox(Player player, String source) {
        PlayerData data = playerDataManager.getOrLoad(player.getUniqueId());
        long now = System.currentTimeMillis();
        long minutes = data.freeBoxNextAtMillis() <= now ? 0L : Math.max(1L, (data.freeBoxNextAtMillis() - now) / 60_000L);
        int pityThreshold = balanceConfig.boxPityThreshold("basic");
        int pityProgress = Math.min(pityThreshold, Math.max(0, data.boxPity().getOrDefault("basic", 0)));

        Inventory inventory = Bukkit.createInventory(new PetGuiHolder("box:" + normalizeSource(source)), 54, title(GameText.guiTitleBox()));
        fillFrame(inventory);
        inventory.setItem(20, item(Material.CLOCK, GameText.boxStatusTitle(), List.of(
            minutes <= 0L ? GameText.boxStatusFreeReady() : GameText.boxStatusFreeCooldown(minutes)
        )));
        long boxCost = Math.max(1L, balanceConfig.boxCost("basic"));
        long pointAttempts = Math.max(0L, economyPoints(player) / boxCost);
        inventory.setItem(13, item(Material.NETHER_STAR, GameText.boxPointsTitle(), List.of(
            GameText.boxPointsBalance(economyPoints(player)),
            GameText.boxPointsCost(boxCost),
            GameText.boxPointsAvailable(pointAttempts)
        )));
        inventory.setItem(22, item(Material.ENDER_CHEST, GameText.boxOpenBasicTitle(), List.of(
            GameText.boxOpenBasicHint(),
            GameText.boxOpenBasicAttemptsHint()
        )));
        inventory.setItem(24, item(Material.AMETHYST_SHARD, GameText.boxPityTitle(), List.of(
            GameText.boxPityProgress(pityProgress, pityThreshold),
            GameText.boxPityHint()
        )));
        inventory.setItem(31, item(Material.BOOK, GameText.boxInfoTitle(), List.of(
            GameText.boxInfoLineOne(),
            GameText.boxInfoLineTwo(),
            GameText.boxInfoLineThree()
        )));
        inventory.setItem(49, back());
        playMenuOpen(player, Sound.BLOCK_ENDER_CHEST_OPEN, 0.7F, 1.1F);
        player.openInventory(inventory);
    }
    private void openRarityForge(Player player) {
        openRarityForge(player, "master");
    }

    private void openRarityForge(Player player, String source) {
        Inventory inventory = Bukkit.createInventory(new PetGuiHolder("forge:" + normalizeSource(source)), 54, title(GameText.guiTitleForge()));
        fillFrame(inventory);

        Optional<OwnedPetData> base = heldPetData(player);
        inventory.setItem(13, base
            .map(pet -> item(
                eggMaterial(PetType.parse(pet.petType()).orElse(PetType.WOLF)),
                "&b" + pet.petName(),
                rarityForgeCoreLore(player, pet)
            ))
            .orElseGet(() -> item(Material.BARRIER, "&c" + GameText.petOverviewNoCore(), List.of(
                GameText.forgeNeedActiveCore(),
                GameText.guiUnavailable()
            ))));
        inventory.setItem(22, base
            .map(pet -> item(Material.ANVIL, "&a" + GameText.forgeUpgradeTitle(), rarityForgeAttemptLore(player, pet)))
            .orElseGet(() -> item(Material.ANVIL, "&7" + GameText.forgeUpgradeTitle(), List.of(
                GameText.forgeNeedActiveCore(),
                GameText.guiUnavailable()
            ))));
        inventory.setItem(31, item(Material.BOOK, "&e" + GameText.forgeInfoTitle(), List.of(
            GameText.forgeInfoLineOne(),
            GameText.forgeInfoLineTwo(),
            GameText.forgeInfoLineThree(),
            GameText.forgeInfoLineFour()
        )));
        inventory.setItem(40, item(Material.CHEST, "&6" + GameText.forgeDonorChestTitle(), List.of(
            GameText.forgeDonorChestHint()
        )));
        inventory.setItem(49, back());
        playMenuOpen(player, Sound.BLOCK_ANVIL_USE, 0.7F, 1.1F);
        player.openInventory(inventory);
    }
    private void openPetOverview(Player player) {
        Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet = petEngineManager.getPet(player);
        Optional<HeldPetCore> heldCore = heldPetCore(player);
        Optional<OwnedPetData> offhandPet = heldCore.map(HeldPetCore::data);
        boolean summoned = runtimePet.isPresent();

        Inventory inventory = Bukkit.createInventory(new PetGuiHolder("pet"), 54, title(GameText.guiTitlePetOverview()));
        fillFrame(inventory);
        inventory.setItem(13, evolutionEntryButton(player, runtimePet, offhandPet));
        inventory.setItem(17, item(Material.BOOK, "&e" + GameText.petOverviewHelpTitle(), List.of(GameText.petOverviewHelpHint())));
        inventory.setItem(9, followPositionButton(runtimePet, offhandPet, 7));
        inventory.setItem(10, followPositionButton(runtimePet, offhandPet, 0));
        inventory.setItem(11, followPositionButton(runtimePet, offhandPet, 1));
        inventory.setItem(18, followPositionButton(runtimePet, offhandPet, 6));
        inventory.setItem(19, followControllerCenter(runtimePet, offhandPet));
        inventory.setItem(20, followPositionButton(runtimePet, offhandPet, 2));
        inventory.setItem(24, repairCoreButton(player, runtimePet, offhandPet));
        inventory.setItem(26, aggressiveStyleButton(runtimePet));
        inventory.setItem(27, followPositionButton(runtimePet, offhandPet, 5));
        inventory.setItem(28, followPositionButton(runtimePet, offhandPet, 4));
        inventory.setItem(29, followPositionButton(runtimePet, offhandPet, 3));
        inventory.setItem(31, item(Material.CHEST, "&e" + GameText.petOverviewVault(), List.of(GameText.petOverviewVaultHint())));
        inventory.setItem(32, passiveEffectButton(runtimePet, offhandPet, PotionEffectType.NIGHT_VISION, Material.ENDER_EYE));
        inventory.setItem(33, passiveEffectButton(runtimePet, offhandPet, PotionEffectType.SLOW_FALLING, Material.FEATHER));
        inventory.setItem(34, passiveEffectButton(runtimePet, offhandPet, PotionEffectType.INVISIBILITY, Material.GLASS));
        inventory.setItem(35, autolootToggle(player));
        inventory.setItem(36, followDistanceDownButton(runtimePet));
        inventory.setItem(37, followDistanceCard(runtimePet, offhandPet));
        inventory.setItem(38, followDistanceUpButton(runtimePet));
        inventory.setItem(49, petCoreUsageInfo(summoned));
        inventory.setItem(51, renamePetButton(runtimePet, offhandPet));
        inventory.setItem(52, exitButton());
        inventory.setItem(53, item(Material.ENDER_PEARL, msg("gui.pet.master.title", "&dTo the Pet Master"), List.of(
            msg("gui.pet.master.line.one", "&7Teleport to the Pet Source."),
            msg("gui.pet.master.line.two", "&7Preparation: 5 seconds.")
        )));
        playMenuOpen(player, Sound.UI_BUTTON_CLICK, 0.6F, 1.15F);
        player.openInventory(inventory);
    }
    private void openHelpOverview(Player player, String source) {
        String normalizedSource = normalizeSource(source);
        Inventory inventory = Bukkit.createInventory(new PetGuiHolder("help:" + normalizedSource), 54, title(GameText.guiTitleHelpOverview()));
        fillFrame(inventory);
        inventory.setItem(10, item(Material.BOOK, "&e" + GameText.petOverviewHelpTitle(), List.of(GameText.petOverviewHelpHint())));
        inventory.setItem(13, item(Material.COOKED_BEEF, msg("gui.help.care.title", "&eCare and food"), List.of(
            msg("gui.help.care.line.one", "&7Each pet card lists its food and role."),
            msg("gui.help.care.line.two", "&7Food restores satiety; rare resources help growth."),
            msg("gui.help.care.line.three", "&7Use this as a bestiary, not as core progress.")
        )));
        inventory.setItem(16, petArmorService.createArmor(dev.li2fox.vibepetcore.pet.armor.PetArmorTier.COPPER));

        int[] petSlots = petHelpSlots();
        List<PetType> types = playablePetTypes();
        for (int index = 0; index < petSlots.length && index < types.size(); index++) {
            PetType type = types.get(index);
            inventory.setItem(petSlots[index], item(eggMaterial(type), "&e" + GameText.petTypeName(type), helpGuiSupport.helpCardLore(type)));
        }

        inventory.setItem(49, back());
        playMenuOpen(player, Sound.UI_BUTTON_CLICK, 0.6F, 1.1F);
        player.openInventory(inventory);
    }

    private void openLegendaryFeatures(Player player, String source) {
        String normalizedSource = normalizeSource(source);
        Inventory inventory = Bukkit.createInventory(new PetGuiHolder("legendary:" + normalizedSource), 54, title(msg("gui.legendary.title", "&dLegendary traits")));
        fillFrame(inventory);
        inventory.setItem(4, item(Material.CALIBRATED_SCULK_SENSOR, msg("gui.legendary.header.title", "&dLegendary traits"), List.of(
            msg("gui.legendary.header.line.one", "&7These effects work only for legendary pets."),
            msg("gui.legendary.header.line.two", "&7Most traits have a 300 sec cooldown and trigger in combat."),
            msg("gui.legendary.header.line.three", "&8Allay keeps its own Vex form logic.")
        )));

        int[] petSlots = petHelpSlots();
        List<PetType> types = playablePetTypes();
        for (int index = 0; index < petSlots.length && index < types.size(); index++) {
            PetType type = types.get(index);
            inventory.setItem(petSlots[index], item(eggMaterial(type), "&d" + GameText.petTypeName(type), legendaryLore(type)));
        }

        inventory.setItem(49, back());
        playMenuOpen(player, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.6F, 1.15F);
        player.openInventory(inventory);
    }

    private void openPetInfo(Player player, String rawType) {
        openPetInfo(player, rawType, "master");
    }

    private void openPetInfo(Player player, String rawType, String source) {
        PetType type = PetType.parse(rawType).orElse(PetType.WOLF);
        String normalizedSource = normalizeSource(source);
        Optional<OwnedPetData> petData = selectedPetForType(player, type);
        int currentStage = petData.map(OwnedPetData::evolutionStage).orElse(1);

        Inventory inventory = Bukkit.createInventory(
            new PetGuiHolder("petinfo:" + normalizedSource + ":" + type.name().toLowerCase(Locale.ROOT)),
            54,
            title(GameText.petTypeName(type))
        );
        fillFrame(inventory);
        inventory.setItem(4, item(eggMaterial(type), "&e" + GameText.petTypeName(type), infoGuiSupport.petInfoLore(type, petData)));
        inventory.setItem(10, item(Material.COMPASS, "&e" + GameText.petInfoRoleTitle(), infoGuiSupport.roleDetails(type)));
        inventory.setItem(16, item(Material.BOOK, "&e" + GameText.petInfoGuideTitle(), helpGuiSupport.helpCardLore(type)));
        if (petData.isPresent()) {
            List<String> evolveLore = new ArrayList<>(evolutionStageLore(player, type, currentStage, petData));
            evolveLore.add("");
            evolveLore.add(GameText.petInfoEvolutionActionHint());
            inventory.setItem(22, item(Material.SCULK_SHRIEKER, "&d" + GameText.petOverviewEvolution(), evolveLore));
        } else {
            inventory.setItem(22, item(Material.BARRIER, "&c" + GameText.petOverviewEvolution(), List.of(
                GameText.petInfoNeedCoreHint(),
                GameText.guiUnavailable()
            )));
        }
        int[] stageSlots = {29, 30, 31, 32, 33};
        for (int stage = 1; stage <= 5; stage++) {
            String title = stage == currentStage
                ? "&a" + GameText.evolutionStageName(stage)
                : "&e" + GameText.evolutionStageName(stage);
            inventory.setItem(stageSlots[stage - 1], item(stageMaterial(stage), title, evolutionStageLore(player, type, stage, petData)));
        }

        inventory.setItem(49, back());
        playMenuOpen(player, Sound.UI_BUTTON_CLICK, 0.6F, 1.1F);
        player.openInventory(inventory);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof PetGuiHolder holder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        try {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }
            if (clicked.getType() == Material.ARROW) {
                handleBackClick(player, holder.menuId());
                return;
            }
            switch (holder.menuId()) {
                case "main", "master" -> handleMainClick(player, event.getSlot());
                case "pet" -> handlePetClick(player, event.getSlot());
                default -> {
                    if (holder.menuId().startsWith("box")) {
                        if (event.getSlot() == 22) {
                            if (allowGuiAction(player)) {
                                openBoxForPlayer(player, sourceFromMenu(holder.menuId()));
                            }
                        }
                    } else if (holder.menuId().startsWith("forge")) {
                        if (event.getSlot() == 22) {
                            if (allowGuiAction(player)) {
                                attemptRarityUpgrade(player, sourceFromMenu(holder.menuId()));
                            }
                        }
                    } else if (holder.menuId().startsWith("quests")) {
                        handleQuestClick(player, holder.menuId(), event.getSlot());
                    } else if (holder.menuId().startsWith("help")) {
                        handleHelpClick(player, holder.menuId(), event.getSlot());
                    } else if (holder.menuId().startsWith("petinfo")) {
                        handlePetInfoClick(player, holder.menuId(), event.getSlot());
                    }
                }
            }
        } catch (RuntimeException exception) {
            debugLogger.errorRateLimited(
                "gui:click:" + player.getUniqueId() + ":" + holder.menuId() + ":" + event.getSlot(),
                "gui",
                "Click failed menu=" + holder.menuId() + " slot=" + event.getSlot() + " player=" + player.getName(),
                exception,
                15_000L
            );
            player.closeInventory();
            player.sendMessage(GameText.guiUnavailable());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PetGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        petMenuClickCooldowns.remove(playerId);
        guiActionCooldowns.remove(playerId);
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == 10) {
            openQuests(player, "all", "master");
            return;
        }
        if (slot == 13) {
            openHelpOverview(player, "master");
            return;
        }
        if (slot == 16) {
            openBox(player, "master");
            return;
        }
        if (slot == 28) {
            openLegendaryFeatures(player, "master");
            return;
        }
        if (slot == 31) {
            openCurrentPetGrowth(player, "master");
            return;
        }
        if (slot == 34) {
            openRarityForge(player, "master");
        }
    }

    private void openCurrentPetGrowth(Player player) {
        openCurrentPetGrowth(player, "pet");
    }

    private void openCurrentPetGrowth(Player player, String source) {
        Optional<OwnedPetData> pet = petEngineManager.getPet(player).map(dev.li2fox.vibepetcore.pet.RuntimePet::data)
            .or(() -> heldPetData(player));
        if (pet.isPresent()) {
            openPetInfo(player, pet.get().petType(), source);
            return;
        }
        openGrowthMissing(player, source);
    }

    private void openGrowthMissing(Player player, String source) {
        String normalizedSource = normalizeSource(source);
        Inventory inventory = Bukkit.createInventory(new PetGuiHolder("growth:" + normalizedSource), 27, title(GameText.mainMenuGrowthTitle()));
        fillFrame(inventory);
        inventory.setItem(13, item(Material.AMETHYST_CLUSTER, GameText.mainMenuGrowthTitle(), List.of(
            msg("gui.growth.missing.line.one", "&7Select an active pet core first."),
            msg("gui.growth.missing.line.two", "&7Summon a pet or hold its core in your hand."),
            msg("gui.growth.missing.line.three", "&8This page shows level, bond, quests, and materials.")
        )));
        inventory.setItem(22, back());
        playMenuOpen(player, Sound.UI_BUTTON_CLICK, 0.6F, 1.05F);
        player.openInventory(inventory);
    }

    private void handlePetClick(Player player, int slot) {
        if (!allowPetMenuClick(player)) {
            return;
        }
        if (slot == 24) {
            if (repairCore(player)) {
                openPetOverview(player);
            }
            return;
        }
        if (slot == 13) {
            openCurrentPetGrowth(player, "pet");
            return;
        }
        if (slot == 9) {
            selectFollowPosition(player, 7);
            return;
        }
        if (slot == 10) {
            selectFollowPosition(player, 0);
            return;
        }
        if (slot == 11) {
            selectFollowPosition(player, 1);
            return;
        }
        if (slot == 18) {
            selectFollowPosition(player, 6);
            return;
        }
        if (slot == 20) {
            selectFollowPosition(player, 2);
            return;
        }
        if (slot == 27) {
            selectFollowPosition(player, 5);
            return;
        }
        if (slot == 28) {
            selectFollowPosition(player, 4);
            return;
        }
        if (slot == 29) {
            selectFollowPosition(player, 3);
            return;
        }
        if (slot == 31) {
            petEngineManager.openActivePetVault(player);
            return;
        }
        if (slot == 32) {
            togglePassiveEffect(player, PotionEffectType.NIGHT_VISION);
            return;
        }
        if (slot == 33) {
            togglePassiveEffect(player, PotionEffectType.SLOW_FALLING);
            return;
        }
        if (slot == 34) {
            togglePassiveEffect(player, PotionEffectType.INVISIBILITY);
            return;
        }
        if (slot == 26) {
            petEngineManager.toggleDefense(player);
            syncOffhandEgg(player);
            openPetOverview(player);
            return;
        }
        if (slot == 35) {
            petEngineManager.toggleAutoloot(player);
            syncOffhandEgg(player);
            openPetOverview(player);
            return;
        }
        if (slot == 36) {
            petEngineManager.decreaseFollowDistance(player);
            syncOffhandEgg(player);
            openPetOverview(player);
            return;
        }
        if (slot == 38) {
            petEngineManager.increaseFollowDistance(player);
            syncOffhandEgg(player);
            openPetOverview(player);
            return;
        }
        if (slot == 17) {
            openHelpOverview(player, "pet");
            return;
        }
        if (slot == 52) {
            player.closeInventory();
            return;
        }
        if (slot == 53) {
            petMasterManager.startSpawnMasterTeleport(player);
            return;
        }
        if (slot == 49) {
            return;
        }
    }

    private void handleBackClick(Player player, String menuId) {
        String source = sourceFromMenu(menuId);
        if ("pet".equals(source)) {
            openPetOverview(player);
            return;
        }
        openMain(player);
    }

    private void handleHelpClick(Player player, String menuId, int slot) {
        if (slot == 16) {
            openPetArmorHelp(player, sourceFromMenu(menuId));
            return;
        }
        PetType clickedType = petTypeByHelpSlot(slot);
        if (clickedType != null) {
            openPetInfo(player, clickedType.name(), sourceFromMenu(menuId));
        }
    }

    private void openPetArmorHelp(Player player, String source) {
        Inventory inventory = Bukkit.createInventory(
            new PetGuiHolder("petarmor:" + normalizeSource(source)),
            54,
            title(GameText.text("pet.armor.gui.title", "VibePet - Броня", "VibePet - Armor"))
        );
        fillFrame(inventory);
        inventory.setItem(4, item(Material.HEART_OF_THE_SEA, "&b" + GameText.text("pet.armor.gui.craft-title", "Крафт кольчуги питомца", "Pet chainmail crafting"), List.of(
            GameText.text("pet.armor.gui.craft.1", "&7Сердце моря ставится в центр.", "&7Heart of the sea goes in the center."),
            GameText.text("pet.armor.gui.craft.2", "&7Вокруг него 8 блоков материала.", "&7Place 8 material blocks around it."),
            GameText.text("pet.armor.gui.craft.3", "&7На выходе: кастомная броня наутилуса.", "&7Output: custom nautilus armor.")
        )));
        int[] slots = {20, 21, 22, 23, 24};
        dev.li2fox.vibepetcore.pet.armor.PetArmorTier[] tiers = dev.li2fox.vibepetcore.pet.armor.PetArmorTier.values();
        for (int index = 0; index < slots.length && index < tiers.length; index++) {
            inventory.setItem(slots[index], petArmorService.createArmor(tiers[index]));
        }
        inventory.setItem(31, item(Material.ENCHANTED_BOOK, "&d" + GameText.text("pet.armor.gui.enchant-title", "Зачарование", "Enchanting"), List.of(
            GameText.text("pet.armor.gui.enchant.1", "&7Кольчугу можно усилить на наковальне.", "&7The chainmail can be upgraded on an anvil."),
            GameText.text("pet.armor.gui.enchant.2", "&7Подходят защитные книги для нагрудника.", "&7Chestplate protection books are supported."),
            GameText.text("pet.armor.gui.enchant.3", "&7Лимит: &fдо 4 &7защитных чар.", "&7Limit: &fup to 4 &7protection enchants."),
            GameText.text("pet.armor.gui.enchant.4", "&7Эффект чар на питомце делится на 2.", "&7Enchant effects on the pet are halved.")
        )));
        inventory.setItem(40, item(Material.CHEST, "&e" + GameText.text("pet.armor.gui.activate-title", "Активация", "Activation"), List.of(
            GameText.text("pet.armor.gui.activate.1", "&7Положите кольчугу в рюкзак питомца.", "&7Place the chainmail in the pet vault."),
            GameText.text("pet.armor.gui.activate.2", "&7Одновременно работает только одна.", "&7Only one works at the same time."),
            GameText.text("pet.armor.gui.activate.3", "&7Если качество выше эволюции, предмет вернётся игроку.", "&7If the tier is too high, it is returned to the player.")
        )));
        inventory.setItem(49, back());
        playMenuOpen(player, Sound.UI_BUTTON_CLICK, 0.6F, 1.1F);
        player.openInventory(inventory);
    }

    private void handlePetInfoClick(Player player, String menuId, int slot) {
        if (slot == 22) {
            if (allowGuiAction(player)) {
                petEngineManager.tryEvolveActivePet(player);
                syncOffhandEgg(player);
                openPetInfo(player, petInfoTypeFromMenu(menuId), petInfoSourceFromMenu(menuId));
            }
        }
    }

    private String petInfoSourceFromMenu(String menuId) {
        String[] parts = menuId.split(":");
        return parts.length >= 3 ? normalizeSource(parts[1]) : "master";
    }

    private String petInfoTypeFromMenu(String menuId) {
        String[] parts = menuId.split(":");
        return parts.length >= 3 ? parts[2] : menuId.substring(menuId.indexOf(':') + 1);
    }

    private void openBoxForPlayer(Player player, String source) {
        player.closeInventory();
        var result = "master".equals(normalizeSource(source))
            ? lootBoxManager.openAtMaster(player)
            : lootBoxManager.openNearby(player);
        player.sendMessage(result.message());
        if (result.success()) {
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0D, 1.0D, 0.0D), 45, 0.6D, 0.8D, 0.6D, 0.04D);
            player.playSound(player.getLocation(), result.rarity() == PetRarity.LEGENDARY ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.25F);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8F, 0.8F);
        }
    }

    private void handleQuestClick(Player player, String menuId, int slot) {
        String category = questCategoryFromMenu(menuId);
        String source = sourceFromMenu(menuId);
        int page = questPageFromMenu(menuId);
        if (slot == 49) {
            openSourceRoot(player, source);
            return;
        }
        if (slot == 45 && page > 0) {
            openQuests(player, category, source, page - 1);
            return;
        }
        if (slot == 53) {
            List<QuestDefinition> quests = visibleQuests(player, category);
            int maxPage = Math.max(0, (quests.size() - 1) / QUEST_SLOTS.length);
            if (page < maxPage) {
                openQuests(player, category, source, page + 1);
            }
            return;
        }
        if (slot >= 1 && slot <= 7) {
            openQuests(player, categoryBySlot(slot), source);
            return;
        }
        List<QuestDefinition> quests = visibleQuests(player, category);
        int index = slotToQuestIndex(slot);
        if (index >= 0) {
            index += page * QUEST_SLOTS.length;
        }
        if (index < 0 || index >= quests.size()) {
            return;
        }
        QuestDefinition quest = quests.get(index);
        QuestProgressData progress = questManager.progress(player.getUniqueId(), quest.id());
        if (progress.completed()) {
            QuestManager.AcceptResult acceptedAgain = questManager.accept(player.getUniqueId(), quest.id(), selectedQuestPetId(player).orElse(null));
            player.sendMessage(questGuiSupport.acceptedAgainMessage(acceptedAgain));
            if (acceptedAgain.accepted()) {
                playQuestFeedback(player, true, false);
            }
            openQuests(player, category, source, page);
            return;
        }
        if (!progress.accepted()) {
            Optional<String> blockReason = questGuiSupport.acceptanceBlockReason(player, quest);
            if (blockReason.isPresent()) {
                player.sendMessage(questGuiSupport.blockedMessage(blockReason.get()));
                playQuestFeedback(player, false, false);
                openQuests(player, category, source, page);
                return;
            }
            QuestManager.AcceptResult accepted = questManager.accept(player.getUniqueId(), quest.id(), selectedQuestPetId(player).orElse(null));
            player.sendMessage(questGuiSupport.acceptedMessage(accepted));
            playQuestFeedback(player, accepted.accepted(), false);
            openQuests(player, category, source, page);
            return;
        }
        boolean turnedIn = questManager.turnIn(player, quest.id(), selectedQuestPetId(player).orElse(null));
        player.sendMessage(questGuiSupport.turnedInMessage(turnedIn));
        playQuestFeedback(player, turnedIn, turnedIn);
        openQuests(player, category, source, page);
    }
    private void playMenuOpen(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void playQuestFeedback(Player player, boolean success, boolean completed) {
        if (!success) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.75F);
            return;
        }
        if (completed) {
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.35D, 0.5D, 0.35D, 0.02D);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.75F, 1.1F);
            return;
        }
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0D, 1.0D, 0.0D), 8, 0.25D, 0.35D, 0.25D, 0.02D);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65F, 1.2F);
    }

    private List<QuestDefinition> visibleQuests(Player player, String category) {
        String normalizedCategory = normalizeCategory(category);
        if (!"evolution".equals(normalizedCategory)) {
            return questManager.visibleQuests(player.getUniqueId(), normalizedCategory);
        }
        int currentStage = petEngineManager.getPet(player).map(pet -> pet.data().evolutionStage())
            .or(() -> heldPetData(player).map(OwnedPetData::evolutionStage))
            .orElse(0);
        if (currentStage <= 0 || currentStage >= 5) {
            return List.of();
        }
        return questManager.evolutionQuestsForStage(currentStage).stream().toList();
    }

    private int slotToQuestIndex(int slot) {
        for (int i = 0; i < QUEST_SLOTS.length; i++) {
            if (QUEST_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private PetType petTypeByOverviewSlot(int slot) {
        return petTypeBySlot(slot, new int[]{10, 11, 12, 13, 14, 15, 16, 19, 28});
    }

    private PetType petTypeByHelpSlot(int slot) {
        return petTypeBySlot(slot, petHelpSlots());
    }

    private int[] petHelpSlots() {
        return new int[]{19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38};
    }

    private PetType petTypeBySlot(int slot, int[] slots) {
        List<PetType> types = playablePetTypes();
        for (int index = 0; index < slots.length && index < types.size(); index++) {
            if (slots[index] == slot) {
                return types.get(index);
            }
        }
        return null;
    }

    private Optional<OwnedPetData> selectedPetForType(Player player, PetType type) {
        Optional<OwnedPetData> runtime = petEngineManager.getPet(player)
            .map(dev.li2fox.vibepetcore.pet.RuntimePet::data)
            .filter(pet -> pet.petType().equalsIgnoreCase(type.name()));
        if (runtime.isPresent()) {
            return runtime;
        }
        return heldPetData(player)
            .filter(pet -> pet.petType().equalsIgnoreCase(type.name()));
    }

    private List<String> petInfoLore(PetType type, Optional<OwnedPetData> petData) {
        return infoGuiSupport.petInfoLore(type, petData);
    }

    private List<String> legendaryLore(PetType type) {
        List<String> lore = new ArrayList<>();
        lore.add(msg("gui.legendary.requirement", "&7Only for: &dLegendary &7pets, usually &fE3+&7."));
        lore.add(msg("gui.legendary.cooldown", "&7Cooldown: &f300 sec&7 after a successful proc."));
        lore.add(msg("gui.legendary.style." + type.name().toLowerCase(Locale.ROOT), "&7Style: &fcombat support."));
        lore.add("");
        lore.add(msg("gui.legendary.ability." + type.name().toLowerCase(Locale.ROOT), "&dSpecial trait: &fCombat burst."));
        return lore;
    }

    private List<String> detailedEvolutionLore(Player player, OwnedPetData pet) {
        return infoGuiSupport.detailedEvolutionLore(player, pet);
    }

    private List<String> evolutionStageLore(Player player, PetType type, int currentStage, Optional<OwnedPetData> petData) {
        return evolutionPreviewGuiSupport.evolutionStageLore(player, type, currentStage, petData);
    }

    private String stageTitle(int stage) {
        return "&d" + GameText.evolutionStageName(stage);
    }

    private String shortStageRequirement(int nextStage) {
        int level = balanceConfig.evolutionRequiredLevel(nextStage);
        int bond = balanceConfig.evolutionRequiredBond(nextStage);
        int quests = balanceConfig.evolutionRequiredQuests(nextStage);
        if (quests > 0) {
            return msg(
                "gui.pet.requirements.summary-quests",
                "&7Required: &fLv. {level} &8| &7Bond &f{bond}/10 &8| &7Quests &f{quests}/{quests}",
                "level", level,
                "bond", bond,
                "quests", quests
            );
        }
        return msg(
            "gui.pet.requirements.summary",
            "&7Required: &fLv. {level} &8| &7Bond &f{bond}/10",
            "level", level,
            "bond", bond
        );
    }

    private String evolutionStageName(OwnedPetData pet) {
        return infoGuiSupport.evolutionStageName(pet);
    }

    private OwnedPetData copyPetData(OwnedPetData source) {
        return evolutionPreviewGuiSupport.copyPetData(source);
    }

    private String stageName(int stage) {
        return evolutionPreviewGuiSupport.stageName(stage);
    }

    private String defenseChanceText(PetType type) {
        return PetGuiText.defenseChanceText(type);
    }

    private String attackRatingLine(PetType type) {
        return GameText.petInfoAttackRating(balanceConfig.petAttackRating(type), balanceConfig.petAttackMultiplier(type));
    }

    private String formatDecimal(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private String usefulEffectsText(PetType type) {
        return PetGuiText.usefulEffectsText(type);
    }

    private ItemStack autolootToggle(Player player) {
        Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet = petEngineManager.getPet(player);
        if (runtimePet.isEmpty()) {
            return item(Material.HOPPER, "&e" + GameText.petOverviewAutoLoot(false), List.of(
                GameText.petOverviewNeedCoreHint(),
                GameText.guiUnavailable()
            ));
        }
        if (!balanceConfig.petAutoLootEnabled(runtimePet.get().type())) {
            return item(Material.HOPPER, "&e" + GameText.petOverviewAutoLoot(false), List.of(
                GameText.petOverviewAutoLootHint(),
                GameText.guiUnavailable()
            ));
        }
        boolean enabled = runtimePet.get().data().autoLootEnabled();
        return item(Material.HOPPER, "&e" + GameText.petOverviewAutoLoot(enabled), List.of(GameText.petOverviewAutoLootHint()));
    }
    private ItemStack defenseToggle(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet) {
        if (runtimePet.isEmpty()) {
            return item(Material.SHIELD, "&e" + GameText.petOverviewDefense(false), List.of(
                GameText.petOverviewNeedCoreHint(),
                GameText.guiUnavailable()
            ));
        }
        boolean enabled = runtimePet.get().data().defenseEnabled();
        return item(Material.SHIELD, "&e" + GameText.petOverviewDefense(enabled), List.of(GameText.petOverviewDefenseHint()));
    }
    private void syncOffhandEgg(Player player) {
        petEngineManager.getPet(player).ifPresent(pet -> {
            heldPetCore(player)
                .filter(core -> core.data().petId().equals(pet.data().petId()))
                .ifPresent(core -> setHeldPetCore(player, core, writeCoreForState(core.item(), pet.data())));
        });
    }

    private Optional<HeldPetCore> heldPetCore(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        Optional<OwnedPetData> mainPet = petEggService.readEgg(mainHand);
        if (mainPet.isPresent()) {
            return Optional.of(new HeldPetCore(mainHand, true, mainPet.get()));
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        Optional<OwnedPetData> offhandPet = petEggService.readEgg(offhand);
        return offhandPet.map(pet -> new HeldPetCore(offhand, false, pet));
    }

    private Optional<OwnedPetData> heldPetData(Player player) {
        return heldPetCore(player).map(HeldPetCore::data);
    }

    private long economyPoints(Player player) {
        return playerDataManager.getOrLoad(player.getUniqueId()).points();
    }

    private Optional<UUID> selectedQuestPetId(Player player) {
        return petEngineManager.getPet(player)
            .map(pet -> pet.data().petId())
            .or(() -> heldPetData(player).map(OwnedPetData::petId));
    }

    private void setHeldPetCore(Player player, HeldPetCore core, ItemStack item) {
        if (core.mainHand()) {
            player.getInventory().setItemInMainHand(item);
        } else {
            player.getInventory().setItemInOffHand(item);
        }
    }

    private ItemStack writeCoreForState(ItemStack item, OwnedPetData data) {
        if (petEggService.isEmptyEgg(item)) {
            return petEggService.writeEmptyEgg(item, data);
        }
        return petEggService.writeEgg(item, data);
    }

    private record HeldPetCore(ItemStack item, boolean mainHand, OwnedPetData data) {
    }

    private String shortFoodList(PetType type) {
        if (balanceConfig.petFoodMaterials(type).isEmpty()) {
            return "pets/" + type.name().toLowerCase(Locale.ROOT) + ".yml";
        }
        return GameText.materialList(balanceConfig.petFoodMaterials(type), 4);
    }
    private ItemStack categoryItem(String category, String activeCategory, Material material, String title) {
        boolean active = normalizeCategory(activeCategory).equals(normalizeCategory(category));
        return item(material, (active ? "&a" : "&e") + title, List.of(
            GameText.questCategoryTabLine(title),
            active ? GameText.questCategoryActiveHint() : GameText.questCategoryOpenHint()
        ));
    }

    private ItemStack questPageItem(Material material, int page, int maxPage, int direction) {
        boolean next = direction > 0;
        String title = next
            ? msg("gui.quest.page.next", "&eNext page")
            : msg("gui.quest.page.previous", "&ePrevious page");
        return item(material, title, List.of(
            msg(
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
            return normalizeCategory(parts[2]);
        }
        return parts.length >= 2 ? normalizeCategory(parts[1]) : "daily";
    }

    private String sourceFromMenu(String menuId) {
        String[] parts = menuId.split(":");
        if (parts.length >= 3 && "petinfo".equalsIgnoreCase(parts[0])) {
            return normalizeSource(parts[1]);
        }
        if (parts.length >= 3 && "quests".equalsIgnoreCase(parts[0])) {
            return normalizeSource(parts[1]);
        }
        if (parts.length >= 2 && ("help".equalsIgnoreCase(parts[0]) || "growth".equalsIgnoreCase(parts[0]) || "legendary".equalsIgnoreCase(parts[0]) || "petarmor".equalsIgnoreCase(parts[0]) || "box".equalsIgnoreCase(parts[0]) || "forge".equalsIgnoreCase(parts[0]))) {
            return normalizeSource(parts[1]);
        }
        return "pet".equalsIgnoreCase(menuId) ? "pet" : "master";
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

    private String normalizeSource(String source) {
        String normalized = source == null ? "master" : source.toLowerCase(Locale.ROOT);
        return "pet".equals(normalized) ? "pet" : "master";
    }

    private void openSourceRoot(Player player, String source) {
        if ("pet".equals(normalizeSource(source))) {
            openPetOverview(player);
            return;
        }
        openMain(player);
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

    private String normalizeCategory(String category) {
        String normalized = category == null ? "daily" : category.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "daily", "weekly", "evolution", "gather", "combat", "explore" -> normalized;
            default -> "daily";
        };
    }

    private void fillFrame(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private ItemStack back() {
        return item(Material.ARROW, "&f" + GameText.guiBack(), List.of("&7\u2190"));
    }

    private ItemStack exitButton() {
        return item(Material.BARRIER, GameText.petOverviewExitTitle(), List.of(GameText.petOverviewExitHint()));
    }

    private ItemStack controllerTitleCard(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        Optional<OwnedPetData> petData = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data).or(() -> offhandPet);
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petOverviewControllerHint());
        petData.ifPresentOrElse(pet -> {
            lore.add("");
            lore.add(GameText.petOverviewControllerCurrent(positionLabel(pet.followPosition()), pet.followDistanceTitle()));
        }, () -> lore.add(GameText.petOverviewNeedCoreHint()));
        return item(Material.MAP, GameText.petOverviewControllerTitle(), lore);
    }

    private ItemStack followControllerCenter(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        Optional<OwnedPetData> petData = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data).or(() -> offhandPet);
        if (petData.isEmpty()) {
            return item(Material.COMPASS, "&b" + GameText.petOverviewControllerTitle(), List.of(
                GameText.petOverviewNeedCoreHint(),
                GameText.guiUnavailable()
            ));
        }
        OwnedPetData pet = petData.get();
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petOverviewControllerCurrent(positionLabel(pet.followPosition()), pet.followDistanceTitle()));
        lore.add(GameText.petOverviewFollowDistanceHint());
        return item(Material.COMPASS, "&b" + GameText.petOverviewControllerTitle(), lore);
    }

    private ItemStack followPositionButton(
        Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet,
        Optional<OwnedPetData> offhandPet,
        int position
    ) {
        Optional<OwnedPetData> petData = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data).or(() -> offhandPet);
        if (petData.isEmpty()) {
            return item(Material.RED_STAINED_GLASS_PANE, msg("gui.pet.position.title", "&7Position"), List.of(GameText.petOverviewNeedCoreHint()));
        }
        OwnedPetData pet = petData.get();
        String label = positionLabel(position);
        boolean active = pet.followPosition() == position;
        String title = active ? "&a" + label : "&c" + label;
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petOverviewControllerCurrent(label, pet.followDistanceTitle()));
        lore.add(GameText.petOverviewFollowDistanceHint());
        return item(active ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE, title, lore);
    }
    private ItemStack followDistanceCard(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        Optional<OwnedPetData> petData = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data).or(() -> offhandPet);
        if (petData.isEmpty()) {
            return item(Material.LEAD, msg("gui.pet.distance.title", "&dDistance"), List.of(GameText.petOverviewNeedCoreHint()));
        }
        OwnedPetData pet = petData.get();
        return item(Material.LEAD, msg("gui.pet.distance.current.title", "&dDistance &f") + pet.followDistanceTitle(), List.of(
            GameText.petOverviewFollowDistanceHint(),
            msg("gui.pet.distance.current.line", "&7Current level: &f") + pet.followDistanceTitle()
        ));
    }

    private ItemStack followDistanceDownButton(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet) {
        boolean enabled = runtimePet.isPresent();
        return item(
            Material.REDSTONE_TORCH,
            GameText.petOverviewFollowBackTitle(),
            enabled ? List.of(msg("gui.pet.follow.closer", "&7Make the pet follow closer."), GameText.petOverviewFollowDistanceHint()) : List.of(GameText.petOverviewNeedCoreHint())
        );
    }

    private ItemStack followDistanceUpButton(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet) {
        boolean enabled = runtimePet.isPresent();
        return item(
            Material.SOUL_TORCH,
            GameText.petOverviewFollowForwardTitle(),
            enabled ? List.of(msg("gui.pet.follow.further", "&7Make the pet follow farther."), GameText.petOverviewFollowDistanceHint()) : List.of(GameText.petOverviewNeedCoreHint())
        );
    }

    private ItemStack evolutionEntryButton(Player player, Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        Optional<OwnedPetData> petData = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data).or(() -> offhandPet);
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
            lore.add(msg("gui.pet.overview.info.click", "&eClick: open evolution, quests, and materials."));
        }, () -> lore.add(GameText.petOverviewNeedCoreHint()));
        return item(Material.CALIBRATED_SCULK_SENSOR, "&d" + GameText.petOverviewInfoTitle(), lore);
    }

    private ItemStack repairCoreButton(Player player, Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        Optional<OwnedPetData> petData = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data).or(() -> offhandPet);
        if (petData.isEmpty()) {
            return item(Material.BARRIER, "&c" + GameText.petOverviewRepairCore(), List.of(
                GameText.petOverviewNeedCoreHint(),
                GameText.coreRepairMissing()
            ));
        }

        OwnedPetData pet = petData.get();
        int maxDurability = balanceConfig.eggMaxDurability();
        int durability = pet.durability();
        int totems = countMaterial(player, Material.TOTEM_OF_UNDYING);
        boolean damaged = durability < maxDurability;
        List<String> lore = new ArrayList<>();
        lore.add(msg("gui.pet.durability", "&7Durability: &f{current}&8/&f{max}")
            .replace("{current}", String.valueOf(durability))
            .replace("{max}", String.valueOf(maxDurability)));
        if (!damaged) {
            lore.add("&a" + GameText.coreRepairAlreadyFull());
            lore.add(msg("gui.pet.repair.no-cost", "&7Totems are not consumed."));
        } else {
            lore.add(msg("gui.pet.repair.totems", "&7Totems in inventory: &f{count}", "count", totems));
            lore.add(totems > 0
                ? msg("gui.pet.repair.consume", "&7Click consumes 1 totem and restores 1 durability.")
                : "&c" + GameText.coreRepairNoTotems());
        }
        String title = damaged ? "&e" + GameText.petOverviewRepairCore() : "&a" + GameText.coreRepairAlreadyFull();
        return item(damaged ? Material.ANVIL : Material.ENCHANTED_BOOK, title, lore);
    }

    private ItemStack passiveEffectButton(
        Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet,
        Optional<OwnedPetData> offhandPet,
        PotionEffectType effectType,
        Material material
    ) {
        Optional<OwnedPetData> petData = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data).or(() -> offhandPet);
        String effectKey = effectType.getKey().getKey().toLowerCase(Locale.ROOT);
        String effectName = GameText.effectName(effectKey);
        if (petData.isEmpty()) {
            return item(Material.GRAY_DYE, msg("gui.pet.buff-toggle.title", "&7Auto-buff: {effect}", "effect", effectName), List.of(
                GameText.petOverviewNeedCoreHint(),
                GameText.guiUnavailable()
            ));
        }
        boolean enabled = petData.get().passiveEffectEnabled(effectKey);
        List<String> lore = new ArrayList<>();
        lore.add(enabled
            ? msg("gui.pet.buff-toggle.enabled", "&aEnabled")
            : msg("gui.pet.buff-toggle.disabled", "&cDisabled"));
        lore.add(msg("gui.pet.buff-toggle.hint", "&7Click to toggle this automatic pet buff."));
        lore.add(msg("gui.pet.buff-toggle.scope", "&8Affects only passive pet casts."));
        return item(
            enabled ? material : Material.GRAY_DYE,
            (enabled ? "&a" : "&c") + msg("gui.pet.buff-toggle.title", "Auto-buff: {effect}", "effect", effectName),
            lore
        );
    }

    private void togglePassiveEffect(Player player, PotionEffectType effectType) {
        if (petEngineManager.togglePassiveEffect(player, effectType)) {
            syncOffhandEgg(player);
            openPetOverview(player);
        }
    }

    private ItemStack petCoreUsageInfo(boolean summoned) {
        return item(Material.BELL, msg("gui.pet.summon.title", "&ePet core"), List.of(
            msg("gui.pet.summon.line.one", "&7Summon: hold the filled core and right-click."),
            msg("gui.pet.summon.line.two", "&7Return: hold the empty matching core and right-click."),
            msg("gui.pet.summon.line.three", "&7The core can be in either hand."),
            summoned ? msg("gui.pet.summon.active", "&aPet is active now.") : msg("gui.pet.summon.prompt", "&8Information card")
        ));
    }

    private ItemStack renamePetButton(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> heldPet) {
        Optional<OwnedPetData> pet = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data).or(() -> heldPet);
        if (pet.isEmpty()) {
            return item(Material.NAME_TAG, GameText.text("gui.pet.rename.button", "&eИмя питомца", "&ePet name"), List.of(
                GameText.text("gui.pet.rename.need-core", "&7Сначала выберите активное ядро.", "&7Select an active core first.")
            ));
        }
        return item(Material.NAME_TAG, GameText.text("gui.pet.rename.button", "&eИмя питомца", "&ePet name"), List.of(
            GameText.text("gui.pet.rename.current", "&7Сейчас: &f{name}", "&7Current: &f{name}").replace("{name}", pet.get().petName()),
            GameText.text("gui.pet.rename.hint", "&7Напишите команду: &f/pet name имя", "&7Use: &f/pet name name"),
            GameText.text("gui.pet.rename.cooldown-hint", "&8Переименование: раз в час.", "&8Rename: once per hour.")
        ));
    }

    private void selectFollowPosition(Player player, int position) {
        petEngineManager.setFollowPosition(player, position);
        syncOffhandEgg(player);
        openPetOverview(player);
    }
    private ItemStack aggressiveStyleButton(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet) {
        if (runtimePet.isEmpty()) {
            return item(Material.SHIELD, GameText.petOverviewAggressiveTitle(), List.of(
                GameText.petOverviewNeedCoreHint(),
                GameText.guiUnavailable()
            ));
        }
        boolean enabled = runtimePet.get().data().defenseEnabled();
        return item(enabled ? Material.IRON_SWORD : Material.SHIELD, GameText.petOverviewAggressiveTitle(), List.of(
            GameText.petOverviewAggressiveHint(enabled)
        ));
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

    private ItemStack currentPetItem(OwnedPetData pet, String fallbackName) {
        PetType type = PetType.parse(pet.petType()).orElse(PetType.WOLF);
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petInfoNameLine(GameText.petTypeName(type)));
        lore.add(GameText.petInfoRarityLine(GameText.rarityName(pet.rarity())));
        lore.add(GameText.petInfoEvolutionLine(evolutionStageName(pet), pet.evolutionStage()));
        lore.add(GameText.petInfoStatusLine(formatState(pet.state())));
        lore.add(attackRatingLine(type));
        lore.add(GameText.petInfoDefenseLine(defenseChanceText(type)));
        return item(eggMaterial(type), "&e" + (fallbackName == null || fallbackName.isBlank() ? GameText.petOverviewCurrentCore() : fallbackName), lore);
    }

    private ItemStack coreButton(Player player, Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet, boolean activeButton) {
        Optional<OwnedPetData> petData = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data).or(() -> offhandPet);
        if (petData.isEmpty()) {
            return item(Material.BARRIER, "&c" + GameText.petOverviewNoCore(), List.of(GameText.petOverviewNeedCoreHint()));
        }
        PetType type = PetType.parse(petData.get().petType()).orElse(PetType.WOLF);
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petInfoRarityLine(GameText.rarityName(petData.get().rarity())));
        lore.add(GameText.petInfoEvolutionLine(evolutionStageName(petData.get()), petData.get().evolutionStage()));
        lore.add(GameText.petInfoStatusLine(runtimePet.map(pet -> formatState(pet.state())).orElse(formatState(petData.get().state()))));
        lore.add(GameText.petInfoDurabilityLine(petData.get().durability(), balanceConfig.eggMaxDurability()));
        lore.add(GameText.petInfoSatietyLine((int) Math.round(petData.get().satiety()), balanceConfig.eggMaxSatiety()));
        lore.add(GameText.petInfoLevelLine(petData.get().level()));
        lore.add(GameText.petInfoBondLine(petEngineManager.evolutionBond(petData.get()), balanceConfig.bondMax()));
        lore.add("");
        lore.add(msg("gui.pet.core.needs.header", "&7Needs:"));
        lore.add(msg("gui.pet.core.needs.health", "&8- &fHP: &7{current}/{max}", "current", Math.round(petData.get().health()), "max", Math.round(petData.get().maxHealth())));
        lore.add(msg("gui.pet.core.needs.food", "&8- &fFood: &7{current}/{max}", "current", Math.round(petData.get().satiety()), "max", balanceConfig.eggMaxSatiety()));
        lore.add(msg("gui.pet.core.food-types", "&7Food: &f{foods}", "foods", GameText.materialList(balanceConfig.petFoodMaterials(type), 6)));
        lore.add("");
        lore.addAll(coreEvolutionNeeds(player, petData.get()));
        lore.add(activeButton ? GameText.petOverviewCoreActiveLabel() : GameText.petOverviewCoreOffhandLabel());
        return item(activeButton ? balanceConfig.activeButtonMaterial() : eggMaterial(type), "&e" + GameText.petOverviewCurrentCore(), lore);
    }

    private List<String> coreEvolutionNeeds(Player player, OwnedPetData pet) {
        List<String> lore = new ArrayList<>();
        if (pet.evolutionStage() >= 5) {
            lore.add(GameText.petInfoMaxEvolution());
            return lore;
        }
        var requirement = petEngineManager.evolutionRequirement(pet);
        int bond = petEngineManager.evolutionBond(pet);
        int completedQuests = petEngineManager.completedEvolutionQuestCompletions(player, pet);
        lore.add(msg("gui.pet.core.evolution.header", "&7Next evolution:"));
        lore.add(GameText.petEvolutionPreviewLevelLine(pet.level(), requirement.requiredLevel()));
        lore.add(GameText.petEvolutionPreviewBondLine(bond, requirement.requiredBond()));
        lore.add(GameText.petEvolutionPreviewQuestLine(completedQuests, requirement.requiredQuests()));
        if (!requirement.materials().isEmpty()) {
            Map<Material, Integer> counts = petEngineManager.evolutionMaterialCounts(player, pet);
            for (Map.Entry<Material, Integer> entry : requirement.materials().entrySet()) {
                int current = counts.getOrDefault(entry.getKey(), 0);
                lore.add(GameText.petEvolutionPreviewMaterialLine(GameText.materialName(entry.getKey()), current, entry.getValue()));
            }
        }
        return lore;
    }

    private String formatState(String rawState) {
        if (rawState == null || rawState.isBlank()) {
            return GameText.stateName(PetState.FOLLOW);
        }
        try {
            return GameText.stateName(PetState.valueOf(rawState.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return GameText.stateName(PetState.FOLLOW);
        }
    }

    private String formatState(PetState state) {
        return GameText.stateName(state == null ? PetState.FOLLOW : state);
    }

    private String percentText(double chance) {
        return Math.round(chance * 100.0D) + "%";
    }

    private List<String> rarityForgeCoreLore(Player player, OwnedPetData pet) {
        PetType type = PetType.parse(pet.petType()).orElse(PetType.WOLF);
        PetRarity rarity = PetRarity.parse(pet.rarity());
        double chancePercent = balanceConfig.eggRarityUpgradeChance(rarity.name().toLowerCase(Locale.ROOT)) * 100.0D;
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petInfoRarityLine(GameText.rarityName(rarity)));
        lore.add(GameText.petInfoEvolutionLine(evolutionStageName(pet), pet.evolutionStage()));
        lore.add(GameText.petInfoStatusLine(formatState(pet.state())));
        lore.add(attackRatingLine(type));
        lore.add(GameText.petInfoDefenseLine(defenseChanceText(type)));
        lore.add("");
        lore.add(GameText.forgeUpgradeCost(balanceConfig.eggRarityUpgradeCost()));
        if (rarity.canUpgrade()) {
            lore.add(GameText.forgeUpgradeAttempt(chancePercent));
        } else {
            lore.add("&6" + GameText.forgeMaxRarity());
        }
        return lore;
    }

    private List<String> rarityForgeAttemptLore(Player player, OwnedPetData pet) {
        PetRarity rarity = PetRarity.parse(pet.rarity());
        if (!rarity.canUpgrade()) {
            return List.of(
                "&6" + GameText.forgeMaxRarity(),
                GameText.guiUnavailable()
            );
        }
        double chancePercent = balanceConfig.eggRarityUpgradeChance(rarity.name().toLowerCase(Locale.ROOT)) * 100.0D;
        return List.of(
            GameText.forgeUpgradeHint(),
            GameText.forgeUpgradeCost(balanceConfig.eggRarityUpgradeCost()),
            GameText.forgeUpgradeAttempt(chancePercent),
            "&a" + GameText.forgeUpgradeTitle()
        );
    }

    private ItemStack petStatusCard(Player player, Optional<OwnedPetData> offhandPet, Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet) {
        Optional<OwnedPetData> petData = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data).or(() -> offhandPet);
        if (petData.isEmpty()) {
            return item(Material.BARRIER, "&c" + GameText.petOverviewNoCore(), List.of(
                GameText.petOverviewNeedCoreHint(),
                GameText.guiUnavailable()
            ));
        }

        OwnedPetData pet = petData.get();
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petInfoRarityLine(GameText.rarityName(pet.rarity())));
        lore.add(GameText.petInfoEvolutionLine(evolutionStageName(pet), pet.evolutionStage()));
        lore.add(GameText.petInfoStatusLine(runtimePet.map(active -> formatState(active.state())).orElse(formatState(pet.state()))));
        lore.add(GameText.petInfoDurabilityLine(pet.durability(), balanceConfig.eggMaxDurability()));
        lore.add(GameText.petInfoSatietyLine((int) Math.round(pet.satiety()), balanceConfig.eggMaxSatiety()));
        return item(Material.HEART_OF_THE_SEA, "&e" + GameText.petOverviewStatusTitle(), lore);
    }

    private ItemStack movementModeToggle(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet) {
        boolean waiting = runtimePet.map(pet -> pet.state() != PetState.IDLE).orElse(false);
        return item(
            waiting ? Material.REDSTONE_TORCH : Material.SOUL_TORCH,
            "&e" + GameText.petOverviewMovementMode(waiting),
            List.of(GameText.petOverviewMovementHint(waiting))
        );
    }

    private ItemStack followPositionToggle(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet) {
        String positionTitle = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data)
            .map(OwnedPetData::followPositionTitle)
            .orElse("\u0441\u0442\u0430\u043d\u0434\u0430\u0440\u0442");
        return item(
            Material.COMPASS,
            "&e" + GameText.petOverviewFollowPosition(),
            List.of(GameText.petOverviewFollowPositionValue(positionTitle))
        );
    }

    private List<String> evolutionLore(Player player, Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
        Optional<OwnedPetData> petData = runtimePet.map(dev.li2fox.vibepetcore.pet.RuntimePet::data).or(() -> offhandPet);
        if (petData.isEmpty()) {
            return List.of(GameText.petOverviewNeedCoreHint());
        }
        OwnedPetData pet = petData.get();
        List<String> lore = new ArrayList<>();
        lore.add(GameText.petInfoEvolutionLine(evolutionStageName(pet), pet.evolutionStage()));
        lore.add(shortStageRequirement(Math.min(5, pet.evolutionStage() + 1)));
        lore.addAll(detailedEvolutionLore(player, pet));
        return lore;
    }

    private List<PetType> playablePetTypes() {
        return Arrays.stream(PetType.values())
            .filter(type -> type != PetType.VEX)
            .toList();
    }

    private Material eggMaterial(PetType type) {
        return petEggService.eggMaterial(type);
    }

    private Material stageMaterial(int stage) {
        return switch (Math.max(1, Math.min(5, stage))) {
            case 1 -> Material.COPPER_INGOT;
            case 2 -> Material.IRON_INGOT;
            case 3 -> Material.GOLD_INGOT;
            case 4 -> Material.DIAMOND;
            case 5 -> Material.NETHER_STAR;
            default -> Material.COPPER_INGOT;
        };
    }

    private void attemptRarityUpgrade(Player player) {
        attemptRarityUpgrade(player, "master");
    }

    private void attemptRarityUpgrade(Player player, String source) {
        Optional<HeldPetCore> heldCore = heldPetCore(player);
        if (heldCore.isEmpty()) {
            player.sendMessage(GameText.forgeNeedActiveCore());
            return;
        }

        HeldPetCore core = heldCore.get();
        OwnedPetData petData = core.data();
        PetType type = PetType.parse(petData.petType()).orElse(PetType.WOLF);
        PetRarity rarity = PetRarity.parse(petData.rarity());
        if (!rarity.canUpgrade()) {
            player.sendMessage(GameText.forgeMaxRarity());
            return;
        }

        int required = Math.max(1, balanceConfig.eggRarityUpgradeCost());
        List<Integer> donorSlots = new ArrayList<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (item == null || item.getType().isAir() || !petEggService.isPetEgg(item) || petEggService.isActiveButton(item)) {
                continue;
            }
            Optional<OwnedPetData> donorOptional = petEggService.readEgg(item);
            if (donorOptional.isEmpty()) {
                continue;
            }
            OwnedPetData donor = donorOptional.get();
            if (isRarityForgeTargetCore(player, core, slot, donor)) {
                continue;
            }
            if (!PetType.parse(donor.petType()).orElse(PetType.WOLF).equals(type)) {
                continue;
            }
            if (PetRarity.parse(donor.rarity()) != rarity) {
                continue;
            }
            donorSlots.add(slot);
            if (donorSlots.size() >= required) {
                break;
            }
        }

        if (donorSlots.size() < required) {
            player.sendMessage(GameText.forgeNeedSacrifices(required - donorSlots.size(), GameText.petTypeName(type), GameText.rarityName(rarity)));
            return;
        }

        for (int slot : donorSlots) {
            consumeOneInventorySlot(player, slot);
        }

        double successChance = balanceConfig.eggRarityUpgradeChance(rarity.name().toLowerCase(Locale.ROOT));
        boolean success = ThreadLocalRandom.current().nextDouble() < successChance;
        if (success) {
            petData.setRarity(rarity.next().name());
            petEngineManager.replaceActivePetData(player, petData);
        }

        setHeldPetCore(player, core, writeCoreForState(core.item(), petData));
        player.sendMessage(success ? GameText.forgeUpgradeSuccess(GameText.rarityName(petData.rarity())) : GameText.forgeUpgradeFail());
        player.playSound(player.getLocation(), success ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_ANVIL_LAND, 0.75F, success ? 1.1F : 0.8F);
        openRarityForge(player, source);
    }

    private boolean isRarityForgeTargetCore(Player player, HeldPetCore core, int inventorySlot, OwnedPetData donor) {
        return donor.petId().equals(core.data().petId())
            || (core.mainHand() && inventorySlot == player.getInventory().getHeldItemSlot());
    }

    private boolean repairCore(Player player) {
        Optional<HeldPetCore> heldCore = heldPetCore(player);
        if (heldCore.isEmpty()) {
            player.sendMessage(GameText.coreRepairMissing());
            return false;
        }

        HeldPetCore core = heldCore.get();
        OwnedPetData data = core.data();
        int maxDurability = balanceConfig.eggMaxDurability();
        int currentDurability = data.durability();
        if (currentDurability >= maxDurability) {
            player.sendMessage(GameText.coreRepairAlreadyFull());
            return false;
        }

        int totemCount = countMaterial(player, Material.TOTEM_OF_UNDYING);
        if (totemCount <= 0) {
            player.sendMessage(GameText.coreRepairNoTotems());
            return false;
        }

        consumeOneMaterial(player, Material.TOTEM_OF_UNDYING);
        int nextDurability = Math.min(maxDurability, currentDurability + 1);
        data.setDurability(nextDurability);
        data.setInactiveUntilMillis(0L);
        data.setSatiety(balanceConfig.eggMaxSatiety());
        data.setHealth(data.maxHealth());
        petEngineManager.replaceActivePetData(player, data);

        setHeldPetCore(player, core, writeCoreForState(core.item(), data));
        player.sendMessage(GameText.coreRepairIncreased(currentDurability, nextDurability));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.75F, 1.1F);
        return true;
    }

    private boolean allowPetMenuClick(Player player) {
        long now = System.currentTimeMillis();
        petMenuClickCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        long nextAllowed = petMenuClickCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextAllowed) {
            return false;
        }
        petMenuClickCooldowns.put(player.getUniqueId(), now + 1_500L);
        return true;
    }

    private boolean allowGuiAction(Player player) {
        long now = System.currentTimeMillis();
        guiActionCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        UUID playerId = player.getUniqueId();
        long nextAllowed = guiActionCooldowns.getOrDefault(playerId, 0L);
        if (now < nextAllowed) {
            return false;
        }
        guiActionCooldowns.put(playerId, now + 750L);
        return true;
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

    private void consumeOneMaterial(Player player, Material material) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length; index++) {
            ItemStack item = contents[index];
            if (item == null || item.getType() != material) {
                continue;
            }
            if (item.getAmount() <= 1) {
                contents[index] = null;
            } else {
                item.setAmount(item.getAmount() - 1);
            }
            player.getInventory().setStorageContents(contents);
            return;
        }
    }

    private void consumeOneInventorySlot(Player player, int slot) {
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (item.getAmount() <= 1) {
            player.getInventory().setItem(slot, null);
            return;
        }
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItem(slot, item);
    }

    private ItemStack item(Material material, String name, List<String> lore) {

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title(name));
        meta.lore(lore.stream().map(this::title).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
