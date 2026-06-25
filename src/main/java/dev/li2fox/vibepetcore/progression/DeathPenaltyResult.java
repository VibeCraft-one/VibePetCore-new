package dev.li2fox.vibepetcore.progression;

public record DeathPenaltyResult(
    long xpLost,
    int levelsLost,
    boolean levelRollbackApplied
) {
    public static DeathPenaltyResult none() {
        return new DeathPenaltyResult(0L, 0, false);
    }
}
