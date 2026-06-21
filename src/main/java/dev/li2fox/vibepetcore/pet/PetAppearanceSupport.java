package dev.li2fox.vibepetcore.pet;

import dev.li2fox.vibepetcore.player.OwnedPetData;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

final class PetAppearanceSupport {
    private PetAppearanceSupport() {
    }

    static void clearEntityHands(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(new ItemStack(Material.AIR));
            equipment.setItemInOffHand(new ItemStack(Material.AIR));
        }
        if (entity instanceof Allay allay) {
            allay.getInventory().clear();
        }
    }

    static void applyPersistentAppearance(LivingEntity entity, OwnedPetData data) {
        if (entity == null || entity.isDead()) {
            return;
        }
        if (entity instanceof Ageable ageable) {
            ageable.setBaby();
        }
        applyAxolotlVariant(entity, data);
        applyCatVariant(entity, data);
        applyFoxVariant(entity, data);
        applyFrogVariant(entity, data);
        applyPandaGenes(entity, data);
        applyWolfVariant(entity, data);
        applyRabbitVariant(entity, data);
        applyParrotVariant(entity, data);
    }

    static void applyVisualState(LivingEntity entity, OwnedPetData data, boolean resting) {
        if (entity instanceof Bat bat) {
            bat.setAwake(true);
        }
        if (entity instanceof Cat cat) {
            cat.setSitting(resting);
        }
        if (entity instanceof Wolf wolf) {
            wolf.setSitting(resting);
            wolf.setAngry(false);
        }
        if (entity instanceof Fox fox) {
            fox.setSleeping(resting);
            fox.setSitting(false);
            fox.setCrouching(false);
        }
    }

    private static void applyAxolotlVariant(LivingEntity entity, OwnedPetData data) {
        if (!(entity instanceof Axolotl axolotl)) {
            return;
        }
        Axolotl.Variant[] variants = Axolotl.Variant.values();
        int index = storedAxolotlVariantIndex(data, variants);
        axolotl.setVariant(variants[index]);
    }

    private static void applyCatVariant(LivingEntity entity, OwnedPetData data) {
        if (!(entity instanceof Cat cat)) {
            return;
        }
        Cat.Type[] types = {
            Cat.Type.ALL_BLACK,
            Cat.Type.BLACK,
            Cat.Type.BRITISH_SHORTHAIR,
            Cat.Type.CALICO,
            Cat.Type.JELLIE,
            Cat.Type.PERSIAN,
            Cat.Type.RAGDOLL,
            Cat.Type.RED,
            Cat.Type.SIAMESE,
            Cat.Type.TABBY,
            Cat.Type.WHITE
        };
        cat.setCatType(types[storedAppearanceIndex(data, "cat_variant", types.length)]);
    }

    private static void applyFoxVariant(LivingEntity entity, OwnedPetData data) {
        if (!(entity instanceof Fox fox)) {
            return;
        }
        Fox.Type[] types = Fox.Type.values();
        int currentIndex = indexOf(types, fox.getFoxType());
        fox.setFoxType(types[storedAppearanceIndex(data, "fox_variant", types.length, currentIndex)]);
    }

    private static void applyFrogVariant(LivingEntity entity, OwnedPetData data) {
        if (!(entity instanceof Frog frog)) {
            return;
        }
        Frog.Variant[] variants = {
            Frog.Variant.TEMPERATE,
            Frog.Variant.WARM,
            Frog.Variant.COLD
        };
        int currentIndex = indexOf(variants, frog.getVariant());
        frog.setVariant(variants[storedAppearanceIndex(data, "frog_variant", variants.length, currentIndex)]);
    }

    private static void applyPandaGenes(LivingEntity entity, OwnedPetData data) {
        if (!(entity instanceof Panda panda)) {
            return;
        }
        Panda.Gene[] genes = Panda.Gene.values();
        int mainIndex = indexOf(genes, panda.getMainGene());
        int hiddenIndex = indexOf(genes, panda.getHiddenGene());
        panda.setMainGene(genes[storedAppearanceIndex(data, "panda_main_gene", genes.length, mainIndex)]);
        panda.setHiddenGene(genes[storedAppearanceIndex(data, "panda_hidden_gene", genes.length, hiddenIndex)]);
    }

    private static void applyWolfVariant(LivingEntity entity, OwnedPetData data) {
        if (!(entity instanceof Wolf wolf)) {
            return;
        }
        Wolf.Variant[] variants = {
            Wolf.Variant.ASHEN,
            Wolf.Variant.BLACK,
            Wolf.Variant.CHESTNUT,
            Wolf.Variant.PALE,
            Wolf.Variant.RUSTY,
            Wolf.Variant.SNOWY,
            Wolf.Variant.SPOTTED,
            Wolf.Variant.STRIPED,
            Wolf.Variant.WOODS
        };
        wolf.setVariant(variants[storedAppearanceIndex(data, "wolf_variant", variants.length)]);
    }

    private static void applyRabbitVariant(LivingEntity entity, OwnedPetData data) {
        if (!(entity instanceof Rabbit rabbit)) {
            return;
        }
        Rabbit.Type[] types = Rabbit.Type.values();
        int currentIndex = indexOf(types, rabbit.getRabbitType());
        rabbit.setRabbitType(types[storedAppearanceIndex(data, "rabbit_variant", types.length, currentIndex)]);
    }

    private static void applyParrotVariant(LivingEntity entity, OwnedPetData data) {
        if (!(entity instanceof Parrot parrot)) {
            return;
        }
        Parrot.Variant[] variants = {
            Parrot.Variant.BLUE,
            Parrot.Variant.CYAN,
            Parrot.Variant.GRAY,
            Parrot.Variant.GREEN,
            Parrot.Variant.RED
        };
        parrot.setVariant(variants[storedAppearanceIndex(data, "parrot_variant", variants.length)]);
    }

    private static int storedAppearanceIndex(OwnedPetData data, String key, int bound) {
        return storedAppearanceIndex(data, key, bound, -1);
    }

    private static int storedAppearanceIndex(OwnedPetData data, String key, int bound, int fallbackIndex) {
        int index = data.progress().getOrDefault(key, -1);
        if (index < 0 || index >= bound) {
            index = fallbackIndex >= 0 && fallbackIndex < bound ? fallbackIndex : ThreadLocalRandom.current().nextInt(bound);
            data.progress().put(key, index);
        }
        return index;
    }

    private static <T> int indexOf(T[] values, T value) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == value) {
                return index;
            }
        }
        return -1;
    }

    private static int storedAxolotlVariantIndex(OwnedPetData data, Axolotl.Variant[] variants) {
        boolean legendary = PetRarity.parse(data.rarity()) == PetRarity.LEGENDARY;
        int index = data.progress().getOrDefault("axolotl_variant", -1);
        if (index >= 0 && index < variants.length && ((legendary && variants[index] == Axolotl.Variant.BLUE) || (!legendary && variants[index] != Axolotl.Variant.BLUE))) {
            return index;
        }
        index = randomAxolotlVariantIndex(variants, legendary);
        data.progress().put("axolotl_variant", index);
        return index;
    }

    private static int randomAxolotlVariantIndex(Axolotl.Variant[] variants, boolean legendary) {
        if (legendary) {
            int blueIndex = indexOf(variants, Axolotl.Variant.BLUE);
            return blueIndex >= 0 ? blueIndex : 0;
        }
        int allowed = 0;
        for (Axolotl.Variant variant : variants) {
            if (variant != Axolotl.Variant.BLUE) {
                allowed++;
            }
        }
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, allowed));
        for (int index = 0; index < variants.length; index++) {
            if (variants[index] != Axolotl.Variant.BLUE) {
                if (roll-- == 0) {
                    return index;
                }
            }
        }
        return 0;
    }

}
