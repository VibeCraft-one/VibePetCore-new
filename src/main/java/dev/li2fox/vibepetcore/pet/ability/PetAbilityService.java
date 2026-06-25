package dev.li2fox.vibepetcore.pet.ability;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.pet.PetType;
import dev.li2fox.vibepetcore.pet.RuntimePet;
import dev.li2fox.vibepetcore.pet.inventory.PetVaultService;
import dev.li2fox.vibepetcore.pet.skill.PetSkill;
import dev.li2fox.vibepetcore.pet.skill.PetSkillRegistry;
import dev.li2fox.vibepetcore.pet.skill.PetSkillSet;
import dev.li2fox.vibepetcore.pet.skill.SkillActivationResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.block.Block;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class PetAbilityService {
    private static final long LEGENDARY_COOLDOWN_MILLIS = 300_000L;
    private static final long LEGENDARY_DURATION_MILLIS = 14_000L;
    private static final String LEGENDARY_RARITY = "LEGENDARY";
    private static final int PASSIVE_EFFECT_EXTRA_COOLDOWN_TICKS = 240;
    private static final int PASSIVE_SATURATION_COOLDOWN_TICKS = 1_200;

    private final BalanceConfig config;
    private final PetVaultService petVaultService;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, Long> activeLegendaryUltimates = new HashMap<>();
    private final Map<UUID, List<UUID>> activeBeeSwarms = new HashMap<>();
    private final Map<UUID, Long> vaultWarnings = new HashMap<>();
    private final Map<UUID, NearbyCacheEntry> nearbyCache = new HashMap<>();
    private long nextCooldownCleanupTick;
    private long nextVaultCleanupMillis;
    private long nearbyCacheTick = -1L;

    private record PassiveEffect(PotionEffectType type, int duration, int amplifier) {
    }

    public PetAbilityService(BalanceConfig config, PetVaultService petVaultService) {
        this.config = config;
        this.petVaultService = petVaultService;
    }

    public double attackDamage(RuntimePet pet) {
        return attackDamage(pet, null, 1.0D);
    }

    public double attackDamage(RuntimePet pet, Entity target, double patternMultiplier) {
        PetType type = pet.effectiveType();
        double damage = config.combatBaseDamage()
            + (Math.max(1, pet.data().level()) - 1) * config.combatDamagePerLevel()
            + (Math.max(1, pet.data().subLevel()) - 1) * config.combatDamagePerSubLevel()
            + pet.data().evolutionStage() * config.combatDamagePerEvolution();
        damage = damage
            * config.combatEvolutionMultiplier(pet.data().evolutionStage())
            * config.combatRarityMultiplier(pet.data().rarity())
            * config.combatTypeMultiplier(type)
            * Math.max(0.1D, patternMultiplier);
        if (target instanceof Player) {
            damage *= config.combatPvpTypeMultiplier(type);
            damage *= config.combatPvpEvolutionMultiplier(pet.data().evolutionStage());
        }
        damage *= legendaryDamageMultiplier(pet);
        return damage;
    }

    public SkillActivationResult tryPlayerUltimate(Player owner, RuntimePet pet) {
        if (owner == null || pet == null) {
            return SkillActivationResult.NO_PET;
        }
        if (pet.data().isDown()) {
            return SkillActivationResult.PET_DOWN;
        }
        if (pet.data().evolutionStage() < 3) {
            return SkillActivationResult.NOT_READY;
        }
        if (pet.entity().isEmpty() || !pet.entity().get().isValid()) {
            return SkillActivationResult.NO_ENTITY;
        }
        if (pet.type() == PetType.ALLAY) {
            return SkillActivationResult.UNSUPPORTED;
        }
        long now = System.currentTimeMillis();
        String cooldownKey = playerUltimateKey(pet);
        if (cooldowns.getOrDefault(cooldownKey, 0L) > now) {
            return SkillActivationResult.COOLDOWN;
        }

        PetSkill ultimate = PetSkillRegistry.skills(pet.type()).ultimate();
        String title = ultimate == null
            ? "Ultimate"
            : config.message(ultimate.nameKey(), "Ultimate");
        boolean activated = switch (pet.type()) {
            case CAT -> activatePlayerDefensiveUltimate(owner, pet, title, BarColor.WHITE, () -> {
                apply(owner, PotionEffectType.ABSORPTION, 120, 1);
                apply(owner, PotionEffectType.REGENERATION, 100, 0);
            });
            case RABBIT -> activatePlayerDefensiveUltimate(owner, pet, title, BarColor.GREEN, () -> {
                apply(owner, PotionEffectType.SPEED, 120, 1);
                apply(owner, PotionEffectType.JUMP_BOOST, 120, 1);
                apply(owner, PotionEffectType.RESISTANCE, 120, 0);
            });
            case ARMADILLO -> activatePlayerDefensiveUltimate(owner, pet, title, BarColor.YELLOW, () -> {
                apply(owner, PotionEffectType.RESISTANCE, 120, 1);
                apply(owner, PotionEffectType.ABSORPTION, 100, 0);
            });
            case AXOLOTL -> activatePlayerDefensiveUltimate(owner, pet, title, BarColor.BLUE, () -> {
                if (owner.isInWater()) {
                    owner.setRemainingAir(owner.getMaximumAir());
                }
                apply(owner, PotionEffectType.WATER_BREATHING, 200, 0);
                apply(owner, PotionEffectType.REGENERATION, 100, 0);
            });
            case VEX -> activatePlayerDefensiveUltimate(owner, pet, title, BarColor.PURPLE, () -> {
                apply(owner, PotionEffectType.SPEED, 140, 1);
                apply(owner, PotionEffectType.STRENGTH, 100, 0);
                pet.entity().ifPresent(entity -> entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 140, 1, true, true, true)));
            });
            default -> {
                LivingEntity enemy = nearestEnemy(owner);
                if (enemy == null) {
                    yield false;
                }
                yield forcePlayerUltimateAggression(owner, pet, enemy);
            }
        };
        if (!activated) {
            return SkillActivationResult.TOO_EARLY;
        }
        return SkillActivationResult.SUCCESS;
    }

    public long playerUltimateCooldownRemainingMillis(RuntimePet pet) {
        if (pet == null) {
            return 0L;
        }
        long readyAt = cooldowns.getOrDefault(playerUltimateKey(pet), 0L);
        return Math.max(0L, readyAt - System.currentTimeMillis());
    }

    public PetSkillSet skillSet(RuntimePet pet) {
        return pet == null ? PetSkillSet.EMPTY : PetSkillRegistry.skills(pet.type());
    }

    public void tryLegendaryAggression(Player owner, RuntimePet pet, Entity target) {
        if (!legendaryEligible(pet) || target == null || target.isDead()) {
            return;
        }
        switch (pet.type()) {
            case WOLF -> activateLegendary(owner, pet, "Blood Alpha", BarColor.RED, 0.50D, () -> {
                pet.entity().ifPresent(entity -> {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, true, true, true));
                    entity.getWorld().spawnParticle(
                        Particle.DUST,
                        entity.getLocation().add(0.0D, 0.8D, 0.0D),
                        18,
                        0.35D,
                        0.25D,
                        0.35D,
                        new Particle.DustOptions(Color.RED, 1.2F)
                    );
                });
            });
            case FOX -> activateLegendary(owner, pet, "Sly Wound", BarColor.RED, 0.40D, () -> {
            });
            case BLAZE -> activateLegendary(owner, pet, "Firebrand", BarColor.YELLOW, 0.45D, () -> {
            });
            case PANDA -> activateLegendary(owner, pet, "Heavy Paw", BarColor.GREEN, 0.40D, () -> {
            });
            case PHANTOM -> activateLegendary(owner, pet, "Night Terror", BarColor.PURPLE, 0.35D, () -> {
            });
            case BAT -> activateLegendary(owner, pet, "Venom Night", BarColor.PURPLE, 0.40D, () -> {
                pet.entity().ifPresent(entity -> entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, true, true, true)));
            });
            case FROG -> activateLegendary(owner, pet, "Tongue Snap", BarColor.GREEN, 0.40D, () -> {
            });
            case BEE -> activateLegendary(owner, pet, "Royal Swarm", BarColor.YELLOW, 0.50D, () -> {
                spawnBeeSwarm(owner, pet);
            });
            case GHAST -> activateLegendary(owner, pet, "Soul Howl", BarColor.PURPLE, 0.40D, () -> {
            });
            case BREEZE -> activateLegendary(owner, pet, "Wind Volley", BarColor.BLUE, 0.40D, () -> {
            });
            case PARROT -> activateLegendary(owner, pet, "Disrupting Cry", BarColor.BLUE, 0.40D, () -> {
                pulseParrot(owner, pet);
            });
            default -> {
            }
        }
    }

    public void applyLegendaryHitEffects(Player owner, RuntimePet pet, Entity target) {
        if (!legendaryEligible(pet) || !(target instanceof LivingEntity livingTarget) || livingTarget.isDead()) {
            return;
        }
        switch (pet.type()) {
            case FOX -> {
                if (legendaryActive(pet) && ThreadLocalRandom.current().nextDouble() < 0.35D) {
                    livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, true, true, true));
                    livingTarget.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, livingTarget.getLocation().add(0.0D, 0.8D, 0.0D), 6, 0.25D, 0.25D, 0.25D);
                }
            }
            case BLAZE -> {
                if (legendaryActive(pet)) {
                    livingTarget.setFireTicks(Math.max(livingTarget.getFireTicks(), 60));
                }
            }
            case PANDA -> {
                if (legendaryActive(pet) && ThreadLocalRandom.current().nextDouble() < 0.30D) {
                    livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, true, true, true));
                    livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, true, true));
                }
            }
            case PHANTOM -> {
                if (legendaryActive(pet) && ThreadLocalRandom.current().nextDouble() < 0.30D) {
                    PotionEffectType effect = ThreadLocalRandom.current().nextBoolean() ? PotionEffectType.BLINDNESS : PotionEffectType.SLOWNESS;
                    livingTarget.addPotionEffect(new PotionEffect(effect, effect == PotionEffectType.BLINDNESS ? 40 : 80, 0, true, true, true));
                }
            }
            case BAT -> {
                if (legendaryActive(pet)) {
                    livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, true, true, true));
                }
            }
            case FROG -> {
                if (legendaryActive(pet)) {
                    livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, true, true, true));
                    livingTarget.setVelocity(livingTarget.getVelocity().add(livingTarget.getLocation().toVector().subtract(owner.getLocation().toVector()).normalize().multiply(0.22D)));
                }
            }
            case PARROT -> {
                if (legendaryActive(pet)) {
                    removePositiveEffects(livingTarget, 2);
                    livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 120, 0, true, true, true));
                }
            }
            default -> {
            }
        }
    }

    public boolean prefersLegendaryRanged(RuntimePet pet) {
        return legendaryEligible(pet) && legendaryActive(pet) && pet.type() == PetType.GHAST;
    }

    public String legendaryHudMarker(RuntimePet pet, String readyMarker) {
        if (!legendaryEligible(pet)) {
            return readyMarker;
        }
        if (legendaryActive(pet)) {
            return readyMarker;
        }
        long readyAt = cooldowns.getOrDefault(legendaryKey(pet, "cooldown"), 0L);
        long now = System.currentTimeMillis();
        if (readyAt <= now) {
            return readyMarker;
        }
        double progress = 1.0D - ((double) (readyAt - now) / (double) LEGENDARY_COOLDOWN_MILLIS);
        if (progress >= 0.75D) {
            return "75%";
        }
        if (progress >= 0.50D) {
            return "50%";
        }
        if (progress >= 0.25D) {
            return "25%";
        }
        return "0%";
    }

    public void applyLegendaryOwnerDefense(Player owner, RuntimePet pet, EntityDamageEvent event) {
        if (!legendaryEligible(pet) || event == null || event.isCancelled()) {
            return;
        }
        switch (pet.type()) {
            case CAT -> tryNinthLife(owner, pet, event);
            case RABBIT -> tryLastHop(owner, pet, event);
            case ARMADILLO -> tryShellWard(owner, pet, event);
            case AXOLOTL -> tryTideBreath(owner, pet);
            default -> {
            }
        }
    }

    public void onPetKillMob(Player owner, RuntimePet pet, Entity killed) {
        if (!legendaryEligible(pet) || pet.type() != PetType.FOX || killed == null || killed instanceof Player || !legendaryActive(pet)) {
            return;
        }
        Material material = rollFoxTreasure();
        killed.getWorld().dropItemNaturally(killed.getLocation(), new ItemStack(material, 1));
        showLegendary(owner, "Lucky Bite", BarColor.YELLOW);
    }

    public void applyPassiveEffects(Player owner, RuntimePet pet) {
        if (pet.data().satiety() <= 1) {
            return;
        }
        int evolution = Math.max(1, Math.min(5, pet.data().evolutionStage()));
        int rawAmplifier = Math.max(0, (pet.data().level() - 1) / Math.max(1, config.passiveAmplifierPerLevels()));
        int amplifier = Math.min(rawAmplifier, config.passiveAmplifierCap(evolution));
        int duration = balancedPassiveDurationTicks(evolution);
        if (!ready(pet, "passive", Math.max(config.passiveCastCooldownTicks(evolution), duration + 60))) {
            return;
        }

        List<PassiveEffect> candidates = new ArrayList<>();
        switch (pet.type()) {
            case CAT -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.NIGHT_VISION, duration, 0);
            case WOLF -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.RESISTANCE, duration, amplifier);
            case PARROT, VEX -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.SPEED, duration, amplifier);
            case BAT -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.NIGHT_VISION, duration, 0);
            case RABBIT -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.JUMP_BOOST, duration, amplifier);
            case ALLAY -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.HASTE, duration, amplifier);
            case BEE -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.REGENERATION, duration, 0);
            case FOX -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.HASTE, duration, amplifier);
            case BLAZE -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.FIRE_RESISTANCE, duration, 0);
            case AXOLOTL -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.WATER_BREATHING, duration, 0);
            case BREEZE -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.SPEED, duration, amplifier);
            case FROG -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.JUMP_BOOST, duration, amplifier);
            case GHAST, PHANTOM -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.SLOW_FALLING, duration, 0);
            case PANDA, ARMADILLO -> addPassiveCandidate(candidates, owner, pet, PotionEffectType.RESISTANCE, duration, 0);
        }
        addEvolutionSkills(candidates, owner, pet, duration);
        if (candidates.isEmpty()) {
            return;
        }
        PassiveEffect selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        apply(owner, selected.type(), selected.duration(), selected.amplifier());
        trigger(pet, "passive");
        trigger(pet, passiveEffectCooldownKey(selected.type()));
    }

    public void applyDefensiveReaction(Player owner, RuntimePet pet, EntityDamageEvent event) {
        double reduction = config.defenseDamageReductionPercent()
            + (pet.data().level() - 1) * config.defenseReductionPerLevel();
        reduction = Math.min(0.35D, reduction);
        event.setDamage(event.getDamage() * (1.0D - reduction));

        if (ready(pet, "defense", config.defenseReactionCooldownTicks())) {
            apply(owner, PotionEffectType.RESISTANCE, 60, 0);
            trigger(pet, "defense");
        }

        AttributeInstance maxHealthAttribute = owner.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null
            ? 20.0D
            : maxHealthAttribute.getValue();
        if (owner.getHealth() / maxHealth <= config.rescueHealthPercent()
            && ready(pet, "rescue", config.rescueCooldownTicks())) {
            int rescueAmplifier = pet.data().evolutionStage() >= 4 ? 1 : 0;
            apply(owner, PotionEffectType.ABSORPTION, config.rescueShieldDurationTicks(), rescueAmplifier);
            apply(owner, PotionEffectType.REGENERATION, config.rescueRegenerationDurationTicks(), 0);
            trigger(pet, "rescue");
        }
    }

    public void runUtilityTick(Player owner, RuntimePet pet) {
        if (config.worldPetBuffsEnabled(owner.getWorld().getName())) {
            applyPassiveEffects(owner, pet);
        }
        runLegendaryTick(owner, pet);
        if (config.worldPetAutolootEnabled(owner.getWorld().getName()) && config.petAutoLootEnabled(pet.type()) && pet.data().autoLootEnabled()) {
            collectNearbyItems(owner, pet, config.petAutoLootRadius(pet.type()));
        }
    }

    public void onBlockBreak(Player owner, RuntimePet pet, Block block) {
        if ((pet.type() != PetType.RABBIT && pet.type() != PetType.FOX && pet.type() != PetType.ALLAY) || block == null) {
            return;
        }
        if (!ready(pet, "mining", config.miningProcCooldownTicks())) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() > config.miningProcChance()) {
            return;
        }

        trigger(pet, "mining");
        apply(owner, PotionEffectType.HASTE, 70, pet.data().evolutionStage() >= 5 ? 1 : 0);
        if (pet.type() == PetType.FOX && isNaturalDropBlock(block.getType())) {
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(block.getType(), 1));
        }
    }

    public void onPlayerPickup(Player owner, RuntimePet pet, Item item) {
        if (pet.type() != PetType.FOX || item == null || !ready(pet, "fox-loot", config.foxExtraLootCooldownTicks())) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() <= config.foxExtraLootChance()) {
            trigger(pet, "fox-loot");
            ItemStack extra = item.getItemStack().clone();
            extra.setAmount(1);
            owner.getInventory().addItem(extra).values().forEach(leftover -> owner.getWorld().dropItemNaturally(owner.getLocation(), leftover));
        }
    }

    public LivingEntity nearestEnemy(Player owner) {
        double radius = config.aggroRadius();
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        Location ownerLocation = owner.getLocation();

        for (Entity entity : nearbyEntities(owner, radius)) {
            if (!withinNearbyBox(ownerLocation, entity.getLocation(), radius)) {
                continue;
            }
            if (!isHostileTarget(entity)) {
                continue;
            }
            Monster monster = (Monster) entity;
            double distance = monster.getLocation().distanceSquared(ownerLocation);
            if (distance < nearestDistance) {
                nearest = monster;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void collectNearbyItems(Player owner, RuntimePet pet, double radius) {
        if (owner.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Location ownerLocation = owner.getLocation();
        for (Entity entity : nearbyEntities(owner, radius)) {
            if (!withinNearbyBox(ownerLocation, entity.getLocation(), radius)) {
                continue;
            }
            if (!(entity instanceof Item item) || item.isDead() || item.getPickupDelay() > 0) {
                continue;
            }
            if (config.isPetAutoLootBlacklisted(pet.type(), item.getItemStack().getType())) {
                continue;
            }
            boolean sampleMode = config.petAutoLootSampleMode(pet.type());
            if (petVaultService.tryStore(pet, item.getItemStack(), sampleMode)) {
                item.remove();
            } else if (petVaultService.wouldBeFull(pet, item.getItemStack(), sampleMode)) {
                warnVaultFull(owner, pet);
            }
        }
    }

    private void apply(Player player, PotionEffectType type, int duration, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
    }

    private void runLegendaryTick(Player owner, RuntimePet pet) {
        long now = System.currentTimeMillis();
        activeLegendaryUltimates.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (!legendaryEligible(pet) || !legendaryActive(pet)) {
            removeBeeSwarm(pet.data().petId());
            return;
        }
        switch (pet.type()) {
            case BEE -> {
                if (legendaryPulseReady(pet, "swarm", 10)) {
                    pulseBee(owner, pet);
                }
            }
            case GHAST -> {
                if (legendaryPulseReady(pet, "soul-howl", 50)) {
                    pulseGhast(owner, pet);
                }
            }
            case BREEZE -> {
                if (legendaryPulseReady(pet, "wind-volley", 50)) {
                    pulseBreeze(owner, pet);
                }
            }
            case PARROT -> {
                if (legendaryPulseReady(pet, "scout-cry", 60)) {
                    pulseParrot(owner, pet);
                }
            }
            default -> {
            }
        }
    }

    private boolean legendaryPulseReady(RuntimePet pet, String pulse, int cooldownTicks) {
        String key = legendaryKey(pet, "pulse:" + pulse);
        long tick = Bukkit.getCurrentTick();
        if (cooldowns.getOrDefault(key, 0L) > tick) {
            return false;
        }
        cooldowns.put(key, tick + Math.max(1, cooldownTicks));
        return true;
    }

    private void pulseBee(Player owner, RuntimePet pet) {
        LivingEntity target = nearestEnemy(owner);
        if (target == null || !sameWorld(owner, target)) {
            return;
        }
        retargetBeeSwarm(owner, pet, target);
        Location center = pet.entity().map(Entity::getLocation).orElse(owner.getLocation()).add(0.0D, 0.8D, 0.0D);
        owner.getWorld().spawnParticle(Particle.WAX_ON, center, 10, 0.8D, 0.45D, 0.8D, 0.02D);
        target.damage(0.55D, owner);
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 30, 0, true, true, true));
    }

    private void spawnBeeSwarm(Player owner, RuntimePet pet) {
        removeBeeSwarm(pet.data().petId());
        Location base = pet.entity().map(Entity::getLocation).orElse(owner.getLocation()).add(0.0D, 0.7D, 0.0D);
        LivingEntity target = nearestEnemy(owner);
        List<UUID> swarm = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Location spawnLocation = base.clone().add((i - 1) * 0.45D, 0.15D + i * 0.08D, i == 1 ? 0.35D : -0.25D);
            Bee bee = owner.getWorld().spawn(spawnLocation, Bee.class, spawned -> {
                spawned.addScoreboardTag(RuntimePet.SPAWN_BYPASS_TAG);
                spawned.setPersistent(false);
                spawned.setRemoveWhenFarAway(true);
                spawned.setAdult();
                spawned.setAnger(220);
                setAttribute(spawned, Attribute.SCALE, 0.50D);
                setAttribute(spawned, Attribute.ATTACK_DAMAGE, 1.5D);
                setAttribute(spawned, Attribute.MOVEMENT_SPEED, 0.36D);
                setAttribute(spawned, Attribute.FLYING_SPEED, 0.70D);
                if (target != null && sameWorld(owner, target)) {
                    spawned.setTarget(target);
                }
            });
            swarm.add(bee.getUniqueId());
        }
        activeBeeSwarms.put(pet.data().petId(), swarm);
        owner.getWorld().playSound(base, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 0.65F, 1.25F);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(PetAbilityService.class), () -> removeBeeSwarm(pet.data().petId()), 300L);
    }

    private void retargetBeeSwarm(Player owner, RuntimePet pet, LivingEntity target) {
        List<UUID> swarm = activeBeeSwarms.get(pet.data().petId());
        if (swarm == null || swarm.isEmpty() || target == null || target.isDead() || !sameWorld(owner, target)) {
            return;
        }
        swarm.removeIf(beeId -> {
            Entity entity = Bukkit.getEntity(beeId);
            if (!(entity instanceof Bee bee) || bee.isDead() || !sameWorld(owner, bee)) {
                return true;
            }
            bee.setAnger(220);
            bee.setTarget(target);
            steerBeeSwarmMember(owner, bee, target);
            return false;
        });
    }

    private void steerBeeSwarmMember(Player owner, Bee bee, LivingEntity target) {
        Vector direction = target.getLocation().add(0.0D, 0.55D, 0.0D).toVector().subtract(bee.getLocation().toVector());
        double distanceSquared = direction.lengthSquared();
        if (distanceSquared > 0.04D) {
            double speed = distanceSquared > 9.0D ? 0.55D : 0.36D;
            bee.setVelocity(direction.normalize().multiply(speed).setY(Math.max(-0.12D, Math.min(0.28D, direction.getY() * 0.22D))));
        }
        if (distanceSquared <= 2.25D && legendaryPulseReadyForBee(bee, 14)) {
            target.damage(0.45D, owner);
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 30, 0, true, true, true));
            bee.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0D, 0.75D, 0.0D), 3, 0.18D, 0.18D, 0.18D, 0.01D);
        }
    }

    private boolean legendaryPulseReadyForBee(Bee bee, int cooldownTicks) {
        String key = "legendary:bee-swarm-hit:" + bee.getUniqueId();
        long tick = Bukkit.getCurrentTick();
        if (cooldowns.getOrDefault(key, 0L) > tick) {
            return false;
        }
        cooldowns.put(key, tick + Math.max(1, cooldownTicks));
        return true;
    }

    private void removeBeeSwarm(UUID petId) {
        List<UUID> swarm = activeBeeSwarms.remove(petId);
        if (swarm == null) {
            return;
        }
        for (UUID beeId : swarm) {
            Entity entity = Bukkit.getEntity(beeId);
            if (entity instanceof Bee bee && !bee.isDead()) {
                bee.remove();
            }
        }
    }

    private void pulseGhast(Player owner, RuntimePet pet) {
        LivingEntity target = nearestEnemy(owner);
        if (target == null || !sameWorld(owner, target)) {
            return;
        }
        Location source = pet.entity().map(Entity::getLocation).orElse(owner.getLocation()).add(0.0D, 1.2D, 0.0D);
        Location targetLocation = target.getLocation().add(0.0D, 0.8D, 0.0D);
        owner.getWorld().playSound(source, Sound.ENTITY_GHAST_SHOOT, 0.55F, 1.25F);
        drawParticleLine(source, targetLocation, Particle.SOUL_FIRE_FLAME, 10);
        for (Entity nearby : target.getNearbyEntities(2.4D, 1.6D, 2.4D)) {
            if (isHostileTarget(nearby)) {
                Monster monster = (Monster) nearby;
                monster.damage(1.6D, owner);
            }
        }
        target.damage(2.2D, owner);
        owner.getWorld().spawnParticle(Particle.EXPLOSION, targetLocation, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private void pulseBreeze(Player owner, RuntimePet pet) {
        LivingEntity target = nearestEnemy(owner);
        if (target == null || !sameWorld(owner, target)) {
            return;
        }
        Location source = pet.entity().map(Entity::getLocation).orElse(owner.getLocation()).add(0.0D, 0.8D, 0.0D);
        Location targetLocation = target.getLocation().add(0.0D, 0.6D, 0.0D);
        drawParticleLine(source, targetLocation, Particle.GUST, 8);
        Vector knockback = target.getLocation().toVector().subtract(owner.getLocation().toVector());
        if (knockback.lengthSquared() > 0.01D) {
            target.setVelocity(target.getVelocity().add(knockback.normalize().multiply(0.55D).setY(0.28D)));
        }
        target.damage(2.0D, owner);
    }

    private void pulseParrot(Player owner, RuntimePet pet) {
        int highlighted = 0;
        for (Entity entity : nearbyEntities(owner, 9.0D)) {
            if (!(entity instanceof LivingEntity living) || living.isDead() || !sameWorld(owner, living)) {
                continue;
            }
            if (!isHostileTarget(living)) {
                continue;
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 120, 0, true, true, true));
            if (highlighted == 0) {
                removePositiveEffects(living, 2);
            }
            highlighted++;
            if (highlighted >= 6) {
                break;
            }
        }
        if (highlighted > 0) {
            owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_PARROT_AMBIENT, 0.45F, 1.35F);
        }
    }

    private void drawParticleLine(Location from, Location to, Particle particle, int points) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        Vector step = to.toVector().subtract(from.toVector());
        if (step.lengthSquared() < 0.01D) {
            return;
        }
        step.multiply(1.0D / Math.max(1, points));
        Location cursor = from.clone();
        for (int i = 0; i < points; i++) {
            cursor.add(step);
            from.getWorld().spawnParticle(particle, cursor, 1, 0.03D, 0.03D, 0.03D, 0.0D);
        }
    }

    private void removePositiveEffects(LivingEntity target, int limit) {
        int removed = 0;
        for (PotionEffect effect : List.copyOf(target.getActivePotionEffects())) {
            if (!positiveLegendaryEffect(effect.getType())) {
                continue;
            }
            target.removePotionEffect(effect.getType());
            removed++;
            if (removed >= limit) {
                return;
            }
        }
    }

    private boolean positiveLegendaryEffect(PotionEffectType type) {
        return type == PotionEffectType.SPEED
            || type == PotionEffectType.STRENGTH
            || type == PotionEffectType.RESISTANCE
            || type == PotionEffectType.REGENERATION
            || type == PotionEffectType.ABSORPTION
            || type == PotionEffectType.FIRE_RESISTANCE
            || type == PotionEffectType.WATER_BREATHING
            || type == PotionEffectType.INVISIBILITY
            || type == PotionEffectType.HASTE
            || type == PotionEffectType.JUMP_BOOST
            || type == PotionEffectType.SLOW_FALLING;
    }

    private boolean sameWorld(Entity first, Entity second) {
        return first.getWorld().equals(second.getWorld());
    }

    private boolean isHostileTarget(Entity entity) {
        return entity instanceof Monster monster
            && !monster.isDead()
            && !monster.getScoreboardTags().contains(RuntimePet.SPAWN_BYPASS_TAG);
    }

    private double legendaryDamageMultiplier(RuntimePet pet) {
        if (!legendaryEligible(pet) || !legendaryActive(pet)) {
            return 1.0D;
        }
        return switch (pet.type()) {
            case WOLF -> 1.25D;
            default -> 1.0D;
        };
    }

    private boolean legendaryEligible(RuntimePet pet) {
        return pet != null
            && pet.type() != PetType.ALLAY
            && LEGENDARY_RARITY.equalsIgnoreCase(pet.data().rarity())
            && pet.data().evolutionStage() >= 3;
    }

    private boolean legendaryActive(RuntimePet pet) {
        return activeLegendaryUltimates.getOrDefault(legendaryKey(pet, "active"), 0L) > System.currentTimeMillis();
    }

    private void activateLegendary(Player owner, RuntimePet pet, String title, BarColor color, double chance, Runnable action) {
        if (!legendaryEligible(pet)) {
            return;
        }
        long now = System.currentTimeMillis();
        String cooldownKey = legendaryKey(pet, "cooldown");
        if (cooldowns.getOrDefault(cooldownKey, 0L) > now || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        cooldowns.put(cooldownKey, now + LEGENDARY_COOLDOWN_MILLIS);
        activeLegendaryUltimates.put(legendaryKey(pet, "active"), now + LEGENDARY_DURATION_MILLIS);
        action.run();
        showLegendary(owner, title, color);
    }

    private void tryNinthLife(Player owner, RuntimePet pet, EntityDamageEvent event) {
        if (owner.getHealth() - event.getFinalDamage() > 0.0D || !triggerInstantLegendary(owner, pet, "Ninth Life", BarColor.WHITE, 0.45D)) {
            return;
        }
        event.setCancelled(true);
        owner.setHealth(Math.min(Math.max(2.0D, owner.getHealth()), maxHealth(owner)));
        apply(owner, PotionEffectType.REGENERATION, 80, 0);
        apply(owner, PotionEffectType.RESISTANCE, 60, 0);
    }

    private void tryLastHop(Player owner, RuntimePet pet, EntityDamageEvent event) {
        if (owner.getHealth() - event.getFinalDamage() > 4.0D || !triggerInstantLegendary(owner, pet, "Last Hop", BarColor.GREEN, 0.45D)) {
            return;
        }
        if (owner.getHealth() - event.getFinalDamage() <= 0.0D) {
            event.setCancelled(true);
            owner.setHealth(Math.min(Math.max(4.0D, owner.getHealth()), maxHealth(owner)));
        }
        apply(owner, PotionEffectType.SPEED, 120, 1);
        apply(owner, PotionEffectType.JUMP_BOOST, 120, 1);
        apply(owner, PotionEffectType.RESISTANCE, 120, 0);
    }

    private void tryShellWard(Player owner, RuntimePet pet, EntityDamageEvent event) {
        if (event.getFinalDamage() < 4.0D || !triggerInstantLegendary(owner, pet, "Shell Ward", BarColor.YELLOW, 0.40D)) {
            return;
        }
        event.setDamage(event.getDamage() * 0.40D);
        apply(owner, PotionEffectType.RESISTANCE, 100, 0);
        apply(owner, PotionEffectType.GLOWING, 40, 0);
    }

    private void tryTideBreath(Player owner, RuntimePet pet) {
        if (!owner.isInWater() || !triggerInstantLegendary(owner, pet, "Tide Breath", BarColor.BLUE, 0.45D)) {
            return;
        }
        owner.setRemainingAir(owner.getMaximumAir());
        apply(owner, PotionEffectType.WATER_BREATHING, 240, 0);
        apply(owner, PotionEffectType.REGENERATION, 120, 0);
    }

    private boolean triggerInstantLegendary(Player owner, RuntimePet pet, String title, BarColor color, double chance) {
        if (!legendaryEligible(pet)) {
            return false;
        }
        long now = System.currentTimeMillis();
        String cooldownKey = legendaryKey(pet, "cooldown");
        if (cooldowns.getOrDefault(cooldownKey, 0L) > now || ThreadLocalRandom.current().nextDouble() >= chance) {
            return false;
        }
        cooldowns.put(cooldownKey, now + LEGENDARY_COOLDOWN_MILLIS);
        showLegendary(owner, title, color);
        return true;
    }

    private Material rollFoxTreasure() {
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < 0.05D) {
            return Material.DIAMOND;
        }
        if (roll < 0.20D) {
            return Material.GOLD_INGOT;
        }
        if (roll < 0.55D) {
            return Material.AMETHYST_SHARD;
        }
        return Material.EMERALD;
    }

    private double maxHealth(Player player) {
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        return maxHealthAttribute == null ? 20.0D : maxHealthAttribute.getValue();
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private String legendaryKey(RuntimePet pet, String suffix) {
        return pet.data().petId() + ":legendary:" + suffix;
    }

    private String playerUltimateKey(RuntimePet pet) {
        return pet.data().petId() + ":player-ultimate";
    }

    private boolean activatePlayerDefensiveUltimate(Player owner, RuntimePet pet, String title, BarColor color, Runnable action) {
        long now = System.currentTimeMillis();
        cooldowns.put(playerUltimateKey(pet), now + LEGENDARY_COOLDOWN_MILLIS);
        activeLegendaryUltimates.put(legendaryKey(pet, "active"), now + LEGENDARY_DURATION_MILLIS);
        action.run();
        showLegendary(owner, title, color);
        return true;
    }

    private boolean forcePlayerUltimateAggression(Player owner, RuntimePet pet, Entity target) {
        if (target == null || target.isDead()) {
            return false;
        }
        long now = System.currentTimeMillis();
        cooldowns.put(playerUltimateKey(pet), now + LEGENDARY_COOLDOWN_MILLIS);
        activeLegendaryUltimates.put(legendaryKey(pet, "active"), now + LEGENDARY_DURATION_MILLIS);
        switch (pet.type()) {
            case WOLF -> {
                showLegendary(owner, "Blood Alpha", BarColor.RED);
                pet.entity().ifPresent(entity -> entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, true, true, true)));
            }
            case FOX -> showLegendary(owner, "Sly Wound", BarColor.RED);
            case BLAZE -> showLegendary(owner, "Firebrand", BarColor.YELLOW);
            case PANDA -> showLegendary(owner, "Heavy Paw", BarColor.GREEN);
            case PHANTOM -> showLegendary(owner, "Night Terror", BarColor.PURPLE);
            case BAT -> {
                showLegendary(owner, "Venom Night", BarColor.PURPLE);
                pet.entity().ifPresent(entity -> entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, true, true, true)));
            }
            case FROG -> showLegendary(owner, "Tongue Snap", BarColor.GREEN);
            case BEE -> {
                showLegendary(owner, "Royal Swarm", BarColor.YELLOW);
                spawnBeeSwarm(owner, pet);
            }
            case GHAST -> showLegendary(owner, "Soul Howl", BarColor.PURPLE);
            case BREEZE -> showLegendary(owner, "Wind Volley", BarColor.BLUE);
            case PARROT -> {
                showLegendary(owner, "Disrupting Cry", BarColor.BLUE);
                pulseParrot(owner, pet);
            }
            default -> {
                PetSkill ultimate = PetSkillRegistry.skills(pet.type()).ultimate();
                String title = ultimate == null
                    ? "Ultimate"
                    : config.message(ultimate.nameKey(), "Ultimate");
                showLegendary(owner, title, BarColor.WHITE);
            }
        }
        pet.attack(target);
        return true;
    }

    private void showLegendary(Player owner, String title, BarColor color) {
        if (owner == null || !owner.isOnline()) {
            return;
        }
        BossBar bossBar = Bukkit.createBossBar("§d" + title, color, BarStyle.SOLID);
        bossBar.setProgress(1.0D);
        bossBar.addPlayer(owner);
        long durationTicks = Math.max(1L, LEGENDARY_DURATION_MILLIS / 50L);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(PetAbilityService.class), () -> {
            bossBar.removeAll();
        }, durationTicks);
    }

    private void addPassiveCandidate(List<PassiveEffect> candidates, Player player, RuntimePet pet, PotionEffectType type, int duration, int amplifier) {
        if (!pet.data().passiveEffectEnabled(effectKey(type))) {
            return;
        }
        if (!ready(pet, passiveEffectCooldownKey(type), passiveEffectCooldownTicks(type, duration))) {
            return;
        }
        int safeDuration = type == PotionEffectType.SATURATION ? Math.min(duration, 20) : duration;
        candidates.add(new PassiveEffect(type, safeDuration, amplifier));
    }

    private String effectKey(PotionEffectType type) {
        return type.getKey().getKey().toLowerCase(java.util.Locale.ROOT);
    }

    private String passiveEffectCooldownKey(PotionEffectType type) {
        return "passive-effect:" + effectKey(type);
    }

    private int passiveEffectCooldownTicks(PotionEffectType type, int duration) {
        if (type == PotionEffectType.SATURATION) {
            return PASSIVE_SATURATION_COOLDOWN_TICKS;
        }
        return duration + PASSIVE_EFFECT_EXTRA_COOLDOWN_TICKS;
    }

    private int balancedPassiveDurationTicks(int evolution) {
        int configured = config.passiveDurationTicks(evolution);
        int cap = switch (Math.max(1, Math.min(5, evolution))) {
            case 1 -> 80;
            case 2, 3 -> 100;
            case 4 -> 120;
            default -> 140;
        };
        return Math.max(40, Math.min(configured, cap));
    }

    private void addEvolutionSkills(List<PassiveEffect> candidates, Player owner, RuntimePet pet, int duration) {
        int evolution = pet.data().evolutionStage();
        switch (pet.type()) {
            case WOLF -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.STRENGTH, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.ABSORPTION, duration, 0);
            }
            case CAT -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SPEED, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.INVISIBILITY, Math.min(duration, 80), 0);
            }
            case ALLAY -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SPEED, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SATURATION, Math.min(duration, 40), 0);
            }
            case FOX -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SPEED, duration, 0);
                if (evolution >= 3) addPassiveCandidate(candidates, owner, pet, PotionEffectType.NIGHT_VISION, duration, 0);
            }
            case RABBIT -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SPEED, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SLOW_FALLING, duration, 0);
            }
            case BEE -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.HASTE, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.ABSORPTION, duration, 0);
            }
            case PARROT, BAT -> {
                if (evolution >= 3 && pet.type() == PetType.PARROT) addPassiveCandidate(candidates, owner, pet, PotionEffectType.NIGHT_VISION, duration, 0);
                if (evolution >= 3 && pet.type() == PetType.BAT) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SPEED, duration, 0);
                if (evolution >= 5) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SLOW_FALLING, duration, 0);
            }
            case BLAZE -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.RESISTANCE, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.STRENGTH, duration, 0);
            }
            case AXOLOTL -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.REGENERATION, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.ABSORPTION, duration, 0);
            }
            case BREEZE -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.JUMP_BOOST, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SLOW_FALLING, duration, 0);
            }
            case FROG -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SPEED, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.WATER_BREATHING, duration, 0);
            }
            case GHAST -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.FIRE_RESISTANCE, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.STRENGTH, duration, 0);
            }
            case PANDA -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.REGENERATION, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.ABSORPTION, duration, 0);
            }
            case PHANTOM -> {
                if (evolution >= 3) addPassiveCandidate(candidates, owner, pet, PotionEffectType.NIGHT_VISION, duration, 0);
                if (evolution >= 5) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SPEED, duration, 0);
            }
            case ARMADILLO -> {
                if (evolution >= 2) addPassiveCandidate(candidates, owner, pet, PotionEffectType.ABSORPTION, duration, 0);
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.SLOW_FALLING, duration, 0);
            }
            case VEX -> {
                if (evolution >= 4) addPassiveCandidate(candidates, owner, pet, PotionEffectType.STRENGTH, duration, 0);
            }
        }
    }

    private void warnVaultFull(Player owner, RuntimePet pet) {
        long now = System.currentTimeMillis();
        cleanupVaultWarnings(now);
        long readyAt = vaultWarnings.getOrDefault(pet.data().petId(), 0L);
        if (readyAt > now) {
            return;
        }
        vaultWarnings.put(pet.data().petId(), now + 10_000L);
        owner.sendActionBar(Component.text(GameText.text(
            "pet.autoloot.vault-full",
            "Рюкзак питомца заполнен: автоподбор временно пропускает лут.",
            "Pet vault is full: auto-loot is skipping drops for now."
        )));
    }

    private boolean ready(RuntimePet pet, String ability, int cooldownTicks) {
        long tick = Bukkit.getCurrentTick();
        cleanupCooldowns(tick);
        int safeCooldown = Math.max(0, cooldownTicks);
        return tick >= cooldowns.getOrDefault(key(pet, ability), (long) -safeCooldown) + safeCooldown;
    }

    private void trigger(RuntimePet pet, String ability) {
        cooldowns.put(key(pet, ability), (long) Bukkit.getCurrentTick());
    }

    private String key(RuntimePet pet, String ability) {
        UUID petId = pet.data().petId();
        return petId + ":" + ability;
    }

    private void cleanupCooldowns(long tick) {
        if (tick < nextCooldownCleanupTick) {
            return;
        }
        nextCooldownCleanupTick = tick + 1200L;
        long staleBefore = tick - 24_000L;
        cooldowns.entrySet().removeIf(entry -> entry.getValue() < staleBefore);
    }

    private void cleanupVaultWarnings(long now) {
        if (now < nextVaultCleanupMillis) {
            return;
        }
        nextVaultCleanupMillis = now + 30_000L;
        vaultWarnings.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private List<Entity> nearbyEntities(Player owner, double radius) {
        long tick = Bukkit.getCurrentTick();
        if (tick != nearbyCacheTick) {
            nearbyCache.clear();
            nearbyCacheTick = tick;
        }

        Location ownerLocation = owner.getLocation();
        NearbyCacheEntry cached = nearbyCache.get(owner.getUniqueId());
        if (cached != null
            && cached.radius() >= radius
            && cached.worldName().equals(owner.getWorld().getName())
            && sameLocation(cached.location(), ownerLocation)) {
            return cached.entities();
        }

        List<Entity> scanned = List.copyOf(owner.getNearbyEntities(radius, radius, radius));
        nearbyCache.put(owner.getUniqueId(), new NearbyCacheEntry(owner.getWorld().getName(), ownerLocation, radius, scanned));
        return scanned;
    }

    private boolean sameLocation(Location first, Location second) {
        return first.getWorld() != null
            && second.getWorld() != null
            && first.getWorld().equals(second.getWorld())
            && first.getX() == second.getX()
            && first.getY() == second.getY()
            && first.getZ() == second.getZ();
    }

    private boolean withinNearbyBox(Location center, Location candidate, double radius) {
        return Math.abs(candidate.getX() - center.getX()) <= radius
            && Math.abs(candidate.getY() - center.getY()) <= radius
            && Math.abs(candidate.getZ() - center.getZ()) <= radius;
    }

    private boolean isNaturalDropBlock(Material material) {
        return material.isBlock() && material != Material.AIR && !material.name().contains("ORE");
    }

    private record NearbyCacheEntry(String worldName, Location location, double radius, List<Entity> entities) {
    }
}
