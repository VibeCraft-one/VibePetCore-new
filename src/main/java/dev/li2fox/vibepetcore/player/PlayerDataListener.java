package dev.li2fox.vibepetcore.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerDataListener implements Listener {
    private final PlayerDataManager playerDataManager;

    public PlayerDataListener(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        playerDataManager.load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerDataManager.unload(event.getPlayer().getUniqueId());
    }
}
