package dev.li2fox.vibepetcore.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.li2fox.vibepetcore.api.ProgressionAPI;
import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.economy.EconomyManager;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.player.PlayerData;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.player.QuestProgressData;
import dev.li2fox.vibepetcore.progression.EvolutionResult;
import dev.li2fox.vibepetcore.progression.FeedResult;
import dev.li2fox.vibepetcore.progression.ProgressionResult;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

final class QuestManagerPetXpRewardTest {
    @Test
    void evolutionQuestTurnInGrantsPetXpToBoundPet() throws Exception {
        AtomicLong xpGranted = new AtomicLong();
        Fixture fixture = fixture(xpGranted);

        UUID playerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        PlayerData playerData = fixture.playerDataManager().getOrLoad(playerId);
        OwnedPetData pet = new OwnedPetData(petId, playerId, "WOLF", "common");
        playerData.pets().add(pet);
        playerData.setActivePetId(petId);

        QuestProgressData progress = fixture.questManager().progress(playerId, "evolution_2_hunt");
        progress.setAccepted(true);
        progress.setBoundPetId(petId);
        progress.setProgress(14, 14);

        Player player = player(playerId);

        assertTrue(fixture.questManager().turnInResult(player, "evolution_2_hunt", petId).turnedIn());
        assertEquals(3L, xpGranted.get());
        assertEquals(3L, pet.xp());
    }

    private Fixture fixture(AtomicLong xpGranted) throws Exception {
        BalanceConfig balanceConfig = new BalanceConfig(null);
        setField(balanceConfig, "config", questConfig());
        PlayerDataManager playerDataManager = new PlayerDataManager(dummyPlugin());
        setField(playerDataManager, "storage", storageProxy());
        EconomyManager economyManager = new EconomyManager(playerDataManager, balanceConfig);
        QuestManager questManager = new QuestManager(balanceConfig, playerDataManager, economyManager);
        questManager.setProgressionAPI(progressionApi(xpGranted));
        questManager.reload();
        return new Fixture(questManager, playerDataManager);
    }

    private ProgressionAPI progressionApi(AtomicLong xpGranted) {
        return new ProgressionAPI() {
            @Override
            public long xpRequiredForSubLevel(OwnedPetData pet) {
                return 100L;
            }

            @Override
            public ProgressionResult addXp(OwnedPetData pet, long amount) {
                xpGranted.addAndGet(amount);
                pet.setXp(pet.xp() + amount);
                return new ProgressionResult(amount, 0, 0, false);
            }

            @Override
            public boolean canGainSubLevel(OwnedPetData pet) {
                return false;
            }

            @Override
            public boolean canEvolve(OwnedPetData pet) {
                return false;
            }

            @Override
            public EvolutionResult tryEvolve(OwnedPetData pet) {
                return EvolutionResult.notAttempted();
            }

            @Override
            public EvolutionResult tryEvolve(OwnedPetData pet, double chanceOverride) {
                return EvolutionResult.notAttempted();
            }

            @Override
            public FeedResult feed(OwnedPetData pet, Material material) {
                return FeedResult.rejected("unused");
            }
        };
    }

    private YamlConfiguration questConfig() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("language", "en");
        yaml.set("progression.quest.evolution-pet-xp-percent", 3.0D);
        yaml.set("economy.quests.evolution_2_hunt.title", "Evolution Hunt");
        yaml.set("economy.quests.evolution_2_hunt.category", "evolution");
        yaml.set("economy.quests.evolution_2_hunt.description", "Bound evolution quest.");
        yaml.set("economy.quests.evolution_2_hunt.type", "KILL_MOB");
        yaml.set("economy.quests.evolution_2_hunt.target", "SPIDER");
        yaml.set("economy.quests.evolution_2_hunt.amount", 14);
        yaml.set("economy.quests.evolution_2_hunt.reward-points", 70L);
        yaml.set("economy.quests.evolution_2_hunt.repeat-cooldown-minutes", -1L);
        yaml.set("economy.quests.evolution_2_hunt.icon", Material.SPIDER_EYE.name());
        return yaml;
    }

    private Object storageProxy() throws Exception {
        Class<?> storageType = Class.forName("dev.li2fox.vibepetcore.player.PlayerStorage");
        return Proxy.newProxyInstance(
            storageType.getClassLoader(),
            new Class<?>[] {storageType},
            (proxy, method, args) -> switch (method.getName()) {
                case "enable", "close" -> null;
                case "load" -> Optional.empty();
                case "save" -> true;
                case "topByPoints", "playerIds" -> List.of();
                case "name" -> "test-storage";
                case "toString" -> "test-storage";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private Player player(UUID playerId) {
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
            PlayerInventory.class.getClassLoader(),
            new Class<?>[] {PlayerInventory.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getStorageContents" -> new org.bukkit.inventory.ItemStack[0];
                case "toString" -> "test-player-inventory";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "getGameMode" -> GameMode.SURVIVAL;
                case "getInventory" -> inventory;
                case "toString" -> "test-player";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private JavaPlugin dummyPlugin() throws Exception {
        return (JavaPlugin) unsafe().allocateInstance(DummyPlugin.class);
    }

    private Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Fixture(QuestManager questManager, PlayerDataManager playerDataManager) {
    }

    public static final class DummyPlugin extends JavaPlugin {
        private static final Logger LOGGER = Logger.getLogger(DummyPlugin.class.getName());

        @Override
        public Logger getLogger() {
            return LOGGER;
        }
    }
}
