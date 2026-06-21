package dev.li2fox.vibepetcore.pet.inventory;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.YamlUtf8IO;
import dev.li2fox.vibepetcore.pet.RuntimePet;
import dev.li2fox.vibepetcore.pet.armor.PetArmorService;
import dev.li2fox.vibepetcore.pet.armor.PetArmorTier;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PetVaultService implements Listener {
    private final JavaPlugin plugin;
    private final BalanceConfig config;
    private final PetArmorService petArmorService;
    private final NamespacedKey petEggKey;
    private final File vaultFolder;
    private final Map<UUID, Integer> openEvolutionStages = new HashMap<>();

    public PetVaultService(JavaPlugin plugin, BalanceConfig config, PetArmorService petArmorService) {
        this.plugin = plugin;
        this.config = config;
        this.petArmorService = petArmorService;
        this.petEggKey = new NamespacedKey(plugin, "pet_egg");
        this.vaultFolder = new File(plugin.getDataFolder(), "pet-vaults");
    }

    public void open(Player player, RuntimePet pet) {
        Inventory inventory = load(pet);
        openEvolutionStages.put(pet.data().petId(), pet.data().evolutionStage());
        player.openInventory(inventory);
    }

    public boolean tryStore(RuntimePet pet, ItemStack stack) {
        return tryStore(pet, stack, true);
    }

    public boolean tryStore(RuntimePet pet, ItemStack stack, boolean sampleMode) {
        if (stack == null || stack.getType().isAir() || isPetCore(stack) || config.isPetAutoLootBlacklisted(pet.type(), stack.getType())) {
            return false;
        }
        Inventory inventory = load(pet);
        if (sampleMode && !containsSample(inventory, stack.getType())) {
            return false;
        }
        ItemStack copy = stack.clone();
        if (!inventory.addItem(copy).isEmpty()) {
            return false;
        }
        save(pet.data().petId(), inventory.getContents());
        return true;
    }

    public boolean wouldBeFull(RuntimePet pet, ItemStack stack, boolean sampleMode) {
        if (stack == null || stack.getType().isAir() || isPetCore(stack) || config.isPetAutoLootBlacklisted(pet.type(), stack.getType())) {
            return false;
        }
        Inventory inventory = load(pet);
        if (sampleMode && !containsSample(inventory, stack.getType())) {
            return false;
        }
        return !inventory.addItem(stack.clone()).isEmpty();
    }

    public int count(RuntimePet pet, Material material) {
        if (material == null || material.isAir()) {
            return 0;
        }
        int amount = 0;
        for (ItemStack item : load(pet).getContents()) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    public boolean consumeOne(RuntimePet pet, Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        Inventory inventory = load(pet);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == material && item.getAmount() > 0) {
                item.setAmount(item.getAmount() - 1);
                inventory.setItem(slot, item.getAmount() <= 0 ? null : item);
                save(pet.data().petId(), inventory.getContents());
                return true;
            }
        }
        return false;
    }

    public Optional<ItemStack> equippedArmor(RuntimePet pet) {
        Inventory inventory = load(pet);
        ItemStack bestArmor = null;
        for (ItemStack item : inventory.getContents()) {
            if (petArmorService.isPetArmor(item) && petArmorService.allowedForEvolution(item, pet.data().evolutionStage())) {
                bestArmor = strongerArmor(bestArmor, item);
            }
        }
        return Optional.ofNullable(bestArmor);
    }

    private Inventory load(RuntimePet pet) {
        int size = config.petVaultSize(pet.data().evolutionStage(), pet.type().name());
        Inventory inventory = Bukkit.createInventory(
            new PetVaultHolder(pet.data().petId()),
            size,
            LegacyComponentSerializer.legacySection().deserialize(config.petVaultTitle() + " - " + pet.data().petName())
        );
        File file = file(pet.data().petId());
        if (!file.exists()) {
            return inventory;
        }
        YamlConfiguration yaml = YamlUtf8IO.load(file);
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = yaml.getItemStack("items." + slot);
            if (item != null) {
                inventory.setItem(slot, item);
            }
        }
        return inventory;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PetVaultHolder holder) {
            int evolution = openEvolutionStages.getOrDefault(holder.petId(), 1);
            ItemStack[] sanitized = sanitizeContents(holder.petId(), event.getInventory().getContents(), evolution, event.getPlayer());
            save(holder.petId(), sanitized);
            openEvolutionStages.remove(holder.petId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof PetVaultHolder)) {
            return;
        }
        boolean placingIntoVault = event.getClickedInventory() != null
            && event.getClickedInventory().equals(topInventory)
            && isPetCore(event.getCursor());
        boolean shiftMovingIntoVault = event.getClickedInventory() != null
            && !event.getClickedInventory().equals(topInventory)
            && event.isShiftClick()
            && isPetCore(event.getCurrentItem());
        boolean hotbarSwapIntoVault = event.getClickedInventory() != null
            && event.getClickedInventory().equals(topInventory)
            && event.getHotbarButton() >= 0
            && event.getWhoClicked() instanceof Player player
            && isPetCore(player.getInventory().getItem(event.getHotbarButton()));
        if (placingIntoVault || shiftMovingIntoVault || hotbarSwapIntoVault) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(config.message(
                    "pet.vault.block-pet-core",
                    "§cЯдра и яйца питомцев нельзя класть в рюкзак питомца."
                ));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof PetVaultHolder) || !isPetCore(event.getOldCursor())) {
            return;
        }
        int topSize = topInventory.getSize();
        boolean touchesVault = event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize);
        if (touchesVault) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(config.message(
                    "pet.vault.block-pet-core",
                    "§cЯдра и яйца питомцев нельзя класть в рюкзак питомца."
                ));
            }
        }
    }

    private ItemStack[] sanitizeContents(UUID petId, ItemStack[] contents, int evolution, HumanEntity viewer) {
        ItemStack[] sanitized = contents.clone();
        int bestArmorSlot = bestAllowedArmorSlot(sanitized, evolution);
        boolean returnedAny = false;
        boolean returnedPetCore = false;
        for (int slot = 0; slot < sanitized.length; slot++) {
            ItemStack item = sanitized[slot];
            if (isPetCore(item)) {
                sanitized[slot] = null;
                returnToViewer(viewer, item);
                returnedAny = true;
                returnedPetCore = true;
                continue;
            }
            if (!petArmorService.isPetArmor(item)) {
                continue;
            }
            if (slot == bestArmorSlot) {
                continue;
            }
            sanitized[slot] = null;
            returnToViewer(viewer, item);
            returnedAny = true;
        }
        if (returnedAny && viewer instanceof Player player) {
            player.sendMessage(returnedPetCore
                ? config.message("pet.vault.pet-core-returned", "§eЯдро питомца возвращено: его нельзя хранить в рюкзаке питомца.")
                : config.message("pet.armor.vault-returned", "§eЛишняя или слишком тяжёлая броня питомца возвращена в инвентарь."));
        }
        return sanitized;
    }

    private int bestAllowedArmorSlot(ItemStack[] contents, int evolution) {
        int bestSlot = -1;
        ItemStack bestArmor = null;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!petArmorService.isPetArmor(item) || !petArmorService.allowedForEvolution(item, evolution)) {
                continue;
            }
            ItemStack stronger = strongerArmor(bestArmor, item);
            if (stronger == item) {
                bestArmor = item;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private ItemStack strongerArmor(ItemStack current, ItemStack candidate) {
        if (candidate == null || candidate.getType().isAir()) {
            return current;
        }
        if (current == null || current.getType().isAir()) {
            return candidate;
        }
        PetArmorTier currentTier = petArmorService.tier(current).orElse(null);
        PetArmorTier candidateTier = petArmorService.tier(candidate).orElse(null);
        if (candidateTier == null) {
            return current;
        }
        if (currentTier == null) {
            return candidate;
        }
        int byProtection = Double.compare(candidateTier.petArmorPoints(), currentTier.petArmorPoints());
        if (byProtection != 0) {
            return byProtection > 0 ? candidate : current;
        }
        return candidateTier.minEvolution() > currentTier.minEvolution() ? candidate : current;
    }

    private boolean isPetCore(ItemStack item) {
        return item != null
            && !item.getType().isAir()
            && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(petEggKey, PersistentDataType.BYTE);
    }

    private void returnToViewer(HumanEntity viewer, ItemStack item) {
        if (!(viewer instanceof Player player) || item == null || item.getType().isAir()) {
            return;
        }
        player.getInventory().addItem(item).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void save(UUID petId, ItemStack[] contents) {
        if (!vaultFolder.exists() && !vaultFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create pet vault folder: " + vaultFolder.getAbsolutePath());
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (int slot = 0; slot < contents.length; slot++) {
            yaml.set("items." + slot, contents[slot]);
        }
        try {
            YamlUtf8IO.save(file(petId), yaml);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save PetVault for " + petId + ": " + exception.getMessage());
        }
    }

    private boolean containsSample(Inventory inventory, Material material) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                return true;
            }
        }
        return false;
    }

    private File file(UUID petId) {
        return new File(vaultFolder, petId + ".yml");
    }
}
