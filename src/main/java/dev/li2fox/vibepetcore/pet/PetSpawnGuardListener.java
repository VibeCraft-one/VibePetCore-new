package dev.li2fox.vibepetcore.pet;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class PetSpawnGuardListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        if (!event.getEntity().getScoreboardTags().contains(RuntimePet.SPAWN_BYPASS_TAG)) {
            return;
        }
        event.setCancelled(false);
    }
}
