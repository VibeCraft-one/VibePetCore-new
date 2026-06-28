package dev.li2fox.vibepetcore.core;

import dev.li2fox.vibepetcore.config.BalanceConfig;
import dev.li2fox.vibepetcore.pet.PetRarity;
import dev.li2fox.vibepetcore.pet.PetState;
import dev.li2fox.vibepetcore.pet.PetType;
import dev.li2fox.vibepetcore.quest.QuestDefinition;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.Material;

public final class GameText {
    private static volatile BalanceConfig balanceConfig;

    private GameText() {
    }

    public static void bind(BalanceConfig config) {
        balanceConfig = config;
    }

    public static String text(String key, String ru, String en) {
        return msg(key, localized(ru, en));
    }

    private static boolean russian() {
        return balanceConfig != null && balanceConfig.useRussianLanguage();
    }

    private static String localized(String ru, String en) {
        return russian() ? ru : en;
    }

    private static String msg(String key, String fallback) {
        return balanceConfig == null ? fallback : balanceConfig.message(key, fallback);
    }

    private static String msg(String key, String fallback, Object... replacements) {
        return balanceConfig == null ? replace(fallback, replacements) : balanceConfig.message(key, fallback, replacements);
    }

    private static String replace(String text, Object... replacements) {
        String resolved = text;
        if (replacements != null) {
            for (int index = 0; index + 1 < replacements.length; index += 2) {
                resolved = resolved.replace("{" + replacements[index] + "}", String.valueOf(replacements[index + 1]));
            }
        }
        return resolved;
    }

    public static String petTypeName(PetType type) {
        String fallback = switch (type) {
            case AXOLOTL -> localized("Аксолотль", "Axolotl");
            case BAT -> localized("Летучая мышь", "Bat");
            case BEE -> localized("Пчела", "Bee");
            case BLAZE -> localized("Ифрит", "Blaze");
            case BREEZE -> localized("Бриз", "Breeze");
            case CAT -> localized("Кот", "Cat");
            case FOX -> localized("Лиса", "Fox");
            case FROG -> localized("Лягушка", "Frog");
            case GHAST -> localized("Гаст", "Ghast");
            case PANDA -> localized("Панда", "Panda");
            case PARROT -> localized("Попугай", "Parrot");
            case PHANTOM -> localized("Фантом", "Phantom");
            case RABBIT -> localized("Кролик", "Rabbit");
            case ALLAY -> localized("Тихоня", "Allay");
            case ARMADILLO -> localized("Броненосец", "Armadillo");
            case VEX -> localized("Вредина", "Vex");
            case WOLF -> localized("Волк", "Wolf");
        };
        return msg("pet.type." + type.name().toLowerCase(Locale.ROOT), fallback);
    }

    public static String rarityName(String rarity) {
        return rarityName(PetRarity.parse(rarity));
    }

    public static String rarityName(PetRarity rarity) {
        String fallback = switch (rarity) {
            case COMMON -> localized("обычная", "common");
            case RARE -> localized("редкая", "rare");
            case EPIC -> localized("эпическая", "epic");
            case LEGENDARY -> localized("легендарная", "legendary");
        };
        return msg("pet.rarity." + rarity.name().toLowerCase(Locale.ROOT), fallback);
    }

    public static String stateName(PetState state) {
        String fallback = switch (state) {
            case FOLLOW -> localized("следует", "following");
            case IDLE -> localized("ждёт", "idle");
            case ATTACK -> localized("в бою", "in combat");
            case RETURN -> localized("возвращается", "returning");
        };
        return msg("pet.state." + state.name().toLowerCase(Locale.ROOT), fallback);
    }

    public static String materialName(Material material) {
        if (material == null) {
            return msg("material.unknown", localized("неизвестный предмет", "unknown item"));
        }
        String fallback = switch (material) {
            case WHEAT -> localized("пшеница", "wheat");
            case BREAD -> localized("хлеб", "bread");
            case APPLE -> localized("яблоко", "apple");
            case CARROT -> localized("морковь", "carrot");
            case SWEET_BERRIES -> localized("сладкие ягоды", "sweet berries");
            case GLOW_BERRIES -> localized("светящиеся ягоды", "glow berries");
            case COD -> localized("сырая треска", "cod");
            case COOKED_COD -> localized("жареная треска", "cooked cod");
            case SALMON -> localized("сырой лосось", "salmon");
            case COOKED_SALMON -> localized("жареный лосось", "cooked salmon");
            case TROPICAL_FISH -> localized("тропическая рыба", "tropical fish");
            case BEEF -> localized("сырая говядина", "raw beef");
            case COOKED_BEEF -> localized("стейк", "cooked beef");
            case CHICKEN -> localized("сырая курица", "raw chicken");
            case COOKED_CHICKEN -> localized("жареная курица", "cooked chicken");
            case MUTTON -> localized("сырая баранина", "raw mutton");
            case COOKED_MUTTON -> localized("жареная баранина", "cooked mutton");
            case RABBIT -> localized("сырой кролик", "raw rabbit");
            case COOKED_RABBIT -> localized("жареный кролик", "cooked rabbit");
            case BONE -> localized("кость", "bone");
            case HONEY_BOTTLE -> localized("бутылочка мёда", "honey bottle");
            case HONEYCOMB -> localized("пчелиные соты", "honeycomb");
            case HONEY_BLOCK -> localized("медовый блок", "honey block");
            case AMETHYST_SHARD -> localized("осколок аметиста", "amethyst shard");
            case RAW_IRON -> localized("сырой железняк", "raw iron");
            case RAW_COPPER -> localized("сырая медь", "raw copper");
            case COPPER_INGOT -> localized("медный слиток", "copper ingot");
            case IRON_INGOT -> localized("железный слиток", "iron ingot");
            case GOLD_INGOT -> localized("золотой слиток", "gold ingot");
            case COAL -> localized("уголь", "coal");
            case DIAMOND -> localized("алмаз", "diamond");
            case EMERALD -> localized("изумруд", "emerald");
            case NETHER_STAR -> localized("звезда Незера", "Nether star");
            case ECHO_SHARD -> localized("осколок эха", "echo shard");
            case QUARTZ -> localized("незер-кварц", "quartz");
            case STONE -> localized("камень", "stone");
            case DEEPSLATE -> localized("глубинный сланец", "deepslate");
            case OAK_LOG -> localized("дубовое бревно", "oak log");
            case BAMBOO -> localized("бамбук", "bamboo");
            case BAMBOO_BLOCK -> localized("блок бамбука", "bamboo block");
            case STONE_BRICKS -> localized("каменные кирпичи", "stone bricks");
            case GUNPOWDER -> localized("порох", "gunpowder");
            case BONE_MEAL -> localized("костная мука", "bone meal");
            case BONE_BLOCK -> localized("костный блок", "bone block");
            case SPIDER_EYE -> localized("паучий глаз", "spider eye");
            case FERMENTED_SPIDER_EYE -> localized("маринованный паучий глаз", "fermented spider eye");
            case ENDER_PEARL -> localized("эндер-жемчуг", "ender pearl");
            case ENDER_EYE -> localized("око Эндера", "ender eye");
            case STRING -> localized("нить", "string");
            case SLIME_BALL -> localized("сгусток слизи", "slime ball");
            case SLIME_BLOCK -> localized("блок слизи", "slime block");
            case BLAZE_ROD -> localized("огненный стержень", "blaze rod");
            case BLAZE_POWDER -> localized("огненный порошок", "blaze powder");
            case MAGMA_CREAM -> localized("магмовый крем", "magma cream");
            case FIRE_CHARGE -> localized("огненный шар", "fire charge");
            case NETHER_WART -> localized("незерский нарост", "nether wart");
            case MANGROVE_ROOTS -> localized("мангровые корни", "mangrove roots");
            case OCHRE_FROGLIGHT -> localized("охристый лягушачий свет", "ochre froglight");
            case VERDANT_FROGLIGHT -> localized("зелёный лягушачий свет", "verdant froglight");
            case PEARLESCENT_FROGLIGHT -> localized("перламутровый лягушачий свет", "pearlescent froglight");
            case GLOW_INK_SAC -> localized("светящийся чернильный мешок", "glow ink sac");
            case RABBIT_HIDE -> localized("кроличья шкурка", "rabbit hide");
            case RABBIT_FOOT -> localized("кроличья лапка", "rabbit foot");
            case FEATHER -> localized("перо", "feather");
            case POPPY -> localized("мак", "poppy");
            case WHEAT_SEEDS -> localized("семена пшеницы", "wheat seeds");
            case MELON_SEEDS -> localized("семена арбуза", "melon seeds");
            case GOLDEN_CARROT -> localized("золотая морковь", "golden carrot");
            case LAPIS_LAZULI -> localized("лазурит", "lapis lazuli");
            case PRISMARINE_CRYSTALS -> localized("призмариновые кристаллы", "prismarine crystals");
            case PRISMARINE_SHARD -> localized("осколок призмарина", "prismarine shard");
            case TROPICAL_FISH_BUCKET -> localized("ведро с тропической рыбой", "tropical fish bucket");
            case WIND_CHARGE -> localized("заряд ветра", "wind charge");
            case BREEZE_ROD -> localized("стержень бриза", "breeze rod");
            case PHANTOM_MEMBRANE -> localized("мембрана фантома", "phantom membrane");
            case GHAST_TEAR -> localized("слеза гаста", "ghast tear");
            case SOUL_SAND -> localized("песок душ", "soul sand");
            case PUFFERFISH -> localized("иглобрюх", "pufferfish");
            case HEART_OF_THE_SEA -> localized("сердце моря", "heart of the sea");
            case SUNFLOWER -> localized("подсолнух", "sunflower");
            case CAKE -> localized("торт", "cake");
            case ARMADILLO_SCUTE -> localized("щиток броненосца", "armadillo scute");
            case FERN -> localized("папоротник", "fern");
            case SHORT_GRASS -> localized("трава", "grass");
            case TALL_GRASS -> localized("высокая трава", "tall grass");
            default -> title(material.name());
        };
        return msg("material." + material.name().toLowerCase(Locale.ROOT), fallback);
    }

    public static String materialList(Collection<Material> materials, int limit) {
        List<String> names = materials.stream().limit(limit).map(GameText::materialName).toList();
        if (names.isEmpty()) {
            return msg("common.none", localized("нет", "none"));
        }
        String suffix = materials.size() > names.size() ? "..." : "";
        return String.join(", ", names) + suffix;
    }

    public static String questTarget(QuestDefinition quest) {
        return switch (quest.type()) {
            case KILL_MOB -> msg("quest.target.kill", localized("убить {target}", "kill {target}"), "target", mobName(quest.target()));
            case BREAK_BLOCK -> msg("quest.target.break", localized("сломать {target}", "break {target}"), "target", materialName(Material.matchMaterial(quest.target())));
            case PICKUP_ITEM -> msg("quest.target.pickup", localized("принести {target}", "bring {target}"), "target", materialName(Material.matchMaterial(quest.target())));
            case EXPLORE -> msg("quest.target.explore", localized("исследовать {target}", "explore {target}"), "target", title(quest.target()));
        };
    }

    public static String rewardPoints(long amount) {
        return msg("reward.pet-points", localized("{amount} очков питомца", "{amount} pet progress points"), "amount", amount);
    }

    public static String mobName(String raw) {
        String value = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ZOMBIE" -> msg("mob.zombie", localized("зомби", "zombie"));
            case "SKELETON" -> msg("mob.skeleton", localized("скелет", "skeleton"));
            case "CREEPER" -> msg("mob.creeper", localized("крипер", "creeper"));
            case "SPIDER" -> msg("mob.spider", localized("паук", "spider"));
            case "CAVE_SPIDER" -> msg("mob.cave_spider", localized("пещерный паук", "cave spider"));
            case "SLIME" -> msg("mob.slime", localized("слизень", "slime"));
            case "ENDERMAN" -> msg("mob.enderman", localized("эндермен", "enderman"));
            case "BLAZE" -> msg("mob.blaze", localized("ифрит", "blaze"));
            case "WITHER" -> msg("mob.wither", localized("визер", "wither"));
            case "WITHER_SKELETON" -> msg("mob.wither_skeleton", localized("визер-скелет", "wither skeleton"));
            case "ENDER_DRAGON" -> msg("mob.ender_dragon", localized("дракон Эндера", "ender dragon"));
            default -> title(value);
        };
    }

    public static String effectName(String raw) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "night_vision" -> msg("effect.night-vision", localized("ночное зрение", "night vision"));
            case "slow_falling" -> msg("effect.slow-falling", localized("плавное падение", "slow falling"));
            case "invisibility" -> msg("effect.invisibility", localized("невидимость", "invisibility"));
            case "speed" -> msg("effect.speed", localized("скорость", "speed"));
            case "jump_boost" -> msg("effect.jump-boost", localized("прыгучесть", "jump boost"));
            case "haste" -> msg("effect.haste", localized("спешка", "haste"));
            case "resistance" -> msg("effect.resistance", localized("сопротивление", "resistance"));
            case "strength" -> msg("effect.strength", localized("сила", "strength"));
            case "absorption" -> msg("effect.absorption", localized("поглощение", "absorption"));
            case "regeneration" -> msg("effect.regeneration", localized("регенерация", "regeneration"));
            case "fire_resistance" -> msg("effect.fire-resistance", localized("огнестойкость", "fire resistance"));
            case "water_breathing" -> msg("effect.water-breathing", localized("подводное дыхание", "water breathing"));
            case "saturation" -> msg("effect.saturation", localized("насыщение", "saturation"));
            default -> title(value);
        };
    }

    public static String title(String raw) {
        if (raw == null || raw.isBlank()) {
            return msg("common.unknown", localized("неизвестно", "unknown"));
        }
        return java.util.Arrays.stream(raw.toLowerCase(Locale.ROOT).split("_"))
            .filter(part -> !part.isBlank())
            .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .collect(Collectors.joining(" "));
    }

    public static String questMenuTitle() { return msg("gui.quests.title", localized("VibePet - Квесты", "VibePet - Quests")); }
    public static String questCategoryName(String category) {
        return switch (category == null ? "daily" : category.toLowerCase(Locale.ROOT)) {
            case "all" -> msg("quest.category.all", localized("Все", "All"));
            case "daily" -> msg("quest.category.daily", localized("Ежедневные", "Daily"));
            case "weekly" -> msg("quest.category.weekly", localized("Недельные", "Weekly"));
            case "evolution" -> msg("quest.category.evolution", localized("Эволюция", "Evolution"));
            case "gather" -> msg("quest.category.gather", localized("Сбор", "Gather"));
            case "combat" -> msg("quest.category.combat", localized("Бой", "Combat"));
            case "explore" -> msg("quest.category.explore", localized("Мир", "World"));
            default -> msg("quest.category.daily", localized("Ежедневные", "Daily"));
        };
    }
    public static String questCategoryLine(String categoryName) { return msg("quest.line.category", localized("Категория: &f{value}", "Category: &f{value}"), "value", categoryName); }
    public static String questTargetLine(String target) { return msg("quest.line.target", localized("Цель: &f{value}", "Target: &f{value}"), "value", target); }
    public static String questProgressLine(int current, int total) { return msg("quest.line.progress", localized("Прогресс: &f{current}/{total}", "Progress: &f{current}/{total}"), "current", current, "total", total); }
    public static String questStatusLine(String status) { return msg("quest.line.status", localized("Статус: &f{value}", "Status: &f{value}"), "value", status); }
    public static String questRewardLine(String reward) { return msg("quest.line.reward", localized("Награда: &f{reward}", "Reward: &f{reward}"), "reward", reward); }
    public static String questAccessLine(String reason) { return msg("quest.line.access", localized("Доступ: &f{reason}", "Access: &f{reason}"), "reason", reason); }
    public static String questRepeatLine(String repeatText) { return msg("quest.line.repeat", localized("Повтор: &fраз в {repeat}", "Repeat: &fonce every {repeat}"), "repeat", repeatText); }
    public static String questActionTakeAgain() { return msg("quest.action.take-again", localized("&aКлик: взять снова", "&aClick: take again")); }
    public static String questActionAlreadyDone() { return msg("quest.action.already-done", localized("&8Квест уже сдан", "&8Quest already turned in")); }
    public static String questActionTurnInWithItems() { return msg("quest.action.turn-in-items", localized("&aКлик: сдать и забрать предметы", "&aClick: turn in and consume items")); }
    public static String questActionTurnIn() { return msg("quest.action.turn-in", localized("&aКлик: сдать", "&aClick: turn in")); }
    public static String questActionCheckProgress() { return msg("quest.action.check-progress", localized("&eКлик: проверить прогресс", "&eClick: check progress")); }
    public static String questActionAccept() { return msg("quest.action.accept", localized("&aКлик: принять квест", "&aClick: accept quest")); }
    public static String questNameReadyAgain(String title) { return "&e↻ " + title; }
    public static String questNameCompleted(String title) { return "&a✔ " + title; }
    public static String questNameReadyToTurnIn(String title) { return "&a! " + title; }
    public static String questNameDefault(String title) { return "&f" + title; }
    public static String questNeedSelectedPet() { return msg("quest.block.need-selected-pet", localized("выберите активного питомца или возьмите ядро в руку", "select an active pet or hold the core")); }
    public static String questWrongEvolutionStage(int requiredStage, int currentStage) { return msg("quest.block.wrong-stage", localized("этот квест для E{required}, а выбран E{current}", "this quest is for E{required}, but E{current} is selected"), "required", requiredStage, "current", currentStage); }
    public static String questAcceptedAgain() { return msg("quest.accepted-again", localized("Квест снова доступен.", "Quest is available again.")); }
    public static String questAcceptedAgainBlocked() { return msg("quest.accepted-again-blocked", localized("Этот квест пока нельзя взять снова.", "This quest cannot be taken again yet.")); }
    public static String questAccepted() { return msg("quest.accepted", localized("Квест принят.", "Quest accepted.")); }
    public static String questAcceptFailed() { return msg("quest.accept-failed", localized("Не удалось принять квест.", "Failed to accept quest.")); }
    public static String questTurnedIn() { return msg("quest.turned-in", localized("Квест завершён. Очки питомца начислены.", "Quest completed. Pet Points awarded.")); }
    public static String questTurnInBlocked() { return msg("quest.turn-in-blocked", localized("Этот квест пока нельзя сдать.", "This quest cannot be turned in yet.")); }
    public static String questTurnInSaveFailed() { return msg("quest.turn-in-save-failed", localized("Квест не удалось сохранить. Предметы и награда восстановлены, попробуйте ещё раз через пару секунд.", "Could not save the quest turn-in. Items and reward were restored. Try again in a few seconds.")); }
    public static String questBlocked(String reason) { return msg("quest.blocked", localized("Сейчас нельзя взять этот квест: {reason}.", "You cannot take this quest right now: {reason}."), "reason", reason); }
    public static String questRepeatWeek() { return msg("time.week", localized("неделю", "week")); }
    public static String questRepeatDay() { return msg("time.day", localized("день", "day")); }
    public static String questRepeatHours(long hours) { return msg("time.hours.short", localized("{hours} ч.", "{hours} hr."), "hours", hours); }
    public static String questRepeatMinutes(long minutes) { return msg("time.minutes.value-short", localized("{minutes} мин.", "{minutes} min."), "minutes", minutes); }
    public static String questStatusReadyAgain() { return msg("quest.status.available-again", localized("Доступно снова", "Available again")); }
    public static String questStatusCooldown(String duration) { return msg("quest.status.cooldown", localized("Кд: {duration}", "Cooldown: {duration}"), "duration", duration); }
    public static String questStatusCompletedPlain() { return msg("quest.status.completed", localized("Завершено", "Completed")); }
    public static String questStatusAvailable() { return msg("quest.status.available", localized("Доступно", "Available")); }
    public static String questStatusReadyTurnIn() { return msg("quest.status.ready", localized("Готово к сдаче", "Ready to turn in")); }
    public static String questStatusRemaining(int missing) { return msg("quest.status.remaining", localized("Осталось: {missing}", "Remaining: {missing}"), "missing", missing); }
    public static String questCategoryTabLine(String categoryName) { return msg("quest.category.tab-line", localized("&7Категория: &f{category}", "&7Category: &f{category}"), "category", categoryName); }
    public static String questCategoryActiveHint() { return msg("quest.category.active-hint", localized("&aТекущая вкладка", "&aCurrent tab")); }
    public static String questCategoryOpenHint() { return msg("quest.category.open-hint", localized("&eКлик: открыть", "&eClick: open")); }
    public static String questEmptyTitle() { return msg("quest.empty.title", localized("&7Квестов пока нет", "&7No quests yet")); }
    public static String questEmptyHint(String categoryName) { return msg("quest.empty.hint", localized("&7Категория &f{category} &7пока пуста.", "&7The &f{category} &7category is empty for now."), "category", categoryName); }
    public static String helpFoodLine(String food) { return msg("gui.help.food-line", localized("&7Еда: &f{food}", "&7Food: &f{food}"), "food", food); }
    public static String helpUseCaseLine(String useCase) { return msg("gui.help.use-case-line", localized("&7Роль / сценарий: &f{useCase}", "&7Role / use case: &f{useCase}"), "useCase", useCase); }
    public static String helpBuffPlanHeader() { return msg("gui.help.buff-plan-header", localized("&7Ключевые баффы:", "&7Key buffs:")); }
    public static String helpCommandHint(String typeKey) { return "&f/pet help " + typeKey; }
    public static String petInfoNameLine(String name) { return msg("gui.pet.info.name-line", localized("&7Имя ядра: &f{name}", "&7Core name: &f{name}"), "name", name); }
    public static String petInfoRarityLine(String rarity) { return msg("gui.pet.info.rarity-line", localized("&7Редкость: &f{rarity}", "&7Rarity: &f{rarity}"), "rarity", rarity); }
    public static String petInfoRoleTitle() { return msg("gui.pet.info.role.title", localized("Роль и боевой профиль", "Role and combat profile")); }
    public static String petInfoGuideTitle() { return msg("gui.pet.info.guide.title", localized("Использование и стиль", "Usage and style")); }
    public static String petInfoEvolutionPreviewTitle() { return msg("gui.pet.info.evolution.preview.title", localized("Следующая эволюция", "Next evolution")); }
    public static String petInfoEvolutionActionHint() { return msg("gui.pet.info.evolution.action-hint", localized("&7Нажмите, чтобы попробовать улучшить активное ядро", "&7Click to try upgrading the active core")); }
    public static String petInfoEvolutionLine(String stageName, int stage) { return msg("gui.pet.info.evolution-line", localized("&7Эволюция: &f{stageName} &7(E{stage})", "&7Evolution: &f{stageName} &7(E{stage})"), "stageName", stageName, "stage", stage); }
    public static String petInfoLevelLine(int level) { return msg("gui.pet.info.level-line", localized("&7Уровень: &f{level}/10", "&7Level: &f{level}/10"), "level", level); }
    public static String petInfoBondLine(int bond, int maxBond) { return msg("gui.pet.info.bond-line", localized("&7Связь: &f{bond}/{max}", "&7Bond: &f{bond}/{max}"), "bond", bond, "max", Math.max(1, maxBond)); }
    public static String petInfoStatusLine(String status) { return msg("gui.pet.info.status-line", localized("&7Статус: &f{status}", "&7Status: &f{status}"), "status", status); }
    public static String petInfoDurabilityLine(int current, int max) { return msg("gui.pet.info.durability-line", localized("&7Прочность ядра: &f{current}/{max}", "&7Core durability: &f{current}/{max}"), "current", current, "max", max); }
    public static String petInfoSatietyLine(int current, int max) { return msg("gui.pet.info.satiety-line", localized("&7Сытость: &f{current}/{max}", "&7Satiety: &f{current}/{max}"), "current", current, "max", max); }
    public static String petInfoNoCoreLine() { return msg("gui.pet.info.no-core-line", localized("&8Активного ядра этого типа пока нет.", "&8No active core of this type yet.")); }
    public static String petInfoNeedCoreHint() { return msg("gui.pet.info.need-core-hint", localized("&7Возьмите яйцо в руку или призовите питомца.", "&7Hold the egg or summon the pet.")); }
    public static String petInfoAttackLine(String attack) { return msg("gui.pet.info.attack-line", localized("&7Атака: &f{attack}", "&7Attack: &f{attack}"), "attack", attack); }
    public static String petInfoDefenseLine(String defense) { return msg("gui.pet.info.defense-line", localized("&7Защита владельца: &f{defense}", "&7Owner defense: &f{defense}"), "defense", defense); }
    public static String petInfoEffectsLine(String effects) { return msg("gui.pet.info.effects-line", localized("&7Полезные эффекты: &f{effects}", "&7Useful effects: &f{effects}"), "effects", effects); }
    public static String petInfoPowerLine(String power) { return msg("gui.pet.info.power-line", localized("&7Боевой профиль: &f{power}", "&7Combat profile: &f{power}"), "power", power); }
    public static String petInfoNextEvolutionLine(int stage) { return msg("gui.pet.info.next-evolution-line", localized("&7Следующая эволюция: &fE{stage}", "&7Next evolution: &fE{stage}"), "stage", stage); }
    public static String petInfoRewardLine(String reward) { return msg("gui.pet.info.reward-line", localized("&7Награда стадии: &f{reward}", "&7Stage reward: &f{reward}"), "reward", reward); }
    public static String petInfoRequirementsLine(int level, int bond, int quests, int requiredQuests) { return msg("gui.pet.info.requirements-line", localized("&7Нужно: &fур. {level}, связь {bond}/10, квесты {quests}/{requiredQuests}", "&7Required: &fLv. {level}, bond {bond}/10, quests {quests}/{requiredQuests}"), "level", level, "bond", bond, "quests", quests, "requiredQuests", requiredQuests); }
    public static String petInfoProgressLine(int level, int bond, int quests, int requiredQuests) { return msg("gui.pet.info.progress-line", localized("&7Сейчас: &fур. {level}, связь {bond}/10, квесты {quests}/{requiredQuests}", "&7Current: &fLv. {level}, bond {bond}/10, quests {quests}/{requiredQuests}"), "level", level, "bond", bond, "quests", quests, "requiredQuests", requiredQuests); }
    public static String petInfoReadyToEvolve() { return msg("gui.pet.info.ready-to-evolve", localized("&aТребования выполнены. Питомец может эволюционировать.", "&aRequirements met. The pet can evolve.")); }
    public static String petInfoNeedMoreProgress() { return msg("gui.pet.info.need-more-progress", localized("&eДля следующей формы нужно ещё немного прогресса.", "&eYou still need a bit more progress for the next form.")); }
    public static String petInfoMaxEvolution() { return msg("gui.pet.info.max-evolution", localized("&aДостигнута максимальная эволюция.", "&aMaximum evolution reached.")); }
    public static String petInfoAttackRating(double rating, double multiplier) { return msg("gui.pet.info.attack-rating", localized("{rating} рейтинг | x{multiplier}", "{rating} rating | x{multiplier}"), "rating", decimal(rating), "multiplier", decimal(multiplier)); }
    public static String petInfoCombatPower(String base, String growth, String attack) { return base + " | " + attack + " | " + growth; }
    public static String combatPowerVeryHigh() { return msg("combat.power.very-high", localized("очень высокий", "very high")); }
    public static String combatPowerHigh() { return msg("combat.power.high", localized("высокий", "high")); }
    public static String combatPowerMedium() { return msg("combat.power.medium", localized("средний", "medium")); }
    public static String combatPowerLow() { return msg("combat.power.low", localized("ниже среднего", "below average")); }
    public static String combatPowerVeryLow() { return msg("combat.power.very-low", localized("лёгкий", "light")); }
    public static String combatGrowthLate() { return msg("combat.growth.late", localized("раскрывается на поздней эволюции", "peaks in late evolution")); }
    public static String combatGrowthMid() { return msg("combat.growth.mid", localized("становится сильнее по мере роста", "unlocks more as it grows")); }
    public static String combatGrowthBase() { return msg("combat.growth.base", localized("стабилен уже в базе", "stable even at base")); }
    public static String evolutionStageName(int stage) {
        return switch (Math.max(1, Math.min(5, stage))) {
            case 1 -> msg("evolution.stage.1", localized("ядро", "core"));
            case 2 -> msg("evolution.stage.2", localized("усиленная форма", "boosted form"));
            case 3 -> msg("evolution.stage.3", localized("зрелая форма", "mature form"));
            case 4 -> msg("evolution.stage.4", localized("поздняя форма", "late form"));
            case 5 -> msg("evolution.stage.5", localized("максимальная форма", "max form"));
            default -> msg("evolution.stage.1", localized("ядро", "core"));
        };
    }
    public static String petEvolutionPreviewTransition(String currentStage, String nextStage) { return msg("gui.pet.evolution.transition", localized("&7Переход: &f{current} &7-> &f{next}", "&7Transition: &f{current} &7-> &f{next}"), "current", currentStage, "next", nextStage); }
    public static String petEvolutionPreviewLevelLine(int currentLevel, int requiredLevel) { return msg("gui.pet.evolution.level-line", localized("&7Уровень: &f{current}/{required}", "&7Level: &f{current}/{required}"), "current", currentLevel, "required", requiredLevel); }
    public static String petEvolutionPreviewBondLine(int currentBond, int requiredBond) { return msg("gui.pet.evolution.bond-line", localized("&7Связь: &f{current}/{required}", "&7Bond: &f{current}/{required}"), "current", currentBond, "required", requiredBond); }
    public static String petEvolutionPreviewQuestLine(int currentQuests, int requiredQuests) { return msg("gui.pet.evolution.quest-line", localized("&7Квесты: &f{current}/{required}", "&7Quests: &f{current}/{required}"), "current", currentQuests, "required", requiredQuests); }
    public static String petEvolutionPreviewMaterialsHeader() { return msg("gui.pet.evolution.materials-header", localized("&7Материалы:", "&7Materials:")); }
    public static String petEvolutionPreviewMaterialLine(String materialName, int currentAmount, int requiredAmount) { return msg("gui.pet.evolution.material-line", localized("&8- &f{material} &7{current}/{required}", "&8- &f{material} &7{current}/{required}"), "material", materialName, "current", currentAmount, "required", requiredAmount); }
    public static String petEvolutionPreviewReady() { return msg("gui.pet.evolution.ready", localized("&aТребования выполнены. Стадия готова к эволюции.", "&aRequirements are complete. The stage is ready to evolve.")); }
    public static String petEvolutionPreviewNotReady() { return msg("gui.pet.evolution.not-ready", localized("&eЭтой форме нужно ещё немного прогресса.", "&eThis form still needs more progress.")); }
    public static String petEvolutionPreviewMaxStage(String stageName) { return msg("gui.pet.evolution.max-stage", localized("&7Форма: &f{stageName}", "&7Form: &f{stageName}"), "stageName", stageName); }
    public static String petEvolutionPreviewCurrentStageHint(String stageName) { return msg("gui.pet.evolution.current-stage-hint", localized("&aЭто пиковая форма: &f{stageName}", "&aThis is the peak form: &f{stageName}"), "stageName", stageName); }
    public static String guiBack() { return msg("gui.back", localized("Назад", "Back")); }
    public static String guiUnavailable() { return msg("gui.unavailable", localized("&7Сейчас недоступно", "&7Currently unavailable")); }
    public static String guiTitleMain() { return msg("gui.main.title", localized("VibePet - Меню", "VibePet - Menu")); }
    public static String mainMenuQuestsTitle() { return msg("gui.main.quests.title", localized("&aКвесты", "&aQuests")); }
    public static String mainMenuQuestsHint() { return msg("gui.main.quests.hint", localized("&7Ежедневные, недельные и эволюционные квесты.", "&7Daily, weekly, and evolution quests.")); }
    public static String mainMenuGuideTitle() { return msg("gui.main.guide.title", localized("&eСправка", "&eGuide")); }
    public static String mainMenuGuideHint() { return msg("gui.main.guide.hint", localized("&7Краткая справка по питомцам и их ролям.", "&7A quick guide to pets and their roles.")); }
    public static String mainMenuPetTitle() { return msg("gui.main.pet.title", localized("&bМой питомец", "&bMy Pet")); }
    public static String mainMenuPetHint() { return msg("gui.main.pet.hint", localized("&7Открыть ядро, статус и боевые настройки.", "&7Open the core, status, and combat settings.")); }
    public static String mainMenuBoxesTitle() { return msg("gui.main.boxes.title", localized("&dИсточник", "&dPet Source")); }
    public static String mainMenuBoxesHint() { return msg("gui.main.boxes.hint", localized("&7Открыть меню Источника и наград.", "&7Open the Pet Source and rewards menu.")); }
    public static String mainMenuQuickGuideTitle() { return msg("gui.main.quick-guide.title", localized("&aБыстрый старт", "&aQuick Start")); }
    public static String mainMenuQuickGuideHint() { return msg("gui.main.quick-guide.hint", localized("&7Начните здесь, если нужна короткая подсказка.", "&7Start here if you need a quick hint.")); }
    public static String mainMenuGrowthTitle() { return msg("gui.main.growth.title", localized("&bРост питомца", "&bPet Growth")); }
    public static String mainMenuGrowthHint() { return msg("gui.main.growth.hint", localized("&7Быстрый переход к эволюции и характеристикам.", "&7Jump straight to the current evolution and stats.")); }
    public static String mainMenuQuickBoxTitle() { return msg("gui.main.quick-box.title", localized("&dОткрыть Источник", "&dOpen Source")); }
    public static String mainMenuQuickBoxHint() { return msg("gui.main.quick-box.hint", localized("&7Быстрый путь к Источнику.", "&7A quick path to the Pet Source.")); }
    public static String guiTitleBox() { return msg("gui.box.title", localized("VibePet - Источник", "VibePet - Source")); }
    public static String boxStatusTitle() { return msg("gui.box.status.title", localized("Бесплатное открытие", "Free opening")); }
    public static String boxStatusFreeReady() { return msg("gui.box.status.free-ready", localized("&aБесплатное открытие готово", "&aFree source opening ready")); }
    public static String boxStatusFreeCooldown(long minutes) { return msg("gui.box.status.free-cooldown", localized("&7Следующее открытие Источника через &f{minutes} &7мин.", "&7Next source opening in &f{minutes} &7min."), "minutes", minutes); }
    public static String boxStatusExtraAttempts(int attempts) { return msg("gui.box.status.extra-attempts", localized("&7Резервные попытки: &f{attempts}", "&7Reserve attempts: &f{attempts}"), "attempts", attempts); }
    public static String boxPointsTitle() { return msg("gui.box.points.title", localized("&dОчки Источника", "&dSource Points")); }
    public static String boxPointsBalance(long points) { return msg("gui.box.points.balance", localized("&7Накоплено: &f{points} &7очков", "&7Saved: &f{points} &7points"), "points", points); }
    public static String boxPointsCost(long cost) { return msg("gui.box.points.cost", localized("&7Цена открытия: &f{cost} &7очков", "&7Opening cost: &f{cost} &7points"), "cost", cost); }
    public static String boxPointsAvailable(long attempts) { return msg("gui.box.points.available", localized("&7Можно открыть за очки: &f{attempts}", "&7Point openings available: &f{attempts}"), "attempts", attempts); }
    public static String boxOpenBasicTitle() { return msg("gui.box.basic.title", localized("&dОткрыть Источник", "&dOpen Source")); }
    public static String boxOpenBasicHint() { return msg("gui.box.basic.hint", localized("&7Даёт обычные и редкие яйца для ядра.", "&7Gives common and rare eggs for the core.")); }
    public static String boxOpenBasicAttemptsHint() { return msg("gui.box.basic.attempts-hint", localized("&7Тратит бесплатное открытие, очки или резервную попытку.", "&7Consumes a free opening, Pet Points, or a reserve attempt.")); }
    public static String boxPityTitle() { return msg("gui.box.pity.title", localized("Порог редкости", "Rare pity")); }
    public static String boxPityProgress(int current, int threshold) { return msg("gui.box.pity.progress", localized("&7Прогресс порога: &f{current}/{threshold}", "&7Pity progress: &f{current}/{threshold}"), "current", current, "threshold", threshold); }
    public static String boxPityHint() { return msg("gui.box.pity.hint", localized("&7На пороге Источник выдаёт редкое без топ-редкостей.", "&7At the threshold, the source gives rare without top rarities.")); }
    public static String boxInfoTitle() { return msg("gui.box.info.title", localized("Как получить попытки", "How to gain attempts")); }
    public static String boxInfoLineOne() { return msg("gui.box.info.line1", localized("&f1. Каждые 2 часа даётся одно бесплатное открытие", "&f1. One free source opening is granted every 2 hours")); }
    public static String boxInfoLineTwo() { return msg("gui.box.info.line2", localized("&f2. Квесты дают очки для платных открытий", "&f2. Quests grant points for paid openings")); }
    public static String boxInfoLineThree() { return msg("gui.box.info.line3", localized("&f3. Дубликаты нужны для кузни ядра", "&f3. Duplicates are needed for the core forge")); }
    public static String mainMenuForgeTitle() { return msg("gui.main.forge.title", localized("&6Кузня ядра", "&6Core Forge")); }
    public static String mainMenuForgeHint() { return msg("gui.main.forge.hint", localized("&7Улучшайте редкость яйца через донорские яйца.", "&7Upgrade egg rarity through donor eggs.")); }
    public static String petOverviewCurrentCore() { return msg("gui.pet.overview.current-core", localized("Текущее ядро", "Current core")); }
    public static String petOverviewNoCore() { return msg("gui.pet.overview.no-core", localized("Ядро не выбрано", "No core selected")); }
    public static String petOverviewNeedCoreHint() { return msg("gui.pet.overview.need-core-hint", localized("&7Возьмите ядро питомца в руку", "&7Hold the pet core")); }
    public static String petOverviewCallPet() { return msg("gui.pet.overview.call-pet", localized("Призвать питомца", "Summon pet")); }
    public static String petOverviewMovementMode(boolean waiting) { return waiting ? msg("gui.pet.overview.waiting-mode", localized("Режим ожидания", "Waiting mode")) : msg("gui.pet.overview.follow-mode", localized("Режим следования", "Follow mode")); }
    public static String petOverviewMovementHint(boolean waiting) { return waiting ? msg("gui.pet.overview.waiting-hint", localized("&7Питомец будет стоять на месте", "&7The pet will stay in place")) : msg("gui.pet.overview.follow-hint", localized("&7Питомец будет следовать за вами", "&7The pet will follow you")); }
    public static String petOverviewFollowPosition() { return msg("gui.pet.overview.follow-position", localized("Позиция следования", "Follow position")); }
    public static String petOverviewFollowPositionValue(String value) { return msg("gui.pet.overview.follow-position-value", localized("&7Текущая позиция: &f{value}", "&7Current position: &f{value}"), "value", value); }
    public static String petOverviewVault() { return msg("gui.pet.overview.vault", localized("Рюкзак питомца", "Pet vault")); }
    public static String petOverviewVaultHint() { return msg("gui.pet.overview.vault-hint", localized("&7Открыть инвентарь активного питомца", "&7Open the active pet inventory")); }
    public static String petOverviewEvolution() { return msg("gui.pet.overview.evolution", localized("Эволюция", "Evolution")); }
    public static String petOverviewAutoLoot(boolean enabled) { return enabled ? msg("gui.pet.overview.autoloot-on", localized("Автолут включён", "Auto-loot enabled")) : msg("gui.pet.overview.autoloot-off", localized("Автолут выключен", "Auto-loot disabled")); }
    public static String petOverviewAutoLootHint() { return msg("gui.pet.overview.autoloot-hint", localized("&7Подбирает предметы рядом с владельцем", "&7Pick up items near the owner")); }
    public static String petOverviewDefense(boolean enabled) { return enabled ? msg("gui.pet.overview.defense-on", localized("Защита владельца включена", "Owner defense enabled")) : msg("gui.pet.overview.defense-off", localized("Защита владельца выключена", "Owner defense disabled")); }
    public static String petOverviewDefenseHint() { return msg("gui.pet.overview.defense-hint", localized("&7Питомец вступит в бой за владельца", "&7The pet will join combat for the owner")); }
    public static String petOverviewRepairCore() { return msg("gui.pet.overview.repair-core", localized("Починить ядро", "Repair core")); }
    public static String petOverviewRepairHint() { return msg("gui.pet.overview.repair-hint", localized("&7Ремонт тратит один тотем и даёт +1 прочности", "&7Repair consumes one totem and adds +1 durability")); }
    public static String petOverviewTrainTitle() { return msg("gui.pet.overview.train", localized("Тренировка", "Training")); }
    public static String petOverviewTrainHint() { return msg("gui.pet.overview.train-hint", localized("&7Дайте опыт призванному питомцу рядом с вами.", "&7Grant XP to your summoned pet while it is nearby.")); }
    public static String petOverviewTrainCooldown(long seconds) { return msg("gui.pet.overview.train-cooldown", localized("&7Повтор через &f{seconds} &7с.", "&7Again in &f{seconds} &7s."), "seconds", seconds); }
    public static String petOverviewTrainReady() { return msg("gui.pet.overview.train-ready", localized("&aГотово к тренировке.", "&aReady to train.")); }
    public static String forgeNeedActiveCore() { return msg("gui.forge.need-active-core", localized("Держите ядро питомца в руке.", "Hold the pet core.")); }
    public static String guiTitleForge() { return msg("gui.forge.title", localized("VibePet - Кузня", "VibePet - Forge")); }
    public static String guiTitlePetOverview() { return msg("gui.pet.overview.title", localized("VibePet - Питомец", "VibePet - Pet")); }
    public static String forgeUpgradeTitle() { return msg("gui.forge.upgrade.title", localized("Улучшить редкость", "Upgrade rarity")); }
    public static String forgeUpgradeHint() { return msg("gui.forge.upgrade.hint", localized("&7Использует текущее ядро и донорские яйца", "&7Uses the current core and donor eggs")); }
    public static String forgeInfoTitle() { return msg("gui.forge.info.title", localized("Как работает кузня", "How the forge works")); }
    public static String forgeInfoLineOne() { return msg("gui.forge.info.line1", localized("&f1. Держите ядро питомца в руке", "&f1. Hold the pet core")); }
    public static String forgeInfoLineTwo() { return msg("gui.forge.info.line2", localized("&f2. Соберите донорские яйца того же типа и редкости", "&f2. Gather donor eggs of the same type and rarity")); }
    public static String forgeInfoLineThree() { return msg("gui.forge.info.line3", localized("&f3. Каждая попытка тратит донорские яйца", "&f3. Each attempt always consumes donor eggs")); }
    public static String forgeInfoLineFour() { return msg("gui.forge.info.line4", localized("&7Редкость растёт по ступеням: common -> rare -> epic -> legendary", "&7Rarity grows by stages: common -> rare -> epic -> legendary")); }
    public static String forgeDonorChestTitle() { return msg("gui.forge.donor-chest.title", localized("Донорские яйца", "Donor eggs")); }
    public static String forgeDonorChestHint() { return msg("gui.forge.donor-chest.hint", localized("&7Нужны точные копии текущего ядра", "&7Need exact duplicates of the current core")); }
    public static String petOverviewInfoTitle() { return msg("gui.pet.overview.info.title", localized("Обзор ядра", "Core overview")); }
    public static String petOverviewInfoLineOne() { return msg("gui.pet.overview.info.line1", localized("&7Здесь собраны основные функции активного питомца", "&7The main active pet functions are gathered here")); }
    public static String petOverviewInfoLineTwo() { return msg("gui.pet.overview.info.line2", localized("&7Здесь можно призвать, лечить и настраивать поведение", "&7From here you can summon, heal, and tune behavior")); }
    public static String petOverviewStatusTitle() { return msg("gui.pet.overview.status.title", localized("Сводка статуса", "Status summary")); }
    public static String petOverviewStatusHint() { return msg("gui.pet.overview.status.hint", localized("&7Основные показатели активного ядра", "&7Key indicators for the active core")); }
    public static String petOverviewCallHint(boolean summoned) { return summoned ? msg("gui.pet.overview.call.active", localized("&aПитомец уже активен", "&aPet is already active")) : msg("gui.pet.overview.call.hint", localized("&7Призвать питомца из ядра", "&7Summon the pet from the core")); }
    public static String petOverviewControllerTitle() { return msg("gui.pet.overview.controller.title", localized("&bКонтроллер следования", "&bFollow controller")); }
    public static String petOverviewControllerHint() { return msg("gui.pet.overview.controller.hint", localized("&7Красные и зелёные стекла выбирают позицию вокруг игрока; факелы меняют дистанцию.", "&7Red and green panes choose the position around the player; torches change distance.")); }
    public static String petOverviewControllerCurrent(String position, String distance) { return msg("gui.pet.overview.controller.current", localized("&7Позиция: &f{position} &8| &7Дистанция: &f{distance}", "&7Position: &f{position} &8| &7Distance: &f{distance}"), "position", position, "distance", distance); }
    public static String petOverviewFollowDisabledTitle() { return msg("gui.pet.overview.follow.disabled.title", localized("&cПитомец ждёт", "&cPet is waiting")); }
    public static String petOverviewFollowDisabledHint() { return msg("gui.pet.overview.follow.disabled.hint", localized("&7Питомец стоит рядом и не следует за вами.", "&7The pet stands nearby and does not follow you.")); }
    public static String petOverviewFollowEnabledTitle() { return msg("gui.pet.overview.follow.enabled.title", localized("&aПитомец следует", "&aPet is following")); }
    public static String petOverviewFollowEnabledHint() { return msg("gui.pet.overview.follow.enabled.hint", localized("&7Питомец следует за вами, как разведчик.", "&7The pet follows you like a scout.")); }
    public static String petOverviewFollowBackTitle() { return msg("gui.pet.overview.follow.back.title", localized("&dБлиже", "&dCloser")); }
    public static String petOverviewFollowForwardTitle() { return msg("gui.pet.overview.follow.forward.title", localized("&dДальше", "&dFurther")); }
    public static String petOverviewFollowDistanceHint() { return msg("gui.pet.overview.follow.distance-hint", localized("&7Кнопки позиции выбирают место; Ближе/Дальше меняют дистанцию.", "&7Position buttons choose the spot; Closer/Further changes distance.")); }
    public static String petOverviewSummonTitle() { return msg("gui.pet.overview.summon.title", localized("&eПризвать питомца", "&eSummon pet")); }
    public static String petOverviewSummonHint() { return msg("gui.pet.overview.summon.hint", localized("&7Питомец призывается из ядра во второй руке.", "&7The pet is summoned from the offhand core.")); }
    public static String petOverviewAggressiveTitle() { return msg("gui.pet.overview.aggressive.title", localized("&6Боевой стиль", "&6Aggressive style")); }
    public static String petOverviewAggressiveHint(boolean enabled) { return enabled ? msg("gui.pet.overview.aggressive.enabled", localized("&7Включено: питомец атакует и защищает вас.", "&7Enabled: the pet attacks and protects you.")) : msg("gui.pet.overview.aggressive.disabled", localized("&7Выключено: питомец избегает боя и остаётся в безопасности.", "&7Disabled: the pet avoids combat and stays safe.")); }
    public static String petOverviewExitTitle() { return msg("gui.pet.overview.exit.title", localized("&cВыход", "&cExit")); }
    public static String petOverviewExitHint() { return msg("gui.pet.overview.exit.hint", localized("&7Закрыть меню питомца.", "&7Close the pet menu.")); }
    public static String petOverviewCoreActiveLabel() { return msg("gui.pet.overview.core.active", localized("&aАктивное ядро", "&aActive core")); }
    public static String petOverviewCoreOffhandLabel() { return msg("gui.pet.overview.core.offhand", localized("&7Ядро в инвентаре", "&7Core in inventory")); }
    public static String petOverviewHelpTitle() { return msg("gui.pet.overview.help.title", localized("Справка по питомцу", "Pet guide")); }
    public static String petOverviewHelpHint() { return msg("gui.pet.overview.help.hint", localized("&7Открыть роли, эволюции и подсказки по типам", "&7Open roles, evolutions, and type hints")); }
    public static String guiTitleHelpOverview() { return msg("gui.help.title", localized("VibePet - Справка", "VibePet - Guide")); }
    public static String forgeMaxRarity() { return msg("gui.forge.max-rarity", localized("Это ядро уже легендарное.", "This core is already legendary.")); }
    public static String forgeNeedSacrifices(int count, String petName, String rarityName) { return msg("gui.forge.need-sacrifices", localized("Нужно ещё {count} яиц {petName} редкости {rarityName}.", "Need {count} more {petName} eggs of {rarityName} rarity."), "count", count, "petName", petName, "rarityName", rarityName); }
    public static String forgeUpgradeAttempt(double chancePercent) { return msg("gui.forge.upgrade.attempt", localized("&7Шанс успеха: &f{chance}%", "&7Success chance: &f{chance}%"), "chance", Math.round(chancePercent)); }
    public static String forgeUpgradeCost(int cost) { return msg("gui.forge.upgrade.cost", localized("&7Стоимость: &f{cost} подходящих яиц", "&7Cost: &f{cost} matching eggs"), "cost", cost); }
    public static String forgeUpgradeSuccess(String rarityName) { return msg("gui.forge.upgrade.success", localized("Улучшение успешно. Новая редкость: {rarity}.", "Upgrade successful. New rarity: {rarity}."), "rarity", rarityName); }
    public static String forgeUpgradeFail() { return msg("gui.forge.upgrade.fail", localized("Улучшение не удалось. Донорские яйца были израсходованы.", "Upgrade failed. The donor eggs were consumed.")); }
    public static String coreRepairSuccess() { return msg("core.repair.success", localized("Ядро починено.", "Core repaired.")); }
    public static String coreRepairMissing() { return msg("core.repair.missing", localized("Возьмите ядро питомца в руку.", "Hold a pet core.")); }
    public static String coreRepairAlreadyFull() { return msg("core.repair.full", localized("Ядро не повреждено.", "Core is not damaged.")); }
    public static String coreRepairNoTotems() { return msg("core.repair.no-totems", localized("У вас нет тотемов.", "You have no totems.")); }
    public static String coreRepairIncreased(int before, int after) { return msg("core.repair.increased", localized("Прочность ядра повышена с {before} до {after}.", "Core durability increased from {before} to {after}."), "before", before, "after", after); }

    private static String decimal(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
