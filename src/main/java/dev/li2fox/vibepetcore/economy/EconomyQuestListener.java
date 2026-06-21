package dev.li2fox.vibepetcore.economy;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.egg.PetEggService;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.quest.QuestManager;
import dev.li2fox.vibepetcore.quest.QuestType;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public final class EconomyQuestListener implements Listener {
    private final BalanceConfig config;
    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;
    private final QuestManager questManager;
    private final PetEggService petEggService;

    public EconomyQuestListener(BalanceConfig config, PlayerDataManager playerDataManager, EconomyManager economyManager, QuestManager questManager, PetEggService petEggService) {
        this.config = config;
        this.playerDataManager = playerDataManager;
        this.economyManager = economyManager;
        this.questManager = questManager;
        this.petEggService = petEggService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        LivingEntity entity = event.getEntity();
        String type = entity.getType().name();
        boolean boss = entity instanceof Wither || entity instanceof EnderDragon;
        economyManager.award(killer.getUniqueId(), boss ? config.bossKillPoints() : config.defaultKillPoints(), boss ? RewardReason.BOSS : RewardReason.KILL, type);
        playerDataManager.getOrLoad(killer.getUniqueId()).statistics().addKill();
        questManager.record(killer.getUniqueId(), QuestType.KILL_MOB, type, killer, selectedQuestPetId(killer).orElse(null));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        playerDataManager.getOrLoad(event.getPlayer().getUniqueId()).statistics().addBlockBroken();
        questManager.record(event.getPlayer().getUniqueId(), QuestType.BREAK_BLOCK, event.getBlock().getType().name(), event.getPlayer(), selectedQuestPetId(event.getPlayer()).orElse(null));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        if (!economyManager.antiAbuse().shouldRewardActivity(player.getUniqueId(), player.getLocation())) {
            return;
        }
        playerDataManager.getOrLoad(player.getUniqueId()).statistics().addActivityTicks(config.activityRewardIntervalTicks());
        economyManager.award(player.getUniqueId(), config.explorationPoints(), RewardReason.ACTIVITY, player.getWorld().getName());
        questManager.record(player.getUniqueId(), QuestType.EXPLORE, player.getWorld().getName(), player, selectedQuestPetId(player).orElse(null));
    }

    private Optional<UUID> selectedQuestPetId(Player player) {
        Optional<UUID> activePetId = playerDataManager.getOrLoad(player.getUniqueId()).activePetId();
        if (activePetId.isPresent()) {
            return activePetId;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        Optional<OwnedPetData> mainPet = petEggService.readEgg(mainHand);
        if (mainPet.isPresent()) {
            return mainPet.map(OwnedPetData::petId);
        }
        return petEggService.readEgg(player.getInventory().getItemInOffHand()).map(OwnedPetData::petId);
    }
}
