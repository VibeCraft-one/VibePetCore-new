package dev.li2fox.vibepetcore.egg;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.PetRarity;
import dev.li2fox.vibepetcore.pet.PetType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PetEggService {
    private static final List<String> APPEARANCE_PROGRESS_KEYS = List.of(
        "axolotl_variant",
        "cat_variant",
        "fox_variant",
        "frog_variant",
        "panda_hidden_gene",
        "panda_main_gene",
        "wolf_variant",
        "rabbit_variant",
        "parrot_variant"
    );

    private final BalanceConfig config;
    private final NamespacedKey markerKey;
    private final NamespacedKey activeButtonKey;
    private final NamespacedKey stateKey;
    private final NamespacedKey uuidKey;
    private final NamespacedKey nameKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey evolutionKey;
    private final NamespacedKey xpKey;
    private final NamespacedKey healthKey;
    private final NamespacedKey maxHealthKey;
    private final NamespacedKey satietyKey;
    private final NamespacedKey durabilityKey;
    private final NamespacedKey inactiveUntilKey;
    private final NamespacedKey autolootKey;
    private final NamespacedKey disabledEffectsKey;
    private final NamespacedKey appearanceProgressKey;
    private final NamespacedKey epochKey;

    public PetEggService(JavaPlugin plugin, BalanceConfig config) {
        this.config = config;
        this.markerKey = new NamespacedKey(plugin, "pet_egg");
        this.activeButtonKey = new NamespacedKey(plugin, "pet_active_button");
        this.stateKey = new NamespacedKey(plugin, "pet_state");
        this.uuidKey = new NamespacedKey(plugin, "pet_uuid");
        this.nameKey = new NamespacedKey(plugin, "pet_name");
        this.typeKey = new NamespacedKey(plugin, "pet_type");
        this.rarityKey = new NamespacedKey(plugin, "pet_rarity");
        this.levelKey = new NamespacedKey(plugin, "pet_level");
        this.evolutionKey = new NamespacedKey(plugin, "pet_evolution");
        this.xpKey = new NamespacedKey(plugin, "pet_xp");
        this.healthKey = new NamespacedKey(plugin, "pet_health");
        this.maxHealthKey = new NamespacedKey(plugin, "pet_max_health");
        this.satietyKey = new NamespacedKey(plugin, "pet_satiety");
        this.durabilityKey = new NamespacedKey(plugin, "pet_durability");
        this.inactiveUntilKey = new NamespacedKey(plugin, "pet_inactive_until");
        this.autolootKey = new NamespacedKey(plugin, "pet_autoloot");
        this.disabledEffectsKey = new NamespacedKey(plugin, "pet_disabled_effects");
        this.appearanceProgressKey = new NamespacedKey(plugin, "pet_appearance");
        this.epochKey = new NamespacedKey(plugin, "pet_epoch");
    }

    public ItemStack createEgg(PetType type, PetRarity rarity, String name) {
        OwnedPetData data = new OwnedPetData(UUID.randomUUID(), null, type.name(), rarity.name());
        data.setPetName(name == null || name.isBlank() ? type.displayName() : name);
        data.setSatiety(config.eggMaxSatiety());
        data.setDurability(config.eggMaxDurability());
        data.setMaxHealth(20.0D);
        data.setHealth(20.0D);
        return writeEgg(null, data);
    }

    public boolean isPetEgg(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    public boolean isPetCoreLikeItem(ItemStack item) {
        return isPetEgg(item) || isLegacyPetCoreVisual(item);
    }

    public boolean isLegacyPetCoreVisual(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        if (!item.getType().name().endsWith("_SPAWN_EGG") && item.getType() != config.activeButtonMaterial()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (containsPetCoreText(meta.displayName())) {
            return true;
        }
        List<Component> lore = meta.lore();
        if (lore == null) {
            return false;
        }
        for (Component line : lore) {
            if (containsPetCoreText(line)) {
                return true;
            }
        }
        return false;
    }

    public boolean isActiveButton(ItemStack item) {
        if (!isPetEgg(item)) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(activeButtonKey, PersistentDataType.BYTE);
    }

    public boolean isEmptyEgg(ItemStack item) {
        if (!isPetEgg(item)) {
            return false;
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return "empty".equalsIgnoreCase(data.getOrDefault(stateKey, PersistentDataType.STRING, "filled"))
            || data.has(activeButtonKey, PersistentDataType.BYTE);
    }

    public Optional<OwnedPetData> readEgg(ItemStack item) {
        if (!isPetEgg(item)) {
            return Optional.empty();
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        if (data.getOrDefault(epochKey, PersistentDataType.INTEGER, 0) != config.eggPurgeEpoch()) {
            return Optional.empty();
        }
        PetType type = PetType.parse(data.getOrDefault(typeKey, PersistentDataType.STRING, "WOLF")).orElse(PetType.WOLF);
        PetRarity rarity = PetRarity.parse(data.getOrDefault(rarityKey, PersistentDataType.STRING, "COMMON"));
        String rawPetId = data.get(uuidKey, PersistentDataType.STRING);
        if (rawPetId == null || rawPetId.isBlank()) {
            return Optional.empty();
        }
        UUID petId;
        try {
            petId = UUID.fromString(rawPetId);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        OwnedPetData pet = new OwnedPetData(petId, null, type.name(), rarity.name());
        pet.setPetName(data.getOrDefault(nameKey, PersistentDataType.STRING, type.displayName()));
        pet.setLevel(data.getOrDefault(levelKey, PersistentDataType.INTEGER, 1));
        pet.setEvolutionStage(data.getOrDefault(evolutionKey, PersistentDataType.INTEGER, 1));
        pet.setXp(data.getOrDefault(xpKey, PersistentDataType.LONG, 0L));
        pet.setMaxHealth(readDouble(data, maxHealthKey, 20.0D));
        pet.setHealth(readDouble(data, healthKey, pet.maxHealth()));
        Double satiety = data.get(satietyKey, PersistentDataType.DOUBLE);
        if (satiety != null) {
            pet.setSatiety(satiety);
        } else {
            Integer legacySatiety = data.get(satietyKey, PersistentDataType.INTEGER);
            pet.setSatiety(legacySatiety == null ? config.eggMaxSatiety() : legacySatiety.doubleValue());
        }
        pet.setDurability(data.getOrDefault(durabilityKey, PersistentDataType.INTEGER, config.eggMaxDurability()));
        pet.setInactiveUntilMillis(data.getOrDefault(inactiveUntilKey, PersistentDataType.LONG, 0L));
        pet.setAutoLootEnabled(data.getOrDefault(autolootKey, PersistentDataType.INTEGER, 1) != 0);
        String disabledEffects = data.getOrDefault(disabledEffectsKey, PersistentDataType.STRING, "");
        for (String effectKey : disabledEffects.split(",")) {
            if (!effectKey.isBlank()) {
                pet.setPassiveEffectEnabled(effectKey.trim(), false);
            }
        }
        readAppearanceProgress(data, pet);
        return Optional.of(pet);
    }

    public ItemStack writeEgg(ItemStack item, OwnedPetData pet) {
        ItemStack egg = new ItemStack(eggMaterial(PetType.parse(pet.petType()).orElse(PetType.WOLF)));
        egg.setAmount(1);
        ItemMeta meta = egg.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.remove(activeButtonKey);
        data.set(stateKey, PersistentDataType.STRING, "filled");
        writePetData(data, pet);
        decorate(meta, pet, false, false);
        egg.setItemMeta(meta);
        return egg;
    }

    public ItemStack writeEmptyEgg(ItemStack item, OwnedPetData pet) {
        ItemStack egg = new ItemStack(eggMaterial(PetType.parse(pet.petType()).orElse(PetType.WOLF)));
        egg.setAmount(1);
        ItemMeta meta = egg.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.remove(activeButtonKey);
        data.set(stateKey, PersistentDataType.STRING, "empty");
        writePetData(data, pet);
        decorate(meta, pet, false, true);
        egg.setItemMeta(meta);
        return egg;
    }

    public ItemStack writeActiveButton(ItemStack item, OwnedPetData pet) {
        ItemStack button = new ItemStack(config.activeButtonMaterial());
        button.setAmount(1);
        ItemMeta meta = button.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(activeButtonKey, PersistentDataType.BYTE, (byte) 1);
        data.set(stateKey, PersistentDataType.STRING, "empty");
        writePetData(data, pet);
        decorate(meta, pet, true, true);
        button.setItemMeta(meta);
        return button;
    }

    public double xpPercent(OwnedPetData pet) {
        return Math.min(100.0D, (pet.xp() * 100.0D) / xpRequired(pet));
    }

    public long xpRequired(OwnedPetData pet) {
        long base = Math.max(1L, config.baseXp());
        double evolutionMultiplier = 1.0D + (pet.evolutionStage() - 1) * 0.35D;
        double levelMultiplier = Math.pow(config.xpMultiplier(), Math.max(0, pet.level() - 1));
        return Math.max(1L, Math.round(base * evolutionMultiplier * levelMultiplier * config.rarityXpMultiplier(pet.rarity())));
    }

    public Material eggMaterial(PetType type) {
        Material material = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return material == null ? Material.WOLF_SPAWN_EGG : material;
    }

    private void writePetData(PersistentDataContainer data, OwnedPetData pet) {
        data.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        data.set(uuidKey, PersistentDataType.STRING, pet.petId().toString());
        data.set(nameKey, PersistentDataType.STRING, pet.petName());
        data.set(typeKey, PersistentDataType.STRING, pet.petType());
        data.set(rarityKey, PersistentDataType.STRING, pet.rarity());
        data.set(levelKey, PersistentDataType.INTEGER, pet.level());
        data.set(evolutionKey, PersistentDataType.INTEGER, pet.evolutionStage());
        data.set(xpKey, PersistentDataType.LONG, pet.xp());
        data.set(healthKey, PersistentDataType.DOUBLE, pet.health());
        data.set(maxHealthKey, PersistentDataType.DOUBLE, pet.maxHealth());
        data.set(satietyKey, PersistentDataType.DOUBLE, pet.satiety());
        data.set(durabilityKey, PersistentDataType.INTEGER, pet.durability());
        data.set(inactiveUntilKey, PersistentDataType.LONG, pet.inactiveUntilMillis());
        data.set(autolootKey, PersistentDataType.INTEGER, pet.autoLootEnabled() ? 1 : 0);
        data.set(disabledEffectsKey, PersistentDataType.STRING, disabledEffects(pet));
        writeAppearanceProgress(data, pet);
        data.set(epochKey, PersistentDataType.INTEGER, config.eggPurgeEpoch());
    }

    private void writeAppearanceProgress(PersistentDataContainer data, OwnedPetData pet) {
        StringBuilder encoded = new StringBuilder();
        for (String key : APPEARANCE_PROGRESS_KEYS) {
            Integer value = pet.progress().get(key);
            if (value == null || value < 0) {
                continue;
            }
            if (encoded.length() > 0) {
                encoded.append(';');
            }
            encoded.append(key).append('=').append(value);
        }
        if (encoded.length() == 0) {
            data.remove(appearanceProgressKey);
            return;
        }
        data.set(appearanceProgressKey, PersistentDataType.STRING, encoded.toString());
    }

    private void readAppearanceProgress(PersistentDataContainer data, OwnedPetData pet) {
        String encoded = data.getOrDefault(appearanceProgressKey, PersistentDataType.STRING, "");
        if (encoded.isBlank()) {
            return;
        }
        for (String entry : encoded.split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator + 1 >= entry.length()) {
                continue;
            }
            String key = entry.substring(0, separator);
            if (!APPEARANCE_PROGRESS_KEYS.contains(key)) {
                continue;
            }
            try {
                int value = Integer.parseInt(entry.substring(separator + 1));
                if (value >= 0) {
                    pet.progress().put(key, value);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy/custom item data.
            }
        }
    }

    private String disabledEffects(OwnedPetData pet) {
        return pet.progress().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("buff_") && entry.getValue() == 0)
            .map(entry -> entry.getKey().substring("buff_".length()))
            .sorted()
            .collect(java.util.stream.Collectors.joining(","));
    }

    private double readDouble(PersistentDataContainer data, NamespacedKey key, double fallback) {
        Double value = data.get(key, PersistentDataType.DOUBLE);
        if (value != null) {
            return value;
        }
        Integer legacyValue = data.get(key, PersistentDataType.INTEGER);
        return legacyValue == null ? fallback : legacyValue.doubleValue();
    }

    private void decorate(ItemMeta meta, OwnedPetData pet, boolean activeButton, boolean emptyEgg) {
        PetRarity rarity = PetRarity.parse(pet.rarity());
        PetType type = PetType.parse(pet.petType()).orElse(PetType.WOLF);
        meta.displayName(legacy(rarityColor(rarity) + pet.petName() + "&7 (" + localizedTypeName(pet) + ")"));
        meta.lore(lore(pet, rarity, activeButton, emptyEgg).stream().map(this::legacy).toList());
        String modelGroup = activeButton ? "active-button" : (emptyEgg ? "empty" : "egg");
        int customModelData = config.eggCustomModelData(type, modelGroup);
        applyCustomModelData(meta, customModelData);
        if (config.eggEnchantedGlint()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    private List<String> lore(OwnedPetData pet, PetRarity rarity, boolean activeButton, boolean emptyEgg) {
        List<String> lore = new ArrayList<>();
        lore.add("&8" + (activeButton ? "Активный питомец" : (emptyEgg ? "Пустое ядро питомца" : "Ядро питомца VibePet")));
        lore.add("&7Редкость: " + rarityColor(rarity) + localizedRarity(rarity));
        if (pet.evolutionStage() >= 5) {
            lore.add("&7Эволюция: &dMAX");
            lore.add("&7Уровень: &dMAX");
        } else {
            lore.add("&7Эволюция: &f" + pet.evolutionStage() + "/5");
            lore.add("&7Уровень: &f" + pet.level() + "/10");
            lore.add("&7Опыт: &f" + Math.round(xpPercent(pet)) + "%");
        }
        lore.add("&7HP: &f" + Math.round(pet.health()) + "/" + Math.round(pet.maxHealth()));
        lore.add("&7Еда: &f" + compactStat(pet.satiety()) + "/" + config.eggMaxSatiety());
        lore.add("&7Прочность ядра: &f" + pet.durability() + "/" + config.eggMaxDurability());
        if (pet.durability() <= 0) {
            lore.add("&cЯдро разрушено. Питомец устал.");
        }
        if (pet.inactiveUntilMillis() > System.currentTimeMillis()) {
            lore.add("&cДоступен через: " + formatRemaining(pet.inactiveUntilMillis() - System.currentTimeMillis()));
        }
        lore.add("");
        if (activeButton || emptyEgg) {
            lore.add("&eПКМ: вернуть питомца в ядро.");
            lore.add("&7Меню питомца: /pet");
        } else {
            lore.add("&eПКМ: выпустить питомца.");
        }
        lore.add("&7Бонусы: &f" + playerBuffSummary(pet));
        lore.add("&7Роль: &f" + petFeatureSummary(pet));
        return lore;
    }
    private String rarityColor(PetRarity rarity) {
        return switch (rarity) {
            case COMMON -> "&a";
            case RARE -> "&e";
            case EPIC -> "&b";
            case LEGENDARY -> "&d";
        };
    }

    private String compactStat(double value) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.000_001D) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private boolean containsPetCoreText(Component component) {
        if (component == null) {
            return false;
        }
        String text = PlainTextComponentSerializer.plainText()
            .serialize(component)
            .toLowerCase(Locale.ROOT);
        return text.contains("vibepet")
            || text.contains("vibe pet")
            || text.contains("ядро питомца")
            || text.contains("пустое ядро")
            || text.contains("pet core");
    }

    private void applyCustomModelData(ItemMeta meta, int customModelData) {
        try {
            Object component = meta.getClass().getMethod("getCustomModelDataComponent").invoke(meta);
            component.getClass()
                .getMethod("setFloats", List.class)
                .invoke(component, customModelData > 0 ? List.of((float) customModelData) : List.of());
            for (java.lang.reflect.Method method : meta.getClass().getMethods()) {
                if (method.getName().equals("setCustomModelDataComponent") && method.getParameterCount() == 1) {
                    method.invoke(meta, component);
                    return;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore when running on older API surfaces where this component is unavailable.
        }
    }

    private String playerBuffSummary(OwnedPetData pet) {
        List<String> buffs = new ArrayList<>();
        switch (PetType.parse(pet.petType()).orElse(PetType.WOLF)) {
            case WOLF -> {
                buffs.add("Сопротивление");
                if (pet.evolutionStage() >= 2) {
                    buffs.add("Сила");
                }
                if (pet.evolutionStage() >= 4) {
                    buffs.add("Поглощение");
                }
            }
            case CAT -> {
                buffs.add("Ночное зрение");
                if (pet.evolutionStage() >= 2) {
                    buffs.add("Скорость");
                }
                if (pet.evolutionStage() >= 4) {
                    buffs.add("Невидимость");
                }
            }
            case ALLAY -> {
                buffs.add("Спешка");
                if (pet.evolutionStage() >= 2) {
                    buffs.add("Скорость");
                }
                if (pet.evolutionStage() >= 4) {
                    buffs.add("Насыщение");
                }
            }
            case FOX -> {
                buffs.add("Спешка");
                if (pet.evolutionStage() >= 2) {
                    buffs.add("Скорость");
                }
                if (pet.evolutionStage() >= 3) {
                    buffs.add("Ночное зрение");
                }
            }
            case RABBIT -> {
                buffs.add("Прыгучесть");
                if (pet.evolutionStage() >= 2) {
                    buffs.add("Скорость");
                }
                if (pet.evolutionStage() >= 4) {
                    buffs.add("Медленное падение");
                }
            }
            case BEE -> {
                buffs.add("Регенерация");
                if (pet.evolutionStage() >= 2) {
                    buffs.add("Спешка");
                }
                if (pet.evolutionStage() >= 4) {
                    buffs.add("Поглощение");
                }
            }
            case PARROT -> {
                buffs.add("Скорость");
                if (pet.evolutionStage() >= 3) {
                    buffs.add("Ночное зрение");
                }
                if (pet.evolutionStage() >= 5) {
                    buffs.add("Медленное падение");
                }
            }
            case BAT -> {
                buffs.add("Ночное зрение");
                if (pet.evolutionStage() >= 3) {
                    buffs.add("Скорость");
                }
                if (pet.evolutionStage() >= 5) {
                    buffs.add("Медленное падение");
                }
            }
            case BLAZE -> {
                buffs.add("Огнестойкость");
                if (pet.evolutionStage() >= 2) {
                    buffs.add("Сопротивление");
                }
                if (pet.evolutionStage() >= 4) {
                    buffs.add("Сила");
                }
            }
            case VEX -> {
                buffs.add("Скорость");
                if (pet.evolutionStage() >= 4) {
                    buffs.add("Сила");
                }
            }
            default -> buffs.add("Поддержка");
        }
        return String.join(", ", buffs);
    }
    private String petFeatureSummary(OwnedPetData pet) {
        return switch (PetType.parse(pet.petType()).orElse(PetType.WOLF)) {
            case WOLF -> "защита игрока и помощь в бою";
            case CAT -> "ночь, осторожность и безопасный отход";
            case ALLAY -> "сбор ресурсов и рюкзак";
            case FOX -> "добыча, скорость и разведка";
            case RABBIT -> "передвижение и лёгкие прыжки";
            case BEE -> "лечение и выживание";
            case PARROT -> "разведка и мобильность";
            case BAT -> "ночная разведка";
            case BLAZE -> "огонь и бой";
            case VEX -> "рывки и резкий урон";
            default -> "поддержка хозяина";
        };
    }
    private String localizedTypeName(OwnedPetData pet) {
        return GameText.petTypeName(PetType.parse(pet.petType()).orElse(PetType.WOLF));
    }

    private String localizedRarity(PetRarity rarity) {
        return switch (rarity) {
            case COMMON -> "Обычный";
            case RARE -> "Редкий";
            case EPIC -> "Эпический";
            case LEGENDARY -> "Легендарный";
        };
    }
    private String formatRemaining(long millis) {
        long seconds = Math.max(1L, (long) Math.ceil(millis / 1000.0D));
        if (seconds >= 60L) {
            long minutes = (long) Math.ceil(seconds / 60.0D);
            return minutes + " мин.";
        }
        return seconds + " сек.";
    }
}
