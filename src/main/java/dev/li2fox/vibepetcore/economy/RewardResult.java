package dev.li2fox.vibepetcore.economy;

public record RewardResult(long requested, long awarded, boolean capped, boolean reduced) {
}
