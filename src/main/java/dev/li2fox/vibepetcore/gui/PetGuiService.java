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
    private final PetGuiRouter guiRouter;
    private final SourceQuestPage sourceQuestPage;
    private final SourceBoxPage sourceBoxPage;
    private final SourceForgePage sourceForgePage;
    private final SourceLegendaryPage sourceLegendaryPage;
    private final SourceHelpPage sourceHelpPage;
    private final PetArmorHelpPage petArmorHelpPage;
    private final GrowthMissingPage growthMissingPage;
    private final PetInfoPage petInfoPage;
    private final PetOverviewPage petOverviewPage;
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
        this.guiRouter = new PetGuiRouter();
        this.guiRouter.register(new SourceMainPage(this));
        this.sourceQuestPage = new SourceQuestPage(this);
        this.guiRouter.register(sourceQuestPage);
        this.sourceBoxPage = new SourceBoxPage(this);
        this.guiRouter.register(sourceBoxPage);
        this.sourceForgePage = new SourceForgePage(this);
        this.guiRouter.register(sourceForgePage);
        this.sourceLegendaryPage = new SourceLegendaryPage(this);
        this.guiRouter.register(sourceLegendaryPage);
        this.sourceHelpPage = new SourceHelpPage(this);
        this.guiRouter.register(sourceHelpPage);
        this.petArmorHelpPage = new PetArmorHelpPage(this);
        this.guiRouter.register(petArmorHelpPage);
        this.growthMissingPage = new GrowthMissingPage(this);
        this.guiRouter.register(growthMissingPage);
        this.petInfoPage = new PetInfoPage(this);
        this.guiRouter.register(petInfoPage);
        this.petOverviewPage = new PetOverviewPage(this);
        this.guiRouter.register(petOverviewPage);
    }

    String msg(String key, String fallback, Object... replacements) {
        return this.balanceConfig.message(key, fallback, replacements);
    }

    Component title(String legacyTitle) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacyTitle.replace('§', '&'));
    }

    public void open(Player player, String menuId) {
        try {
            String normalizedMenuId = menuId.toLowerCase(Locale.ROOT);
            if (normalizedMenuId.startsWith("quests")) {
                sourceQuestPage.openFromMenu(player, menuId);
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
                case "help" -> openHelpOverview(player, "pet");
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
        guiRouter.open(GuiPageId.SOURCE_MAIN, player);
    }

    void openQuests(Player player, String category, String source) {
        sourceQuestPage.open(player, category, source, 0);
    }

    private void openBox(Player player) {
        openBox(player, "master");
    }

    void openBox(Player player, String source) {
        sourceBoxPage.open(player, source);
    }
    private void openRarityForge(Player player) {
        openRarityForge(player, "master");
    }

    void openRarityForge(Player player, String source) {
        sourceForgePage.open(player, source);
    }
    private void openPetOverview(Player player) {
        guiRouter.open(GuiPageId.PET_OVERVIEW, player);
    }
    void openHelpOverview(Player player, String source) {
        sourceHelpPage.open(player, source);
    }

    void openLegendaryFeatures(Player player, String source) {
        sourceLegendaryPage.open(player, source);
    }

    private void openPetInfo(Player player, String rawType) {
        openPetInfo(player, rawType, "master");
    }

    void openPetInfo(Player player, String rawType, String source) {
        petInfoPage.open(player, rawType, source);
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
                case "main", "master" -> guiRouter.handleClick(holder.menuId(), player, event.getSlot());
                case "pet" -> guiRouter.handleClick(holder.menuId(), player, event.getSlot());
                default -> {
                    if (guiRouter.handleClick(holder.menuId(), player, event.getSlot())) {
                        return;
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

    private void openCurrentPetGrowth(Player player) {
        openCurrentPetGrowth(player, "pet");
    }

    void openCurrentPetGrowth(Player player, String source) {
        Optional<OwnedPetData> pet = petEngineManager.getPet(player).map(dev.li2fox.vibepetcore.pet.RuntimePet::data)
            .or(() -> heldPetData(player));
        if (pet.isPresent()) {
            openPetInfo(player, pet.get().petType(), source);
            return;
        }
        openGrowthMissing(player, source);
    }

    private void openGrowthMissing(Player player, String source) {
        growthMissingPage.open(player, source);
    }

    private void handleBackClick(Player player, String menuId) {
        String source = sourceFromMenu(menuId);
        if ("pet".equals(source)) {
            openPetOverview(player);
            return;
        }
        openMain(player);
    }

    void openPetArmorHelp(Player player, String source) {
        petArmorHelpPage.open(player, source);
    }

    String petInfoSourceFromMenu(String menuId) {
        String[] parts = menuId.split(":");
        return parts.length >= 3 ? normalizeSource(parts[1]) : "master";
    }

    String petInfoTypeFromMenu(String menuId) {
        String[] parts = menuId.split(":");
        return parts.length >= 3 ? parts[2] : menuId.substring(menuId.indexOf(':') + 1);
    }

    void openBoxForPlayer(Player player, String source) {
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

    void playMenuOpen(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    void playQuestFeedback(Player player, boolean success, boolean completed) {
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

    List<QuestDefinition> visibleQuests(Player player, String category) {
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

    private PetType petTypeByOverviewSlot(int slot) {
        return petTypeBySlot(slot, new int[]{10, 11, 12, 13, 14, 15, 16, 19, 28});
    }

    PetType petTypeByHelpSlot(int slot) {
        return petTypeBySlot(slot, petHelpSlots());
    }

    int[] petHelpSlots() {
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

    Optional<OwnedPetData> selectedPetForType(Player player, PetType type) {
        Optional<OwnedPetData> runtime = petEngineManager.getPet(player)
            .map(dev.li2fox.vibepetcore.pet.RuntimePet::data)
            .filter(pet -> pet.petType().equalsIgnoreCase(type.name()));
        if (runtime.isPresent()) {
            return runtime;
        }
        return heldPetData(player)
            .filter(pet -> pet.petType().equalsIgnoreCase(type.name()));
    }

    List<String> petInfoLore(PetType type, Optional<OwnedPetData> petData) {
        return infoGuiSupport.petInfoLore(type, petData);
    }

    List<String> roleDetails(PetType type) {
        return infoGuiSupport.roleDetails(type);
    }

    List<String> helpCardLore(PetType type) {
        return helpGuiSupport.helpCardLore(type);
    }

    ItemStack createPetArmor(dev.li2fox.vibepetcore.pet.armor.PetArmorTier tier) {
        return petArmorService.createArmor(tier);
    }

    List<String> legendaryLore(PetType type) {
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

    List<String> evolutionStageLore(Player player, PetType type, int currentStage, Optional<OwnedPetData> petData) {
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
                "&7Required: &fLv. {level} &8| &7Bond &f{bond}/10 &8| &7Stage quests: &f{quests}",
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

    ItemStack autolootToggle(Player player) {
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
    void syncOffhandEgg(Player player) {
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

    Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet(Player player) {
        return petEngineManager.getPet(player);
    }

    Optional<OwnedPetData> heldPetData(Player player) {
        return heldPetCore(player).map(HeldPetCore::data);
    }

    PlayerData playerData(Player player) {
        return playerDataManager.getOrLoad(player.getUniqueId());
    }

    BalanceConfig balanceConfig() {
        return balanceConfig;
    }

    long economyPoints(Player player) {
        return playerDataManager.getOrLoad(player.getUniqueId()).points();
    }

    Optional<UUID> selectedQuestPetId(Player player) {
        return petEngineManager.getPet(player)
            .map(pet -> pet.data().petId())
            .or(() -> heldPetData(player).map(OwnedPetData::petId));
    }

    QuestManager questManager() {
        return questManager;
    }

    PetQuestGuiSupport questGuiSupport() {
        return questGuiSupport;
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
    String sourceFromMenu(String menuId) {
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

    String normalizeSource(String source) {
        String normalized = source == null ? "master" : source.toLowerCase(Locale.ROOT);
        return "pet".equals(normalized) ? "pet" : "master";
    }

    void openSourceRoot(Player player, String source) {
        if ("pet".equals(normalizeSource(source))) {
            openPetOverview(player);
            return;
        }
        openMain(player);
    }

    String normalizeCategory(String category) {
        String normalized = category == null ? "daily" : category.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "daily", "weekly", "evolution", "gather", "combat", "explore" -> normalized;
            default -> "daily";
        };
    }

    void fillFrame(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                inventory.setItem(slot, filler);
            }
        }
    }

    ItemStack back() {
        return item(Material.ARROW, "&f" + GameText.guiBack(), List.of("&7\u2190"));
    }

    ItemStack exitButton() {
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

    ItemStack followControllerCenter(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
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

    ItemStack followPositionButton(
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
    ItemStack followDistanceCard(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
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

    ItemStack followDistanceDownButton(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet) {
        boolean enabled = runtimePet.isPresent();
        return item(
            Material.REDSTONE_TORCH,
            GameText.petOverviewFollowBackTitle(),
            enabled ? List.of(msg("gui.pet.follow.closer", "&7Make the pet follow closer."), GameText.petOverviewFollowDistanceHint()) : List.of(GameText.petOverviewNeedCoreHint())
        );
    }

    ItemStack followDistanceUpButton(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet) {
        boolean enabled = runtimePet.isPresent();
        return item(
            Material.SOUL_TORCH,
            GameText.petOverviewFollowForwardTitle(),
            enabled ? List.of(msg("gui.pet.follow.further", "&7Make the pet follow farther."), GameText.petOverviewFollowDistanceHint()) : List.of(GameText.petOverviewNeedCoreHint())
        );
    }

    ItemStack evolutionEntryButton(Player player, Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
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

    ItemStack repairCoreButton(Player player, Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> offhandPet) {
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

    ItemStack passiveEffectButton(
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

    void togglePassiveEffect(Player player, PotionEffectType effectType) {
        if (petEngineManager.togglePassiveEffect(player, effectType)) {
            syncOffhandEgg(player);
            openPetOverview(player);
        }
    }

    ItemStack petCoreUsageInfo(boolean summoned) {
        return item(Material.BELL, msg("gui.pet.summon.title", "&ePet core"), List.of(
            msg("gui.pet.summon.line.one", "&7Summon: hold the filled core and right-click."),
            msg("gui.pet.summon.line.two", "&7Return: hold the empty matching core and right-click."),
            msg("gui.pet.summon.line.three", "&7The core can be in either hand."),
            summoned ? msg("gui.pet.summon.active", "&aPet is active now.") : msg("gui.pet.summon.prompt", "&8Information card")
        ));
    }

    ItemStack renamePetButton(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet, Optional<OwnedPetData> heldPet) {
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

    void selectFollowPosition(Player player, int position) {
        petEngineManager.setFollowPosition(player, position);
        syncOffhandEgg(player);
        openPetOverview(player);
    }
    ItemStack aggressiveStyleButton(Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet) {
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

    List<String> rarityForgeCoreLore(Player player, OwnedPetData pet) {
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

    List<String> rarityForgeAttemptLore(Player player, OwnedPetData pet) {
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

    ItemStack petStatusCard(Player player, Optional<OwnedPetData> offhandPet, Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet) {
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

    List<PetType> playablePetTypes() {
        return Arrays.stream(PetType.values())
            .filter(type -> type != PetType.VEX)
            .toList();
    }

    Material eggMaterial(PetType type) {
        return petEggService.eggMaterial(type);
    }

    Material stageMaterial(int stage) {
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

    void attemptRarityUpgrade(Player player, String source) {
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

    void tryEvolveActivePet(Player player) {
        petEngineManager.tryEvolveActivePet(player);
    }

    private boolean isRarityForgeTargetCore(Player player, HeldPetCore core, int inventorySlot, OwnedPetData donor) {
        return donor.petId().equals(core.data().petId())
            || (core.mainHand() && inventorySlot == player.getInventory().getHeldItemSlot());
    }

    boolean repairCore(Player player) {
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

    boolean allowPetMenuClick(Player player) {
        long now = System.currentTimeMillis();
        petMenuClickCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        long nextAllowed = petMenuClickCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextAllowed) {
            return false;
        }
        petMenuClickCooldowns.put(player.getUniqueId(), now + 1_500L);
        return true;
    }

    void openActivePetVault(Player player) {
        petEngineManager.openActivePetVault(player);
    }

    void toggleDefense(Player player) {
        petEngineManager.toggleDefense(player);
        syncOffhandEgg(player);
        openPetOverview(player);
    }

    void toggleAutoloot(Player player) {
        petEngineManager.toggleAutoloot(player);
        syncOffhandEgg(player);
        openPetOverview(player);
    }

    void decreaseFollowDistance(Player player) {
        petEngineManager.decreaseFollowDistance(player);
        syncOffhandEgg(player);
        openPetOverview(player);
    }

    void increaseFollowDistance(Player player) {
        petEngineManager.increaseFollowDistance(player);
        syncOffhandEgg(player);
        openPetOverview(player);
    }

    void startSourceTeleport(Player player) {
        petMasterManager.startSpawnMasterTeleport(player);
    }

    boolean allowGuiAction(Player player) {
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

    ItemStack item(Material material, String name, List<String> lore) {

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title(name));
        meta.lore(lore.stream().map(this::title).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
