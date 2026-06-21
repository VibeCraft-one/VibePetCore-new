package dev.li2fox.vibepetcore.progression;

public record ProgressionResult(
    long xpAdded,
    int subLevelsGained,
    int levelsGained,
    boolean capped
) {
    public static ProgressionResult none() {
        return new ProgressionResult(0L, 0, 0, false);
    }
}
