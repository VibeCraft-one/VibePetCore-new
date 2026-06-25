package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.CoreModule;
import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.core.PetDebugLogger;
import dev.li2fox.vibepetcore.egg.PetEggService;
import dev.li2fox.vibepetcore.master.PetMasterManager;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.player.PlayerData;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.progression.FeedResult;
import dev.li2fox.vibepetcore.progression.FeedType;
import dev.li2fox.vibepetcore.api.ProgressionAPI;
import dev.li2fox.vibepetcore.pet.ability.PetAbilityService;
import dev.li2fox.vibepetcore.pet.armor.PetArmorService;
import dev.li2fox.vibepetcore.pet.inventory.PetVaultService;
import dev.li2fox.vibepetcore.quest.QuestManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class PetEngineManager implements CoreModule {
    private static final long PLAYER_COMBAT_WINDOW_MILLIS = 10_000L;
    private static final long PET_PROVOKE_WINDOW_MILLIS = 6_000L;
    private static final long PET_DUEL_WINDOW_MILLIS = 8_000L;
    private static final long PET_RENAME_COOLDOWN_MILLIS = 3_600_000L;
    private static final double SOURCE_EVOLUTION_RADIUS = 10.0D;
    private static final double SOURCE_EVOLUTION_NEAR_CHANCE = 1.0D;
    private static final double SOURCE_EVOLUTION_FAR_CHANCE = 0.25D;
    private static final Pattern PET_NAME_PATTERN = Pattern.compile("[\\p{L}\\p{N} _\\-]{2,16}");

    public record EvolutionRequirement(
        int currentStage,
        int nextStage,
        int requiredLevel,
        int requiredBond,
        Map<Material, Integer> materials,
        int requiredQuests
    ) {
    }

    public record RenameResult(boolean success, String message) {
    }

    private final PlayerDataManager playerDataManager;
    private final BalanceConfig balanceConfig;
    private final PetAbilityService abilityService;
    private final PetVaultService petVaultService;
    private final PetArmorService petArmorService;
    private final PetDebugLogger debugLogger;
    private final WorldGuardCombatBridge worldGuardCombatBridge;
    private final LegendaryAllayVexSupport legendaryAllayVexSupport;
    private ProgressionAPI progressionAPI;
    private QuestManager questManager;
    private PetEggService petEggService;
    private PetMasterManager petMasterManager;
    private final Map<UUID, RuntimePet> activePets = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastPetXpLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastKillXpLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> killXpZoneCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> specialTraitCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> actionBarTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> actionBarVisibleUntilMillis = new ConcurrentHashMap<>();
    private final Map<String, Long> bondCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> xpCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> trainingCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> satietyDrainCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> socialPairCooldowns = new ConcurrentHashMap<>();
    private final Map<String, PetSocialCoordinatorSupport.CombatLink> combatLinks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> renameCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> satietyRegenCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mobTargetCoverCooldowns = new ConcurrentHashMap<>();
    private final JavaPlugin plugin = JavaPlugin.getProvidingPlugin(PetEngineManager.class);
    private int lastSocialScanTick;
    private int socialScanOffset;

    public PetEngineManager(PlayerDataManager playerDataManager, BalanceConfig balanceConfig, PetVaultService petVaultService, PetArmorService petArmorService, PetDebugLogger debugLogger) {
        this.playerDataManager = playerDataManager;
        this.balanceConfig = balanceConfig;
        this.petVaultService = petVaultService;
        this.petArmorService = petArmorService;
        this.debugLogger = debugLogger;
        this.abilityService = new PetAbilityService(balanceConfig, petVaultService);
        this.worldGuardCombatBridge = WorldGuardCombatBridge.create();
        this.legendaryAllayVexSupport = new LegendaryAllayVexSupport(balanceConfig, specialTraitCooldowns, debugLogger);
    }

    public void setProgressionAPI(ProgressionAPI progressionAPI) {
        this.progressionAPI = progressionAPI;
    }

    public void setQuestManager(QuestManager questManager) {
        this.questManager = questManager;
    }

    public void setPetEggService(PetEggService petEggService) {
        this.petEggService = petEggService;
    }

    public void setPetMasterManager(PetMasterManager petMasterManager) {
        this.petMasterManager = petMasterManager;
    }

    @Override
    public void enable() {
        // Pets are activated by the pet egg in the offhand slot. Old JSON activePetId
        // is intentionally not auto-spawned anymore.
    }

    @Override
    public void disable() {
        for (RuntimePet pet : activePets.values()) {
            pet.despawn();
        }
        activePets.clear();
    }

    public RuntimePet spawnPet(Player player, PetType type) {
        return spawnPet(player, type, rollRarity());
    }

    public RuntimePet spawnPet(Player player, PetType type, PetRarity rarity) {
        if (!balanceConfig.worldPetsEnabled(player.getWorld().getName())) {
            debugLogger.debug("world", "Blocked admin pet spawn for " + player.getName() + " in " + player.getWorld().getName());
            throw new IllegalArgumentException("Pets are disabled in world: " + player.getWorld().getName());
        }
        if (!balanceConfig.petEnabled(type)) {
            debugLogger.debug("spawn", "Blocked disabled pet type " + type.name() + " for " + player.getName());
            throw new IllegalArgumentException("Pet type is disabled in config: " + type.name());
        }
        removePet(player);

        OwnedPetData petData = new OwnedPetData(UUID.randomUUID(), player.getUniqueId(), type.name(), rarity.name());
        PlayerData playerData = playerDataManager.getOrLoad(player.getUniqueId());
        playerData.pets().add(petData);
        playerData.setActivePetId(petData.petId());

        RuntimePet runtimePet = new RuntimePet(petData, type);
        runtimePet.spawn(player, balanceConfig);
        if (runtimePet.entity().isEmpty()) {
            playerData.setActivePetId(null);
            throw new IllegalArgumentException(msg("pet.spawn.failed-location", "Could not spawn pet in this location."));
        }
        activePets.put(player.getUniqueId(), runtimePet);
        showActionBar(player, 2_500L);
        return runtimePet;
    }

    public Optional<RuntimePet> activatePet(Player player, OwnedPetData petData) {
        return activatePet(player, petData, null);
    }

    public Optional<RuntimePet> activatePet(Player player, OwnedPetData petData, Location summonLocation) {
        if (!balanceConfig.worldPetsEnabled(player.getWorld().getName())) {
            debugLogger.debug("world", "Blocked pet activation for " + player.getName() + " in " + player.getWorld().getName());
            throw new IllegalArgumentException("Pets are disabled in world: " + player.getWorld().getName());
        }
        UUID playerId = player.getUniqueId();
        PlayerData playerData = playerDataManager.getOrLoad(playerId);
        Optional<UUID> previousActivePetId = playerData.activePetId();
        OwnedPetData previousStoredPet = playerData.pets().stream()
            .filter(stored -> stored.petId().equals(petData.petId()))
            .findFirst()
            .map(PetEngineManager::snapshotPet)
            .orElse(null);
        petData.setOwnerId(player.getUniqueId());
        PetType type = PetType.parse(petData.petType()).orElseThrow(() -> new IllegalArgumentException("Unknown pet type: " + petData.petType()));
        if (!balanceConfig.petEnabled(type)) {
            debugLogger.debug("spawn", "Blocked disabled pet egg " + type.name() + " for " + player.getName());
            throw new IllegalArgumentException("Pet type is disabled in config: " + type.name());
        }
        RuntimePet existing = activePets.get(player.getUniqueId());
        if (existing != null && existing.data().petId().equals(petData.petId())) {
            existing.data().setOwnerId(player.getUniqueId());
            if (existing.entity().filter(entity -> entity.getWorld().equals(player.getWorld())).isEmpty()) {
                existing.spawn(player, balanceConfig, summonLocation, false);
            }
            existing.refreshName();
            rememberActivePet(player, existing.data());
            if (!playerDataManager.save(playerId)) {
                rollbackFailedActivation(playerData, petData.petId(), previousActivePetId, previousStoredPet);
                if (previousActivePetId.filter(id -> id.equals(petData.petId())).isEmpty()) {
                    despawnPet(player);
                }
                return Optional.empty();
            }
            return Optional.of(existing);
        }
        OwnedPetData storedPet = storedOrIncomingPetData(player, petData);
        RuntimePet runtimePet = new RuntimePet(storedPet, type);
        runtimePet.spawn(player, balanceConfig, summonLocation, false);
        if (runtimePet.entity().isEmpty()) {
            throw new IllegalArgumentException(msg("pet.spawn.failed-location", "Could not spawn pet in this location."));
        }
        rememberActivePet(player, runtimePet.data());
        if (!playerDataManager.save(playerId)) {
            rollbackFailedActivation(playerData, runtimePet.data().petId(), previousActivePetId, previousStoredPet);
            runtimePet.despawn();
            return Optional.empty();
        }
        despawnPet(player);
        activePets.put(playerId, runtimePet);
        showActionBar(player, 2_500L);
        return Optional.of(runtimePet);
    }

    public Optional<UUID> activePetId(Player player) {
        return playerDataManager.getOrLoad(player.getUniqueId()).activePetId();
    }

    public void removePet(Player player) {
        PlayerData playerData = playerDataManager.getOrLoad(player.getUniqueId());
        playerData.activePetId().ifPresent(activePetId -> playerData.pets().removeIf(pet -> pet.petId().equals(activePetId)));
        playerData.setActivePetId(null);
        despawnPet(player);
        playerDataManager.save(player.getUniqueId());
    }

    public void despawnPet(Player player) {
        RuntimePet runtimePet = activePets.remove(player.getUniqueId());
        if (runtimePet != null) {
            specialTraitCooldowns.remove(runtimePet.data().petId());
            satietyRegenCooldowns.remove(runtimePet.data().petId());
            runtimePet.despawn();
        }
        lastPetXpLocations.remove(player.getUniqueId());
        lastKillXpLocations.remove(player.getUniqueId());
        killXpZoneCounts.remove(player.getUniqueId());
        actionBarTicks.remove(player.getUniqueId());
        actionBarVisibleUntilMillis.remove(player.getUniqueId());
    }

    public Optional<RuntimePet> getPet(Player player) {
        return Optional.ofNullable(activePets.get(player.getUniqueId()));
    }

    public Optional<OwnedPetData> activePetData(Player player) {
        PlayerData playerData = playerDataManager.getOrLoad(player.getUniqueId());
        return playerData.activePetId()
            .flatMap(activeId -> playerData.pets().stream().filter(pet -> pet.petId().equals(activeId)).findFirst());
    }

    public Optional<OwnedPetData> storedPetData(Player player, UUID petId) {
        if (petId == null) {
            return Optional.empty();
        }
        return playerDataManager.getOrLoad(player.getUniqueId()).pets().stream()
            .filter(pet -> pet.petId().equals(petId))
            .findFirst();
    }

    public OwnedPetData restoreInactivePetData(Player player, OwnedPetData petData) {
        petData.setOwnerId(player.getUniqueId());
        PlayerData playerData = playerDataManager.getOrLoad(player.getUniqueId());
        OwnedPetData storedPet = playerData.pets().stream()
            .filter(stored -> stored.petId().equals(petData.petId()))
            .findFirst()
            .orElseGet(() -> {
                playerData.pets().add(petData);
                return petData;
            });
        if (storedPet != petData) {
            storedPet.copyProgressionFrom(petData);
        }
        if (playerData.activePetId().map(id -> id.equals(petData.petId())).orElse(false)) {
            playerData.setActivePetId(null);
        }
        playerDataManager.save(player.getUniqueId());
        return storedPet;
    }

    public boolean flushPetData(Player player) {
        return player != null && playerDataManager.save(player.getUniqueId());
    }

    public RenameResult renameActivePet(Player player, String rawName) {
        Optional<OwnedPetData> activeData = activePetData(player);
        if (activeData.isEmpty()) {
            return new RenameResult(false, GameText.text(
                "pet.rename.no-active",
                "Сначала призовите питомца или активируйте его ядро.",
                "Summon a pet or activate its core first."
            ));
        }
        String name = normalizePetName(rawName);
        if (name.isBlank() || !PET_NAME_PATTERN.matcher(name).matches()) {
            return new RenameResult(false, GameText.text(
                "pet.rename.invalid",
                "Имя: 2-16 символов, буквы/цифры/пробел/_/-.",
                "Name: 2-16 chars, letters/digits/space/_/-."
            ));
        }
        long now = System.currentTimeMillis();
        long nextAllowed = renameCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (nextAllowed > now && !activeData.get().petName().equals(name)) {
            long minutes = Math.max(1L, (nextAllowed - now) / 60_000L);
            return new RenameResult(false, GameText.text(
                "pet.rename.cooldown",
                "Переименовать питомца можно через {minutes} мин.",
                "You can rename the pet again in {minutes} min."
            ).replace("{minutes}", Long.toString(minutes)));
        }
        OwnedPetData data = activeData.get();
        data.setPetName(name);
        getPet(player)
            .filter(runtimePet -> runtimePet.data().petId().equals(data.petId()))
            .ifPresent(runtimePet -> {
                runtimePet.data().setPetName(name);
                runtimePet.refreshName();
                showActionBar(player, 2_000L);
            });
        renameCooldowns.put(player.getUniqueId(), now + PET_RENAME_COOLDOWN_MILLIS);
        playerDataManager.save(player.getUniqueId());
        return new RenameResult(true, GameText.text(
            "pet.rename.success",
            "Имя питомца изменено: {name}.",
            "Pet name changed: {name}."
        ).replace("{name}", name));
    }

    public boolean clearActivePet(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerData playerData = playerDataManager.getOrLoad(playerId);
        Optional<UUID> previousActivePetId = playerData.activePetId();
        if (previousActivePetId.isEmpty()) {
            despawnPet(player);
            return true;
        }
        playerData.setActivePetId(null);
        if (!playerDataManager.save(playerId)) {
            playerData.setActivePetId(previousActivePetId.get());
            return false;
        }
        despawnPet(player);
        return true;
    }

    public void replaceActivePetData(Player player, OwnedPetData petData) {
        playerDataManager.getOrLoad(player.getUniqueId()).pets().stream()
            .filter(stored -> stored.petId().equals(petData.petId()))
            .findFirst()
            .ifPresent(stored -> stored.copyProgressionFrom(petData));
        getPet(player)
            .filter(runtimePet -> runtimePet.data().petId().equals(petData.petId()))
            .ifPresent(runtimePet -> {
                runtimePet.data().copyProgressionFrom(petData);
                runtimePet.refreshName();
            });
    }

    public String debugPet(Player player) {
        return getPet(player)
            .map(pet -> pet.debugLine(player, balanceConfig))
            .orElse(msg("debugpet.no-runtime", "No runtime pet for {player}. Check the active core and summon state.", "player", player.getName()));
    }

    private String msg(String key, String fallback, Object... replacements) {
        return this.balanceConfig.message(key, fallback, replacements);
    }

    private String normalizePetName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName
            .replace('§', ' ')
            .replace('&', ' ')
            .replaceAll("\\p{Cntrl}", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    public void updatePet(RuntimePet pet) {
        Player owner = ownerFor(pet);
        if (owner == null || !owner.isOnline()) {
            deactivatePetWithoutOwner(pet, "missing-owner");
            return;
        }
        if (!balanceConfig.worldPetsEnabled(owner.getWorld().getName())) {
            deactivatePet(owner, pet, "update-world-disabled");
            return;
        }
        try {
            if (isPetWorldCombatSuppressed(owner) && pet.state() == PetState.ATTACK) {
                pet.clearCombat();
            }
            pet.update(owner, balanceConfig, abilityService, debugLogger);
            pet.refreshName();
            if (shouldShowActionBar(owner, pet)) {
                sendActionBar(owner, pet);
            }
        } catch (Throwable throwable) {
            debugLogger.errorRateLimited(
                "pet:update:" + pet.data().petId(),
                "pet-runtime",
                "Update failed for owner=" + owner.getName()
                    + " pet=" + pet.data().petId()
                    + " type=" + pet.type().name()
                    + " state=" + pet.state(),
                throwable,
                15_000L
            );
            deactivatePet(owner, pet, "update-exception");
        }
    }

    public void updateAll() {
        for (RuntimePet pet : activePets.values()) {
            updatePet(pet);
        }
        int tick = Bukkit.getCurrentTick();
        if (tick - lastSocialScanTick >= 20) {
            lastSocialScanTick = tick;
            int petCount = activePets.size();
            int socialPairBudget = PetSocialCoordinatorSupport.socialPairBudget(petCount);
            if (petCount > 1) {
                socialScanOffset = Math.floorMod(socialScanOffset + Math.max(1, petCount / 7), petCount);
            } else {
                socialScanOffset = 0;
            }
            PetSocialCoordinatorSupport.pruneTransientState(socialPairCooldowns, combatLinks, System.currentTimeMillis());
            PetSocialCoordinatorSupport.runSocialInteractions(
                activePets,
                socialPairCooldowns,
                System::currentTimeMillis,
                Bukkit::getPlayer,
                this::pairInCombat,
                (first, second) -> roll(socialChance(first, second)),
                (owner, ignoredPet) -> showActionBar(owner, 2_500L),
                socialScanOffset,
                socialPairBudget
            );
        }
    }

    public void spawnActivePet(Player player) {
        PlayerData playerData = playerDataManager.getOrLoad(player.getUniqueId());
        playerData.activePetId()
            .flatMap(activeId -> playerData.pets().stream().filter(pet -> pet.petId().equals(activeId)).findFirst())
            .flatMap(this::runtimeFromData)
            .ifPresent(runtimePet -> {
                runtimePet.spawn(player, balanceConfig);
                if (runtimePet.entity().isEmpty()) {
                    return;
                }
                activePets.put(player.getUniqueId(), runtimePet);
            });
    }

    public void onOwnerDamaged(Player owner, Entity attacker) {
        if (attacker == null) {
            debugLogger.warnRateLimited(
                "pet:combat:damaged:" + owner.getUniqueId() + ":null",
                "pet-combat",
                "Ignored owner-damaged event for " + owner.getName() + ": attacker=null",
                2_000L
            );
            return;
        }
        if (isPetWorldCombatSuppressed(owner)) {
            debugLogger.warnRateLimited(
                "pet:combat:damaged:" + owner.getUniqueId() + ":suppressed",
                "pet-combat",
                "Suppressed owner-damaged combat for " + owner.getName()
                    + " world=" + owner.getWorld().getName()
                    + " reason=" + combatSuppressionReason(owner, false)
                    + " attacker=" + attacker.getType(),
                2_000L
            );
            return;
        }
        if (attacker instanceof Player && isPetPvpCombatSuppressed(owner)) {
            debugLogger.warnRateLimited(
                "pet:combat:damaged:" + owner.getUniqueId() + ":pvp-suppressed",
                "pet-combat",
                "Suppressed owner-damaged PvP combat for " + owner.getName()
                    + " world=" + owner.getWorld().getName()
                    + " reason=" + combatSuppressionReason(owner, true)
                    + " attacker=" + attacker.getType(),
                2_000L
            );
            return;
        }
        if (attacker instanceof Player playerAttacker && !playerAttacker.getUniqueId().equals(owner.getUniqueId())) {
            recordPlayerCombat(playerAttacker, owner);
        }
        showActionBar(owner, 2_000L);
        getPet(owner).ifPresent(pet -> {
            debugLogger.warnRateLimited(
                "pet:combat:damaged:" + owner.getUniqueId() + ":event",
                "pet-combat",
                "Owner-damaged event owner=" + owner.getName()
                    + " world=" + owner.getWorld().getName()
                    + " defense=" + pet.data().defenseEnabled()
                    + " attacker=" + attacker.getType()
                    + " before=" + pet.combatSnapshot(),
                2_000L
            );
            if (pet.data().defenseEnabled()) {
                legendaryAllayVexSupport.tryTrigger(owner, pet, attacker);
                abilityService.tryLegendaryAggression(owner, pet, attacker);
                boolean started = pet.attack(attacker);
                if (started) {
                    grantCombatBond(pet);
                }
                debugLogger.warnRateLimited(
                    "pet:combat:damaged:" + owner.getUniqueId() + ":attack",
                    "pet-combat",
                    "Owner-damaged attack owner=" + owner.getName()
                        + " started=" + started
                        + " after=" + pet.combatSnapshot(),
                    2_000L
                );
            }
        });
    }

    public void onOwnerDamageIncoming(Player owner, EntityDamageEvent event) {
        if (!balanceConfig.worldPetBuffsEnabled(owner.getWorld().getName())) {
            return;
        }
        showActionBar(owner, 2_000L);
        getPet(owner)
            .filter(pet -> pet.data().defenseEnabled())
            .ifPresent(pet -> {
                abilityService.applyDefensiveReaction(owner, pet, event);
                abilityService.applyLegendaryOwnerDefense(owner, pet, event);
            });
    }

    public void onOwnerAttacked(Player owner, Entity target) {
        if (isPetWorldCombatSuppressed(owner)) {
            debugLogger.warnRateLimited(
                "pet:combat:attacked:" + owner.getUniqueId() + ":suppressed",
                "pet-combat",
                "Suppressed owner-attacked combat for " + owner.getName()
                    + " world=" + owner.getWorld().getName()
                    + " reason=" + combatSuppressionReason(owner, false)
                    + " target=" + (target == null ? "null" : target.getType()),
                2_000L
            );
            return;
        }
        if (target instanceof Player && isPetPvpCombatSuppressed(owner)) {
            debugLogger.warnRateLimited(
                "pet:combat:attacked:" + owner.getUniqueId() + ":pvp-suppressed",
                "pet-combat",
                "Suppressed owner-attacked PvP combat for " + owner.getName()
                    + " world=" + owner.getWorld().getName()
                    + " reason=" + combatSuppressionReason(owner, true)
                    + " target=" + target.getType(),
                2_000L
            );
            return;
        }
        if (target instanceof Player victim && !victim.getUniqueId().equals(owner.getUniqueId())) {
            recordPlayerCombat(owner, victim);
        }
        showActionBar(owner, 2_000L);
        getPet(owner).ifPresent(pet -> {
            debugLogger.warnRateLimited(
                "pet:combat:attacked:" + owner.getUniqueId() + ":event",
                "pet-combat",
                "Owner-attacked event owner=" + owner.getName()
                    + " world=" + owner.getWorld().getName()
                    + " defense=" + pet.data().defenseEnabled()
                    + " target=" + (target == null ? "null" : target.getType())
                    + " before=" + pet.combatSnapshot(),
                2_000L
            );
            if (pet.data().defenseEnabled()) {
                legendaryAllayVexSupport.tryTrigger(owner, pet, target);
                abilityService.tryLegendaryAggression(owner, pet, target);
                boolean started = pet.attack(target);
                if (started) {
                    grantCombatBond(pet);
                }
                debugLogger.warnRateLimited(
                    "pet:combat:attacked:" + owner.getUniqueId() + ":attack",
                    "pet-combat",
                    "Owner-attacked attack owner=" + owner.getName()
                        + " started=" + started
                        + " after=" + pet.combatSnapshot(),
                    2_000L
                );
            }
        });
    }

    public void requestFollowUpdate(Player player) {
        getPet(player).ifPresent(pet -> {
            if (pet.state() == PetState.IDLE) {
                pet.setState(PetState.FOLLOW);
                showActionBar(player, 2_000L);
            }
        });
    }

    public void grantActivityXp() {
        if (progressionAPI == null) {
            return;
        }
        for (RuntimePet pet : activePets.values()) {
            Player owner = ownerFor(pet);
            if (owner == null || !owner.isOnline()) {
                deactivatePetWithoutOwner(pet, "activity-missing-owner");
                continue;
            }
            if (!balanceConfig.worldPetsEnabled(owner.getWorld().getName())) {
                deactivatePet(owner, pet, "activity-world-disabled");
                continue;
            }
            try {
                applyPassiveSatietyDrain(owner, pet);
                grantNearbyTimeXp(owner, pet);
                if (movedEnoughForPetXp(owner)) {
                    grantActivityXpReward(owner, pet);
                    grantBond(pet, balanceConfig.bondNearbyGain(), "nearby", balanceConfig.bondNearbyCooldownMillis());
                }
                abilityService.runUtilityTick(owner, pet);
                regeneratePetFromSatiety(owner, pet);
                if (pet.data().defenseEnabled() && pet.state() != PetState.ATTACK && roll(autoAggroChance(pet))) {
                    LivingEntity enemy = abilityService.nearestEnemy(owner);
                    if (enemy != null) {
                        legendaryAllayVexSupport.tryTrigger(owner, pet, enemy);
                        abilityService.tryLegendaryAggression(owner, pet, enemy);
                        boolean started = pet.attack(enemy);
                        debugLogger.warnRateLimited(
                            "pet:combat:auto:" + owner.getUniqueId(),
                            "pet-combat",
                            "Auto-aggro owner=" + owner.getName()
                                + " started=" + started
                                + " target=" + enemy.getType()
                                + " snapshot=" + pet.combatSnapshot(),
                            3_000L
                        );
                    }
                }
                pet.refreshName();
            } catch (Throwable throwable) {
                debugLogger.errorRateLimited(
                    "pet:activity:" + pet.data().petId(),
                    "pet-runtime",
                    "Activity tick failed for owner=" + owner.getName()
                        + " pet=" + pet.data().petId()
                        + " type=" + pet.type().name()
                        + " state=" + pet.state(),
                    throwable,
                    15_000L
                );
                deactivatePet(owner, pet, "activity-exception");
            }
        }
    }

    private void grantActivityXpReward(Player owner, RuntimePet pet) {
        grantProgressionXp(owner, pet, balanceConfig.activityXp(), "activity-xp", balanceConfig.activityXpCooldownMillis());
    }

    private void grantNearbyTimeXp(Player owner, RuntimePet pet) {
        grantProgressionXp(owner, pet, balanceConfig.nearbyTimeXp(), "nearby-time-xp", balanceConfig.nearbyTimeXpCooldownMillis());
    }

    private void grantProgressionXp(Player owner, RuntimePet pet, long amount, String reason, long cooldownMillis) {
        if (progressionAPI == null || amount <= 0L || pet.data().isDown()) {
            return;
        }
        String key = pet.data().petId() + ":" + reason;
        long now = System.currentTimeMillis();
        if (cooldownMillis > 0L && xpCooldowns.getOrDefault(key, 0L) > now) {
            return;
        }
        var result = progressionAPI.addXp(pet.data(), amount);
        if (result.xpAdded() <= 0L) {
            return;
        }
        if (cooldownMillis > 0L) {
            xpCooldowns.put(key, now + cooldownMillis);
        }
        pet.refreshName();
        syncPetCoreInInventory(owner, pet, false);
        showActionBar(owner, 1_500L);
    }

    private void applyPassiveSatietyDrain(Player owner, RuntimePet pet) {
        double drainAmount = balanceConfig.satietyPassiveDrainAmount();
        if (drainAmount <= 0.0D) {
            return;
        }
        OwnedPetData data = pet.data();
        if (data.isDown() || data.isStarving()) {
            return;
        }
        String key = data.petId() + ":passive-drain";
        long now = System.currentTimeMillis();
        long cooldownMillis = balanceConfig.satietyPassiveDrainCooldownMillis();
        if (cooldownMillis > 0L && satietyDrainCooldowns.getOrDefault(key, 0L) > now) {
            return;
        }
        double before = data.satiety();
        data.adjustSatiety(-drainAmount);
        if (data.satiety() >= before) {
            return;
        }
        if (cooldownMillis > 0L) {
            satietyDrainCooldowns.put(key, now + cooldownMillis);
        }
        pet.refreshName();
        syncPetCoreInInventory(owner, pet, false);
    }

    public TrainResult trainPet(Player player) {
        if (player == null) {
            return TrainResult.failure(msg("train.player-only", "Only players can train a pet."));
        }
        Optional<RuntimePet> petOptional = getPet(player);
        if (petOptional.isEmpty()) {
            return TrainResult.failure(msg("train.no-pet", "Summon your pet first."));
        }
        RuntimePet pet = petOptional.get();
        if (pet.data().isDown()) {
            return TrainResult.failure(msg("train.pet-down", "The pet is resting and cannot train."));
        }
        LivingEntity entity = pet.entity().orElse(null);
        if (entity == null || !entity.isValid()) {
            return TrainResult.failure(msg("train.no-entity", "The pet is not nearby."));
        }
        if (!player.getWorld().equals(entity.getWorld())
            || player.getLocation().distanceSquared(entity.getLocation()) > balanceConfig.trainingMaxDistance() * balanceConfig.trainingMaxDistance()) {
            return TrainResult.failure(msg("train.too-far", "Stay closer to your pet to train."));
        }
        String cooldownKey = pet.data().petId() + ":train";
        long now = System.currentTimeMillis();
        long readyAt = trainingCooldowns.getOrDefault(cooldownKey, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (readyAt - now + 999L) / 1_000L);
            return TrainResult.failure(msg("train.cooldown", "Training again in {seconds}s.", "seconds", seconds));
        }
        if (progressionAPI == null || balanceConfig.trainingXp() <= 0L) {
            return TrainResult.failure(msg("train.unavailable", "Training is unavailable right now."));
        }
        var result = progressionAPI.addXp(pet.data(), balanceConfig.trainingXp());
        if (result.xpAdded() <= 0L) {
            return TrainResult.failure(msg("train.no-xp", "The pet is too hungry or already at the level cap."));
        }
        trainingCooldowns.put(cooldownKey, now + balanceConfig.trainingCooldownMillis());
        pet.refreshName();
        syncPetCoreInInventory(player, pet, false);
        entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation().add(0.0D, 0.6D, 0.0D), 8, 0.35D, 0.25D, 0.35D, 0.01D);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.2F);
        showActionBar(player, 2_000L);
        return TrainResult.success(msg("train.success", "Training complete. The pet gained experience."));
    }

    public record TrainResult(boolean success, String message) {
        public static TrainResult success(String message) {
            return new TrainResult(true, message);
        }

        public static TrainResult failure(String message) {
            return new TrainResult(false, message);
        }
    }

    private void regeneratePetFromSatiety(Player owner, RuntimePet pet) {
        if (owner == null || pet == null) {
            return;
        }
        long tick = Bukkit.getCurrentTick();
        long nextRegenTick = satietyRegenCooldowns.getOrDefault(pet.data().petId(), 0L);
        if (tick < nextRegenTick) {
            return;
        }
        satietyRegenCooldowns.put(pet.data().petId(), tick + 40L);
        OwnedPetData data = pet.data();
        if (data.isDown() || data.health() >= data.maxHealth() || data.satiety() <= 1.0D) {
            return;
        }
        double satietyCost = Math.min(0.08D, data.satiety() - 1.0D);
        double healthGain = Math.min(data.maxHealth() - data.health(), satietyCost * 7.5D);
        if (satietyCost <= 0.0D) {
            return;
        }
        data.adjustHealth(healthGain);
        data.adjustSatiety(-satietyCost);
        pet.refreshName();
        syncPetCoreInInventory(owner, pet, false);
    }

    public Optional<FeedResult> feedPet(Player player, Entity entity, org.bukkit.Material material) {
        if (progressionAPI == null) {
            return Optional.empty();
        }
        return getPet(player)
            .filter(pet -> pet.isEntity(entity))
            .map(pet -> {
                FeedResult result = progressionAPI.feed(pet.data(), material);
                if (result.evolutionResult().success()) {
                    finishEvolution(player, pet);
                }
                if (result.accepted()) {
                    if (result.feedType() == FeedType.RARE_RESOURCE) {
                        grantBond(pet, balanceConfig.bondRareFoodGain(), "rare-food", balanceConfig.bondRareFoodCooldownMillis());
                    } else if (result.feedType() == FeedType.FOOD) {
                        grantBond(pet, balanceConfig.bondFoodGain(), "food", balanceConfig.bondFoodCooldownMillis());
                    }
                }
                pet.refreshName();
                if (result.accepted()) {
                    showActionBar(player, 2_000L);
                    if (result.feedType() == FeedType.FOOD || result.feedType() == FeedType.RARE_RESOURCE) {
                        playFeedingParticles(pet);
                    }
                }
                return result;
            });
    }

    private void playFeedingParticles(RuntimePet pet) {
        pet.entity().ifPresent(entity -> {
            Location location = entity.getLocation().add(0.0D, 0.65D, 0.0D);
            entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 6, 0.25D, 0.25D, 0.25D, 0.0D);
        });
    }

    public Optional<RuntimePet> getPetByEntity(Entity entity) {
        return activePets.values().stream()
            .filter(pet -> pet.isEntity(entity))
            .findFirst();
    }

    public Optional<LivingEntity> tryPetCoverTarget(Player owner, Monster monster) {
        if (owner == null || monster == null || monster.isDead() || isPetWorldCombatSuppressed(owner)) {
            return Optional.empty();
        }
        Optional<RuntimePet> coverPet = getPet(owner)
            .filter(pet -> pet.data().defenseEnabled());
        if (coverPet.isEmpty()) {
            return Optional.empty();
        }
        LivingEntity currentTarget = monster.getTarget();
        if (currentTarget != null
            && !currentTarget.getUniqueId().equals(owner.getUniqueId())
            && !coverPet.get().isEntity(currentTarget)) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        long readyAt = mobTargetCoverCooldowns.getOrDefault(monster.getUniqueId(), 0L);
        if (readyAt > now) {
            return Optional.empty();
        }
        mobTargetCoverCooldowns.put(monster.getUniqueId(), now + 3_500L);
        return coverPet
            .filter(pet -> roll(PetCombatChanceSupport.coverTargetChance(pet)))
            .flatMap(pet -> pet.entity()
                .filter(petEntity -> petEntity.getWorld().equals(monster.getWorld()))
                .filter(petEntity -> petEntity.getLocation().distanceSquared(owner.getLocation()) <= Math.pow(balanceConfig.aggroRadius() + 2.0D, 2.0D))
                .map(petEntity -> {
                    legendaryAllayVexSupport.tryTrigger(owner, pet, monster);
                    abilityService.tryLegendaryAggression(owner, pet, monster);
                    boolean started = pet.attack(monster);
                    if (started) {
                        grantCombatBond(pet);
                    }
                    return petEntity;
                }));
    }

    public boolean onPetDamagedByPlayer(Player attacker, Entity entity) {
        Optional<RuntimePet> targetPetOptional = getPetByEntity(entity);
        if (targetPetOptional.isEmpty()) {
            return false;
        }

        RuntimePet targetPet = targetPetOptional.get();
        if (targetPet.data().ownerId().equals(attacker.getUniqueId())) {
            targetPet.showHint(GameText.text("pet.combat.own-pet", "Осторожно", "Careful"), 1_400L);
            showActionBar(attacker, 1_600L);
            return true;
        }

        Player defenderOwner = Bukkit.getPlayer(targetPet.data().ownerId());
        if (defenderOwner == null || !defenderOwner.isOnline()) {
            return true;
        }

        if (isPetPvpCombatSuppressed(attacker) || isPetPvpCombatSuppressed(defenderOwner)) {
            targetPet.showHint(GameText.text("pet.combat.disabled-hint", "Не дерётся здесь", "Does not fight here"), 1_600L);
            showActionBar(attacker, 1_600L);
            showActionBar(defenderOwner, 1_600L);
            return true;
        }

        recordPlayerCombat(attacker, defenderOwner);
        boolean duelReady = registerPetProvocation(attacker, defenderOwner);
        targetPet.showHint(
            duelReady
                ? GameText.text("pet.combat.duel-ready-hint", "Вступает в схватку", "Joining the spar")
                : GameText.text("pet.combat.provoked-hint", "Насторожился", "Alerted"),
            duelReady ? 2_000L : 1_800L
        );
        showActionBar(attacker, 2_000L);
        showActionBar(defenderOwner, 2_000L);

        if (!duelReady) {
            return true;
        }

        Optional<RuntimePet> attackerPetOptional = getPet(attacker).filter(pet -> pet.data().defenseEnabled());
        Optional<RuntimePet> defenderPetOptional = getPet(defenderOwner).filter(pet -> pet.data().defenseEnabled());
        if (attackerPetOptional.isEmpty() || defenderPetOptional.isEmpty()) {
            return true;
        }

        RuntimePet attackerPet = attackerPetOptional.get();
        RuntimePet defenderPet = defenderPetOptional.get();
        if (!petsCanSpar(attackerPet, defenderPet)) {
            return true;
        }

        attackerPet.spar(defenderPet);
        defenderPet.spar(attackerPet);
        String sparHint = GameText.text("pet.combat.spar-hint", "Сцепились", "Sparring");
        attackerPet.showHint(sparHint, 2_000L);
        defenderPet.showHint(sparHint, 2_000L);
        showActionBar(attacker, 2_500L);
        showActionBar(defenderOwner, 2_500L);
        return true;
    }

    public boolean onPetDamagedByPlayer(Player attacker, Entity entity, double rawDamage, EntityDamageEvent.DamageCause cause) {
        Optional<RuntimePet> targetPetOptional = getPetByEntity(entity);
        if (targetPetOptional.isEmpty()) {
            return false;
        }
        RuntimePet targetPet = targetPetOptional.get();
        Player owner = Bukkit.getPlayer(targetPet.data().ownerId());
        if (owner == null || !owner.isOnline()) {
            return true;
        }
        if (targetPet.data().ownerId().equals(attacker.getUniqueId())
            || isPetPvpCombatSuppressed(attacker)
            || isPetPvpCombatSuppressed(owner)) {
            onPetDamagedByPlayer(attacker, entity);
            return true;
        }
        onPetDamagedByPlayer(attacker, entity);
        applyPetIncomingDamage(owner, targetPet, attacker, rawDamage, cause);
        return true;
    }

    public boolean onPetDamagedByEntity(Entity damager, Entity entity, double rawDamage, EntityDamageEvent.DamageCause cause) {
        Optional<RuntimePet> targetPetOptional = getPetByEntity(entity);
        if (targetPetOptional.isEmpty()) {
            return false;
        }
        RuntimePet targetPet = targetPetOptional.get();
        Player owner = Bukkit.getPlayer(targetPet.data().ownerId());
        if (owner == null || !owner.isOnline()) {
            return true;
        }
        applyPetIncomingDamage(owner, targetPet, damager, rawDamage, cause);
        return true;
    }

    public void applyPetDeathPenalty(Player owner, RuntimePet pet) {
        long now = System.currentTimeMillis();
        int penaltyMinutes = balanceConfig.deathPenaltyMinutes(pet.data().evolutionStage());
        pet.data().setInactiveUntilMillis(now + penaltyMinutes * 60_000L);
        adjustBond(pet, -balanceConfig.bondDeathLoss());
        int durabilityBefore = pet.data().durability();
        pet.data().setDurability(pet.data().durability() - 1);
        if (pet.data().satiety() <= 1.0D) {
            pet.data().setDurability(pet.data().durability() - 1);
        }
        int durabilityLoss = Math.max(0, durabilityBefore - pet.data().durability());
        if (durabilityLoss > 0) {
            pet.data().setHealth(recoveryHealth(pet.data()));
            pet.data().setSatiety(recoverySatiety());
            pet.refreshName();
        }
        if (owner != null) {
            syncPetCoreInInventory(owner, pet, pet.data().durability() <= 0);
            showPetDeathCoreNotice(owner, pet.data().durability(), balanceConfig.eggMaxDurability());
        }
    }

    private void syncPetCoreInInventory(Player owner, RuntimePet pet, boolean removeBrokenCore) {
        if (petEggService == null || owner == null || pet == null) {
            return;
        }
        boolean updated = false;
        for (int slot = 0; slot < owner.getInventory().getSize(); slot++) {
            ItemStack item = owner.getInventory().getItem(slot);
            Optional<OwnedPetData> itemPet = petEggService.readEgg(item);
            if (itemPet.isEmpty() || !itemPet.get().petId().equals(pet.data().petId())) {
                continue;
            }
            if (removeBrokenCore) {
                owner.getInventory().setItem(slot, null);
            } else if (petEggService.isEmptyEgg(item)) {
                owner.getInventory().setItem(slot, petEggService.writeEmptyEgg(item, pet.data()));
            } else {
                owner.getInventory().setItem(slot, petEggService.writeEgg(item, pet.data()));
            }
            updated = true;
        }
        if (removeBrokenCore) {
            playerDataManager.getOrLoad(owner.getUniqueId()).setActivePetId(null);
        }
        if (updated) {
            playerDataManager.save(owner.getUniqueId());
        }
    }

    private void showPetDeathCoreNotice(Player owner, int durability, int maxDurability) {
        int safeDurability = Math.max(0, durability);
        int safeMax = Math.max(1, maxDurability);
        long durationMillis = safeDurability <= 0 ? 7_000L : 5_000L;
        String message = petDeathCoreNotice(safeDurability, safeMax);
        showActionBar(owner, durationMillis);
        owner.sendActionBar(Component.text(message));
        if (safeDurability <= 0) {
            owner.playSound(owner.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.9F, 0.55F);
            owner.playSound(owner.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.35F, 0.75F);
        }
        int repeats = Math.max(1, (int) Math.ceil(durationMillis / 1_000.0D) - 1);
        for (int index = 1; index <= repeats; index++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (owner.isOnline()) {
                    owner.sendActionBar(Component.text(message));
                }
            }, index * 20L);
        }
    }

    private String petDeathCoreNotice(int durability, int maxDurability) {
        if (durability <= 0) {
            return GameText.text(
                "pet.death.core.broken",
                "Ядро рассыпалось. Питомец реально погиб.",
                "The core shattered. The pet is truly gone."
            );
        }
        return switch (Math.min(durability, maxDurability)) {
            case 1 -> GameText.text(
                "pet.death.core.status.1",
                "Ваш питомец восстанавливается. В ядре осталась последняя искра.",
                "Your pet is recovering. The core has one last spark."
            );
            case 2 -> GameText.text(
                "pet.death.core.status.2",
                "Ваш питомец восстанавливается. Ядро на грани разрушения.",
                "Your pet is recovering. The core is on the brink."
            );
            case 3 -> GameText.text(
                "pet.death.core.status.3",
                "Ваш питомец восстанавливается. Ядро сильно ослабло.",
                "Your pet is recovering. The core is badly weakened."
            );
            case 4 -> GameText.text(
                "pet.death.core.status.4",
                "Ваш питомец восстанавливается. Ядро мерцает нестабильно.",
                "Your pet is recovering. The core flickers unstably."
            );
            case 5 -> GameText.text(
                "pet.death.core.status.5",
                "Ваш питомец восстанавливается. Ядро повреждено, но стабильно.",
                "Your pet is recovering. The core is damaged, but stable."
            );
            case 6 -> GameText.text(
                "pet.death.core.status.6",
                "Ваш питомец восстанавливается. Ядро получило первую трещину.",
                "Your pet is recovering. The core has its first crack."
            );
            default -> GameText.text(
                "pet.death.core.status.ok",
                "Ваш питомец восстанавливается. Ядро выдержало удар.",
                "Your pet is recovering. The core held the blow."
            );
        };
    }

    private double recoveryHealth(OwnedPetData data) {
        return Math.max(1.0D, data.maxHealth() * 0.9D);
    }

    private double recoverySatiety() {
        return Math.max(1.0D, balanceConfig.eggMaxSatiety() * 0.9D);
    }

    public void onPetKillMob(Player owner, RuntimePet pet, Entity killed) {
        if (owner == null || pet == null) {
            return;
        }
        if (questManager != null && killed != null && !(killed instanceof Player)) {
            questManager.record(owner.getUniqueId(), dev.li2fox.vibepetcore.quest.QuestType.KILL_MOB, killed.getType().name(), owner, pet.data().petId());
        }
        if (movedEnoughForKillXp(owner)) {
            grantKillReward(owner, pet);
        }
        abilityService.onPetKillMob(owner, pet, killed);
        pet.data().adjustSatiety(-0.5D);
    }

    public void onOwnerKillMob(Player owner, Entity killed) {
        if (owner == null || killed == null || killed instanceof Player) {
            return;
        }
        if (!movedEnoughForKillXp(owner)) {
            return;
        }
        getPet(owner).ifPresent(pet -> grantKillReward(owner, pet));
    }

    private void applyPetIncomingDamage(Player owner, RuntimePet pet, Entity source, double rawDamage, EntityDamageEvent.DamageCause cause) {
        if (pet == null || rawDamage <= 0.0D) {
            return;
        }
        double multiplier = balanceConfig.petIncomingDamageMultiplier(pet.data().evolutionStage());
        double armorMultiplier = petVaultService.equippedArmor(pet)
            .map(armor -> petArmorService.damageMultiplier(armor, cause))
            .orElse(1.0D);
        multiplier *= armorMultiplier;
        double adjustedDamage = Math.max(0.1D, rawDamage * multiplier);
        double before = pet.data().health();
        double foodBefore = pet.data().satiety();
        pet.data().adjustHealth(-adjustedDamage);
        pet.data().adjustSatiety(-petDamageSatietyLoss(adjustedDamage));
        pet.refreshName();
        if (balanceConfig.debugPetDamageEnabled()) {
            debugLogger.debugRateLimited(
                "pet:damage:" + pet.data().petId(),
                "pet-damage",
                "Урон по пету owner=" + owner.getName()
                    + " pet=" + pet.data().petId()
                    + " source=" + (source == null ? "null" : source.getType())
                    + " stage=" + pet.data().evolutionStage()
                    + " raw=" + rawDamage
                    + " multiplier=" + multiplier
                    + " applied=" + adjustedDamage
                    + " hpBefore=" + before
                    + " hpAfter=" + pet.data().health()
                    + " foodBefore=" + foodBefore
                    + " foodAfter=" + pet.data().satiety(),
                1_000L
            );
        }
        showActionBar(owner, 2_000L);
        debugLogger.warnRateLimited(
            "pet:damage:" + pet.data().petId(),
            "pet-combat",
            "Pet damage owner=" + owner.getName()
                + " source=" + (source == null ? "null" : source.getType())
                + " raw=" + rawDamage
                + " multiplier=" + multiplier
                + " applied=" + adjustedDamage
                + " healthBefore=" + before
                + " healthAfter=" + pet.data().health()
                + " foodBefore=" + foodBefore
                + " foodAfter=" + pet.data().satiety(),
            2_000L
        );
        if (pet.data().defenseEnabled() && source instanceof LivingEntity livingSource && !pet.isSparringAttack()) {
            boolean started = pet.attack(livingSource);
            if (started) {
                grantCombatBond(pet);
            }
            if (balanceConfig.debugPetDamageEnabled()) {
                debugLogger.debugRateLimited(
                    "pet:damage:retaliate:" + pet.data().petId(),
                    "pet-damage",
                    "Pet retaliated owner=" + owner.getName()
                        + " source=" + livingSource.getType()
                        + " started=" + started
                        + " snapshot=" + pet.combatSnapshot(),
                    1_500L
                );
            }
        }
        if (pet.data().isDown()) {
            if (balanceConfig.debugPetDamageEnabled()) {
                debugLogger.debug("pet-damage", "Питомец ушёл в KO owner=" + owner.getName()
                    + " pet=" + pet.data().petId()
                    + " stage=" + pet.data().evolutionStage()
                    + " durability=" + pet.data().durability()
                    + " food=" + pet.data().satiety());
            }
            applyPetDeathPenalty(owner, pet);
            despawnPet(owner);
        }
    }

    private double petDamageSatietyLoss(double adjustedDamage) {
        return Math.max(0.03D, Math.min(0.08D, adjustedDamage * 0.015D));
    }

    public boolean openPetVault(Player player, Entity entity) {
        Optional<RuntimePet> pet = getPet(player).filter(runtimePet -> runtimePet.isEntity(entity));
        pet.ifPresent(runtimePet -> petVaultService.open(player, runtimePet));
        return pet.isPresent();
    }

    public boolean isOwnPetEntity(Player player, Entity entity) {
        return getPet(player).filter(pet -> pet.isEntity(entity)).isPresent();
    }

    public boolean nudgeOwnPet(Player player, Entity entity) {
        Optional<RuntimePet> petOptional = getPet(player).filter(pet -> pet.isEntity(entity));
        if (petOptional.isEmpty()) {
            return false;
        }
        RuntimePet pet = petOptional.get();
        boolean resting = PetCompanionComfortSupport.requestRest(player, pet);
        if (!resting) {
            pet.entity().ifPresent(petEntity -> {
                PetCollisionSupport.applyOwnerExemption(player, petEntity);
                pet.showHint(GameText.text("pet.control.nudge", "Аккуратно", "Easy"), 900L);
            });
        }
        showActionBar(player, 1_000L);
        return true;
    }

    public boolean toggleWaitMode(Player player, Entity entity) {
        return PetPlayerControlSupport.toggleWaitMode(player, entity, getPet(player).filter(runtimePet -> runtimePet.isEntity(entity)));
    }

    public boolean callPet(Player player, Entity entity) {
        return PetPlayerControlSupport.callPet(player, getPet(player).filter(runtimePet -> runtimePet.isEntity(entity)), this::showActionBar);
    }

    public boolean callPet(Player player) {
        return PetPlayerControlSupport.callPet(player, getPet(player), this::showActionBar);
    }

    public boolean setWaitMode(Player player, boolean waiting) {
        return PetPlayerControlSupport.setWaitMode(player, getPet(player), waiting, this::showActionBar);
    }

    public boolean openActivePetVault(Player player) {
        Optional<RuntimePet> pet = getPet(player);
        pet.ifPresent(runtimePet -> petVaultService.open(player, runtimePet));
        return pet.isPresent();
    }

    public boolean toggleAutoloot(Player player) {
        return PetPlayerControlSupport.toggleAutoloot(player, getPet(player), balanceConfig, this::showActionBar);
    }

    public boolean toggleDefense(Player player) {
        return PetPlayerControlSupport.toggleDefense(player, getPet(player), this::showActionBar);
    }

    public boolean togglePassiveEffect(Player player, PotionEffectType effectType) {
        Optional<RuntimePet> pet = getPet(player);
        if (pet.isEmpty()) {
            player.sendMessage(GameText.text("pet.effect-toggle.no-active", "Нет активного питомца.", "No active pet."));
            return false;
        }
        String effectKey = effectType.getKey().getKey().toLowerCase(java.util.Locale.ROOT);
        boolean enabled = pet.get().data().togglePassiveEffect(effectKey);
        if (!enabled) {
            player.removePotionEffect(effectType);
        }
        showActionBar(player, 2_000L);
        player.sendMessage(GameText.text(
            enabled ? "pet.effect-toggle.enabled" : "pet.effect-toggle.disabled",
            enabled ? "Автобаф включён: {effect}." : "Автобаф выключен: {effect}.",
            enabled ? "Auto-buff enabled: {effect}." : "Auto-buff disabled: {effect}."
        ).replace("{effect}", GameText.effectName(effectKey)));
        return true;
    }

    public boolean cycleFollowPosition(Player player) {
        return PetPlayerControlSupport.cycleFollowPosition(player, getPet(player), this::showActionBar);
    }

    public boolean previousFollowPosition(Player player) {
        return PetPlayerControlSupport.previousFollowPosition(player, getPet(player), this::showActionBar);
    }

    public boolean setFollowPosition(Player player, int position) {
        return PetPlayerControlSupport.setFollowPosition(player, getPet(player), position, this::showActionBar);
    }

    public boolean increaseFollowDistance(Player player) {
        return PetPlayerControlSupport.increaseFollowDistance(player, getPet(player), this::showActionBar);
    }

    public boolean decreaseFollowDistance(Player player) {
        return PetPlayerControlSupport.decreaseFollowDistance(player, getPet(player), this::showActionBar);
    }

    public boolean tryEvolveActivePet(Player player) {
        return PetEvolutionFlowSupport.tryEvolveActivePet(
            player,
            getPet(player),
            progressionAPI,
            this::evolutionRequirement,
            this::evolutionBond,
            this::hasEvolutionMaterials,
            this::requiredEvolutionQuestCompletions,
            this::completedEvolutionQuestCompletions,
            this::evolutionAttemptChance,
            this::consumeEvolutionMaterials,
            petVaultService::snapshotContents,
            petVaultService::restoreContents,
            this::saveEvolutionState,
            this::finishEvolution,
            this::showActionBar
        );
    }


    public void onOwnerBlockBreak(Player owner, Block block) {
        getPet(owner).ifPresent(pet -> abilityService.onBlockBreak(owner, pet, block));
    }

    public void onOwnerPickupItem(Player owner, Item item) {
        getPet(owner).ifPresent(pet -> abilityService.onPlayerPickup(owner, pet, item));
    }

    private void grantKillXp(RuntimePet pet) {
        if (progressionAPI != null) {
            progressionAPI.addXp(pet.data(), balanceConfig.combatXp());
            pet.refreshName();
        }
    }

    public double evolutionAttemptChance(Player player) {
        return isNearEvolutionSource(player) ? SOURCE_EVOLUTION_NEAR_CHANCE : SOURCE_EVOLUTION_FAR_CHANCE;
    }

    public boolean isNearEvolutionSource(Player player) {
        if (player == null) {
            return false;
        }
        return petMasterManager != null && petMasterManager.isNearSource(player.getLocation(), SOURCE_EVOLUTION_RADIUS);
    }

    private double evolutionAttemptChance(Player player, RuntimePet pet) {
        return evolutionAttemptChance(player);
    }

    private void grantKillReward(Player owner, RuntimePet pet) {
        grantKillXp(pet);
        grantBond(pet, balanceConfig.bondKillGain(), "kill", balanceConfig.bondKillCooldownMillis());
        pet.refreshName();
        showActionBar(owner, 1_800L);
    }

    private void grantCombatBond(RuntimePet pet) {
        grantBond(pet, balanceConfig.bondCombatGain(), "combat", balanceConfig.bondCombatCooldownMillis());
    }

    private void grantBond(RuntimePet pet, int delta, String reason, long cooldownMillis) {
        if (pet == null || delta == 0) {
            return;
        }
        String key = pet.data().petId() + ":" + reason;
        long now = System.currentTimeMillis();
        if (cooldownMillis > 0L && bondCooldowns.getOrDefault(key, 0L) > now) {
            return;
        }
        if (adjustBond(pet, delta) && cooldownMillis > 0L) {
            bondCooldowns.put(key, now + cooldownMillis);
        }
    }

    private boolean adjustBond(RuntimePet pet, int delta) {
        if (pet == null || delta == 0) {
            return false;
        }
        int before = pet.data().bond();
        pet.data().adjustBond(delta);
        if (before != pet.data().bond()) {
            pet.refreshName();
            return true;
        }
        return false;
    }

    public int countEvolutionMaterial(Player player, OwnedPetData data, Material material) {
        return PetEvolutionRuntimeSupport.countEvolutionMaterial(
            player,
            data,
            getPet(player),
            petVaultService,
            material
        );
    }

    public EvolutionRequirement evolutionRequirement(OwnedPetData data) {
        int currentStage = Math.max(1, Math.min(5, data.evolutionStage()));
        int nextStage = Math.min(5, currentStage + 1);
        PetType type = PetType.parse(data.petType()).orElse(PetType.WOLF);
        int requiredQuests = balanceConfig.evolutionRequiredQuests(nextStage);
        return new EvolutionRequirement(
            currentStage,
            nextStage,
            balanceConfig.evolutionRequiredLevel(nextStage),
            balanceConfig.evolutionRequiredBond(nextStage),
            PetEvolutionMaterialSupport.materialsForNextStage(type, nextStage),
            requiredQuests
        );
    }

    public Map<Material, Integer> evolutionMaterialCounts(Player player, OwnedPetData data) {
        return PetEvolutionRuntimeSupport.evolutionMaterialCounts(
            player,
            data,
            evolutionRequirement(data),
            getPet(player),
            petVaultService
        );
    }

    public boolean hasEvolutionMaterials(Player player, OwnedPetData data) {
        return PetEvolutionRuntimeSupport.hasEvolutionMaterials(
            player,
            data,
            evolutionRequirement(data),
            getPet(player),
            petVaultService
        );
    }

    public int requiredEvolutionQuestCompletions(OwnedPetData data) {
        return evolutionRequirement(data).requiredQuests();
    }

    public int totalEvolutionQuestOptions(OwnedPetData data) {
        if (questManager == null) {
            return 0;
        }
        return questManager.evolutionQuestsForStage(data.evolutionStage()).size();
    }

    public int completedEvolutionQuestCompletions(Player player, OwnedPetData data) {
        if (questManager == null) {
            return 0;
        }
        return questManager.completedEvolutionQuests(player.getUniqueId(), data.evolutionStage(), data.petId());
    }

    public int evolutionBond(OwnedPetData data) {
        return PetEvolutionRuntimeSupport.evolutionBond(data, balanceConfig);
    }

    private void consumeEvolutionMaterials(Player player, RuntimePet pet, Map<Material, Integer> requirements) {
        PetEvolutionRuntimeSupport.consumeEvolutionMaterials(player, pet, requirements, petVaultService);
    }

    private boolean saveEvolutionState(Player player, RuntimePet pet) {
        return playerDataManager.save(player.getUniqueId());
    }

    private double counterAttackChance(RuntimePet pet) {
        return PetCombatChanceSupport.counterAttackChance(pet);
    }

    private double assistAttackChance(RuntimePet pet) {
        return PetCombatChanceSupport.assistAttackChance(pet);
    }

    private double autoAggroChance(RuntimePet pet) {
        Player owner = ownerFor(pet);
        return PetCombatChanceSupport.autoAggroChance(pet, owner != null && isPetWorldCombatSuppressed(owner));
    }

    private boolean roll(double chance) {
        return chance > 0.0D && ThreadLocalRandom.current().nextDouble() < chance;
    }

    private boolean movedEnoughForPetXp(Player owner) {
        Location current = owner.getLocation();
        Location previous = lastPetXpLocations.put(owner.getUniqueId(), current.clone());
        if (previous == null || previous.getWorld() == null || !previous.getWorld().equals(current.getWorld())) {
            return false;
        }
        return previous.distance(current) >= Math.max(1.0D, balanceConfig.afkDistanceThreshold() * 0.25D);
    }

    private boolean movedEnoughForKillXp(Player owner) {
        Location current = owner.getLocation();
        UUID ownerId = owner.getUniqueId();
        Location previous = lastKillXpLocations.get(ownerId);
        if (previous == null || previous.getWorld() == null || !previous.getWorld().equals(current.getWorld())) {
            lastKillXpLocations.put(ownerId, current.clone());
            killXpZoneCounts.put(ownerId, 1);
            return true;
        }
        if (previous.distance(current) >= Math.max(8.0D, balanceConfig.afkDistanceThreshold() * 2.0D)) {
            lastKillXpLocations.put(ownerId, current.clone());
            killXpZoneCounts.put(ownerId, 1);
            return true;
        }
        int count = killXpZoneCounts.getOrDefault(ownerId, 0);
        if (count >= 2) {
            return false;
        }
        killXpZoneCounts.put(ownerId, count + 1);
        return true;
    }

    private void sendActionBar(Player owner, RuntimePet pet) {
        int tick = Bukkit.getCurrentTick();
        int previous = actionBarTicks.getOrDefault(owner.getUniqueId(), 0);
        if (tick - previous < 20) {
            return;
        }
        actionBarTicks.put(owner.getUniqueId(), tick);
        owner.sendActionBar(Component.text(pet.hudLine(balanceConfig, abilityService)));
    }

    private void recordPlayerCombat(Player attacker, Player victim) {
        PetSocialCoordinatorSupport.recordPlayerCombat(
            combatLinks,
            attacker.getUniqueId(),
            victim.getUniqueId(),
            PLAYER_COMBAT_WINDOW_MILLIS
        );
    }

    private boolean registerPetProvocation(Player attacker, Player victim) {
        return PetSocialCoordinatorSupport.registerPetProvocation(
            combatLinks,
            attacker.getUniqueId(),
            victim.getUniqueId(),
            PLAYER_COMBAT_WINDOW_MILLIS,
            PET_PROVOKE_WINDOW_MILLIS,
            PET_DUEL_WINDOW_MILLIS
        );
    }

    private boolean petsCanSpar(RuntimePet first, RuntimePet second) {
        return PetSocialCoordinatorSupport.petsCanSpar(first, second);
    }

    private boolean pairInCombat(Player first, Player second) {
        return PetSocialCoordinatorSupport.pairInCombat(combatLinks, first.getUniqueId(), second.getUniqueId(), System.currentTimeMillis());
    }

    private double socialChance(RuntimePet first, RuntimePet second) {
        double base = (socialStageChance(first.data().evolutionStage()) + socialStageChance(second.data().evolutionStage())) * 0.5D;
        if (first.type() == second.type()) {
            base += 0.06D;
        }
        return Math.min(0.42D, base);
    }

    private double socialStageChance(int stage) {
        return switch (stage) {
            case 1 -> 0.30D;
            case 2 -> 0.24D;
            case 3 -> 0.18D;
            case 4 -> 0.20D;
            default -> 0.25D;
        };
    }

    private void finishEvolution(Player player, RuntimePet pet) {
        Location refreshLocation = pet.entity()
            .map(Entity::getLocation)
            .orElse(player.getLocation());
        playEvolutionEffect(player, pet);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refreshActivePet(player, refreshLocation);
            }
        }, 40L);
    }

    private void playEvolutionEffect(Player player, RuntimePet pet) {
        pet.entity().ifPresent(entity -> {
            Location center = entity.getLocation().add(0.0D, 0.8D, 0.0D);
            AtomicReference<BukkitTask> taskRef = new AtomicReference<>();
            final int[] tick = {0};
            taskRef.set(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline() || entity.isDead() || tick[0] > 40) {
                    BukkitTask task = taskRef.get();
                    if (task != null) {
                        task.cancel();
                    }
                    return;
                }
                Location base = entity.getLocation().add(0.0D, 0.85D, 0.0D);
                for (int index = 0; index < 2; index++) {
                    double angle = (tick[0] * 0.42D) + (Math.PI * index);
                    Location orb = base.clone().add(Math.cos(angle) * 0.55D, Math.sin(tick[0] * 0.22D) * 0.18D, Math.sin(angle) * 0.55D);
                    entity.getWorld().spawnParticle(Particle.ENCHANT, orb, 2, 0.03D, 0.03D, 0.03D, 0.02D);
                    entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, orb, 1, 0.02D, 0.02D, 0.02D, 0.0D);
                }
                tick[0] += 4;
            }, 0L, 4L));
            entity.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center, 22, 0.45D, 0.65D, 0.45D, 0.025D);
            entity.getWorld().playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.25F);
            entity.getWorld().playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.6F);
            player.sendMessage(pet.data().evolutionStage() >= 5
                ? GameText.text("pet.evolution.max-success", "Питомец достиг максимальной эволюции!", "The pet reached maximum evolution!")
                : GameText.text("pet.evolution.success", "Питомец эволюционировал!", "The pet evolved!"));
        });
    }

    private Optional<RuntimePet> runtimeFromData(OwnedPetData data) {
        return PetType.parse(data.petType()).map(type -> new RuntimePet(data, type));
    }

    public void refreshActivePet(Player player, Location preferredLocation) {
        Optional<OwnedPetData> petData = activePetData(player);
        if (petData.isEmpty()) {
            return;
        }
        despawnPet(player);
        activatePet(player, petData.get(), preferredLocation);
    }

    private OwnedPetData storedOrIncomingPetData(Player player, OwnedPetData petData) {
        return playerDataManager.getOrLoad(player.getUniqueId()).pets().stream()
            .filter(stored -> stored.petId().equals(petData.petId()))
            .findFirst()
            .map(stored -> {
                stored.setOwnerId(player.getUniqueId());
                return stored;
            })
            .orElse(petData);
    }

    private void rememberActivePet(Player player, OwnedPetData petData) {
        PlayerData playerData = playerDataManager.getOrLoad(player.getUniqueId());
        playerData.pets().stream()
            .filter(stored -> stored.petId().equals(petData.petId()))
            .findFirst()
            .ifPresentOrElse(stored -> stored.copyProgressionFrom(petData), () -> playerData.pets().add(petData));
        playerData.setActivePetId(petData.petId());
    }

    static OwnedPetData snapshotPet(OwnedPetData source) {
        OwnedPetData snapshot = new OwnedPetData(source.petId(), source.ownerId(), source.petType(), source.rarity());
        restorePet(snapshot, source);
        return snapshot;
    }

    static void rollbackFailedActivation(PlayerData playerData, UUID activatedPetId, Optional<UUID> previousActivePetId, OwnedPetData previousStoredPet) {
        playerData.setActivePetId(previousActivePetId.orElse(null));
        if (previousStoredPet == null) {
            playerData.pets().removeIf(pet -> pet.petId().equals(activatedPetId));
            return;
        }
        playerData.pets().stream()
            .filter(stored -> stored.petId().equals(activatedPetId))
            .findFirst()
            .ifPresentOrElse(stored -> restorePet(stored, previousStoredPet), () -> playerData.pets().add(previousStoredPet));
    }

    private static void restorePet(OwnedPetData target, OwnedPetData source) {
        target.copyProgressionFrom(source);
        target.setOwnerId(source.ownerId());
        target.setState(source.state());
    }

    public int activePetCount() {
        return activePets.size();
    }

    public void showActionBar(Player player, long durationMillis) {
        actionBarVisibleUntilMillis.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(500L, durationMillis));
    }

    private void deactivatePet(Player owner, RuntimePet pet, String reason) {
        specialTraitCooldowns.remove(pet.data().petId());
        pet.despawn();
        activePets.remove(owner.getUniqueId());
        lastPetXpLocations.remove(owner.getUniqueId());
        lastKillXpLocations.remove(owner.getUniqueId());
        killXpZoneCounts.remove(owner.getUniqueId());
        actionBarTicks.remove(owner.getUniqueId());
        actionBarVisibleUntilMillis.remove(owner.getUniqueId());
        debugLogger.warnRateLimited(
            "pet:deactivate:" + owner.getUniqueId() + ":" + reason,
            "pet-runtime",
            "Deactivated pet owner=" + owner.getName()
                + " world=" + owner.getWorld().getName()
                + " pet=" + pet.data().petId()
                + " reason=" + reason,
            20_000L
        );
    }

    private Player ownerFor(RuntimePet pet) {
        UUID ownerId = pet.data().ownerId();
        return ownerId == null ? null : Bukkit.getPlayer(ownerId);
    }

    private void deactivatePetWithoutOwner(RuntimePet pet, String reason) {
        UUID ownerId = pet.data().ownerId();
        specialTraitCooldowns.remove(pet.data().petId());
        pet.despawn();
        if (ownerId != null) {
            activePets.remove(ownerId);
            lastPetXpLocations.remove(ownerId);
            lastKillXpLocations.remove(ownerId);
            killXpZoneCounts.remove(ownerId);
            actionBarTicks.remove(ownerId);
            actionBarVisibleUntilMillis.remove(ownerId);
        } else {
            activePets.values().removeIf(runtimePet -> runtimePet == pet);
        }
        debugLogger.warnRateLimited(
            "pet:deactivate:orphan:" + pet.data().petId() + ":" + reason,
            "pet-runtime",
            "Deactivated orphan pet pet=" + pet.data().petId()
                + " type=" + pet.type().name()
                + " reason=" + reason,
            20_000L
        );
    }

    public boolean isPetCombatSuppressed(Player owner) {
        return isPetPvpCombatSuppressed(owner);
    }

    public boolean isPetWorldCombatSuppressed(Player owner) {
        return !balanceConfig.worldPetAttacksEnabled(owner.getWorld().getName())
            || balanceConfig.worldDecorativeOnly(owner.getWorld().getName());
    }

    public boolean isPetPvpCombatSuppressed(Player owner) {
        return isPetWorldCombatSuppressed(owner)
            || (worldGuardCombatBridge != null && worldGuardCombatBridge.blocksCombat(owner));
    }

    private String combatSuppressionReason(Player owner, boolean includeWorldGuard) {
        if (!balanceConfig.worldPetAttacksEnabled(owner.getWorld().getName())) {
            return "attacks-disabled";
        }
        if (balanceConfig.worldDecorativeOnly(owner.getWorld().getName())) {
            return "decorative-only";
        }
        if (includeWorldGuard && worldGuardCombatBridge != null && worldGuardCombatBridge.blocksCombat(owner)) {
            return "worldguard";
        }
        return "unknown";
    }
    private String combatSuppressionReason(Player owner) {
        if (!balanceConfig.worldPetAttacksEnabled(owner.getWorld().getName())) {
            return "attacks-disabled";
        }
        if (balanceConfig.worldDecorativeOnly(owner.getWorld().getName())) {
            return "decorative-only";
        }
        if (worldGuardCombatBridge != null && worldGuardCombatBridge.blocksCombat(owner)) {
            return "worldguard";
        }
        return "unknown";
    }

    private PetRarity rollRarity() {
        double roll = ThreadLocalRandom.current().nextDouble();
        double legendary = balanceConfig.rarityChance("legendary");
        double epic = balanceConfig.rarityChance("epic");
        double rare = balanceConfig.rarityChance("rare");
        if (roll < legendary) {
            return PetRarity.LEGENDARY;
        }
        if (roll < legendary + epic) {
            return PetRarity.EPIC;
        }
        if (roll < legendary + epic + rare) {
            return PetRarity.RARE;
        }
        return PetRarity.COMMON;
    }

    private boolean shouldShowActionBar(Player owner, RuntimePet pet) {
        long visibleUntil = actionBarVisibleUntilMillis.getOrDefault(owner.getUniqueId(), 0L);
        return visibleUntil > System.currentTimeMillis() || pet.isLookedAtBy(owner) || pet.hasActionHint();
    }
}
