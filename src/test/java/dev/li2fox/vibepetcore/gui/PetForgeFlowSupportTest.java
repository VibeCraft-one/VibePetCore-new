package dev.li2fox.vibepetcore.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import dev.li2fox.vibepetcore.pet.PetRarity;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

final class PetForgeFlowSupportTest {
    @Test
    void saveFailureRestoresDonorEggsAndOriginalRarity() {
        UUID playerId = UUID.randomUUID();
        OwnedPetData petData = new OwnedPetData(UUID.randomUUID(), playerId, "wolf", "common");
        Player player = player(playerId, GameMode.SURVIVAL, new FakeItemStack(Material.STONE, 1), new FakeItemStack(Material.WOLF_SPAWN_EGG, 2));
        OwnedPetData runtimeState = new OwnedPetData(petData.petId(), playerId, "wolf", "common");
        PetForgeFlowSupport.ForgeStateSnapshot snapshot = PetForgeFlowSupport.snapshotState(player, petData);

        PetForgeFlowSupport.ForgeAttemptResult result = PetForgeFlowSupport.attemptRarityUpgrade(
            player,
            petData,
            PetRarity.COMMON,
            List.of(1),
            snapshot,
            1.0D,
            () -> 0.0D,
            runtimeState::copyProgressionFrom,
            () -> false
        );

        assertFalse(result.upgraded());
        assertTrue(result.saveFailed());
        assertEquals("common", petData.rarity().toLowerCase());
        assertEquals("common", runtimeState.rarity().toLowerCase());
        assertEquals(2, count(player, 1));
    }

    @Test
    void successfulSaveConsumesDonorEggsAndUpgradesRarity() {
        UUID playerId = UUID.randomUUID();
        OwnedPetData petData = new OwnedPetData(UUID.randomUUID(), playerId, "wolf", "common");
        Player player = player(playerId, GameMode.SURVIVAL, new FakeItemStack(Material.STONE, 1), new FakeItemStack(Material.WOLF_SPAWN_EGG, 2));
        OwnedPetData runtimeState = new OwnedPetData(petData.petId(), playerId, "wolf", "common");
        PetForgeFlowSupport.ForgeStateSnapshot snapshot = PetForgeFlowSupport.snapshotState(player, petData);

        PetForgeFlowSupport.ForgeAttemptResult result = PetForgeFlowSupport.attemptRarityUpgrade(
            player,
            petData,
            PetRarity.COMMON,
            List.of(1),
            snapshot,
            1.0D,
            () -> 0.0D,
            runtimeState::copyProgressionFrom,
            () -> true
        );

        assertTrue(result.upgraded());
        assertFalse(result.saveFailed());
        assertEquals("rare", petData.rarity().toLowerCase());
        assertEquals("rare", runtimeState.rarity().toLowerCase());
        assertEquals(1, count(player, 1));
    }

    private Player player(UUID playerId, GameMode gameMode, ItemStack mainHand, ItemStack storageItem) {
        ItemStack[] storage = new ItemStack[] {mainHand == null ? null : mainHand.clone(), storageItem == null ? null : storageItem.clone()};
        AtomicReference<ItemStack[]> storageRef = new AtomicReference<>(cloneContents(storage));
        AtomicReference<ItemStack> mainHandRef = new AtomicReference<>(mainHand == null ? null : mainHand.clone());
        AtomicReference<ItemStack> offHandRef = new AtomicReference<>(null);
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
            PlayerInventory.class.getClassLoader(),
            new Class<?>[] {PlayerInventory.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getStorageContents" -> cloneContents(storageRef.get());
                case "setStorageContents" -> {
                    storageRef.set(cloneContents((ItemStack[]) args[0]));
                    yield null;
                }
                case "getItemInMainHand" -> mainHandRef.get();
                case "setItemInMainHand" -> {
                    mainHandRef.set(cloneItem((ItemStack) args[0]));
                    yield null;
                }
                case "getItemInOffHand" -> offHandRef.get();
                case "setItemInOffHand" -> {
                    offHandRef.set(cloneItem((ItemStack) args[0]));
                    yield null;
                }
                case "getItem" -> cloneItem(storageRef.get()[(int) args[0]]);
                case "setItem" -> {
                    ItemStack[] current = cloneContents(storageRef.get());
                    current[(int) args[0]] = cloneItem((ItemStack) args[1]);
                    storageRef.set(current);
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
                case "toString" -> "test-player";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private int count(Player player, int slot) {
        ItemStack item = player.getInventory().getItem(slot);
        return item == null ? 0 : item.getAmount();
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            clone[index] = cloneItem(source[index]);
        }
        return clone;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
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
