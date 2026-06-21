package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.core.PetDebugLogger;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Bukkit;

public final class PetEngineListener implements Listener {
    private final PetEngineManager petEngineManager;
    private final PetDebugLogger debugLogger;

    public PetEngineListener(PetEngineManager petEngineManager, PetDebugLogger debugLogger) {
        this.petEngineManager = petEngineManager;
        this.debugLogger = debugLogger;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onOwnerLeftClickOwnPet(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !petEngineManager.isOwnPetEntity(player, event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        petEngineManager.nudgeOwnPet(player, event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        Entity damager = combatSource(event.getDamager());
        if (damager instanceof Player player && petEngineManager.isOwnPetEntity(player, event.getEntity())) {
            event.setCancelled(true);
            petEngineManager.nudgeOwnPet(player, event.getEntity());
            return;
        }
        if (event.getEntity() instanceof Monster monster) {
            petEngineManager.getPetByEntity(damager).flatMap(RuntimePet::entity).ifPresent(petEntity -> {
                LivingEntity currentTarget = monster.getTarget();
                if (currentTarget == null || currentTarget.getUniqueId().equals(petEntity.getUniqueId())) {
                    monster.setTarget(petEntity);
                }
                if (debugLogger != null) {
                    debugLogger.debugRateLimited(
                        "pet:retaliate:" + monster.getUniqueId() + ":" + petEntity.getUniqueId(),
                        "pet-runtime",
                        "Mob retaliated owner="
                            + (petEngineManager.getPetByEntity(petEntity).map(pet -> pet.data().ownerId().toString()).orElse("unknown"))
                            + " mob=" + monster.getType()
                            + " pet=" + petEntity.getType()
                            + " petId=" + petEntity.getUniqueId()
                            + " target=" + petEntity.getUniqueId(),
                        2_000L
                    );
                }
            });
        }
        if (petEngineManager.getPetByEntity(event.getEntity()).isPresent()) {
            double finalDamage = event.getFinalDamage();
            event.setCancelled(true);
            if (damager instanceof Player player) {
                petEngineManager.onPetDamagedByPlayer(player, event.getEntity(), finalDamage, event.getCause());
            } else {
                petEngineManager.onPetDamagedByEntity(damager, event.getEntity(), finalDamage, event.getCause());
            }
            return;
        }

        if (event.getEntity() instanceof Player player) {
            if (damager instanceof Monster monster) {
                petEngineManager.tryPetCoverTarget(player, monster)
                    .ifPresent(petEntity -> {
                        monster.setTarget(petEntity);
                        if (debugLogger != null) {
                            debugLogger.debugRateLimited(
                                "pet:monster-shift:" + monster.getUniqueId() + ":" + player.getUniqueId(),
                                "pet-runtime",
                                "Redirected monster attack owner=" + player.getName()
                                    + " mob=" + monster.getType()
                                    + " fromPlayer=" + player.getUniqueId()
                                    + " toPet=" + petEntity.getUniqueId(),
                                2_000L
                            );
                        }
                    });
            }
            petEngineManager.onOwnerDamageIncoming(player, event);
            petEngineManager.onOwnerDamaged(player, damager);
            return;
        }

        if (damager instanceof Player player) {
            petEngineManager.onOwnerAttacked(player, event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamagedByEnvironment(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (petEngineManager.getPetByEntity(event.getEntity()).isPresent()) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player player) {
            petEngineManager.onOwnerDamageIncoming(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetCombust(EntityCombustEvent event) {
        if (event instanceof EntityCombustByBlockEvent || event instanceof EntityCombustByEntityEvent) {
            return;
        }
        petEngineManager.getPetByEntity(event.getEntity())
            .filter(pet -> pet.type() == PetType.PHANTOM)
            .flatMap(RuntimePet::entity)
            .ifPresent(entity -> {
                event.setCancelled(true);
                entity.setFireTicks(0);
            });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        petEngineManager.requestFollowUpdate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetInteract(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!petEngineManager.isOwnPetEntity(event.getPlayer(), event.getRightClicked())) {
            return;
        }

        if (event.getPlayer().isSneaking()) {
            petEngineManager.toggleWaitMode(event.getPlayer(), event.getRightClicked());
            event.setCancelled(true);
            return;
        }

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item.getType() != Material.AIR) {
            var feedResult = petEngineManager.feedPet(event.getPlayer(), event.getRightClicked(), item.getType());
            if (feedResult.isPresent()) {
                var result = feedResult.get();
                event.setCancelled(true);
                event.getPlayer().sendMessage(result.message());
                if (result.accepted() && item.getAmount() > 0 && !event.getPlayer().getGameMode().name().equals("CREATIVE")) {
                    item.setAmount(item.getAmount() - 1);
                }
                return;
            }
        }

        if (petEngineManager.openPetVault(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPetPickup(EntityPickupItemEvent event) {
        if (petEngineManager.getPetByEntity(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPetTarget(EntityTargetEvent event) {
        var targetingPet = petEngineManager.getPetByEntity(event.getEntity());
        if (targetingPet.isPresent()) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Monster monster) {
                monster.setTarget(null);
            }
            return;
        }
        if (!(event.getEntity() instanceof Monster monster)) {
            return;
        }
        var targetPet = petEngineManager.getPetByEntity(event.getTarget());
        if (targetPet.isPresent() && !targetPet.get().isAttackTarget(monster)) {
            event.setCancelled(true);
            monster.setTarget(null);
            return;
        }
        if (!(event.getTarget() instanceof Player owner)) {
            return;
        }
        petEngineManager.getPet(owner)
            .flatMap(ignored -> petEngineManager.tryPetCoverTarget(owner, monster))
            .ifPresent(petEntity -> {
                event.setTarget((LivingEntity) petEntity);
                if (debugLogger != null) {
                    debugLogger.debugRateLimited(
                        "pet:target:" + owner.getUniqueId() + ":" + monster.getType().name(),
                        "pet-runtime",
                        "Redirected mob target owner=" + owner.getName()
                            + " mob=" + monster.getType()
                            + " from=" + owner.getType()
                            + " to=" + petEntity.getType()
                            + " pet=" + petEntity.getUniqueId(),
                        2_000L
                    );
                }
            });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent)) {
            return;
        }
        Entity damager = combatSource(damageEvent.getDamager());
        var killingPet = petEngineManager.getPetByEntity(damager);
        if (killingPet.isPresent()) {
            RuntimePet pet = killingPet.get();
            if (pet.isSparringAttack()) {
                return;
            }
            Player owner = Bukkit.getPlayer(pet.data().ownerId());
            if (owner != null && owner.isOnline()) {
                petEngineManager.onPetKillMob(owner, pet, event.getEntity());
            }
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null && killer.isOnline()) {
            petEngineManager.onOwnerKillMob(killer, event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        petEngineManager.onOwnerBlockBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            petEngineManager.onOwnerPickupItem(player, event.getItem());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        petEngineManager.despawnPet(event.getPlayer());
    }

    private Entity combatSource(Entity entity) {
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return entity;
    }
}
