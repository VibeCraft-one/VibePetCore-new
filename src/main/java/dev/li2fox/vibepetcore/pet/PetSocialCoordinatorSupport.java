package dev.li2fox.vibepetcore.pet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

final class PetSocialCoordinatorSupport {
    private PetSocialCoordinatorSupport() {
    }

    static final class CombatLink {
        long lastEventAt;
        long combatUntil;
        long duelUntil;
        long petProvocationUntil;
        UUID lastAggressorId;
        int exchanges;
        int petProvocations;
    }

    static void runSocialInteractions(
        Map<UUID, RuntimePet> activePets,
        Map<String, Long> socialPairCooldowns,
        Supplier<Long> nowSupplier,
        Function<UUID, Player> playerLookup,
        BiPredicate<Player, Player> pairInCombat,
        BiPredicate<RuntimePet, RuntimePet> socialChanceRoll,
        BiConsumer<Player, RuntimePet> showActionBar
    ) {
        List<RuntimePet> pets = new ArrayList<>(activePets.values());
        if (pets.size() < 2) {
            return;
        }

        long now = nowSupplier.get();
        for (int i = 0; i < pets.size(); i++) {
            RuntimePet first = pets.get(i);
            Player firstOwner = playerLookup.apply(first.data().ownerId());
            if (firstOwner == null || !firstOwner.isOnline() || !first.canSocialize()) {
                continue;
            }
            for (int j = i + 1; j < pets.size(); j++) {
                RuntimePet second = pets.get(j);
                Player secondOwner = playerLookup.apply(second.data().ownerId());
                if (secondOwner == null || !secondOwner.isOnline() || !second.canSocialize() || !firstOwner.getWorld().equals(secondOwner.getWorld())) {
                    continue;
                }
                String pairKey = socialPairKey(first, second);
                if (socialPairCooldowns.getOrDefault(pairKey, 0L) > now) {
                    continue;
                }
                if (pairInCombat.test(firstOwner, secondOwner)) {
                    continue;
                }
                socialPairCooldowns.put(pairKey, now + ThreadLocalRandom.current().nextLong(70_000L, 150_000L));
                if (!socialChanceRoll.test(first, second)) {
                    continue;
                }
                if (first.trySocializeWith(firstOwner, second, secondOwner)) {
                    showActionBar.accept(firstOwner, first);
                    showActionBar.accept(secondOwner, second);
                }
            }
        }
    }

    static void pruneTransientState(Map<String, Long> socialPairCooldowns, Map<String, CombatLink> combatLinks, long now) {
        socialPairCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        combatLinks.entrySet().removeIf(entry -> {
            CombatLink link = entry.getValue();
            return link.combatUntil <= now && link.duelUntil <= now && link.petProvocationUntil <= now;
        });
    }

    static void recordPlayerCombat(Map<String, CombatLink> combatLinks, UUID attackerId, UUID victimId, long playerCombatWindowMillis) {
        if (attackerId.equals(victimId)) {
            return;
        }
        long now = System.currentTimeMillis();
        CombatLink link = combatLinks.computeIfAbsent(combatKey(attackerId, victimId), key -> new CombatLink());
        if (now - link.lastEventAt > playerCombatWindowMillis) {
            link.exchanges = 0;
        }
        if (link.lastAggressorId != null && !link.lastAggressorId.equals(attackerId)) {
            link.exchanges++;
        }
        link.exchanges++;
        link.lastAggressorId = attackerId;
        link.lastEventAt = now;
        link.combatUntil = now + playerCombatWindowMillis;
        if (link.exchanges >= 3) {
            link.duelUntil = Math.max(link.duelUntil, now + 8_000L);
        }
    }

    static boolean registerPetProvocation(
        Map<String, CombatLink> combatLinks,
        UUID attackerId,
        UUID victimId,
        long playerCombatWindowMillis,
        long petProvokeWindowMillis,
        long petDuelWindowMillis
    ) {
        CombatLink link = combatLinks.computeIfAbsent(combatKey(attackerId, victimId), key -> new CombatLink());
        long now = System.currentTimeMillis();
        recordPlayerCombat(combatLinks, attackerId, victimId, playerCombatWindowMillis);
        if (now > link.petProvocationUntil) {
            link.petProvocations = 0;
        }
        link.petProvocations++;
        link.petProvocationUntil = now + petProvokeWindowMillis;
        if (link.petProvocations >= 2 || link.exchanges >= 3) {
            link.duelUntil = Math.max(link.duelUntil, now + petDuelWindowMillis);
            return true;
        }
        return false;
    }

    static boolean petsCanSpar(RuntimePet first, RuntimePet second) {
        Optional<Entity> firstEntity = first.entity().map(entity -> (Entity) entity);
        Optional<Entity> secondEntity = second.entity().map(entity -> (Entity) entity);
        if (firstEntity.isEmpty() || secondEntity.isEmpty()) {
            return false;
        }
        Entity a = firstEntity.get();
        Entity b = secondEntity.get();
        return a.getWorld().equals(b.getWorld()) && a.getLocation().distanceSquared(b.getLocation()) <= 100.0D;
    }

    static boolean pairInCombat(Map<String, CombatLink> combatLinks, UUID firstId, UUID secondId, long now) {
        CombatLink link = combatLinks.get(combatKey(firstId, secondId));
        return link != null && link.combatUntil > now;
    }

    private static String socialPairKey(RuntimePet first, RuntimePet second) {
        String a = first.data().petId().toString();
        String b = second.data().petId().toString();
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }

    private static String combatKey(UUID first, UUID second) {
        String a = first.toString();
        String b = second.toString();
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }
}
