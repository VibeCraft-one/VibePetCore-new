package dev.li2fox.vibepetcore.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.li2fox.vibepetcore.api.ProgressionAPI;
import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.progression.DeathPenaltyResult;
import dev.li2fox.vibepetcore.progression.EvolutionResult;
import dev.li2fox.vibepetcore.progression.FeedResult;
import dev.li2fox.vibepetcore.progression.ProgressionResult;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

final class PetEvolutionFlowRollbackTest {
    @Test
    void saveFailureRestoresMaterialsAndPetState() {
        UUID playerId = UUID.randomUUID();
        OwnedPetData petData = readyToEvolvePet(playerId);
        RuntimePet runtimePet = new RuntimePet(petData, PetType.WOLF);
        Player player = player(playerId, GameMode.SURVIVAL, new FakeItemStack(Material.DIAMOND, 1));
        AtomicReference<ItemStack[]> vaultContents = new AtomicReference<>(new ItemStack[] {new FakeItemStack(Material.DIAMOND, 1)});
        AtomicBoolean playedEffect = new AtomicBoolean(false);
        AtomicLong actionBarDuration = new AtomicLong(0L);

        boolean result = PetEvolutionFlowSupport.tryEvolveActivePet(
            player,
            Optional.of(runtimePet),
            successfulEvolution(),
            ignored -> new PetEngineManager.EvolutionRequirement(1, 2, 3, 4, Map.of(Material.DIAMOND, 2), 0),
            OwnedPetData::bond,
            (ignoredPlayer, ignoredData) -> true,
            ignored -> 0,
            (ignoredPlayer, ignoredData) -> 0,
            (ignoredPlayer, ignoredPet) -> 1.0D,
            (ignoredPlayer, ignoredPet, ignoredMaterials) -> consumeForTest(player, vaultContents),
            ignoredPet -> cloneContents(vaultContents.get()),
            (ignoredPet, restored) -> vaultContents.set(cloneContents(restored)),
            (ignoredPlayer, ignoredPet) -> false,
            (ignoredPlayer, ignoredPet) -> playedEffect.set(true),
            (ignoredPlayer, durationMillis) -> actionBarDuration.set(durationMillis)
        );

        assertFalse(result);
        assertEquals(1, count(player, Material.DIAMOND));
        assertEquals(1, count(vaultContents.get(), Material.DIAMOND));
        assertEquals(1, petData.evolutionStage());
        assertEquals(3, petData.level());
        assertEquals(2, petData.subLevel());
        assertEquals(4, petData.bond());
        assertEquals(12L, petData.xp());
        assertFalse(playedEffect.get());
        assertEquals(2_000L, actionBarDuration.get());
    }

    @Test
    void successfulSaveKeepsSpentMaterialsAndEvolvedStage() {
        UUID playerId = UUID.randomUUID();
        OwnedPetData petData = readyToEvolvePet(playerId);
        RuntimePet runtimePet = new RuntimePet(petData, PetType.WOLF);
        Player player = player(playerId, GameMode.SURVIVAL, new FakeItemStack(Material.DIAMOND, 1));
        AtomicReference<ItemStack[]> vaultContents = new AtomicReference<>(new ItemStack[] {new FakeItemStack(Material.DIAMOND, 1)});
        AtomicBoolean playedEffect = new AtomicBoolean(false);
        AtomicLong actionBarDuration = new AtomicLong(0L);

        boolean result = PetEvolutionFlowSupport.tryEvolveActivePet(
            player,
            Optional.of(runtimePet),
            successfulEvolution(),
            ignored -> new PetEngineManager.EvolutionRequirement(1, 2, 3, 4, Map.of(Material.DIAMOND, 2), 0),
            OwnedPetData::bond,
            (ignoredPlayer, ignoredData) -> true,
            ignored -> 0,
            (ignoredPlayer, ignoredData) -> 0,
            (ignoredPlayer, ignoredPet) -> 1.0D,
            (ignoredPlayer, ignoredPet, ignoredMaterials) -> consumeForTest(player, vaultContents),
            ignoredPet -> cloneContents(vaultContents.get()),
            (ignoredPet, restored) -> vaultContents.set(cloneContents(restored)),
            (ignoredPlayer, ignoredPet) -> true,
            (ignoredPlayer, ignoredPet) -> playedEffect.set(true),
            (ignoredPlayer, durationMillis) -> actionBarDuration.set(durationMillis)
        );

        assertTrue(result);
        assertEquals(0, count(player, Material.DIAMOND));
        assertEquals(0, count(vaultContents.get(), Material.DIAMOND));
        assertEquals(2, petData.evolutionStage());
        assertEquals(1, petData.level());
        assertEquals(1, petData.subLevel());
        assertEquals(0, petData.bond());
        assertEquals(0L, petData.xp());
        assertTrue(playedEffect.get());
        assertEquals(2_500L, actionBarDuration.get());
    }

    private OwnedPetData readyToEvolvePet(UUID playerId) {
        OwnedPetData petData = new OwnedPetData(UUID.randomUUID(), playerId, "wolf", "common");
        petData.setLevel(3);
        petData.setSubLevel(2);
        petData.setBond(4);
        petData.setXp(12L);
        return petData;
    }

    private ProgressionAPI successfulEvolution() {
        return new ProgressionAPI() {
            @Override
            public long xpRequiredForSubLevel(OwnedPetData pet) {
                return 0L;
            }

            @Override
            public ProgressionResult addXp(OwnedPetData pet, long amount) {
                return ProgressionResult.none();
            }

            @Override
            public boolean canGainSubLevel(OwnedPetData pet) {
                return false;
            }

            @Override
            public boolean canEvolve(OwnedPetData pet) {
                return true;
            }

            @Override
            public EvolutionResult tryEvolve(OwnedPetData pet) {
                return tryEvolve(pet, 1.0D);
            }

            @Override
            public EvolutionResult tryEvolve(OwnedPetData pet, double chanceOverride) {
                pet.setEvolutionStage(2);
                pet.setLevel(1);
                pet.setSubLevel(1);
                pet.setBond(0);
                pet.setXp(0L);
                return new EvolutionResult(true, true, 2, chanceOverride);
            }

            @Override
            public FeedResult feed(OwnedPetData pet, Material material) {
                throw new UnsupportedOperationException();
            }

            @Override
            public DeathPenaltyResult applyDeathXpPenalty(OwnedPetData pet) {
                return DeathPenaltyResult.none();
            }
        };
    }

    private void consumeForTest(Player player, AtomicReference<ItemStack[]> vaultContents) {
        ItemStack[] contents = player.getInventory().getContents();
        contents[0].setAmount(contents[0].getAmount() - 1);
        if (contents[0].getAmount() <= 0) {
            contents[0] = null;
        }
        player.getInventory().setContents(contents);

        ItemStack[] vault = cloneContents(vaultContents.get());
        vault[0].setAmount(vault[0].getAmount() - 1);
        if (vault[0].getAmount() <= 0) {
            vault[0] = null;
        }
        vaultContents.set(vault);
    }

    private Player player(UUID playerId, GameMode gameMode, ItemStack... contents) {
        AtomicReference<ItemStack[]> storage = new AtomicReference<>(cloneContents(contents));
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
            PlayerInventory.class.getClassLoader(),
            new Class<?>[] {PlayerInventory.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getContents" -> storage.get();
                case "setContents" -> {
                    storage.set(cloneContents((ItemStack[]) args[0]));
                    yield null;
                }
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
                case "getGameMode" -> gameMode;
                case "getInventory" -> inventory;
                case "sendMessage" -> null;
                case "toString" -> "test-player";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private int count(Player player, Material material) {
        return count(player.getInventory().getContents(), material);
    }

    private int count(ItemStack[] contents, Material material) {
        int total = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            clone[index] = source[index] == null ? null : source[index].clone();
        }
        return clone;
    }

    private static final class FakeItemStack extends ItemStack {
        private Material type;
        private int amount;

        private FakeItemStack(Material type, int amount) {
            this.type = type;
            this.amount = amount;
        }

        @Override
        public Material getType() {
            return type;
        }

        @Override
        public void setType(Material type) {
            this.type = type;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(int amount) {
            this.amount = amount;
        }

        @Override
        public ItemStack clone() {
            return new FakeItemStack(type, amount);
        }
    }
}
