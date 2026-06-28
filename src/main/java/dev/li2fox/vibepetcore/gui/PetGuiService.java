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
            .or(() -> petMenuHeldPetData(player));
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
            .or(() -> petMenuHeldPetData(player).map(OwnedPetData::evolutionStage))
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
        return petMenuHeldPetData(player)
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

    List<String> evolutionStageLore(Player player, PetType type, int currentStage, Optional<OwnedPetData> petData) {
        return evolutionPreviewGuiSupport.evolutionStageLore(player, type, currentStage, petData);
    }

    private String evolutionStageName(OwnedPetData pet) {
        return infoGuiSupport.evolutionStageName(pet);
    }

    private OwnedPetData copyPetData(OwnedPetData source) {
        return evolutionPreviewGuiSupport.copyPetData(source);
    }

    private String defenseChanceText(PetType type) {
        return PetGuiText.defenseChanceText(type);
    }

    private String attackRatingLine(PetType type) {
        return GameText.petInfoAttackRating(balanceConfig.petAttackRating(type), balanceConfig.petAttackMultiplier(type));
    }

    void syncOffhandEgg(Player player) {
        petEngineManager.getPet(player).ifPresent(pet -> {
            offhandPetCore(player)
                .filter(core -> core.data().petId().equals(pet.data().petId()))
                .ifPresent(core -> setHeldPetCore(player, core, writeCoreForState(core.item(), pet.data())));
        });
    }

    private Optional<HeldPetCore> heldPetCore(Player player) {
        return PetGuiCoreSelectionSupport.selectPreferredGuiCore(
            runtimePet(player).map(pet -> pet.data().petId()),
            mainHandPetCore(player),
            offhandPetCore(player),
            core -> core.data().petId()
        );
    }

    private Optional<HeldPetCore> mainHandPetCore(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        Optional<OwnedPetData> mainPet = petEggService.readEgg(mainHand);
        if (mainPet.isPresent()) {
            return Optional.of(new HeldPetCore(mainHand, true, mainPet.get()));
        }
        return Optional.empty();
    }

    private Optional<HeldPetCore> offhandPetCore(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        Optional<OwnedPetData> offhandPet = petEggService.readEgg(offhand);
        return offhandPet.map(pet -> new HeldPetCore(offhand, false, pet));
    }

    private Optional<HeldPetCore> petMenuHeldPetCore(Player player) {
        return heldPetCore(player);
    }

    Optional<dev.li2fox.vibepetcore.pet.RuntimePet> runtimePet(Player player) {
        return petEngineManager.getPet(player);
    }

    Optional<OwnedPetData> heldPetData(Player player) {
        return heldPetCore(player).map(HeldPetCore::data);
    }

    Optional<OwnedPetData> petMenuHeldPetData(Player player) {
        return petMenuHeldPetCore(player).map(HeldPetCore::data);
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
            .or(() -> petMenuHeldPetData(player).map(OwnedPetData::petId));
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

    void togglePassiveEffect(Player player, PotionEffectType effectType) {
        if (petEngineManager.togglePassiveEffect(player, effectType)) {
            syncOffhandEgg(player);
            openPetOverview(player);
        }
    }

    void selectFollowPosition(Player player, int position) {
        petEngineManager.setFollowPosition(player, position);
        syncOffhandEgg(player);
        openPetOverview(player);
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

        double successChance = balanceConfig.eggRarityUpgradeChance(rarity.name().toLowerCase(Locale.ROOT));
        PetForgeFlowSupport.ForgeStateSnapshot snapshot = PetForgeFlowSupport.snapshotState(player, petData);
        PetForgeFlowSupport.ForgeAttemptResult result = PetForgeFlowSupport.attemptRarityUpgrade(
            player,
            petData,
            rarity,
            donorSlots,
            snapshot,
            successChance,
            ThreadLocalRandom.current()::nextDouble,
            restored -> petEngineManager.replaceActivePetData(player, restored),
            () -> playerDataManager.save(player.getUniqueId())
        );
        if (result.saveFailed()) {
            player.sendMessage(GameText.text(
                "forge.upgrade.save-failed",
                "Не удалось сохранить кузню ядра. Донорские яйца и состояние питомца восстановлены, попробуйте ещё раз через пару секунд.",
                "Could not save the core forge upgrade. Donor eggs and pet state were restored. Try again in a few seconds."
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.75F);
            openRarityForge(player, source);
            return;
        }

        setHeldPetCore(player, core, writeCoreForState(core.item(), petData));
        player.sendMessage(result.upgraded() ? GameText.forgeUpgradeSuccess(GameText.rarityName(petData.rarity())) : GameText.forgeUpgradeFail());
        player.playSound(player.getLocation(), result.upgraded() ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_ANVIL_LAND, 0.75F, result.upgraded() ? 1.1F : 0.8F);
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
        Optional<HeldPetCore> heldCore = petMenuHeldPetCore(player);
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

        int nextDurability = Math.min(maxDurability, currentDurability + 1);
        PetCoreRepairFlowSupport.RepairStateSnapshot snapshot = PetCoreRepairFlowSupport.snapshotState(player, data);
        PetCoreRepairFlowSupport.RepairAttemptResult result = PetCoreRepairFlowSupport.attemptRepair(
            player,
            data,
            nextDurability,
            balanceConfig.eggMaxSatiety(),
            snapshot,
            restored -> petEngineManager.replaceActivePetData(player, restored),
            () -> playerDataManager.save(player.getUniqueId())
        );
        if (result.saveFailed()) {
            player.sendMessage(GameText.text(
                "core.repair.save-failed",
                "Не удалось сохранить ремонт ядра. Тотем и состояние питомца восстановлены, попробуйте ещё раз через пару секунд.",
                "Could not save the core repair. The totem and pet state were restored. Try again in a few seconds."
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.75F);
            return false;
        }

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
        if (!petEngineManager.openActivePetVault(player)) {
            player.sendMessage(GameText.text("pet.control.no-active", "Нет активного питомца.", "No active pet."));
        }
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

    void trainPet(Player player) {
        if (!allowPetMenuClick(player)) {
            return;
        }
        PetEngineManager.TrainResult result = petEngineManager.trainPet(player);
        player.sendMessage(result.message());
        if (result.success()) {
            syncOffhandEgg(player);
        }
        openPetOverview(player);
    }

    long trainingCooldownSeconds(UUID petId) {
        return petEngineManager.trainingCooldownRemainingSeconds(petId);
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
