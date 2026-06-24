package dev.li2fox.vibepetcore.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.economy.EconomyManager;
import dev.li2fox.vibepetcore.player.PlayerDataManager;
import dev.li2fox.vibepetcore.player.QuestProgressData;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

final class QuestManagerStatusLineTest {
    @Test
    void evolutionQuestStatusUsesSelectedPetBinding() throws Exception {
        Fixture fixture = fixture();
        UUID playerId = UUID.randomUUID();
        UUID boundPetId = UUID.randomUUID();
        UUID wrongPetId = UUID.randomUUID();
        QuestProgressData progress = fixture.questManager().progress(playerId, "evolution_2_hunt");
        progress.setAccepted(true);
        progress.setBoundPetId(boundPetId);
        progress.setProgress(0, 1);
        Player player = player(playerId);

        String status = fixture.questManager().statusLine(player, fixture.questManager().quest("evolution_2_hunt").orElseThrow(), wrongPetId);

        assertEquals("different pet", status);
    }

    @Test
    void evolutionQuestStatusShowsRemainingForMatchingPet() throws Exception {
        Fixture fixture = fixture();
        UUID playerId = UUID.randomUUID();
        UUID boundPetId = UUID.randomUUID();
        QuestProgressData progress = fixture.questManager().progress(playerId, "evolution_2_hunt");
        progress.setAccepted(true);
        progress.setBoundPetId(boundPetId);
        progress.setProgress(0, 1);
        Player player = player(playerId);

        String status = fixture.questManager().statusLine(player, fixture.questManager().quest("evolution_2_hunt").orElseThrow(), boundPetId);

        assertEquals("Remaining: 1", status);
    }

    private Fixture fixture() throws Exception {
        BalanceConfig balanceConfig = new BalanceConfig(null);
        setField(balanceConfig, "config", questConfig());
        PlayerDataManager playerDataManager = new PlayerDataManager(dummyPlugin());
        setField(playerDataManager, "storage", storageProxy());
        EconomyManager economyManager = new EconomyManager(playerDataManager, balanceConfig);
        QuestManager questManager = new QuestManager(balanceConfig, playerDataManager, economyManager);
        questManager.reload();
        return new Fixture(questManager, playerDataManager);
    }

    private YamlConfiguration questConfig() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("language", "en");
        yaml.set("economy.quests.evolution_2_hunt.title", "Evolution Hunt");
        yaml.set("economy.quests.evolution_2_hunt.category", "evolution");
        yaml.set("economy.quests.evolution_2_hunt.description", "Bound evolution quest.");
        yaml.set("economy.quests.evolution_2_hunt.type", "KILL_MOB");
        yaml.set("economy.quests.evolution_2_hunt.target", "ZOMBIE");
        yaml.set("economy.quests.evolution_2_hunt.amount", 1);
        yaml.set("economy.quests.evolution_2_hunt.reward-points", 10L);
        yaml.set("economy.quests.evolution_2_hunt.repeat-cooldown-minutes", 0L);
        yaml.set("economy.quests.evolution_2_hunt.icon", Material.IRON_SWORD.name());
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
                case "save" -> null;
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

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
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
