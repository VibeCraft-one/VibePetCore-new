package dev.li2fox.vibepetcore.box;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.core.GameText;
import dev.li2fox.vibepetcore.egg.PetEggService;
import dev.li2fox.vibepetcore.economy.EconomyManager;
import dev.li2fox.vibepetcore.pet.PetRarity;
import dev.li2fox.vibepetcore.pet.PetType;
import dev.li2fox.vibepetcore.player.PlayerData;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;

public final class BoxManager {
    private static final String BASIC_BOX = "basic";

    private final BalanceConfig config;
    private final EconomyManager economyManager;
    private final PetEggService petEggService;

    public BoxManager(BalanceConfig config, EconomyManager economyManager, PetEggService petEggService) {
        this.config = config;
        this.economyManager = economyManager;
        this.petEggService = petEggService;
    }

    public BoxOpenResult openBasic(Player player) {
        if (player.getInventory().firstEmpty() < 0) {
            return new BoxOpenResult(false, config.message("box.open.inventory-full", "Free one inventory slot before opening a box."), null, null, false);
        }

        PlayerData data = economyManager.data(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (data.freeBoxNextAtMillis() <= now) {
            data.setFreeBoxNextAtMillis(now + config.boxFreeCooldownMillis(BASIC_BOX));
        } else if (!economyManager.take(player.getUniqueId(), Math.max(1L, config.boxCost(BASIC_BOX))) && !data.takeExtraBoxAttempt()) {
            long minutes = Math.max(1L, (data.freeBoxNextAtMillis() - now) / 60_000L);
            return new BoxOpenResult(false, config.message(
                "box.open.no-attempts",
                "Not enough Pet Points. Complete quests or wait {minutes} min.",
                "minutes", minutes
            ), null, null, false);
        }

        int pityCount = data.boxPity().getOrDefault(BASIC_BOX, 0) + 1;
        boolean pity = pityCount >= config.boxPityThreshold(BASIC_BOX);
        PetRarity rarity = pity && config.boxGuaranteeOnlyRare(BASIC_BOX) ? PetRarity.RARE : rollRarity();
        data.boxPity().put(BASIC_BOX, rarity == PetRarity.RARE ? 0 : pityCount);
        data.statistics().addBoxOpened();

        PetType petType = randomPetType();
        player.getInventory().addItem(petEggService.createEgg(petType, rarity, petType.displayName()));
        String messageKey = pity ? "box.open.success-pity" : "box.open.success";
        String messageFallback = pity
            ? "Box opened: {rarity} {pet}. Pity guarantee triggered!"
            : "Box opened: {rarity} {pet}.";
        return new BoxOpenResult(true, config.message(
            messageKey,
            messageFallback,
            "rarity", GameText.rarityName(rarity),
            "pet", GameText.petTypeName(petType)
        ), petType, rarity, pity);
    }

    private PetRarity rollRarity() {
        double roll = ThreadLocalRandom.current().nextDouble();
        double rare = config.boxRarityChance(BASIC_BOX, "rare");
        if (roll < rare) {
            return PetRarity.RARE;
        }
        return PetRarity.COMMON;
    }

    private PetType randomPetType() {
        PetType[] values = Arrays.stream(PetType.values())
            .filter(type -> type != PetType.VEX)
            .filter(config::petEnabled)
            .toArray(PetType[]::new);
        if (values.length == 0) {
            values = new PetType[] {PetType.WOLF};
        }
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
