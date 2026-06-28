package dev.li2fox.vibepetcore.egg;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.CoreModule;
import dev.li2fox.vibepetcore.core.PetDebugLogger;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.PetEngineManager;
import dev.li2fox.vibepetcore.pet.RuntimePet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PetEggController implements CoreModule, Listener {
    private final JavaPlugin plugin;
    private final BalanceConfig config;
    private final PetEngineManager petEngineManager;
    private final PetEggService eggService;
    private final PetDebugLogger debugLogger;
    private final Map<UUID, List<ItemStack>> pendingDeathReturns = new java.util.HashMap<>();
    private final Map<UUID, String> pendingDeathCoreNotice = new java.util.HashMap<>();
    private final Set<UUID> pendingSync = new HashSet<>();
    private final Map<UUID, SummonCharge> summonCharges = new java.util.HashMap<>();
    private final Map<UUID, Long> saveFailureNoticeUntil = new java.util.HashMap<>();
    private BukkitTask flushTask;

    private enum ChargeAction {
        SUMMON
    }

    private record SummonCharge(UUID petId, ChargeAction action, BukkitTask task, Location anchorLocation) {
    }

    public PetEggController(JavaPlugin plugin, BalanceConfig config, PetEngineManager petEngineManager, PetEggService eggService, PetDebugLogger debugLogger) {
        this.plugin = plugin;
        this.config = config;
        this.petEngineManager = petEngineManager;
        this.eggService = eggService;
        this.debugLogger = debugLogger;
    }

    private String msg(String key, String fallback, Object... replacements) {
        return this.config.message(key, fallback, replacements);
    }

    @Override
    public void enable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduleSync(player);
        }
    }

    @Override
    public void disable() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        for (SummonCharge charge : summonCharges.values()) {
            charge.task().cancel();
        }
        summonCharges.clear();
        pendingSync.clear();
        pendingDeathCoreNotice.clear();
        saveFailureNoticeUntil.clear();
    }

    private void flushPendingSync() {
        pendingSync.removeIf(playerId -> {
            Player player = Bukkit.getPlayer(playerId);
            return player == null || !player.isOnline();
        });
        List<UUID> playerIds = new ArrayList<>(pendingSync);
        pendingSync.clear();
        flushTask = null;
        for (UUID playerId : playerIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            try {
                syncPlayer(player);
            } catch (Throwable throwable) {
                debugLogger.errorRateLimited(
                    "egg:sync:" + player.getUniqueId(),
                    "pet-egg",
                    "Pet sync failed for " + player.getName(),
                    throwable,
                    15_000L
                );
                petEngineManager.despawnPet(player);
            }
        }
    }

    private void scheduleSync(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        pendingSync.add(player.getUniqueId());
        if (flushTask == null) {
            flushTask = Bukkit.getScheduler().runTask(plugin, this::flushPendingSync);
        }
    }

    private void syncPlayer(Player player) {
        sanitizePetInventory(player);
        if (isRestrictedPetMode(player.getGameMode())) {
            deactivateForRestrictedMode(player, false);
            return;
        }
        Optional<OwnedPetData> activePet = petEngineManager.activePetData(player);
        Optional<RuntimePet> runtimePet = petEngineManager.getPet(player);

        if (activePet.isEmpty()) {
            runtimePet.ifPresent(ignored -> petEngineManager.despawnPet(player));
            cancelSummonCharge(player, false);
            return;
        }

        OwnedPetData pet = activePet.get();
        int inventorySlot = findPetSlot(player, pet.petId());
        if (!PetCoreSlotSupport.keepsActiveRuntime(inventorySlot)) {
            clearActivePetMissingOffhand(player, pet, inventorySlot);
            cancelSummonCharge(player, false);
            return;
        }
        refreshPetItemSlot(player, inventorySlot, pet);

        if (pet.inactiveUntilMillis() > System.currentTimeMillis()) {
            petEngineManager.despawnPet(player);
            cancelSummonCharge(player, false);
            return;
        }

        if (pet.durability() <= 0) {
            petEngineManager.clearActivePet(player);
            cancelSummonCharge(player, false);
            return;
        }

        boolean sameRuntime = runtimePet.map(current -> current.data().petId().equals(pet.petId())).orElse(false);
        if (!sameRuntime) {
            try {
                if (petEngineManager.activatePet(player, pet).isEmpty()) {
                    debugLogger.warnRateLimited(
                        "egg:sync:" + player.getUniqueId() + ":activate-save",
                        "pet-egg",
                        "Pet sync activation save failed for " + player.getName() + ", keeping core unchanged.",
                        10_000L
                    );
                    player.sendActionBar(Component.text(msg(
                        "pet-egg.save-failed",
                        "Could not save pet data. Try again in a moment."
                    )));
                    return;
                }
            } catch (Throwable throwable) {
                debugLogger.errorRateLimited(
                    "egg:sync:" + player.getUniqueId() + ":activate",
                    "pet-egg",
                    "Pet sync failed during activation for " + player.getName()
                        + " world=" + player.getWorld().getName()
                        + " pet=" + pet.petId()
                        + " stage=" + pet.evolutionStage(),
                    throwable,
                    15_000L
                );
                player.sendActionBar(Component.text(msg(
                    "pet-egg.save-failed",
                    "Could not save pet data. Try again in a moment."
                )));
                return;
            }
        }
        ItemStack item = player.getInventory().getItem(inventorySlot);
        if (item != null && !eggService.isEmptyEgg(item)) {
            player.getInventory().setItem(inventorySlot, writeActivePetCoreItem(inventorySlot, item, pet));
        }
    }

    private void startSummonCharge(Player player, EquipmentSlot hand) {
        if (isRestrictedPetMode(player.getGameMode())) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.restricted-mode",
                "In creative and spectator mode, the pet is kept only as a core."
            )));
            return;
        }
        Optional<EquipmentSlot> preparedHand = prepareSummonHand(player, hand);
        if (preparedHand.isEmpty()) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.offhand-required",
                "Put the pet core in your offhand and right-click."
            )));
            return;
        }
        final EquipmentSlot summonHand = preparedHand.get();
        ItemStack heldItem = itemInHand(player, summonHand);
        Optional<OwnedPetData> heldPet = eggService.readEgg(heldItem);
        if (heldPet.isEmpty()) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.core-required",
                "Hold a pet core and right-click."
            )));
            return;
        }

        OwnedPetData pet = heldPet.get();
        recoverExpiredPet(player, summonHand, heldItem, pet);
        SummonCharge currentCharge = summonCharges.get(player.getUniqueId());
        if (currentCharge != null && currentCharge.action() == ChargeAction.SUMMON && currentCharge.petId().equals(pet.petId())) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.summon-already",
                "Summon is already in progress."
            )));
            return;
        }
        if (pet.inactiveUntilMillis() > System.currentTimeMillis()) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.inactive",
                "Death penalty is active: {time} remaining.",
                "time", formatRemaining(pet.inactiveUntilMillis() - System.currentTimeMillis())
            )));
            return;
        }
        if (pet.durability() <= 0) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.broken",
                "The pet core has broken."
            )));
            return;
        }

        Optional<UUID> activePetId = petEngineManager.activePetId(player);
        if (activePetId.isPresent()) {
            player.sendActionBar(Component.text(msg(
                activePetId.get().equals(pet.petId()) ? "pet-egg.already-summoned" : "pet-egg.other-active",
                activePetId.get().equals(pet.petId()) ? "The pet is already nearby." : "Return the active pet before summoning another."
            )));
            petEngineManager.showActionBar(player, 2_000L);
            return;
        }

        boolean firstSummon = pet.progress().getOrDefault("first_summon_done", 0) == 0;
        int totalSeconds = firstSummon ? 3 : 2;
        Location summonAnchor = captureSummonAnchor(player);
        cancelSummonCharge(player, false);
        final int[] step = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelSummonCharge(player, false);
                return;
            }

            Optional<OwnedPetData> currentHeld = eggService.readEgg(itemInHand(player, summonHand));
            if (currentHeld.isEmpty() || !currentHeld.get().petId().equals(pet.petId())) {
                cancelSummonCharge(player, true);
                return;
            }

            if (firstSummon) {
                spawnFirstSummonEffect(player, summonAnchor);
            }

            if (step[0] < totalSeconds) {
                int shown = totalSeconds - step[0];
                String prefix = firstSummon
                    ? msg("pet-egg.first-summon-prefix", "First awakening: ")
                    : msg("pet-egg.summon-prefix", "Summoning pet: ");
                player.sendActionBar(Component.text(prefix + shown + "..."));
                step[0]++;
                return;
            }

            cancelSummonCharge(player, false);
            activateHeldPet(player, summonHand, firstSummon, summonAnchor);
        }, 0L, 20L);
        summonCharges.put(player.getUniqueId(), new SummonCharge(pet.petId(), ChargeAction.SUMMON, task, summonAnchor));
    }

    private void handlePetMenuClick(Player player) {
        player.sendActionBar(Component.text(msg(
            "pet-egg.menu-command-hint",
            "Use /pet to open the pet menu."
        )));
    }

    private void activateHeldPet(Player player, EquipmentSlot hand, boolean firstSummon, Location summonAnchor) {
        if (isRestrictedPetMode(player.getGameMode())) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.restricted-mode",
                "In creative and spectator mode, the pet is kept only as a core."
            )));
            return;
        }
        Optional<EquipmentSlot> preparedHand = prepareSummonHand(player, hand);
        if (preparedHand.isEmpty()) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.offhand-required",
                "Put the pet core in your offhand and right-click."
            )));
            return;
        }
        final EquipmentSlot summonHand = preparedHand.get();
        ItemStack heldItem = itemInHand(player, summonHand);
        Optional<OwnedPetData> heldPet = eggService.readEgg(heldItem);
        if (heldPet.isEmpty()) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.core-required",
                "Hold a pet core and right-click."
            )));
            return;
        }

        OwnedPetData pet = heldPet.get();
        recoverExpiredPet(player, summonHand, heldItem, pet);
        if (pet.inactiveUntilMillis() > System.currentTimeMillis()) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.inactive",
                "Death penalty is active: {time} remaining.",
                "time", formatRemaining(pet.inactiveUntilMillis() - System.currentTimeMillis())
            )));
            return;
        }
        if (pet.durability() <= 0) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.broken",
                "The pet core has broken."
            )));
            return;
        }

        if (firstSummon) {
            pet.progress().put("first_summon_done", 1);
        }
        try {
            if (petEngineManager.activatePet(player, pet, summonAnchor).isEmpty()) {
                debugLogger.warnRateLimited(
                    "egg:activate-save:" + player.getUniqueId() + ":" + pet.petId(),
                    "pet-egg",
                    "Pet activation save failed for " + player.getName() + ", keeping core filled.",
                    10_000L
                );
                player.sendActionBar(Component.text(msg(
                    "pet-egg.save-failed",
                    "Could not save pet data. Try again in a moment."
                )));
                return;
            }
            OwnedPetData activeData = petEngineManager.activePetData(player).orElse(pet);
            if (firstSummon) {
                playFirstSummonFinish(player, summonAnchor);
            }
            setItemInHand(player, summonHand, summonHand == EquipmentSlot.OFF_HAND
                ? eggService.writeActiveButton(heldItem, activeData)
                : eggService.writeEmptyEgg(heldItem, activeData));
            syncPlayer(player);
            petEngineManager.showActionBar(player, 2_500L);
            if (!firstSummon) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.45F, 1.35F);
            }
        } catch (IllegalArgumentException exception) {
            debugLogger.warnRateLimited(
                "egg:activate:" + player.getUniqueId() + ":" + pet.petId(),
                "pet-egg",
                "Activation blocked for " + player.getName() + ": " + exception.getMessage(),
                10_000L
            );
            player.sendActionBar(Component.text(exception.getMessage()));
        }
    }

    private void recoverExpiredPet(Player player, EquipmentSlot hand, ItemStack item, OwnedPetData pet) {
        if (pet.inactiveUntilMillis() <= 0L || pet.inactiveUntilMillis() > System.currentTimeMillis() || pet.durability() <= 0) {
            return;
        }
        double minHealth = Math.max(1.0D, pet.maxHealth() * 0.9D);
        double minSatiety = Math.max(1.0D, config.eggMaxSatiety() * 0.9D);
        if (pet.health() >= minHealth && pet.satiety() >= minSatiety) {
            return;
        }
        pet.recoverAfterRest(minHealth, minSatiety);
        ItemStack updated = eggService.isEmptyEgg(item)
            ? eggService.writeEmptyEgg(item, pet)
            : eggService.writeEgg(item, pet);
        setItemInHand(player, hand, updated);
    }

    private void returnActivePetToEgg(Player player, EquipmentSlot hand, ItemStack item) {
        Optional<OwnedPetData> clickedPet = eggService.readEgg(item);
        Optional<OwnedPetData> activePet = petEngineManager.activePetData(player);
        if (activePet.isEmpty()) {
            if (restoreStoredPetCore(player, hand, item, clickedPet)) {
                return;
            }
            player.sendActionBar(Component.text(msg(
                "pet-egg.empty-restored-missing",
                "This core is empty, and the saved pet was not found."
            )));
            return;
        }
        if (!canEmptyCoreReceiveActivePet(item, activePet.get())) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.no-matching-active",
                "This empty core does not match your active pet."
            )));
            return;
        }
        OwnedPetData freshPet = activePet.get();
        if (!clearActivePetForCoreChange(player, "recall")) {
            return;
        }
        setItemInHand(player, hand, eggService.writeEgg(item, freshPet));
        cancelSummonCharge(player, false);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.45F, 0.85F);
        player.sendActionBar(Component.text(msg(
            "pet-egg.recalled",
            "The pet was put back into the core."
        )));
    }

    private boolean restoreStoredPetCore(Player player, EquipmentSlot hand, ItemStack item, Optional<OwnedPetData> clickedPet) {
        if (clickedPet.isEmpty()) {
            return false;
        }
        PetCoreRecoverySupport.RecoveryResult recovery = PetCoreRecoverySupport.recoverInactiveCore(player, petEngineManager, clickedPet);
        if (!recovery.recovered()) {
            return false;
        }
        setItemInHand(player, hand, eggService.writeEgg(item, recovery.pet()));
        cancelSummonCharge(player, false);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.65F, 1.15F);
        player.sendActionBar(Component.text(msg(
            "pet-egg.empty-restored",
            "The empty core restored the saved pet. Use it again to summon."
        )));
        return true;
    }

    private boolean canEmptyCoreReceiveActivePet(ItemStack item, OwnedPetData activePet) {
        return PetEggRecallSupport.canReceiveActivePet(eggService.readEgg(item), activePet);
    }

    private ItemStack itemInHand(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
    }

    private void setItemInHand(Player player, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
    }

    private void cancelSummonCharge(Player player, boolean notify) {
        SummonCharge charge = summonCharges.remove(player.getUniqueId());
        if (charge != null) {
            charge.task().cancel();
            if (notify) {
                player.sendActionBar(Component.text(msg(
                    "pet-egg.summon-cancelled",
                    "Pet summon cancelled."
                )));
            }
        }
    }

    private boolean clearActivePetForCoreChange(Player player, String action) {
        if (petEngineManager.clearActivePet(player)) {
            return true;
        }
        debugLogger.warnRateLimited(
            "egg:clear-save:" + player.getUniqueId() + ":" + action,
            "pet-egg",
            "Could not save active pet clear for " + player.getName() + " during " + action + "; core state was left unchanged.",
            10_000L
        );
        player.sendActionBar(Component.text(msg(
            "pet-egg.save-failed",
            "Could not save pet data. Try again in a moment."
        )));
        return false;
    }

    private void clearActivePetAfterDeath(Player player) {
        if (petEngineManager.clearActivePet(player)) {
            return;
        }
        debugLogger.warnRateLimited(
            "egg:clear-save:" + player.getUniqueId() + ":death",
            "pet-egg",
            "Could not save active pet clear for " + player.getName() + " during death; runtime pet was despawned and data kept dirty.",
            10_000L
        );
        petEngineManager.despawnPet(player);
    }

    private void clearActivePetMissingOffhand(Player player, OwnedPetData pet, int matchingSlot) {
        if (petEngineManager.clearActivePet(player)) {
            return;
        }
        debugLogger.warnRateLimited(
            "egg:clear-save:" + player.getUniqueId() + ":missing-offhand",
            "pet-egg",
            "Could not save active pet clear for " + player.getName()
                + " after offhand core mismatch; runtime pet was despawned and data kept dirty."
                + " pet=" + pet.petId()
                + " matchingSlot=" + matchingSlot,
            10_000L
        );
        long now = System.currentTimeMillis();
        long nextNotice = saveFailureNoticeUntil.getOrDefault(player.getUniqueId(), 0L);
        if (nextNotice <= now) {
            saveFailureNoticeUntil.put(player.getUniqueId(), now + 10_000L);
            player.sendActionBar(Component.text(msg(
                "pet-egg.save-failed",
                "Could not save pet data. Try again in a moment."
            )));
        }
        petEngineManager.despawnPet(player);
    }

    private void restoreButtonsOutsideOffhand(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item == offhand || !eggService.isActiveButton(item)) {
                continue;
            }
            int targetSlot = slot;
            eggService.readEgg(item).ifPresent(pet -> player.getInventory().setItem(targetSlot, eggService.writeEmptyEgg(item, pet)));
        }
    }

    private void sanitizePetInventory(Player player) {
        restoreButtonsOutsideOffhand(player);
        Map<UUID, Integer> preferredSlots = new java.util.HashMap<>();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            Optional<OwnedPetData> pet = eggService.readEgg(item);
            if (pet.isEmpty()) {
                continue;
            }
            preferredSlots.merge(pet.get().petId(), slot, (current, candidate) -> {
                if (candidate == PetCoreSlotSupport.OFFHAND_SLOT) {
                    return candidate;
                }
                return current == PetCoreSlotSupport.OFFHAND_SLOT ? current : Math.min(current, candidate);
            });
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            Optional<OwnedPetData> pet = eggService.readEgg(item);
            if (pet.isEmpty()) {
                continue;
            }
            int preferredSlot = preferredSlots.getOrDefault(pet.get().petId(), slot);
            if (slot != preferredSlot) {
                player.getInventory().setItem(slot, null);
                continue;
            }
            if (slot != PetCoreSlotSupport.OFFHAND_SLOT && eggService.isActiveButton(item)) {
                player.getInventory().setItem(slot, eggService.writeEmptyEgg(item, pet.get()));
            }
        }
    }

    private boolean isRestrictedPetMode(GameMode gameMode) {
        return gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;
    }

    private void deactivateForRestrictedMode(Player player, boolean notify) {
        cancelSummonCharge(player, false);
        Optional<OwnedPetData> pet = petEngineManager.activePetData(player)
            .or(() -> eggService.readEgg(player.getInventory().getItemInOffHand()));
        if (pet.isPresent()) {
            OwnedPetData freshPet = pet.get();
            if (!clearActivePetForCoreChange(player, "restricted-mode")) {
                return;
            }
            UUID petId = freshPet.petId();
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                Optional<OwnedPetData> slotPet = eggService.readEgg(item);
                if (slotPet.isPresent() && slotPet.get().petId().equals(petId)) {
                    player.getInventory().setItem(slot, eggService.writeEgg(item, freshPet));
                }
            }
        } else {
            petEngineManager.clearActivePet(player);
        }
        sanitizePetInventory(player);
        if (notify) {
            player.sendActionBar(Component.text(msg(
                "pet-egg.recalled",
                "The pet was put back into the core until you return to survival."
            )));
        }
    }

    private void returnActivePetToInventory(Player player) {
        cancelSummonCharge(player, false);
        Optional<OwnedPetData> activePet = petEngineManager.activePetData(player);
        if (activePet.isEmpty()) {
            petEngineManager.despawnPet(player);
            return;
        }
        OwnedPetData pet = activePet.get();
        int slot = findPetSlot(player, pet.petId());
        if (!clearActivePetForCoreChange(player, "return-inventory")) {
            return;
        }
        if (slot >= 0) {
            ItemStack item = player.getInventory().getItem(slot);
            if (eggService.isPetEgg(item)) {
                player.getInventory().setItem(slot, eggService.writeEgg(item, pet));
            }
        }
    }

    private int findPetSlot(Player player, UUID petId) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            Optional<OwnedPetData> pet = eggService.readEgg(item);
            if (pet.isPresent() && pet.get().petId().equals(petId)) {
                return slot;
            }
        }
        return -1;
    }

    private void refreshPetItemSlot(Player player, int slot, OwnedPetData pet) {
        ItemStack item = player.getInventory().getItem(slot);
        if (!eggService.isPetEgg(item)) {
            return;
        }
        player.getInventory().setItem(slot, writeActivePetCoreItem(slot, item, pet));
    }

    private ItemStack writeActivePetCoreItem(int slot, ItemStack item, OwnedPetData pet) {
        return slot == PetCoreSlotSupport.OFFHAND_SLOT
            ? eggService.writeActiveButton(item, pet)
            : eggService.writeEmptyEgg(item, pet);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVanillaEggUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Optional<CoreUse> coreUse = resolveCoreUse(event);
        if (coreUse.isEmpty()) {
            return;
        }
        CoreUse use = coreUse.get();
        if (shouldPreserveBlockInteraction(event)) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        if (!eggService.isPetEgg(use.item())) {
            blockLegacyPetCoreUse(event.getPlayer());
            return;
        }
        if (eggService.isEmptyEgg(use.item())) {
            returnActivePetToEgg(event.getPlayer(), use.hand(), use.item());
        } else {
            startSummonCharge(event.getPlayer(), use.hand());
        }
    }

    private Optional<CoreUse> resolveCoreUse(PlayerInteractEvent event) {
        ItemStack eventItem = event.getItem();
        if (eggService.isPetCoreLikeItem(eventItem)) {
            EquipmentSlot hand = event.getHand() == EquipmentSlot.OFF_HAND
                ? EquipmentSlot.OFF_HAND
                : EquipmentSlot.HAND;
            return Optional.of(new CoreUse(hand, eventItem));
        }
        if (!config.eggOffhandActivation() || event.getHand() != EquipmentSlot.HAND) {
            return Optional.empty();
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR) {
            return Optional.empty();
        }
        ItemStack offhand = event.getPlayer().getInventory().getItemInOffHand();
        if (!eggService.isPetCoreLikeItem(offhand)) {
            return Optional.empty();
        }
        return Optional.of(new CoreUse(EquipmentSlot.OFF_HAND, offhand));
    }

    private Optional<EquipmentSlot> prepareSummonHand(Player player, EquipmentSlot hand) {
        if (PetCoreSlotSupport.canSummonFromHand(config.eggOffhandActivation(), hand)) {
            return Optional.of(hand);
        }
        if (!config.eggOffhandActivation() || hand != EquipmentSlot.HAND) {
            return Optional.empty();
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!eggService.isPetEgg(mainHand) || eggService.isEmptyEgg(mainHand) || eggService.readEgg(mainHand).isEmpty()) {
            return Optional.empty();
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            return Optional.empty();
        }
        player.getInventory().setItemInOffHand(mainHand);
        player.getInventory().setItemInMainHand(null);
        return Optional.of(EquipmentSlot.OFF_HAND);
    }

    private record CoreUse(EquipmentSlot hand, ItemStack item) {
    }

    private boolean shouldPreserveBlockInteraction(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        return event.getAction() == Action.RIGHT_CLICK_BLOCK
            && clickedBlock != null
            && isPetEggBlockInteractionTarget(clickedBlock.getType());
    }

    private boolean isPetEggBlockInteractionTarget(Material type) {
        return Tag.SHULKER_BOXES.isTagged(type)
            || Tag.DOORS.isTagged(type)
            || Tag.TRAPDOORS.isTagged(type)
            || Tag.FENCE_GATES.isTagged(type)
            || Tag.BUTTONS.isTagged(type)
            || Tag.BEDS.isTagged(type)
            || switch (type) {
                case CHEST,
                    TRAPPED_CHEST,
                    BARREL,
                    ENDER_CHEST,
                    CRAFTING_TABLE,
                    CRAFTER,
                    FURNACE,
                    BLAST_FURNACE,
                    SMOKER,
                    BREWING_STAND,
                    ENCHANTING_TABLE,
                    ANVIL,
                    CHIPPED_ANVIL,
                    DAMAGED_ANVIL,
                    CARTOGRAPHY_TABLE,
                    FLETCHING_TABLE,
                    GRINDSTONE,
                    LOOM,
                    SMITHING_TABLE,
                    STONECUTTER,
                    BEACON,
                    CONDUIT,
                    LECTERN,
                    BELL,
                    LEVER,
                    JUKEBOX,
                    NOTE_BLOCK,
                    COMPOSTER,
                    RESPAWN_ANCHOR,
                    CHISELED_BOOKSHELF -> true;
                default -> false;
            };
    }

    private void blockLegacyPetCoreUse(Player player) {
        player.sendActionBar(Component.text(msg(
            "pet-egg.legacy-core-blocked",
            "This old or damaged pet core was protected from vanilla use. Ask an admin to replace it."
        )));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45F, 0.75F);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVanillaEggEntityUse(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (eggService.isPetCoreLikeItem(item)) {
            event.setCancelled(true);
            if (!eggService.isPetEgg(item)) {
                blockLegacyPetCoreUse(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVanillaEggAtEntityUse(PlayerInteractAtEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (eggService.isPetCoreLikeItem(item)) {
            event.setCancelled(true);
            if (!eggService.isPetEgg(item)) {
                blockLegacyPetCoreUse(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPetEggPlace(BlockPlaceEvent event) {
        if (eggService.isPetCoreLikeItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            cancelSummonCharge(player, false);
            scheduleSync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            cancelSummonCharge(player, false);
            scheduleSync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        cancelSummonCharge(event.getPlayer(), false);
        scheduleSync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        cancelSummonCharge(player, false);
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (eggService.isEmptyEgg(dropped)) {
            Optional<OwnedPetData> activePet = petEngineManager.activePetData(player);
            if (activePet.isPresent() && canEmptyCoreReceiveActivePet(dropped, activePet.get())) {
                if (!clearActivePetForCoreChange(player, "drop")) {
                    event.setCancelled(true);
                    scheduleSync(player);
                    return;
                }
                event.getItemDrop().setItemStack(eggService.writeEgg(dropped, activePet.get()));
                player.sendActionBar(Component.text(msg(
                    "pet-egg.recalled",
                    "The pet was put back into the core."
                )));
            }
        }
        scheduleSync(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && eggService.isPetCoreLikeItem(event.getItem().getItemStack())) {
            scheduleSync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleSync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        saveFailureNoticeUntil.remove(player.getUniqueId());
        returnActivePetToInventory(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        cancelSummonCharge(event.getPlayer(), false);
        scheduleSync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        cancelSummonCharge(event.getPlayer(), false);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isRestrictedPetMode(event.getNewGameMode())) {
                deactivateForRestrictedMode(event.getPlayer(), true);
            }
            scheduleSync(event.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        cancelSummonCharge(player, false);
        List<ItemStack> protectedPetEggs = extractPetEggDrops(event.getDrops());
        Optional<OwnedPetData> activePet = petEngineManager.activePetData(player);
        if (activePet.isEmpty()) {
            queueDeathReturns(player, protectedPetEggs);
            petEngineManager.despawnPet(player);
            return;
        }

        OwnedPetData pet = activePet.get();
        int slot = findPetSlot(player, pet.petId());
        if (slot < 0) {
            queueDeathReturns(player, protectedPetEggs);
            clearActivePetAfterDeath(player);
            return;
        }

        ItemStack item = player.getInventory().getItem(slot);
        if (item == null) {
            queueDeathReturns(player, protectedPetEggs);
            clearActivePetAfterDeath(player);
            return;
        }

        int penaltyMinutes = config.deathPenaltyMinutes(pet.evolutionStage());
        pet.setInactiveUntilMillis(System.currentTimeMillis() + penaltyMinutes * 60_000L);
        int durabilityBefore = pet.durability();
        pet.setDurability(pet.durability() - 1);
        int durabilityLoss = Math.max(0, durabilityBefore - pet.durability());
        boolean coreBroken = pet.durability() <= 0;
        if (durabilityLoss > 0) {
            pendingDeathCoreNotice.put(player.getUniqueId(), coreBroken
                ? msg("pet-egg.death-core-broken", "The core has broken.")
                : msg("pet-egg.death-core-damaged", "The core was damaged: -{loss} durability.", "loss", durabilityLoss));
        }

        protectedPetEggs.removeIf(drop -> eggService.readEgg(drop)
            .map(data -> data.petId().equals(pet.petId()))
            .orElse(false));
        List<ItemStack> keep = new ArrayList<>();
        if (!coreBroken) {
            keep.add(eggService.writeEgg(item, pet));
        }
        keep.addAll(protectedPetEggs);
        keep.addAll(keptEquippedItems(player, pet.evolutionStage(), event.getDrops()));
        queueDeathReturns(player, keep);
        clearActivePetAfterDeath(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        List<ItemStack> items = pendingDeathReturns.remove(event.getPlayer().getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ItemStack item : items) {
                if (eggService.isPetEgg(item)) {
                    restoreProtectedPetEgg(event.getPlayer(), item);
                    continue;
                }
                event.getPlayer().getInventory().addItem(item).values()
                    .forEach(leftover -> event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), leftover));
            }
            String notice = pendingDeathCoreNotice.remove(event.getPlayer().getUniqueId());
            if (notice != null) {
                event.getPlayer().sendActionBar(Component.text(notice));
                event.getPlayer().sendMessage(notice);
                if (notice.equals(msg("pet-egg.death-core-broken", "The core has broken."))) {
                    event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.9F, 0.55F);
                    event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_WITHER_DEATH, 0.35F, 0.75F);
                }
            }
            scheduleSync(event.getPlayer());
        });
    }

    private void restoreProtectedPetEgg(Player player, ItemStack item) {
        restoreProtectedPetEgg(player, item, 12);
    }

    private void restoreProtectedPetEgg(Player player, ItemStack item, int attemptsLeft) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (leftovers.isEmpty()) {
            return;
        }
        List<ItemStack> stillBlocked = new ArrayList<>(player.getEnderChest().addItem(leftovers.values().toArray(ItemStack[]::new)).values());
        if (stillBlocked.isEmpty()) {
            player.sendMessage(msg("pet-egg.returned-to-ender", "Your pet core was returned to the ender chest because your inventory was full."));
            return;
        }
        queueDeathReturns(player, stillBlocked);
        scheduleProtectedPetEggRetry(player, attemptsLeft);
        player.sendMessage(msg("pet-egg.return-delayed", "Your pet core is safe, but there is no free inventory or ender chest slot yet."));
    }

    private void scheduleProtectedPetEggRetry(Player player, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            List<ItemStack> items = pendingDeathReturns.remove(player.getUniqueId());
            if (items == null || items.isEmpty()) {
                return;
            }
            for (ItemStack item : items) {
                if (eggService.isPetEgg(item)) {
                    restoreProtectedPetEgg(player, item, attemptsLeft - 1);
                    continue;
                }
                player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
        }, 100L);
    }

    private List<ItemStack> keptEquippedItems(Player player, int evolution, List<ItemStack> drops) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return List.of();
        }
        double chance = config.equippedKeepChance(evolution);
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR) {
                candidates.add(armor.clone());
            }
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.AIR) {
            candidates.add(mainHand.clone());
        }

        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack item : candidates) {
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
                drops.removeIf(drop -> drop.isSimilar(item));
                kept.add(item);
            }
        }
        return kept;
    }

    private List<ItemStack> extractPetEggDrops(List<ItemStack> drops) {
        List<ItemStack> protectedEggs = new ArrayList<>();
        drops.removeIf(drop -> {
            if (!eggService.isPetCoreLikeItem(drop)) {
                return false;
            }
            protectedEggs.add(drop.clone());
            return true;
        });
        return protectedEggs;
    }

    private void queueDeathReturns(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        pendingDeathReturns.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).addAll(items);
    }

    public PetEggService eggService() {
        return eggService;
    }

    private void spawnFirstSummonEffect(Player player, Location summonAnchor) {
        var center = summonAnchor.clone().add(0.0D, 1.15D, 0.0D);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 208, 80), 1.25F);
        player.getWorld().spawnParticle(Particle.DUST, center, 26, 0.28D, 0.35D, 0.28D, dust);
        player.getWorld().spawnParticle(Particle.END_ROD, center, 5, 0.18D, 0.25D, 0.18D, 0.01D);
        player.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.28F, 1.55F);
    }

    private void playFirstSummonFinish(Player player, Location summonAnchor) {
        var center = summonAnchor.clone().add(0.0D, 1.1D, 0.0D);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center, 24, 0.45D, 0.55D, 0.45D, 0.02D);
        player.getWorld().spawnParticle(Particle.END_ROD, center, 18, 0.35D, 0.45D, 0.35D, 0.01D);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.45F);
    }

    private Location captureSummonAnchor(Player player) {
        var eye = player.getEyeLocation();
        var anchor = eye.clone().add(eye.getDirection().normalize().multiply(2.0D)).add(0.0D, -1.35D, 0.0D);
        return anchor.toCenterLocation();
    }

    private String formatRemaining(long millis) {
        long seconds = Math.max(1L, (long) Math.ceil(millis / 1000.0D));
        if (seconds >= 60L) {
            return (long) Math.ceil(seconds / 60.0D) + " " + msg("time.minutes.short", "min.");
        }
        return seconds + " " + msg("time.seconds.short", "sec.");
    }
}
