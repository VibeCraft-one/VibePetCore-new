package dev.li2fox.vibepetcore.api.impl;

import dev.li2fox.vibepetcore.api.EconomyAPI;
import dev.li2fox.vibepetcore.economy.EconomyManager;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import java.util.UUID;

public final class CoreEconomyAPI implements EconomyAPI {
    private final EconomyManager economyManager;

    public CoreEconomyAPI(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public long points(UUID playerId) {
        return economyManager.points(playerId);
    }

    @Override
    public void addPoints(UUID playerId, long amount) {
        economyManager.data(playerId).addPoints(amount);
    }

    @Override
    public boolean takePoints(UUID playerId, long amount) {
        return economyManager.take(playerId, amount);
    }
}
