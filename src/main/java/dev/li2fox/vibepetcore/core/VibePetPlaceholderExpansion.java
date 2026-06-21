package dev.li2fox.vibepetcore.core;

import dev.li2fox.vibepetcore.player.PlayerData;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.pet.PetEngineManager;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VibePetPlaceholderExpansion extends PlaceholderExpansion {
    private final VibePetCorePlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final PetEngineManager petEngineManager;

    public VibePetPlaceholderExpansion(VibePetCorePlugin plugin, PlayerDataManager playerDataManager, PetEngineManager petEngineManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.petEngineManager = petEngineManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "vibepet";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Li2Fox";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull String onPlaceholderRequest(@Nullable Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        PlayerData data = playerDataManager.getOrLoad(player.getUniqueId());
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "points" -> Long.toString(data.points());
            case "pets" -> Integer.toString(data.pets().size());
            case "quests" -> Long.toString(data.statistics().questsCompleted());
            case "boxes" -> Long.toString(data.statistics().boxesOpened());
            case "activity" -> Long.toString(data.statistics().activityTicks());
            case "pet_name" -> petEngineManager.getPet(player).map(pet -> pet.data().petName()).orElse("-");
            case "pet_type" -> petEngineManager.getPet(player).map(pet -> pet.type().name()).orElse("-");
            case "pet_rarity" -> petEngineManager.getPet(player).map(pet -> pet.data().rarity()).orElse("-");
            case "pet_evolution" -> petEngineManager.getPet(player).map(pet -> pet.data().evolutionStage() >= 5 ? "MAX" : pet.data().evolutionStage() + "/5").orElse("-");
            case "pet_level" -> petEngineManager.getPet(player).map(pet -> pet.data().evolutionStage() >= 5 ? "MAX" : pet.data().level() + "/10").orElse("-");
            case "pet_bond" -> petEngineManager.getPet(player).map(pet -> pet.data().bond() + "/" + 10).orElse("-");
            case "pet_bond_raw" -> petEngineManager.getPet(player).map(pet -> Integer.toString(pet.data().bond())).orElse("-");
            case "pet_satiety" -> petEngineManager.getPet(player).map(pet -> formatPetStat(pet.data().satiety())).orElse("-");
            case "pet_state" -> petEngineManager.getPet(player).map(pet -> pet.state().name()).orElse("-");
            default -> "";
        };
    }

    private String formatPetStat(double value) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001D) {
            return Integer.toString((int) Math.rint(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
