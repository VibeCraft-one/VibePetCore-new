package dev.li2fox.vibepetcore.pet.armor;

import dev.li2fox.vibepetcore.core.CoreModule;
import dev.li2fox.vibepetcore.core.GameText;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PetArmorService implements CoreModule, Listener {
    private static final int MAX_ENCHANTS = 4;
    private static final double ARMOR_POINT_REDUCTION = 0.05D;
    private static final EnumSet<EntityDamageEvent.DamageCause> FIRE_CAUSES = EnumSet.of(
        EntityDamageEvent.DamageCause.FIRE,
        EntityDamageEvent.DamageCause.FIRE_TICK,
        EntityDamageEvent.DamageCause.LAVA,
        EntityDamageEvent.DamageCause.HOT_FLOOR
    );
    private static final EnumSet<EntityDamageEvent.DamageCause> BLAST_CAUSES = EnumSet.of(
        EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
        EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
    );
    private static final EnumSet<EntityDamageEvent.DamageCause> PROJECTILE_CAUSES = EnumSet.of(
        EntityDamageEvent.DamageCause.PROJECTILE
    );

    private final JavaPlugin plugin;
    private final NamespacedKey armorKey;
    private final NamespacedKey tierKey;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    public PetArmorService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.armorKey = new NamespacedKey(plugin, "pet_armor");
        this.tierKey = new NamespacedKey(plugin, "pet_armor_tier");
    }

    @Override
    public void enable() {
        for (PetArmorTier tier : PetArmorTier.values()) {
            NamespacedKey key = new NamespacedKey(plugin, "pet_armor_" + tier.id());
            ShapedRecipe recipe = new ShapedRecipe(key, createArmor(tier));
            recipe.shape("MMM", "MHM", "MMM");
            recipe.setIngredient('M', tier.recipeMaterial());
            recipe.setIngredient('H', Material.HEART_OF_THE_SEA);
            plugin.getServer().addRecipe(recipe);
            recipeKeys.add(key);
        }
    }

    @Override
    public void disable() {
        for (NamespacedKey key : recipeKeys) {
            plugin.getServer().removeRecipe(key);
        }
        recipeKeys.clear();
    }

    public ItemStack createArmor(PetArmorTier tier) {
        ItemStack item = new ItemStack(tier.itemMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(legacy("&b" + tier.displayName()));
        meta.lore(lore(tier).stream().map(this::legacy).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(armorKey, PersistentDataType.BYTE, (byte) 1);
        data.set(tierKey, PersistentDataType.STRING, tier.id());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPetArmor(ItemStack item) {
        return item != null
            && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(armorKey, PersistentDataType.BYTE);
    }

    public Optional<PetArmorTier> tier(ItemStack item) {
        if (!isPetArmor(item)) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        for (PetArmorTier tier : PetArmorTier.values()) {
            if (tier.id().equalsIgnoreCase(raw)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    public boolean allowedForEvolution(ItemStack item, int evolution) {
        return tier(item).map(tier -> evolution >= tier.minEvolution()).orElse(false);
    }

    public double damageMultiplier(ItemStack item, EntityDamageEvent.DamageCause cause) {
        Optional<PetArmorTier> tier = tier(item);
        if (tier.isEmpty()) {
            return 1.0D;
        }
        double baseReduction = baseReduction(tier.get());
        double enchantReduction = enchantReduction(item, cause);
        return Math.max(0.1D, (1.0D - baseReduction) * (1.0D - enchantReduction));
    }

    public List<String> guideLore() {
        List<String> lore = new ArrayList<>();
        lore.add(t("pet.armor.guide.craft", "&7Крафт: 8 блоков материала вокруг сердца моря.", "&7Craft: 8 material blocks around a heart of the sea."));
        lore.add(t("pet.armor.guide.item", "&7Предмет: &fкастомная броня наутилуса &7с меткой питомца.", "&7Item: &fcustom nautilus armor &7tagged as pet armor."));
        lore.add("");
        for (PetArmorTier tier : PetArmorTier.values()) {
            lore.add(tierLine(tier));
        }
        lore.add("");
        lore.add(t("pet.armor.guide.activate", "&7Для активации положите в рюкзак питомца.", "&7Place it in the pet vault to activate."));
        lore.add(t("pet.armor.guide.limit", "&7Работает максимум одна кольчуга.", "&7Only one chainmail works at a time."));
        lore.add(t("pet.armor.guide.protection", "&7Броня снижает входящий урон, защитные чары усиливают её.", "&7Armor reduces incoming damage, protection enchants improve it."));
        return lore;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack base = inventory.getFirstItem();
        ItemStack book = inventory.getSecondItem();
        if (!isPetArmor(base) || book == null || book.getType() != Material.ENCHANTED_BOOK || !(book.getItemMeta() instanceof EnchantmentStorageMeta bookMeta)) {
            return;
        }
        ItemStack result = base.clone();
        ItemMeta resultMeta = result.getItemMeta();
        int currentEnchantCount = resultMeta.getEnchants().size();
        boolean changed = false;
        for (var entry : bookMeta.getStoredEnchants().entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!isAllowedArmorEnchant(enchantment) || currentEnchantCount >= MAX_ENCHANTS && !resultMeta.hasEnchant(enchantment)) {
                continue;
            }
            int level = Math.min(entry.getValue(), enchantment.getMaxLevel());
            int existing = resultMeta.getEnchantLevel(enchantment);
            if (level > existing) {
                resultMeta.addEnchant(enchantment, level, true);
                if (existing == 0) {
                    currentEnchantCount++;
                }
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        refreshLore(resultMeta, tier(base).orElse(PetArmorTier.COPPER));
        result.setItemMeta(resultMeta);
        event.setResult(result);
        event.getView().setRepairCost(3);
    }

    private double enchantReduction(ItemStack item, EntityDamageEvent.DamageCause cause) {
        ItemMeta meta = item.getItemMeta();
        int levels = meta.getEnchantLevel(Enchantment.PROTECTION);
        if (FIRE_CAUSES.contains(cause)) {
            levels += meta.getEnchantLevel(Enchantment.FIRE_PROTECTION);
        }
        if (BLAST_CAUSES.contains(cause)) {
            levels += meta.getEnchantLevel(Enchantment.BLAST_PROTECTION);
        }
        if (PROJECTILE_CAUSES.contains(cause)) {
            levels += meta.getEnchantLevel(Enchantment.PROJECTILE_PROTECTION);
        }
        return Math.min(0.32D, levels * 0.02D);
    }

    private double baseReduction(PetArmorTier tier) {
        double toughnessBonus = tier == PetArmorTier.NETHERITE ? 0.05D : 0.0D;
        return Math.min(0.55D, tier.petArmorPoints() * ARMOR_POINT_REDUCTION + toughnessBonus);
    }

    private boolean isAllowedArmorEnchant(Enchantment enchantment) {
        return enchantment.equals(Enchantment.PROTECTION)
            || enchantment.equals(Enchantment.FIRE_PROTECTION)
            || enchantment.equals(Enchantment.BLAST_PROTECTION)
            || enchantment.equals(Enchantment.PROJECTILE_PROTECTION);
    }

    private List<String> lore(PetArmorTier tier) {
        List<String> lore = new ArrayList<>();
        lore.add(t("pet.armor.item.quality", "&7Качество: &f{tier}", "&7Quality: &f{tier}", "tier", tierName(tier)));
        lore.add(t("pet.armor.item.evolution", "&7Доступно питомцам: &fE{evolution}+", "&7Available to pets: &fE{evolution}+", "evolution", tier.minEvolution()));
        lore.add(t(
            "pet.armor.item.armor",
            "&7Броня питомца: &f+{armor}",
            "&7Pet armor: &f+{armor}",
            "armor",
            format(tier.petArmorPoints())
        ));
        lore.add(t(
            "pet.armor.item.reduction",
            "&7Снижает входящий урон: &f-{reduction}%",
            "&7Incoming damage reduction: &f-{reduction}%",
            "reduction",
            formatPercent(baseReduction(tier))
        ));
        lore.add("");
        lore.add(t("pet.armor.item.activate", "&7Для активации положите в рюкзак питомца.", "&7Place it in the pet vault to activate."));
        lore.add(t("pet.armor.item.limit", "&7Максимум: &f1 &7кольчуга на питомца.", "&7Maximum: &f1 &7chainmail per pet."));
        lore.add(t("pet.armor.item.enchants", "&7Защитные чары добавляют защиту по своему типу.", "&7Protection enchants add protection by type."));
        lore.add(t("pet.armor.item.enchants-limit", "&8Можно наложить до 4 защитных книг.", "&8Up to 4 protection books can be applied."));
        return lore;
    }

    private void refreshLore(ItemMeta meta, PetArmorTier tier) {
        meta.lore(lore(tier).stream().map(this::legacy).toList());
    }

    private String tierName(PetArmorTier tier) {
        String key = "pet.armor.tier." + tier.id();
        String fallback = switch (tier) {
            case COPPER -> "медная";
            case IRON -> "железная";
            case GOLD -> "золотая";
            case DIAMOND -> "алмазная";
            case NETHERITE -> "незеритовая";
        };
        return GameText.text(key, fallback, tier.name().toLowerCase(Locale.ROOT));
    }

    private String tierLine(PetArmorTier tier) {
        return t("pet.armor.guide.tier-line", "&8- &f{item} &7с E{evolution}", "&8- &f{item} &7from E{evolution}", "item", tier.displayName(), "evolution", tier.minEvolution());
    }

    private String t(String key, String ru, String en, Object... replacements) {
        String text = GameText.text(key, ru, en);
        if (replacements != null) {
            for (int index = 0; index + 1 < replacements.length; index += 2) {
                text = text.replace("{" + replacements[index] + "}", String.valueOf(replacements[index + 1]));
            }
        }
        return text;
    }

    private String format(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private String formatPercent(double value) {
        double percent = value * 100.0D;
        return percent == Math.rint(percent) ? String.valueOf((int) percent) : String.format(Locale.ROOT, "%.1f", percent);
    }

    private Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
