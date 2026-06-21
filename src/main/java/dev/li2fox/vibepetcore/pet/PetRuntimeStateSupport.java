package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.LivingEntity;

final class PetRuntimeStateSupport {
    private PetRuntimeStateSupport() {
    }

    static long setState(OwnedPetData data, PetState state, long stateChangedMillis) {
        if (!state.name().equals(data.state())) {
            stateChangedMillis = System.currentTimeMillis();
        }
        data.setState(state.name());
        return stateChangedMillis;
    }

    static void refreshName(LivingEntity entity, OwnedPetData data, PetType type) {
        if (entity != null && !entity.isDead()) {
            String progress = data.evolutionStage() >= 5 ? PetDisplaySupport.stageSymbol(data) : "E" + data.evolutionStage() + " Lv." + data.level();
            String legacyName = PetRuntimeViewSupport.rarityColor(data) + PetRuntimeViewSupport.displayLabel(data, type) + "§7 [" + progress + "]";
            entity.customName(LegacyComponentSerializer.legacySection().deserialize(legacyName));
        }
    }

    static long actionHintUntilMillis(long durationMillis) {
        return PetRuntimeViewSupport.actionHintUntilMillis(durationMillis);
    }
}
