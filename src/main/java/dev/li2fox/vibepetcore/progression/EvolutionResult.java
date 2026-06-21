package dev.li2fox.vibepetcore.progression;

public record EvolutionResult(
    boolean attempted,
    boolean success,
    int newStage,
    double chance
) {
    public static EvolutionResult notAttempted() {
        return new EvolutionResult(false, false, 0, 0.0D);
    }
}
