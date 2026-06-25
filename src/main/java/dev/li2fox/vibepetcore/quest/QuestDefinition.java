package dev.li2fox.vibepetcore.quest;

import org.bukkit.Material;

public record QuestDefinition(
    String id,
    String title,
    String category,
    String description,
    QuestType type,
    String target,
    int amount,
    long rewardPoints,
    long rewardPetXp,
    long repeatCooldownMinutes,
    Material icon
) {
    public boolean repeatable() {
        return repeatCooldownMinutes > 0L;
    }

    public int requiredEvolutionStage() {
        if (!"evolution".equalsIgnoreCase(category) || !id.startsWith("evolution_")) {
            return 0;
        }
        try {
            String suffix = id.substring("evolution_".length());
            String stageToken = suffix.contains("_") ? suffix.substring(0, suffix.indexOf('_')) : suffix;
            return Math.max(1, Integer.parseInt(stageToken) - 1);
        } catch (RuntimeException exception) {
            return 0;
        }
    }
}
