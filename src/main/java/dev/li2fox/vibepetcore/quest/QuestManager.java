package dev.li2fox.vibepetcore.quest;

import dev.li2fox.vibepetcore.api.ProgressionAPI;
import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.CoreModule;
import dev.li2fox.vibepetcore.economy.EconomyManager;
import dev.li2fox.vibepetcore.economy.RewardReason;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.player.PlayerData;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.player.QuestProgressData;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class QuestManager implements CoreModule {
    private final BalanceConfig config;
    private final PlayerDataManager playerDataManager;
    private final EconomyManager economyManager;
    private ProgressionAPI progressionAPI;
    private final Map<String, QuestDefinition> quests = new LinkedHashMap<>();

    public QuestManager(BalanceConfig config, PlayerDataManager playerDataManager, EconomyManager economyManager) {
        this.config = config;
        this.playerDataManager = playerDataManager;
        this.economyManager = economyManager;
    }

    public void setProgressionAPI(ProgressionAPI progressionAPI) {
        this.progressionAPI = progressionAPI;
    }

    private String msg(String key, String fallback, Object... replacements) {
        return this.config.message(key, fallback, replacements);
    }

    @Override
    public void enable() {
        reload();
    }

    @Override
    public void disable() {
        quests.clear();
    }

    @Override
    public void reload() {
        quests.clear();
        for (String id : config.questIds()) {
            QuestType type = parseType(config.questType(id));
            quests.put(id, new QuestDefinition(
                id,
                config.questTitle(id),
                normalizeCategory(config.questCategory(id)),
                config.questDescription(id),
                type,
                config.questTarget(id).toUpperCase(Locale.ROOT),
                config.questAmount(id),
                config.questRewardPoints(id),
                config.questRewardPetXp(id),
                config.questRepeatCooldownMinutes(id),
                config.questIcon(id, iconFor(type))
            ));
        }
    }

    public Collection<QuestDefinition> quests() {
        return quests.values();
    }

    public List<QuestDefinition> quests(String category) {
        String normalized = normalizeCategory(category);
        if (normalized.equals("all")) {
            return quests.values().stream().toList();
        }
        return quests.values().stream()
            .filter(quest -> quest.category().equals(normalized))
            .toList();
    }

    public List<QuestDefinition> visibleQuests(UUID playerId, String category) {
        String normalized = normalizeCategory(category);
        if (normalized.equals("all")) {
            return visibleAllQuests(playerId);
        }
        if (normalized.equals("evolution") || normalized.equals("daily")) {
            return quests(normalized);
        }
        List<QuestDefinition> visible = rotatedQuests(normalized);
        appendActiveQuest(playerId, normalized, visible);
        return visible;
    }

    public Optional<QuestDefinition> quest(String id) {
        return Optional.ofNullable(quests.get(id));
    }

    public List<QuestDefinition> evolutionQuestsForStage(int currentStage) {
        return quests.values().stream()
            .filter(quest -> "evolution".equalsIgnoreCase(quest.category()))
            .filter(quest -> quest.requiredEvolutionStage() == currentStage)
            .toList();
    }

    public int completedEvolutionQuests(UUID playerId, int currentStage) {
        return completedEvolutionQuests(playerId, currentStage, null);
    }

    public int completedEvolutionQuests(UUID playerId, int currentStage, UUID selectedPetId) {
        return (int) evolutionQuestsForStage(currentStage).stream()
            .filter(quest -> {
                QuestProgressData progress = progress(playerId, quest.id());
                return progress.completed() && bindingMatches(quest, progress, selectedPetId);
            })
            .count();
    }

    public AcceptResult accept(UUID playerId, String questId) {
        return accept(playerId, questId, null);
    }

    public AcceptResult accept(UUID playerId, String questId, UUID boundPetId) {
        QuestDefinition quest = quests.get(questId);
        if (quest == null) {
            return AcceptResult.failed();
        }
        if (isEvolutionQuest(quest) && boundPetId == null) {
            return AcceptResult.failed();
        }
        QuestProgressData progress = progress(playerId, questId);
        if (progress.completed() && !canRepeatNow(quest, progress)) {
            return AcceptResult.failed();
        }
        boolean replaced = cancelActiveQuestsExcept(playerId, questId);
        if (progress.completed()) {
            progress.resetForRepeat();
        }
        if (isEvolutionQuest(quest)) {
            progress.setBoundPetId(boundPetId);
        }
        progress.setAccepted(true);
        return new AcceptResult(true, replaced);
    }

    public record AcceptResult(boolean accepted, boolean replacedPrevious) {
        public static AcceptResult failed() {
            return new AcceptResult(false, false);
        }
    }

    public record TurnInResult(boolean turnedIn, boolean saveFailed) {
        private static TurnInResult success() {
            return new TurnInResult(true, false);
        }

        private static TurnInResult blocked() {
            return new TurnInResult(false, false);
        }

        private static TurnInResult saveFailedResult() {
            return new TurnInResult(false, true);
        }
    }

    public boolean cancel(UUID playerId, String questId) {
        QuestDefinition quest = quests.get(questId);
        if (quest == null) {
            return false;
        }
        QuestProgressData progress = progress(playerId, questId);
        if (!progress.accepted() || progress.completed()) {
            return false;
        }
        progress.cancel();
        return true;
    }

    public Optional<String> activeQuestId(UUID playerId) {
        PlayerData data = playerDataManager.getOrLoad(playerId);
        return data.quests().entrySet().stream()
            .filter(entry -> entry.getValue().accepted() && !entry.getValue().completed())
            .map(Map.Entry::getKey)
            .filter(quests::containsKey)
            .findFirst();
    }

    private boolean cancelActiveQuestsExcept(UUID playerId, String questId) {
        PlayerData data = playerDataManager.getOrLoad(playerId);
        boolean replaced = false;
        for (Map.Entry<String, QuestProgressData> entry : data.quests().entrySet()) {
            if (entry.getKey().equals(questId) || !quests.containsKey(entry.getKey())) {
                continue;
            }
            QuestProgressData progress = entry.getValue();
            if (progress.accepted() && !progress.completed()) {
                progress.cancel();
                replaced = true;
            }
        }
        return replaced;
    }

    public boolean turnIn(UUID playerId, String questId) {
        return turnInResult(playerId, questId).turnedIn();
    }

    public TurnInResult turnInResult(UUID playerId, String questId) {
        QuestDefinition quest = quests.get(questId);
        if (quest == null) {
            return TurnInResult.blocked();
        }
        PlayerData playerData = playerDataManager.getOrLoad(playerId);
        QuestProgressData progress = progress(playerId, questId);
        if (!progress.accepted() || progress.completed() || progress.progress() < quest.amount()) {
            return TurnInResult.blocked();
        }
        return finishTurnIn(playerId, playerData, quest, progress, snapshotTurnInState(playerData, progress), null, null);
    }

    public boolean turnIn(Player player, String questId) {
        return turnIn(player, questId, null);
    }

    public boolean turnIn(Player player, String questId, UUID selectedPetId) {
        return turnInResult(player, questId, selectedPetId).turnedIn();
    }

    public TurnInResult turnInResult(Player player, String questId) {
        return turnInResult(player, questId, null);
    }

    public TurnInResult turnInResult(Player player, String questId, UUID selectedPetId) {
        QuestDefinition quest = quests.get(questId);
        if (quest == null) {
            return TurnInResult.blocked();
        }
        UUID playerId = player.getUniqueId();
        PlayerData playerData = playerDataManager.getOrLoad(playerId);
        QuestProgressData progress = progress(playerId, questId);
        if (!progress.accepted() || progress.completed()) {
            return TurnInResult.blocked();
        }
        if (!bindingMatches(quest, progress, selectedPetId)) {
            return TurnInResult.blocked();
        }
        if (quest.type() == QuestType.PICKUP_ITEM) {
            Material material = Material.matchMaterial(quest.target());
            if (material == null || count(player, material) < quest.amount()) {
                return TurnInResult.blocked();
            }
            TurnInStateSnapshot stateSnapshot = snapshotTurnInState(playerData, progress);
            InventorySnapshot inventorySnapshot = null;
            if (player.getGameMode() != GameMode.CREATIVE) {
                inventorySnapshot = snapshotInventory(player);
                consume(player, material, quest.amount());
            }
            progress.setProgress(quest.amount(), quest.amount());
            return finishTurnIn(playerId, playerData, quest, progress, stateSnapshot, player, inventorySnapshot);
        }
        if (progress.progress() < quest.amount()) {
            return TurnInResult.blocked();
        }
        return finishTurnIn(playerId, playerData, quest, progress, snapshotTurnInState(playerData, progress), null, null);
    }

    public int displayProgress(Player player, QuestDefinition quest) {
        QuestProgressData progress = progress(player.getUniqueId(), quest.id());
        return displayProgress(player, quest, progress, null);
    }

    public int displayProgress(Player player, QuestDefinition quest, UUID selectedPetId) {
        QuestProgressData progress = progress(player.getUniqueId(), quest.id());
        return displayProgress(player, quest, progress, selectedPetId);
    }

    private int displayProgress(Player player, QuestDefinition quest, QuestProgressData progress, UUID selectedPetId) {
        if (!bindingMatches(quest, progress, selectedPetId)) {
            return 0;
        }
        if (quest.type() != QuestType.PICKUP_ITEM) {
            return progress.progress();
        }
        Material material = Material.matchMaterial(quest.target());
        if (material == null) {
            return 0;
        }
        return Math.min(quest.amount(), count(player, material));
    }

    public boolean readyToTurnIn(Player player, QuestDefinition quest) {
        return readyToTurnIn(player, quest, null);
    }

    public boolean readyToTurnIn(Player player, QuestDefinition quest, UUID selectedPetId) {
        QuestProgressData progress = progress(player.getUniqueId(), quest.id());
        return progress.accepted() && !progress.completed() && displayProgress(player, quest, progress, selectedPetId) >= quest.amount();
    }

    public long cooldownRemainingMillis(QuestDefinition quest, QuestProgressData progress) {
        if (!progress.completed() || !quest.repeatable()) {
            return 0L;
        }
        long readyAt = progress.completedAtMillis() + quest.repeatCooldownMinutes() * 60_000L;
        return Math.max(0L, readyAt - System.currentTimeMillis());
    }

    public String statusLine(Player player, QuestDefinition quest) {
        return statusLine(player, quest, null);
    }

    public String statusLine(Player player, QuestDefinition quest, UUID selectedPetId) {
        QuestProgressData progress = progress(player.getUniqueId(), quest.id());
        if (progress.completed()) {
            long remaining = cooldownRemainingMillis(quest, progress);
            if (remaining <= 0L && quest.repeatable()) {
                return msg("quest.status.available-again", "Available again");
            }
            return quest.repeatable()
                ? msg("quest.status.cooldown", "Cooldown: {duration}", "duration", formatDuration(remaining))
                : msg("quest.status.completed", "Completed");
        }
        if (!progress.accepted()) {
            return msg("quest.status.available", "Available");
        }
        if (!bindingMatches(quest, progress, selectedPetId)) {
            return msg("quest.status.wrong-pet", "different pet");
        }
        int missing = Math.max(0, quest.amount() - displayProgress(player, quest, progress, selectedPetId));
        return missing == 0
            ? msg("quest.status.ready", "Ready to turn in")
            : msg("quest.status.remaining", "Remaining: {missing}", "missing", missing);
    }

    private TurnInResult finishTurnIn(
        UUID playerId,
        PlayerData playerData,
        QuestDefinition quest,
        QuestProgressData progress,
        TurnInStateSnapshot snapshot,
        Player player,
        InventorySnapshot inventorySnapshot
    ) {
        complete(playerId, playerData, quest, progress);
        if (playerDataManager.save(playerId)) {
            return TurnInResult.success();
        }
        rollbackTurnInState(playerData, progress, snapshot);
        if (player != null && inventorySnapshot != null) {
            restoreInventory(player, inventorySnapshot);
        }
        return TurnInResult.saveFailedResult();
    }

    private void complete(UUID playerId, PlayerData playerData, QuestDefinition quest, QuestProgressData progress) {
        progress.setCompleted(true);
        playerData.statistics().addQuestCompleted();
        economyManager.award(playerId, quest.rewardPoints(), RewardReason.QUEST, quest.id());
        grantQuestPetXp(playerData, quest, progress);
    }

    private void grantQuestPetXp(PlayerData playerData, QuestDefinition quest, QuestProgressData progress) {
        if (progressionAPI == null) {
            return;
        }
        UUID petId = progress.boundPetId();
        if (petId == null) {
            petId = playerData.activePetId().orElse(null);
        }
        if (petId == null) {
            return;
        }
        OwnedPetData pet = findPet(playerData, petId);
        if (pet == null) {
            return;
        }
        long rewardXp = resolveQuestPetXp(quest, pet);
        if (rewardXp <= 0L) {
            return;
        }
        progressionAPI.addXp(pet, rewardXp);
    }

    private long resolveQuestPetXp(QuestDefinition quest, OwnedPetData pet) {
        if (quest.rewardPetXp() > 0L) {
            return quest.rewardPetXp();
        }
        if (!isEvolutionQuest(quest)) {
            return 0L;
        }
        double percent = config.questEvolutionPetXpPercent();
        if (percent <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, Math.round(progressionAPI.xpRequiredForSubLevel(pet) * percent / 100.0D));
    }

    private OwnedPetData findPet(PlayerData playerData, UUID petId) {
        for (OwnedPetData pet : playerData.pets()) {
            if (pet.petId().equals(petId)) {
                return pet;
            }
        }
        return null;
    }

    static TurnInStateSnapshot snapshotTurnInState(PlayerData playerData, QuestProgressData progress) {
        return new TurnInStateSnapshot(progress.snapshot(), playerData.points(), playerData.statistics().questsCompleted());
    }

    static void rollbackTurnInState(PlayerData playerData, QuestProgressData progress, TurnInStateSnapshot snapshot) {
        progress.restore(snapshot.progress());
        long pointDelta = snapshot.points() - playerData.points();
        if (pointDelta > 0L) {
            playerData.addPoints(pointDelta);
        } else if (pointDelta < 0L) {
            playerData.takePoints(-pointDelta);
        }
        playerData.statistics().setQuestsCompleted(snapshot.questsCompleted());
    }

    public void record(UUID playerId, QuestType type, String target) {
        record(playerId, type, target, null);
    }

    public void record(UUID playerId, QuestType type, String target, Player player) {
        record(playerId, type, target, player, null);
    }

    public void record(UUID playerId, QuestType type, String target, Player player, UUID selectedPetId) {
        if (type == QuestType.PICKUP_ITEM) {
            return;
        }
        String normalizedTarget = target.toUpperCase(Locale.ROOT);
        for (QuestDefinition quest : quests.values()) {
            if (quest.type() != type || !quest.target().equals(normalizedTarget)) {
                continue;
            }
            QuestProgressData progress = progress(playerId, quest.id());
            if (progress.accepted() && !progress.completed() && bindingMatches(quest, progress, selectedPetId)) {
                if (isEvolutionQuest(quest) && progress.boundPetId() == null && selectedPetId != null) {
                    progress.setBoundPetId(selectedPetId);
                }
                int before = progress.progress();
                progress.addProgress(1, quest.amount());
                if (player != null && before < quest.amount() && progress.progress() >= quest.amount()) {
                    player.sendMessage(msg("quest.ready-message", "Quest is ready. Return to the Pet Source to turn it in."));
                }
            }
        }
    }

    public QuestProgressData progress(UUID playerId, String questId) {
        PlayerData data = playerDataManager.getOrLoad(playerId);
        return data.quests().computeIfAbsent(questId, ignored -> new QuestProgressData());
    }

    public boolean bindingMatches(QuestDefinition quest, QuestProgressData progress, UUID selectedPetId) {
        if (!isEvolutionQuest(quest)) {
            return true;
        }
        UUID boundPetId = progress.boundPetId();
        return selectedPetId != null && (boundPetId == null || boundPetId.equals(selectedPetId));
    }

    private boolean isEvolutionQuest(QuestDefinition quest) {
        return "evolution".equalsIgnoreCase(quest.category());
    }

    private QuestType parseType(String value) {
        try {
            return QuestType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return QuestType.KILL_MOB;
        }
    }

    private boolean canRepeatNow(QuestDefinition quest, QuestProgressData progress) {
        return quest.repeatable() && cooldownRemainingMillis(quest, progress) <= 0L;
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "daily" : category.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "daily", "weekly", "evolution", "gather", "combat", "explore" -> normalized;
            default -> "daily";
        };
    }

    private List<QuestDefinition> visibleAllQuests(UUID playerId) {
        Set<String> seen = new LinkedHashSet<>();
        List<QuestDefinition> visible = new ArrayList<>();
        for (String category : List.of("daily", "weekly", "gather", "combat", "explore")) {
            for (QuestDefinition quest : visibleQuests(playerId, category)) {
                if (seen.add(quest.id())) {
                    visible.add(quest);
                }
            }
        }
        appendAllActiveQuests(playerId, seen, visible);
        return visible;
    }

    private List<QuestDefinition> rotatedQuests(String category) {
        List<QuestDefinition> categoryQuests = quests(category);
        int limit = visibleQuestLimit(category);
        if (limit <= 0 || categoryQuests.size() <= limit) {
            return new ArrayList<>(categoryQuests);
        }
        List<QuestDefinition> ranked = new ArrayList<>(categoryQuests);
        int weekId = currentWeekId();
        ranked.sort((left, right) -> Long.compare(
            rotationScore(category, left.id(), weekId),
            rotationScore(category, right.id(), weekId)
        ));
        Set<String> selected = ranked.stream()
            .limit(limit)
            .map(QuestDefinition::id)
            .collect(java.util.stream.Collectors.toSet());
        return categoryQuests.stream()
            .filter(quest -> selected.contains(quest.id()))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private void appendActiveQuest(UUID playerId, String category, List<QuestDefinition> visible) {
        Set<String> seen = new HashSet<>();
        visible.forEach(quest -> seen.add(quest.id()));
        appendActiveQuests(playerId, category, seen, visible);
    }

    private void appendAllActiveQuests(UUID playerId, Set<String> seen, List<QuestDefinition> visible) {
        for (String category : List.of("daily", "weekly", "gather", "combat", "explore", "evolution")) {
            appendActiveQuests(playerId, category, seen, visible);
        }
    }

    private void appendActiveQuests(UUID playerId, String category, Set<String> seen, List<QuestDefinition> visible) {
        PlayerData data = playerDataManager.getOrLoad(playerId);
        for (Map.Entry<String, QuestProgressData> entry : data.quests().entrySet()) {
            QuestDefinition quest = quests.get(entry.getKey());
            if (quest == null || !quest.category().equals(category) || seen.contains(quest.id())) {
                continue;
            }
            QuestProgressData progress = entry.getValue();
            if (progress.accepted() && !progress.completed()) {
                visible.add(quest);
                seen.add(quest.id());
            }
        }
    }

    private int visibleQuestLimit(String category) {
        return switch (category) {
            case "weekly" -> 4;
            case "gather", "combat" -> 6;
            case "explore" -> 6;
            default -> 0;
        };
    }

    private int currentWeekId() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        WeekFields fields = WeekFields.ISO;
        return today.get(fields.weekBasedYear()) * 100 + today.get(fields.weekOfWeekBasedYear());
    }

    private long rotationScore(String category, String questId, int weekId) {
        String key = category + ':' + questId + ':' + weekId;
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash & Long.MAX_VALUE;
    }

    private String formatDuration(long millis) {
        long minutes = Math.max(1L, millis / 60_000L);
        long hours = minutes / 60L;
        long restMinutes = minutes % 60L;
        if (hours <= 0L) {
            return msg("time.minutes.short", "{minutes} min.", "minutes", minutes);
        }
        return msg("quest.duration.hours-minutes", "{hours} hr. {minutes} min.", "hours", hours, "minutes", restMinutes);
    }

    private Material iconFor(QuestType type) {
        return switch (type) {
            case KILL_MOB -> Material.IRON_SWORD;
            case BREAK_BLOCK -> Material.IRON_PICKAXE;
            case PICKUP_ITEM -> Material.HOPPER;
            case EXPLORE -> Material.COMPASS;
        };
    }

    private int count(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void consume(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack item : contents) {
            if (item == null || item.getType() != material || remaining <= 0) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
        }
        player.getInventory().setStorageContents(contents);
    }

    private InventorySnapshot snapshotInventory(Player player) {
        return new InventorySnapshot(cloneContents(player.getInventory().getStorageContents()));
    }

    private void restoreInventory(Player player, InventorySnapshot snapshot) {
        player.getInventory().setStorageContents(cloneContents(snapshot.contents()));
    }

    private ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            clone[index] = source[index] == null ? null : source[index].clone();
        }
        return clone;
    }

    static record TurnInStateSnapshot(QuestProgressData.Snapshot progress, long points, long questsCompleted) {
    }

    private record InventorySnapshot(ItemStack[] contents) {
    }
}
