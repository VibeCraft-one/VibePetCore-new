package dev.li2fox.vibepetcore.master;

import dev.li2fox.vibepetcore.core.CoreModule;
import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.core.PetDebugLogger;
import dev.li2fox.vibepetcore.core.YamlUtf8IO;
import dev.li2fox.vibepetcore.gui.PetGuiService;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PetMasterManager implements CoreModule, Listener {
    private static final Material SOURCE_BLOCK = Material.CONDUIT;
    private static final double BOX_RADIUS = 8.0D;
    private static final double ACTIVE_RADIUS = 10.0D;
    private static final long TELEPORT_DELAY_TICKS = 100L;
    private static final String LEGACY_ENTITY_TAG = "vibepetcore_pet_master";
    private static final String LEGACY_SECTION_SIGN_MOJIBAKE = "\u0412\u00A7";
    private static final String LEGACY_DOUBLE_SECTION_SIGN_MOJIBAKE = "\u0420\u2019\u0412\u00A7";

    private final JavaPlugin plugin;
    private final PetDebugLogger debugLogger;
    private final File file;
    private final Map<UUID, BukkitTask> pendingTeleports = new LinkedHashMap<>();
    private final Map<UUID, Long> lastGuiOpenMillis = new LinkedHashMap<>();

    private PetGuiService guiService;
    private BukkitTask tickTask;
    private Location masterLocation;
    private Location teleportLocation;
    private Style style = Style.SOURCE;
    private VisualMode visualMode = VisualMode.SOURCE_BLOCK;
    private long tickCounter;

    public enum Style {
        SOURCE("source"),
        CRYSTAL("crystal"),
        BOOK("book"),
        SAGE("sage"),
        KEEPER("keeper"),
        SUMMONER("summoner");

        private final String id;

        Style(String id) {
            this.id = id;
        }

        public String id() {
            return id;
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

    public enum VisualMode {
        SOURCE_BLOCK("source_block");

        private final String id;

        VisualMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<VisualMode> parse(String raw) {
            String normalized = raw == null ? "" : raw.trim().toLowerCase();
            for (VisualMode value : values()) {
                if (value.id.equals(normalized)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }

    public PetMasterManager(JavaPlugin plugin, PetDebugLogger debugLogger) {
        this.plugin = plugin;
        this.debugLogger = debugLogger;
        this.file = new File(plugin.getDataFolder(), "pet-master.yml");
    }

    public void setGuiService(PetGuiService guiService) {
        this.guiService = guiService;
    }

    @Override
    public void enable() {
        load();
        spawnMaster();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @Override
    public void disable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        pendingTeleports.values().forEach(BukkitTask::cancel);
        pendingTeleports.clear();
        lastGuiOpenMillis.clear();
        cleanupLegacyMasterEntities();
    }

    public boolean configured() {
        return masterLocation != null && masterLocation.getWorld() != null;
    }

    public Location location() {
        return masterLocation == null ? null : masterLocation.clone();
    }

    public boolean teleportConfigured() {
        return teleportLocation != null && teleportLocation.getWorld() != null;
    }

    public Location teleportLocation() {
        return teleportLocation == null ? null : teleportLocation.clone();
    }

    public Style style() {
        return style;
    }

    public VisualMode visualMode() {
        return visualMode;
    }

    public void place(Location location) {
        cleanupLegacyMasterEntities();
        if (configured()) {
            removeSourceBlock();
        }
        masterLocation = blockLocation(location);
        visualMode = VisualMode.SOURCE_BLOCK;
        style = Style.SOURCE;
        ensureSourceBlock();
        save();
    }

    public void setTeleportLocation(Location location) {
        teleportLocation = location == null ? null : location.clone();
        save();
    }

    public boolean clearTeleportLocation() {
        if (!teleportConfigured()) {
            return false;
        }
        teleportLocation = null;
        save();
        return true;
    }

    public boolean remove() {
        boolean removed = cleanupLegacyMasterEntities();
        if (configured()) {
            removed = removeSourceBlock() || removed;
            masterLocation = null;
            save();
        }
        return removed;
    }

    public boolean setStyle(String styleId) {
        Optional<Style> parsed = Style.parse(styleId);
        if (parsed.isEmpty()) {
            return false;
        }
        style = parsed.get();
        ensureSourceBlock();
        save();
        return true;
    }

    public boolean setVisualMode(String rawMode) {
        Optional<VisualMode> parsed = VisualMode.parse(rawMode);
        if (parsed.isEmpty()) {
            return false;
        }
        visualMode = parsed.get();
        ensureSourceBlock();
        save();
        return true;
    }

    public void respawn() {
        cleanupLegacyMasterEntities();
        ensureSourceBlock();
    }

    public boolean startSpawnMasterTeleport(Player player) {
        Optional<Location> target = spawnMasterTeleportLocation();
        if (target.isEmpty()) {
            player.closeInventory();
            player.sendMessage(GameText.text(
                "pet-master.teleport.not-placed",
                "Точка телепорта к Источнику питомцев не настроена.",
                "Pet Source teleport point is not configured."
            ));
            return false;
        }
        if (pendingTeleports.containsKey(player.getUniqueId())) {
            player.closeInventory();
            player.sendMessage(GameText.text(
                "pet-master.teleport.already-preparing",
                "Телепорт к Источнику питомцев уже готовится.",
                "Teleport to the Pet Source is already preparing."
            ));
            return true;
        }

        UUID playerId = player.getUniqueId();
        player.closeInventory();
        player.sendMessage(GameText.text(
            "pet-master.teleport.preparing",
            "Телепорт к Источнику питомцев через 5 секунд.",
            "Teleporting to the Pet Source in 5 seconds."
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6F, 1.35F);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> finishSpawnMasterTeleport(playerId), TELEPORT_DELAY_TICKS);
        pendingTeleports.put(playerId, task);
        return true;
    }

    public boolean canOpenHere(Location location, double radius) {
        if (!configured() || location == null || location.getWorld() == null || masterLocation.getWorld() == null) {
            return false;
        }
        if (!masterLocation.getWorld().getName().equals(location.getWorld().getName())) {
            return false;
        }
        double effective = Math.max(radius, BOX_RADIUS);
        return sourceCenter().distanceSquared(location) <= effective * effective;
    }

    public boolean isNearSource(Location location, double radius) {
        if (!configured() || location == null || location.getWorld() == null || masterLocation.getWorld() == null) {
            return false;
        }
        if (!masterLocation.getWorld().getName().equals(location.getWorld().getName())) {
            return false;
        }
        double effective = Math.max(0.0D, radius);
        return sourceCenter().distanceSquared(location) <= effective * effective;
    }

    public void playLootboxCast(Player player) {
        if (!configured() || masterLocation.getWorld() == null) {
            return;
        }
        Location center = sourceCenter();
        World world = center.getWorld();
        world.spawnParticle(Particle.ENCHANT, center, 18, 0.35D, 0.5D, 0.35D, 0.2D);
        world.spawnParticle(Particle.NAUTILUS, center, 12, 0.35D, 0.4D, 0.35D, 0.04D);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 8, 0.18D, 0.3D, 0.18D, 0.01D);
        world.playSound(center, Sound.BLOCK_CONDUIT_ACTIVATE, 0.65F, 1.2F);
        world.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8F, 1.15F);
        if (player.getWorld().equals(world)) {
            player.spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0D, 1.0D, 0.0D), 8, 0.18D, 0.25D, 0.18D, 0.05D);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onSourceInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || !isSourceBlock(clicked.getLocation())) {
            return;
        }
        event.setCancelled(true);
        openMain(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isLegacyMasterEntity(event.getRightClicked())) {
            event.setCancelled(true);
            event.getRightClicked().remove();
            openMain(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (isLegacyMasterEntity(event.getRightClicked())) {
            event.setCancelled(true);
            event.getRightClicked().remove();
            openMain(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (isLegacyMasterEntity(event.getRightClicked())) {
            event.setCancelled(true);
            event.getRightClicked().remove();
            openMain(event.getPlayer());
        }
    }

    private Optional<Location> spawnMasterTeleportLocation() {
        if (!teleportConfigured()) {
            return Optional.empty();
        }
        return Optional.of(teleportLocation.clone());
    }

    private void finishSpawnMasterTeleport(UUID playerId) {
        pendingTeleports.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        Optional<Location> target = spawnMasterTeleportLocation();
        if (target.isEmpty()) {
            player.sendMessage(GameText.text(
                "pet-master.teleport.no-longer-available",
                "Точка телепорта к Источнику питомцев больше недоступна.",
                "Pet Source teleport point is no longer available."
            ));
            return;
        }
        Location destination = target.get();
        if (!player.teleport(destination)) {
            player.sendMessage(GameText.text(
                "pet-master.teleport.failed",
                "Не удалось телепортироваться к Источнику питомцев.",
                "Failed to teleport to the Pet Source."
            ));
            return;
        }
        player.sendMessage(GameText.text(
            "pet-master.teleport.success",
            "Вы у Источника питомцев.",
            "You are at the Pet Source."
        ));
        player.playSound(destination, Sound.BLOCK_BEACON_ACTIVATE, 0.75F, 1.25F);
        destination.getWorld().spawnParticle(Particle.PORTAL, destination.clone().add(0.0D, 1.0D, 0.0D), 28, 0.35D, 0.55D, 0.35D, 0.02D);
    }

    private void openMain(Player player) {
        if (guiService == null) {
            player.sendMessage(GameText.text(
                "pet-master.gui.not-ready",
                "GUI Источника питомцев ещё не готов.",
                "The Pet Source GUI is not ready yet."
            ));
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastGuiOpenMillis.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 250L) {
            return;
        }
        Location center = configured() ? sourceCenter() : player.getLocation().add(0.0D, 1.0D, 0.0D);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.2D, 0.35D, 0.2D, 0.01D);
        player.getWorld().spawnParticle(Particle.NAUTILUS, center, 10, 0.25D, 0.25D, 0.25D, 0.03D);
        player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.7F, 1.2F);
        guiService.open(player, "main");
    }

    private void tick() {
        tickCounter++;
        if (!configured()) {
            return;
        }
        ensureSourceBlock();
        if (!hasNearbyPlayers(sourceCenter(), ACTIVE_RADIUS)) {
            return;
        }
        Location center = sourceCenter();
        World world = center.getWorld();
        world.spawnParticle(Particle.NAUTILUS, center, 8, 0.35D, 0.35D, 0.35D, 0.02D);
        world.spawnParticle(Particle.ENCHANT, center, 6, 0.28D, 0.4D, 0.28D, 0.08D);
        if (tickCounter % 6L == 0L) {
            world.playSound(center, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.35F, 1.4F);
        }
    }

    private boolean hasNearbyPlayers(Location center, double radius) {
        if (center == null || center.getWorld() == null) {
            return false;
        }
        double radiusSquared = radius * radius;
        for (Player player : center.getWorld().getPlayers()) {
            if (!player.isOnline() || player.isDead() || player.getGameMode().name().equalsIgnoreCase("SPECTATOR")) {
                continue;
            }
            if (player.getLocation().distanceSquared(center) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private void spawnMaster() {
        cleanupLegacyMasterEntities();
        ensureSourceBlock();
    }

    private void ensureSourceBlock() {
        if (!configured()) {
            return;
        }
        Block block = masterLocation.getBlock();
        if (block.getType() != SOURCE_BLOCK) {
            block.setType(SOURCE_BLOCK, false);
        }
    }

    private boolean removeSourceBlock() {
        if (!configured()) {
            return false;
        }
        Block block = masterLocation.getBlock();
        if (block.getType() != SOURCE_BLOCK) {
            return false;
        }
        block.setType(Material.AIR, false);
        return true;
    }

    private boolean cleanupLegacyMasterEntities() {
        boolean removed = false;
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (!isLegacyMasterEntity(stand)) {
                    continue;
                }
                stand.remove();
                removed = true;
            }
        }
        return removed;
    }

    private boolean isLegacyMasterEntity(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) {
            return false;
        }
        if (stand.getScoreboardTags().contains(LEGACY_ENTITY_TAG)) {
            return true;
        }
        Component customName = stand.customName();
        String name = customName == null
            ? null
            : PlainTextComponentSerializer.plainText().serialize(customName);
        if (name == null) {
            return false;
        }
        String normalized = name.replace("\u00A7", "")
            .replace(LEGACY_SECTION_SIGN_MOJIBAKE, "")
            .replace(LEGACY_DOUBLE_SECTION_SIGN_MOJIBAKE, "");
        return normalized.equalsIgnoreCase("6Pet Master") || normalized.equalsIgnoreCase("Pet Master");
    }

    private boolean isSourceBlock(Location location) {
        if (!configured() || location == null || location.getWorld() == null || masterLocation.getWorld() == null) {
            return false;
        }
        return location.getWorld().getName().equals(masterLocation.getWorld().getName())
            && location.getBlockX() == masterLocation.getBlockX()
            && location.getBlockY() == masterLocation.getBlockY()
            && location.getBlockZ() == masterLocation.getBlockZ();
    }

    private Location sourceCenter() {
        return masterLocation.clone().add(0.5D, 0.5D, 0.5D);
    }

    private Location blockLocation(Location location) {
        Location block = location.getBlock().getLocation();
        block.setYaw(location.getYaw());
        block.setPitch(location.getPitch());
        return block;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlUtf8IO.load(file);
        style = Style.parse(yaml.getString("style", "source")).orElse(Style.SOURCE);
        visualMode = VisualMode.SOURCE_BLOCK;
        String worldName = yaml.getString("location.world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world != null) {
            masterLocation = new Location(
                world,
                yaml.getDouble("location.x"),
                yaml.getDouble("location.y"),
                yaml.getDouble("location.z"),
                (float) yaml.getDouble("location.yaw"),
                (float) yaml.getDouble("location.pitch")
            );
        }
        String teleportWorldName = yaml.getString("teleport.world");
        World teleportWorld = teleportWorldName == null ? null : Bukkit.getWorld(teleportWorldName);
        if (teleportWorld != null) {
            teleportLocation = new Location(
                teleportWorld,
                yaml.getDouble("teleport.x"),
                yaml.getDouble("teleport.y"),
                yaml.getDouble("teleport.z"),
                (float) yaml.getDouble("teleport.yaw"),
                (float) yaml.getDouble("teleport.pitch")
            );
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("style", style.id());
        yaml.set("visual-mode", visualMode.id());
        if (masterLocation != null && masterLocation.getWorld() != null) {
            yaml.set("location.world", masterLocation.getWorld().getName());
            yaml.set("location.x", masterLocation.getX());
            yaml.set("location.y", masterLocation.getY());
            yaml.set("location.z", masterLocation.getZ());
            yaml.set("location.yaw", masterLocation.getYaw());
            yaml.set("location.pitch", masterLocation.getPitch());
        }
        if (teleportLocation != null && teleportLocation.getWorld() != null) {
            yaml.set("teleport.world", teleportLocation.getWorld().getName());
            yaml.set("teleport.x", teleportLocation.getX());
            yaml.set("teleport.y", teleportLocation.getY());
            yaml.set("teleport.z", teleportLocation.getZ());
            yaml.set("teleport.yaw", teleportLocation.getYaw());
            yaml.set("teleport.pitch", teleportLocation.getPitch());
        }
        try {
            YamlUtf8IO.save(file, yaml);
        } catch (IOException exception) {
            debugLogger.errorRateLimited(
                "pet-source-save",
                "pet-source",
                "Could not save pet-master.yml",
                exception,
                15_000L
            );
        }
    }
}
