package dev.li2fox.vibepetcore.gui;

import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.egg.PetEggService;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.player.QuestProgressData;
import dev.li2fox.vibepetcore.pet.PetEngineManager;
import dev.li2fox.vibepetcore.quest.QuestDefinition;
import dev.li2fox.vibepetcore.quest.QuestManager;
import dev.li2fox.vibepetcore.quest.QuestType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class PetQuestGuiSupport {
    private final QuestManager questManager;
    private final PetEngineManager petEngineManager;
    private final PetEggService petEggService;

    PetQuestGuiSupport(QuestManager questManager, PetEngineManager petEngineManager, PetEggService petEggService) {
        this.questManager = questManager;
        this.petEngineManager = petEngineManager;
        this.petEggService = petEggService;
    }

    String menuTitle() {
        return GameText.questMenuTitle();
    }

    String categoryLabel(String category) {
        return GameText.questCategoryName(normalizeCategory(category));
    }

    List<String> questLore(Player player, QuestDefinition quest, QuestProgressData progress, int visibleProgress) {
        List<String> lore = new ArrayList<>();
        if (!quest.description().isBlank()) {
            lore.add("&7" + quest.description());
            lore.add("");
        }
        lore.add("&7" + GameText.questCategoryLine(categoryLabel(quest.category())));
        lore.add("&7" + GameText.questTargetLine(GameText.questTarget(quest)));
        lore.add("&7" + GameText.questProgressLine(visibleProgress, quest.amount()));
        lore.add("&7" + GameText.questStatusLine(statusLine(player, quest, progress, visibleProgress)));
        lore.add("&7" + GameText.questRewardLine(GameText.rewardPoints(quest.rewardPoints())));
        questManager.activeQuestId(player.getUniqueId())
            .filter(activeId -> !activeId.equals(quest.id()))
            .flatMap(questManager::quest)
            .ifPresent(active -> lore.add(GameText.text("quest.active-other", "&6Активно другое: &f", "&6Another active: &f") + active.title()));
        acceptanceBlockReason(player, quest).ifPresent(reason -> lore.add("&6" + GameText.questAccessLine(reason)));
        if (quest.repeatable()) {
            lore.add("&7" + GameText.questRepeatLine(repeatText(quest.repeatCooldownMinutes())));
        }
        lore.add("");
        lore.add(actionLine(player, quest, progress));
        return lore;
    }

    String questName(Player player, QuestDefinition quest, QuestProgressData progress, UUID selectedPetId) {
        if (progress.completed()) {
            boolean readyAgain = quest.repeatable() && questManager.cooldownRemainingMillis(quest, progress) <= 0L;
            return readyAgain ? GameText.questNameReadyAgain(quest.title()) : GameText.questNameCompleted(quest.title());
        }
        return questManager.readyToTurnIn(player, quest, selectedPetId) ? GameText.questNameReadyToTurnIn(quest.title()) : GameText.questNameDefault(quest.title());
    }

    Optional<String> acceptanceBlockReason(Player player, QuestDefinition quest) {
        if (!"evolution".equalsIgnoreCase(quest.category())) {
            return Optional.empty();
        }
        int requiredStage = quest.requiredEvolutionStage();
        int currentStage = selectedPetEvolutionStage(player);
        if (currentStage <= 0) {
            return Optional.of(GameText.questNeedSelectedPet());
        }
        if (currentStage != requiredStage) {
            return Optional.of(GameText.questWrongEvolutionStage(requiredStage, currentStage));
        }
        return Optional.empty();
    }

    String acceptedAgainMessage(QuestManager.AcceptResult result) {
        return result.accepted() ? acceptedMessage(result) : GameText.questAcceptedAgainBlocked();
    }

    String acceptedMessage(QuestManager.AcceptResult result) {
        if (!result.accepted()) {
            return GameText.questAcceptFailed();
        }
        return result.replacedPrevious()
            ? GameText.text("quest.replaced-previous", "Вы отказались от текущего задания и взяли новое.", "You abandoned the current quest and accepted a new one.")
            : GameText.questAccepted();
    }

    String turnedInMessage(QuestManager.TurnInResult result) {
        if (result.turnedIn()) {
            return GameText.questTurnedIn();
        }
        return result.saveFailed() ? GameText.questTurnInSaveFailed() : GameText.questTurnInBlocked();
    }

    String blockedMessage(String reason) {
        return GameText.questBlocked(reason);
    }

    private String actionLine(Player player, QuestDefinition quest, QuestProgressData progress) {
        if (progress.completed()) {
            boolean readyAgain = quest.repeatable() && questManager.cooldownRemainingMillis(quest, progress) <= 0L;
            return readyAgain ? GameText.questActionTakeAgain() : GameText.questActionAlreadyDone();
        }
        if (questManager.readyToTurnIn(player, quest, selectedPetId(player).orElse(null))) {
            return quest.type() == QuestType.PICKUP_ITEM ? GameText.questActionTurnInWithItems() : GameText.questActionTurnIn();
        }
        return progress.accepted() ? GameText.questActionCheckProgress() : GameText.questActionAccept();
    }

    private String statusLine(Player player, QuestDefinition quest, QuestProgressData progress, int visibleProgress) {
        if (progress.completed()) {
            if (!quest.repeatable()) {
                return GameText.questStatusCompletedPlain();
            }
            long remaining = questManager.cooldownRemainingMillis(quest, progress);
            return remaining <= 0L ? GameText.questStatusReadyAgain() : GameText.questStatusCooldown(formatDuration(remaining));
        }
        if (!progress.accepted()) {
            return GameText.questStatusAvailable();
        }
        if (!questManager.bindingMatches(quest, progress, selectedPetId(player).orElse(null))) {
            return GameText.text("quest.status.wrong-pet", "другой питомец", "different pet");
        }
        int missing = Math.max(0, quest.amount() - visibleProgress);
        return missing == 0 ? GameText.questStatusReadyTurnIn() : GameText.questStatusRemaining(missing);
    }
    private String formatDuration(long millis) {
        long minutes = Math.max(1L, millis / 60_000L);
        long hours = minutes / 60L;
        long restMinutes = minutes % 60L;
        if (hours <= 0L) {
            return GameText.questRepeatMinutes(minutes);
        }
        return GameText.questRepeatHours(hours) + " " + GameText.questRepeatMinutes(restMinutes);
    }
    private int selectedPetEvolutionStage(Player player) {
        return petEngineManager.getPet(player).map(pet -> pet.data().evolutionStage())
            .or(() -> heldPetData(player).map(OwnedPetData::evolutionStage))
            .orElse(0);
    }

    private Optional<UUID> selectedPetId(Player player) {
        return petEngineManager.getPet(player).map(pet -> pet.data().petId())
            .or(() -> heldPetData(player).map(OwnedPetData::petId));
    }

    private Optional<OwnedPetData> heldPetData(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        Optional<OwnedPetData> mainPet = petEggService.readEgg(mainHand);
        Optional<OwnedPetData> offhandPet = petEggService.readEgg(player.getInventory().getItemInOffHand());
        return selectQuestGuiHeldPet(Optional.empty(), mainPet, offhandPet);
    }

    static Optional<OwnedPetData> selectQuestGuiHeldPet(
        Optional<UUID> activePetId,
        Optional<OwnedPetData> mainHandPet,
        Optional<OwnedPetData> offhandPet
    ) {
        return PetGuiCoreSelectionSupport.selectPreferredGuiCore(
            activePetId,
            mainHandPet,
            offhandPet,
            OwnedPetData::petId
        );
    }

    private String repeatText(long minutes) {
        if (minutes >= 10_080L) {
            return GameText.questRepeatWeek();
        }
        if (minutes >= 1_440L) {
            return GameText.questRepeatDay();
        }
        if (minutes >= 60L) {
            return GameText.questRepeatHours(minutes / 60L);
        }
        return GameText.questRepeatMinutes(minutes);
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "daily" : category.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "daily", "weekly", "evolution", "gather", "combat", "explore" -> normalized;
            default -> "daily";
        };
    }
}
