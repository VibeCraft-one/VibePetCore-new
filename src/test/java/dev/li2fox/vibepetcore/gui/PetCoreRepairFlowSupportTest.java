package dev.li2fox.vibepetcore.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

final class PetCoreRepairFlowSupportTest {
    @Test
    void saveFailureRestoresTotemAndPetState() {
        UUID playerId = UUID.randomUUID();
        OwnedPetData petData = damagedPet(playerId);
        OwnedPetData runtimeState = damagedPet(playerId, petData.petId());
        Player player = player(playerId, GameMode.SURVIVAL, new FakeItemStack(Material.TOTEM_OF_UNDYING, 1));
        PetCoreRepairFlowSupport.RepairStateSnapshot snapshot = PetCoreRepairFlowSupport.snapshotState(player, petData);

        PetCoreRepairFlowSupport.RepairAttemptResult result = PetCoreRepairFlowSupport.attemptRepair(
            player,
            petData,
            4,
            5.0D,
            snapshot,
            runtimeState::copyProgressionFrom,
            () -> false
        );

        assertFalse(result.repaired());
        assertTrue(result.saveFailed());
        assertEquals(1, count(player));
        assertEquals(3, petData.durability());
        assertEquals(3, runtimeState.durability());
        assertEquals(2.0D, petData.satiety());
        assertEquals(2.0D, runtimeState.satiety());
        assertEquals(5.0D, petData.health());
        assertEquals(5.0D, runtimeState.health());
        assertEquals(12345L, petData.inactiveUntilMillis());
        assertEquals(12345L, runtimeState.inactiveUntilMillis());
    }

    @Test
    void successfulSaveConsumesTotemAndRepairsPetState() {
        UUID playerId = UUID.randomUUID();
        OwnedPetData petData = damagedPet(playerId);
        OwnedPetData runtimeState = damagedPet(playerId, petData.petId());
        Player player = player(playerId, GameMode.SURVIVAL, new FakeItemStack(Material.TOTEM_OF_UNDYING, 1));
        PetCoreRepairFlowSupport.RepairStateSnapshot snapshot = PetCoreRepairFlowSupport.snapshotState(player, petData);

        PetCoreRepairFlowSupport.RepairAttemptResult result = PetCoreRepairFlowSupport.attemptRepair(
            player,
            petData,
            4,
            5.0D,
            snapshot,
            runtimeState::copyProgressionFrom,
            () -> true
        );

        assertTrue(result.repaired());
        assertFalse(result.saveFailed());
        assertEquals(0, count(player));
        assertEquals(4, petData.durability());
        assertEquals(4, runtimeState.durability());
        assertEquals(5.0D, petData.satiety());
        assertEquals(5.0D, runtimeState.satiety());
        assertEquals(20.0D, petData.health());
        assertEquals(20.0D, runtimeState.health());
        assertEquals(0L, petData.inactiveUntilMillis());
        assertEquals(0L, runtimeState.inactiveUntilMillis());
    }

    private OwnedPetData damagedPet(UUID playerId) {
        return damagedPet(playerId, UUID.randomUUID());
    }

    private OwnedPetData damagedPet(UUID playerId, UUID petId) {
        OwnedPetData petData = new OwnedPetData(petId, playerId, "wolf", "common");
        petData.setDurability(3);
        petData.setSatiety(2.0D);
        petData.setHealth(5.0D);
        petData.setInactiveUntilMillis(12345L);
        return petData;
    }

    private Player player(UUID playerId, GameMode gameMode, ItemStack storageItem) {
        AtomicReference<ItemStack[]> storageRef = new AtomicReference<>(cloneContents(new ItemStack[] {cloneItem(storageItem)}));
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
            PlayerInventory.class.getClassLoader(),
            new Class<?>[] {PlayerInventory.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getStorageContents" -> cloneContents(storageRef.get());
                case "setStorageContents" -> {
                    storageRef.set(cloneContents((ItemStack[]) args[0]));
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

    private int count(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack item = contents.length == 0 ? null : contents[0];
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
