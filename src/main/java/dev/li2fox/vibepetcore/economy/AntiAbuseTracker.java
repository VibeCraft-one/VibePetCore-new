package dev.li2fox.vibepetcore.economy;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public final class AntiAbuseTracker {
    private final BalanceConfig config;
    private final Map<UUID, Long> minuteWindowStart = new HashMap<>();
    private final Map<UUID, Long> minutePoints = new HashMap<>();
    private final Map<String, ActionCounter> actionCounters = new HashMap<>();
    private final Map<UUID, Location> lastActiveLocation = new HashMap<>();
    private final Map<UUID, Long> lastActivityRewardTick = new HashMap<>();
    private final Map<UUID, Long> lastSeenTick = new HashMap<>();
    private long nextCleanupTick;

    public AntiAbuseTracker(BalanceConfig config) {
        this.config = config;
    }

    public RewardResult limit(UUID playerId, long amount, String actionKey) {
        long tick = Bukkit.getCurrentTick();
        touch(playerId, tick);
        cleanup(tick);
        long windowStart = minuteWindowStart.getOrDefault(playerId, tick);
        if (tick - windowStart >= 1200L) {
            minuteWindowStart.put(playerId, tick);
            minutePoints.put(playerId, 0L);
        }

        boolean reduced = repeatedAction(playerId, actionKey);
        long adjusted = reduced ? Math.max(1L, Math.round(amount * config.repeatedActionMultiplier())) : amount;
        long used = minutePoints.getOrDefault(playerId, 0L);
        long allowed = Math.max(0L, config.minutePointCap() - used);
        long awarded = Math.min(adjusted, allowed);
        minutePoints.put(playerId, used + awarded);
        return new RewardResult(amount, awarded, awarded < adjusted, reduced);
    }

    public boolean shouldRewardActivity(UUID playerId, Location location) {
        long tick = Bukkit.getCurrentTick();
        touch(playerId, tick);
        cleanup(tick);
        long lastReward = lastActivityRewardTick.getOrDefault(playerId, 0L);
        Location previous = lastActiveLocation.get(playerId);
        if (previous == null || !previous.getWorld().equals(location.getWorld())) {
            lastActiveLocation.put(playerId, location.clone());
            return false;
        }

        if (previous.distance(location) < config.afkDistanceThreshold()) {
            return false;
        }

        lastActiveLocation.put(playerId, location.clone());
        if (tick - lastReward < config.activityRewardIntervalTicks()) {
            return false;
        }

        lastActivityRewardTick.put(playerId, tick);
        return true;
    }

    private boolean repeatedAction(UUID playerId, String actionKey) {
        long tick = Bukkit.getCurrentTick();
        String key = playerId + ":" + actionKey;
        ActionCounter counter = actionCounters.get(key);
        if (counter == null || tick - counter.windowStart() > config.repeatedActionWindowTicks()) {
            actionCounters.put(key, new ActionCounter(tick, 1));
            return false;
        }

        counter.increment();
        return counter.count() > config.repeatedActionSoftLimit();
    }

    private void touch(UUID playerId, long tick) {
        lastSeenTick.put(playerId, tick);
    }

    private void cleanup(long tick) {
        if (tick < nextCleanupTick) {
            return;
        }
        nextCleanupTick = tick + 1200L;

        long stalePlayersBefore = tick - 7200L;
        lastSeenTick.entrySet().removeIf(entry -> entry.getValue() < stalePlayersBefore);
        minuteWindowStart.keySet().retainAll(lastSeenTick.keySet());
        minutePoints.keySet().retainAll(lastSeenTick.keySet());
        lastActiveLocation.keySet().retainAll(lastSeenTick.keySet());
        lastActivityRewardTick.keySet().retainAll(lastSeenTick.keySet());

        long staleActionBefore = tick - Math.max(2400L, config.repeatedActionWindowTicks() * 2L);
        actionCounters.entrySet().removeIf(entry -> entry.getValue().windowStart() < staleActionBefore);
    }

    private static final class ActionCounter {
        private final long windowStart;
        private int count;

        private ActionCounter(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }

        private long windowStart() {
            return windowStart;
        }

        private int count() {
            return count;
        }

        private void increment() {
            count++;
        }
    }
}
