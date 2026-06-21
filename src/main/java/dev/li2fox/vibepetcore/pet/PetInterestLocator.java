package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

final class PetInterestLocator {
    private PetInterestLocator() {
    }

    static Optional<Location> interestingLocation(
        Player owner,
        Entity petEntity,
        PetType type,
        BalanceConfig config,
        org.bukkit.util.Vector ownerForward,
        double spawnDistance,
        double followHeight
    ) {
        Optional<Location> hazardEscape = hazardEscapeLocation(owner, petEntity, type, ownerForward, followHeight);
        if (hazardEscape.isPresent()) {
            return hazardEscape;
        }

        Material hand = owner.getInventory().getItemInMainHand().getType();
        if (hand != Material.AIR && (config.isPetFood(type, hand) || config.isPetRareResource(type, hand) || config.isEvolutionItem(hand))) {
            return Optional.of(owner.getLocation().clone().add(ownerForward.clone().multiply(spawnDistance * 0.75D)).add(0.0D, type.flying() ? followHeight : 0.0D, 0.0D));
        }

        List<Entity> nearbyEntities = owner.getNearbyEntities(7.0D, 4.0D, 7.0D);
        if (type == PetType.ALLAY || type == PetType.FOX) {
            Optional<Location> item = nearestInterestingItem(owner, petEntity, type, config, nearbyEntities);
            if (item.isPresent()) {
                return item;
            }
        }

        Optional<Entity> threat = nearestThreat(owner, petEntity, nearbyEntities);
        if (threat.isEmpty()) {
            return Optional.empty();
        }

        Entity enemy = threat.get();
        if (type == PetType.CAT && enemy instanceof Creeper) {
            return Optional.of(PetGeometrySupport.awayFrom(enemy.getLocation(), owner.getLocation(), ownerForward, type.flying() ? followHeight : 0.0D, 3.5D));
        }
        if (type == PetType.RABBIT || type == PetType.PARROT || type == PetType.BAT) {
            return Optional.of(PetGeometrySupport.awayFrom(enemy.getLocation(), owner.getLocation(), ownerForward, type.flying() ? followHeight : 0.0D, 3.0D));
        }
        if (type == PetType.WOLF || type == PetType.BEE || type == PetType.BLAZE) {
            if (!type.flying() && Math.abs(enemy.getLocation().getY() - owner.getLocation().getY()) > 1.8D) {
                return Optional.empty();
            }
            return Optional.of(PetGeometrySupport.between(owner.getLocation(), enemy.getLocation(), type.flying() ? followHeight : 0.0D));
        }
        return Optional.empty();
    }

    private static Optional<Location> nearestInterestingItem(Player owner, Entity petEntity, PetType type, BalanceConfig config, List<Entity> nearbyEntities) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        Location ownerLocation = owner.getLocation();
        for (Entity nearby : nearbyEntities) {
            if (!(nearby instanceof Item item) || item.isDead() || !item.getWorld().equals(owner.getWorld())) {
                continue;
            }
            if (!PetGeometrySupport.withinNearbyBox(ownerLocation, item.getLocation(), 6.0D, 3.5D, 6.0D)) {
                continue;
            }
            if (config.isAutoPickupBlacklisted(item.getItemStack().getType())) {
                continue;
            }
            double distance = item.getLocation().distanceSquared(petEntity.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        Location target = best.getLocation().clone();
        if (type.flying()) {
            target.add(0.0D, 0.75D, 0.0D);
        }
        return Optional.of(target);
    }

    private static Optional<Entity> nearestThreat(Player owner, Entity petEntity, List<Entity> nearbyEntities) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        Location ownerLocation = owner.getLocation();
        for (Entity nearby : nearbyEntities) {
            if (!(nearby instanceof Monster || nearby instanceof Creeper) || nearby.isDead() || !nearby.getWorld().equals(owner.getWorld())) {
                continue;
            }
            if (nearby.getUniqueId().equals(petEntity.getUniqueId())) {
                continue;
            }
            if (nearby.getScoreboardTags().contains(RuntimePet.SPAWN_BYPASS_TAG)) {
                continue;
            }
            double distance = nearby.getLocation().distanceSquared(ownerLocation);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = nearby;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<Location> hazardEscapeLocation(Player owner, Entity petEntity, PetType type, org.bukkit.util.Vector ownerForward, double followHeight) {
        Location petLocation = petEntity.getLocation();
        int baseX = petLocation.getBlockX();
        int baseY = petLocation.getBlockY();
        int baseZ = petLocation.getBlockZ();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 0; y++) {
                for (int z = -1; z <= 1; z++) {
                    Material material = petLocation.getWorld().getBlockAt(baseX + x, baseY + y, baseZ + z).getType();
                    if (PetEnvironmentSupport.isHazard(material)) {
                        return Optional.of(PetGeometrySupport.behindOwner(owner, ownerForward, type.flying() ? followHeight : 0.0D, 1.5D));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
