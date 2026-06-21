package dev.li2fox.vibepetcore.progression;

public record FeedResult(
    boolean accepted,
    FeedType feedType,
    ProgressionResult progressionResult,
    EvolutionResult evolutionResult,
    String message
) {
    public static FeedResult rejected(String message) {
        return new FeedResult(false, FeedType.NONE, ProgressionResult.none(), EvolutionResult.notAttempted(), message);
    }
}
