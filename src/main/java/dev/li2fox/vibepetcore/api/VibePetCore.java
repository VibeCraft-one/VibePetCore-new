package dev.li2fox.vibepetcore.api;

import dev.li2fox.vibepetcore.core.VibePetCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class VibePetCore {
    private VibePetCore() {
    }

    public static PetAPI pets() {
        return plugin().petAPI();
    }

    public static ProgressionAPI progression() {
        return plugin().progressionAPI();
    }

    public static EconomyAPI economy() {
        return plugin().economyAPI();
    }

    private static VibePetCorePlugin plugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("VibePetCore");
        if (!(plugin instanceof VibePetCorePlugin vibePetCorePlugin)) {
            throw new IllegalStateException("VibePetCore is not enabled");
        }
        return vibePetCorePlugin;
    }
}
