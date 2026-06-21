package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.ability.PetAbilityService;
import java.util.Locale;
import java.util.Optional;

final class PetHudSupport {
    private PetHudSupport() {
    }

    static String spawnStatusLine(long retrySeconds) {
        return retrySeconds > 0L
            ? GameText.text(
                "pet.hud.spawn.retry",
                "Питомец: ждёт призыва, повтор через {seconds}с.",
                "Pet: spawn blocked, retry in {seconds}s."
            ).replace("{seconds}", Long.toString(retrySeconds))
            : GameText.text(
                "pet.hud.spawn.waiting",
                "Питомец: ждёт призыва...",
                "Pet: waiting to spawn..."
            );
    }

    static String compactMissingLine(OwnedPetData data, PetType type, PetAbilityService abilityService, RuntimePet pet, long retrySeconds) {
        return retrySeconds > 0L
            ? PetDisplaySupport.shortDisplayLabel(data, type) + hudMarker(data, abilityService, pet) + " ⧖" + retrySeconds + "с"
            : PetDisplaySupport.shortDisplayLabel(data, type) + hudMarker(data, abilityService, pet) + " …";
    }

    static String compactLine(OwnedPetData data, PetType type, PetAbilityService abilityService, RuntimePet pet) {
        return PetDisplaySupport.shortDisplayLabel(data, type) + hudMarker(data, abilityService, pet)
            + " ❤" + Math.round(data.health())
            + " 🍖" + compactStat(data.satiety());
    }

    private static String hudMarker(OwnedPetData data, PetAbilityService abilityService, RuntimePet pet) {
        String marker = PetDisplaySupport.stageSymbol(data);
        return abilityService == null || pet == null ? marker : abilityService.legendaryHudMarker(pet, marker);
    }

    static Optional<String> criticalNote(OwnedPetData data) {
        if (data.durability() <= 0) {
            return Optional.of(GameText.text("pet.hud.status.core-broken", "ядро 0", "core 0"));
        }
        if (data.durability() <= 1) {
            return Optional.of(GameText.text("pet.hud.status.core-critical", "ядро {durability}", "core {durability}")
                .replace("{durability}", Integer.toString(data.durability())));
        }
        if (data.health() <= data.maxHealth() * 0.35D) {
            return Optional.of(GameText.text("pet.hud.status.hurt", "ранен", "hurt"));
        }
        if (data.satiety() <= 1) {
            return Optional.of(GameText.text("pet.hud.status.hungry", "голоден", "hungry"));
        }
        return Optional.empty();
    }

    static String localizedActionHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return hint;
        }
        return switch (hint.toLowerCase(Locale.ROOT)) {
            case "protecting", "defends" -> GameText.text("pet.hud.action.protecting", "защищает", "protecting");
            case "spawning" -> GameText.text("pet.hud.action.spawning", "появляется", "spawning");
            case "attacks" -> GameText.text("pet.hud.action.attacks", "атакует", "attacks");
            case "engages" -> GameText.text("pet.hud.action.engages", "сражается", "engages");
            case "calms down" -> GameText.text("pet.hud.action.calms-down", "успокоился", "calms down");
            case "heard" -> GameText.text("pet.hud.action.heard", "услышал зов", "heard");
            case "sits nearby" -> GameText.text("pet.hud.action.sits-nearby", "сидит рядом", "sits nearby");
            case "rests nearby" -> GameText.text("pet.hud.action.rests-nearby", "отдыхает рядом", "rests nearby");
            case "watches" -> GameText.text("pet.hud.action.watches", "наблюдает", "watches");
            case "inspects item" -> GameText.text("pet.hud.action.inspects-item", "изучает предмет", "inspects item");
            case "explores the ground" -> GameText.text("pet.hud.action.explores-ground", "изучает землю", "explores the ground");
            case "sniffs around" -> GameText.text("pet.hud.action.sniffs-around", "принюхивается", "sniffs around");
            case "looks around" -> GameText.text("pet.hud.action.looks-around", "оглядывается", "looks around");
            case "searches for a target" -> GameText.text("pet.hud.action.searches-target", "ищет цель", "searches for a target");
            case "curious" -> GameText.text("pet.hud.action.curious", "любопытствует", "curious");
            case "circles nearby" -> GameText.text("pet.hud.action.circles-nearby", "кружит рядом", "circles nearby");
            case "plays nearby" -> GameText.text("pet.hud.action.plays-nearby", "играет рядом", "plays nearby");
            case "alert" -> GameText.text("pet.hud.action.alert", "насторожен", "alert");
            case "greets" -> GameText.text("pet.hud.action.greets", "приветствует", "greets");
            case "chats" -> GameText.text("pet.hud.action.chats", "общается", "chats");
            case "nearby" -> GameText.text("pet.hud.action.nearby", "рядом", "nearby");
            default -> hint;
        };
    }

    private static String compactStat(double value) {
        double rounded = Math.round(value * 10.0D) / 10.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.000_001D) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
