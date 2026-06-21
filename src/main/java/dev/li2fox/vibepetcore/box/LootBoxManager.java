package dev.li2fox.vibepetcore.box;

import dev.li2fox.vibepetcore.core.CoreModule;
import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.core.PetDebugLogger;
import dev.li2fox.vibepetcore.core.YamlUtf8IO;
import dev.li2fox.vibepetcore.master.PetMasterManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class LootBoxManager implements CoreModule, Listener {
    private static final double OPEN_RADIUS = 8.0D;
    private static final double REMOVE_RADIUS = 6.0D;
    private static final long ENTITY_REPAIR_INTERVAL_TICKS = 5L;
    private static final String ENTITY_TAG = "vibepetcore_lootbox";

    private final JavaPlugin plugin;
    private final BoxManager boxManager;
    private final PetMasterManager petMasterManager;
    private final PetDebugLogger debugLogger;
    private final File file;
    private final List<BoxPoint> boxes = new ArrayList<>();

    private BukkitTask tickTask;
    private long tickCounter;

    private static final class BoxPoint {
        private final UUID id;
        private final Location location;
        private Style style;
        private UUID baseStandId;
        private UUID interactionId;

        private BoxPoint(UUID id, Location location, Style style) {
            this.id = id;
            this.location = location;
            this.style = style;
        }
    }

    public enum Style {
        ARCANE("arcane", "Arcane Altar", Material.ENDER_CHEST, Material.AMETHYST_SHARD, Color.fromRGB(0x78, 0x5E, 0xC6), Particle.ENCHANT),
        NATURE("nature", "Nature Altar", Material.MOSS_BLOCK, Material.GLOW_BERRIES, Color.fromRGB(0x58, 0x9C, 0x52), Particle.HAPPY_VILLAGER),
        ROYAL("royal", "Royal Altar", Material.BLUE_GLAZED_TERRACOTTA, Material.GOLD_INGOT, Color.fromRGB(0xE3, 0xB5, 0x51), Particle.END_ROD);

        private final String id;
        private final String title;
        private final Material baseBlock;
        private final Material focusItem;
        private final Color accentColor;
        private final Particle idleParticle;

        Style(String id, String title, Material baseBlock, Material focusItem, Color accentColor, Particle idleParticle) {
            this.id = id;
            this.title = title;
            this.baseBlock = baseBlock;
            this.focusItem = focusItem;
            this.accentColor = accentColor;
            this.idleParticle = idleParticle;
        }

        public String id() {
            return id;
        }

        public String title() {
            return title;
        }

        public Material baseBlock() {
            return baseBlock;
        }

        public Material focusItem() {
            return focusItem;
        }

        public Color accentColor() {
            return accentColor;
        }

        public Particle idleParticle() {
            return idleParticle;
        }

        public static Optional<Style> parse(String raw) {
            String normalized = raw == null ? "" : raw.trim().toLowerCase();
            for (Style value : values()) {
                if (value.id.equals(normalized)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }

    public LootBoxManager(JavaPlugin plugin, BoxManager boxManager, PetMasterManager petMasterManager, PetDebugLogger debugLogger) {
        this.plugin = plugin;
        this.boxManager = boxManager;
        this.petMasterManager = petMasterManager;
        this.debugLogger = debugLogger;
        this.file = new File(plugin.getDataFolder(), "lootboxes.yml");
    }

    @Override
    public void enable() {
        load();
        respawnAll();
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @Override
    public void disable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        despawnAll();
    }

    public boolean set(Location location, String styleId) {
        Optional<Style> parsed = Style.parse(styleId);
        if (parsed.isEmpty()) {
            return false;
        }
        BoxPoint existing = nearestPoint(location, 2.0D).orElse(null);
        if (existing != null) {
            existing.location.setWorld(location.getWorld());
            existing.location.setX(location.getX());
            existing.location.setY(location.getY());
            existing.location.setZ(location.getZ());
            existing.location.setYaw(location.getYaw());
            existing.location.setPitch(location.getPitch());
            existing.style = parsed.get();
            spawn(existing);
        } else {
            boxes.add(new BoxPoint(UUID.randomUUID(), location.clone(), parsed.get()));
            spawn(boxes.get(boxes.size() - 1));
        }
        save();
        return true;
    }

    public boolean removeNearest(Location location) {
        Optional<BoxPoint> nearest = nearestPoint(location, REMOVE_RADIUS);
        if (nearest.isEmpty()) {
            return false;
        }
        despawn(nearest.get());
        boxes.remove(nearest.get());
        save();
        return true;
    }

    public boolean setNearestStyle(Location location, String styleId) {
        Optional<Style> parsed = Style.parse(styleId);
        Optional<BoxPoint> nearest = nearestPoint(location, REMOVE_RADIUS);
        if (parsed.isEmpty() || nearest.isEmpty()) {
            return false;
        }
        nearest.get().style = parsed.get();
        spawn(nearest.get());
        save();
        return true;
    }

    public void respawnAll() {
        for (BoxPoint box : boxes) {
            spawn(box);
        }
    }

    public List<String> locations() {
        List<String> lines = new ArrayList<>();
        for (BoxPoint box : boxes) {
            Location loc = box.location;
            lines.add(box.style.id() + " -> " + loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
        }
        return lines;
    }

    public boolean canOpenHere(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        double radiusSquared = radius * radius;
        for (BoxPoint box : boxes) {
            if (box.location.getWorld() == null || !box.location.getWorld().getName().equals(location.getWorld().getName())) {
                continue;
            }
            if (box.location.clone().add(0.0D, 0.8D, 0.0D).distanceSquared(location) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    public BoxOpenResult openNearby(Player player) {
        Optional<BoxPoint> nearest = nearestPoint(player.getLocation(), OPEN_RADIUS);
        boolean nearMaster = petMasterManager.canOpenHere(player.getLocation(), OPEN_RADIUS);
        if (nearest.isEmpty() && !nearMaster) {
            return new BoxOpenResult(false, GameText.text(
                "box.open.location-required",
                "Источник можно открыть рядом с Источником питомцев или у старого алтаря.",
                "The source can only be opened next to the Pet Source or at a legacy altar."
            ), null, null, false);
        }
        BoxOpenResult result = boxManager.openBasic(player);
        nearest.ifPresentOrElse(
            box -> playBoxEffect(player, box, result.success()),
            () -> playMasterBoxEffect(player, result.success())
        );
        return result;
    }

    public BoxOpenResult openAtMaster(Player player) {
        if (!petMasterManager.canOpenHere(player.getLocation(), OPEN_RADIUS)) {
            return new BoxOpenResult(false, GameText.text(
                "box.open.master-required",
                "Подойдите к Источнику питомцев, чтобы открыть награду.",
                "Stand next to the Pet Source to open a reward."
            ), null, null, false);
        }
        BoxOpenResult result = boxManager.openBasic(player);
        playMasterBoxEffect(player, result.success());
        return result;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (handleInteraction(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (handleInteraction(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private boolean handleInteraction(Player player, Entity entity) {
        BoxPoint box = boxByInteraction(entity.getUniqueId()).orElse(null);
        if (box == null) {
            return false;
        }
        BoxOpenResult result = boxManager.openBasic(player);
        player.sendMessage(result.message());
        playBoxEffect(player, box, result.success());
        return true;
    }

    private void playBoxEffect(Player player, BoxPoint box, boolean success) {
        Location center = box.location.clone().add(0.0D, 1.1D, 0.0D);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        if (petMasterManager.canOpenHere(center, 8.0D)) {
            petMasterManager.playLootboxCast(player);
        }
        if (success) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 12, 0.2D, 0.3D, 0.2D, 0.02D);
            world.spawnParticle(box.style.idleParticle(), center, 10, 0.25D, 0.3D, 0.25D, 0.02D);
            world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.2F);
            world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.65F, 1.25F);
        } else {
            world.playSound(center, Sound.BLOCK_CHEST_LOCKED, 0.8F, 0.8F);
        }
    }

    private void playMasterBoxEffect(Player player, boolean success) {
        petMasterManager.playLootboxCast(player);
        player.playSound(player.getLocation(), success ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_CHEST_LOCKED, 0.8F, success ? 1.25F : 0.8F);
    }

    private void tick() {
        tickCounter++;
        boolean repairTick = tickCounter % ENTITY_REPAIR_INTERVAL_TICKS == 0L;
        Iterator<BoxPoint> iterator = boxes.iterator();
        while (iterator.hasNext()) {
            BoxPoint box = iterator.next();
            Interaction interaction = interactionEntity(box);
            if ((interaction == null || !interaction.isValid()) && repairTick) {
                spawn(box);
                interaction = interactionEntity(box);
            }
            if (interaction == null || !interaction.isValid()) {
                continue;
            }
        }
    }

    private void spawn(BoxPoint box) {
        despawn(box);
        World world = box.location.getWorld();
        if (world == null) {
            return;
        }
        cleanupOldVisuals(box);

        ArmorStand base = world.spawn(box.location.clone().add(0.0D, 0.2D, 0.0D), ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(false);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCollidable(false);
            stand.setPersistent(false);
            stand.setCustomNameVisible(true);
            stand.customName(Component.text(box.style.title(), NamedTextColor.LIGHT_PURPLE));
            stand.getEquipment().setHelmet(new ItemStack(box.style.baseBlock()));
            stand.getEquipment().setChestplate(coloredChest(box.style.accentColor()));
            stand.addScoreboardTag(ENTITY_TAG);
        });


        Interaction interaction = world.spawn(box.location.clone().add(0.0D, 0.9D, 0.0D), Interaction.class, hitbox -> {
            hitbox.setInteractionWidth(1.4F);
            hitbox.setInteractionHeight(1.8F);
            hitbox.setResponsive(true);
            hitbox.setPersistent(false);
            hitbox.addScoreboardTag(ENTITY_TAG);
        });

        box.baseStandId = base.getUniqueId();
        box.interactionId = interaction.getUniqueId();
    }

    private ItemStack coloredChest(Color color) {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void despawnAll() {
        for (BoxPoint box : boxes) {
            despawn(box);
        }
    }

    private void despawn(BoxPoint box) {
        removeEntity(box.baseStandId);
        removeEntity(box.interactionId);
        box.baseStandId = null;
        box.interactionId = null;
    }

    private void removeEntity(UUID entityId) {
        if (entityId == null) {
            return;
        }
        Entity entity = plugin.getServer().getEntity(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    private Interaction interactionEntity(BoxPoint box) {
        Entity entity = box.interactionId == null ? null : plugin.getServer().getEntity(box.interactionId);
        return entity instanceof Interaction interaction ? interaction : null;
    }

    private void cleanupOldVisuals(BoxPoint box) {
        World world = box.location.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(box.location, 2.0D, 2.5D, 2.0D)) {
            if (entity.getUniqueId().equals(box.baseStandId) || entity.getUniqueId().equals(box.interactionId)) {
                continue;
            }
            if (entity.getScoreboardTags().contains(ENTITY_TAG) || isLegacyLootboxEntity(entity, box)) {
                entity.remove();
            }
        }
    }

    private boolean isLegacyLootboxEntity(Entity entity, BoxPoint box) {
        if (entity instanceof Interaction) {
            return true;
        }
        if (!(entity instanceof ArmorStand stand)) {
            return false;
        }
        ItemStack helmet = stand.getEquipment() == null ? null : stand.getEquipment().getHelmet();
        Material helmetType = helmet == null ? Material.AIR : helmet.getType();
        if (helmetType == box.style.baseBlock()) {
            return true;
        }
        if (helmetType == Material.AMETHYST_SHARD || helmetType == Material.GLOW_BERRIES || helmetType == Material.GOLD_INGOT) {
            return true;
        }
        Component customName = stand.customName();
        String name = customName == null
            ? null
            : PlainTextComponentSerializer.plainText().serialize(customName);
        return name != null && name.contains(box.style.title());
    }

    private Optional<BoxPoint> boxByInteraction(UUID entityId) {
        return boxes.stream().filter(box -> entityId.equals(box.interactionId)).findFirst();
    }

    private Optional<BoxPoint> nearestPoint(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        BoxPoint nearest = null;
        double best = radius * radius;
        for (BoxPoint box : boxes) {
            if (box.location.getWorld() == null || !box.location.getWorld().getName().equals(location.getWorld().getName())) {
                continue;
            }
            double distance = box.location.distanceSquared(location);
            if (distance <= best) {
                best = distance;
                nearest = box;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        boxes.clear();
        YamlConfiguration yaml = YamlUtf8IO.load(file);
        for (Object raw : yaml.getMapList("boxes")) {
            if (!(raw instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            try {
                World world = plugin.getServer().getWorld(String.valueOf(map.get("world")));
                if (world == null) {
                    continue;
                }
                String styleId = String.valueOf(map.containsKey("style") ? map.get("style") : "arcane");
                Style style = Style.parse(styleId).orElse(Style.ARCANE);
                UUID id = UUID.fromString(String.valueOf(map.get("id")));
                String yaw = String.valueOf(map.containsKey("yaw") ? map.get("yaw") : 0.0D);
                String pitch = String.valueOf(map.containsKey("pitch") ? map.get("pitch") : 0.0D);
                Location location = new Location(
                    world,
                    Double.parseDouble(String.valueOf(map.get("x"))),
                    Double.parseDouble(String.valueOf(map.get("y"))),
                    Double.parseDouble(String.valueOf(map.get("z"))),
                    Float.parseFloat(yaw),
                    Float.parseFloat(pitch)
                );
                boxes.add(new BoxPoint(id, location, style));
            } catch (RuntimeException exception) {
                debugLogger.errorRateLimited(
                    "lootbox-load:" + String.valueOf(map.get("id")),
                    "lootbox",
                    "Skipped broken lootbox record from lootboxes.yml",
                    exception,
                    15_000L
                );
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<java.util.Map<String, Object>> serialized = new ArrayList<>();
        for (BoxPoint box : boxes) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", box.id.toString());
            map.put("style", box.style.id());
            map.put("world", box.location.getWorld().getName());
            map.put("x", box.location.getX());
            map.put("y", box.location.getY());
            map.put("z", box.location.getZ());
            map.put("yaw", box.location.getYaw());
            map.put("pitch", box.location.getPitch());
            serialized.add(map);
        }
        yaml.set("boxes", serialized);
        try {
            YamlUtf8IO.save(file, yaml);
        } catch (IOException exception) {
            debugLogger.errorRateLimited(
                "lootbox-save",
                "lootbox",
                "Could not save lootboxes.yml",
                exception,
                15_000L
            );
        }
    }
}
