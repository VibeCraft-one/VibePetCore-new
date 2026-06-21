package dev.li2fox.vibepetcore.api;

import java.util.UUID;

public interface EconomyAPI {
    long points(UUID playerId);

    void addPoints(UUID playerId, long amount);

    boolean takePoints(UUID playerId, long amount);
}
