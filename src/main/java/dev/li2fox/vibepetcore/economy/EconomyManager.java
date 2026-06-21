package dev.li2fox.vibepetcore.economy;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.CoreModule;
import dev.li2fox.vibepetcore.player.PlayerData;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import java.util.UUID;

public final class EconomyManager implements CoreModule {
    private final PlayerDataManager playerDataManager;
    private final AntiAbuseTracker antiAbuseTracker;

    public EconomyManager(PlayerDataManager playerDataManager, BalanceConfig config) {
        this.playerDataManager = playerDataManager;
        this.antiAbuseTracker = new AntiAbuseTracker(config);
    }

    @Override
    public void enable() {
    }

    @Override
    public void disable() {
    }

    public RewardResult award(UUID playerId, long amount, RewardReason reason, String actionKey) {
        if (reason == RewardReason.QUEST) {
            long awarded = Math.max(0L, amount);
            if (awarded > 0L) {
                playerDataManager.getOrLoad(playerId).addPoints(awarded);
            }
            return new RewardResult(amount, awarded, false, false);
        }
        RewardResult result = antiAbuseTracker.limit(playerId, amount, reason.name() + ":" + actionKey);
        if (result.awarded() > 0L) {
            playerDataManager.getOrLoad(playerId).addPoints(result.awarded());
        }
        return result;
    }

    public boolean take(UUID playerId, long amount) {
        return playerDataManager.getOrLoad(playerId).takePoints(amount);
    }

    public long points(UUID playerId) {
        return playerDataManager.getOrLoad(playerId).points();
    }

    public AntiAbuseTracker antiAbuse() {
        return antiAbuseTracker;
    }

    public PlayerData data(UUID playerId) {
        return playerDataManager.getOrLoad(playerId);
    }
}
