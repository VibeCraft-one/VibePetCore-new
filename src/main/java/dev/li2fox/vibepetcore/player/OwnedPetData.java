package dev.li2fox.vibepetcore.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OwnedPetData {
    private static final Runnable NO_OP = () -> {};
    private static final int FOLLOW_POSITION_COUNT = 8;
    private static final int FOLLOW_DISTANCE_MIN = 2;
    private static final int FOLLOW_DISTANCE_MAX = 6;

    private UUID petId;
    private UUID ownerId;
    private String petName;
    private String petType;
    private String rarity;
    private int level;
    private int subLevel;
    private int evolutionStage;
    private int bond;
    private double health;
    private double maxHealth;
    private double satiety;
    private int durability;
    private long inactiveUntilMillis;
    private long growthBoostUntil;
    private String state;
    private long xp;
    private final TrackedProgressMap progress = new TrackedProgressMap();
    private transient Runnable dirtyMarker = NO_OP;

    public OwnedPetData() {
        this(UUID.randomUUID(), null, "unknown", "common");
    }

    public OwnedPetData(UUID petId, UUID ownerId, String petType, String rarity) {
        this.petId = petId;
        this.ownerId = ownerId;
        this.petName = petType;
        this.petType = petType;
        this.rarity = rarity;
        this.level = 1;
        this.subLevel = 1;
        this.evolutionStage = 1;
        this.bond = 0;
        this.maxHealth = 20.0D;
        this.health = maxHealth;
        this.satiety = 5.0D;
        this.durability = 7;
        this.state = "FOLLOW";
    }

    public UUID petId() {
        return petId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
        markDirty();
    }

    public String petName() {
        return petName == null || petName.isBlank() ? petType : petName;
    }

    public void setPetName(String petName) {
        this.petName = petName;
        markDirty();
    }

    public String petType() {
        return petType;
    }

    public String rarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
        markDirty();
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(10, level));
        markDirty();
    }

    public int subLevel() {
        return subLevel;
    }

    public void setSubLevel(int subLevel) {
        this.subLevel = Math.max(1, Math.min(10, subLevel));
        markDirty();
    }

    public int evolutionStage() {
        return evolutionStage;
    }

    public void setEvolutionStage(int evolutionStage) {
        this.evolutionStage = Math.max(1, Math.min(5, evolutionStage));
        markDirty();
    }

    public int bond() {
        return Math.max(0, bond);
    }

    public void setBond(int bond) {
        this.bond = Math.max(0, Math.min(10, bond));
        markDirty();
    }

    public void adjustBond(int delta) {
        setBond(bond() + delta);
    }

    public double health() {
        return Math.max(0.0D, health);
    }

    public void setHealth(double health) {
        this.health = Math.max(0.0D, Math.min(maxHealth(), health));
        markDirty();
    }

    public void adjustHealth(double delta) {
        setHealth(health() + delta);
    }

    public double maxHealth() {
        return maxHealth <= 0.0D ? 20.0D : maxHealth;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = Math.max(1.0D, maxHealth);
        this.health = Math.min(this.health, this.maxHealth);
        markDirty();
    }

    public double satiety() {
        return satiety;
    }

    public void setSatiety(double satiety) {
        this.satiety = Math.max(0.0D, Math.min(5.0D, satiety));
        markDirty();
    }

    public void adjustSatiety(double delta) {
        setSatiety(satiety() + delta);
    }

    public boolean isDown() {
        return health() <= 0.0D;
    }

    public boolean isStarving() {
        return satiety() <= 0.0D;
    }

    public boolean isWounded() {
        return health() < maxHealth() * 0.35D;
    }

    public boolean isHungry() {
        return satiety() <= 1.0D;
    }

    public void restoreFullVitals() {
        this.health = maxHealth();
        this.satiety = 5.0D;
        markDirty();
    }

    public int durability() {
        return durability;
    }

    public void setDurability(int durability) {
        this.durability = Math.max(0, Math.min(99, durability));
        markDirty();
    }

    public long inactiveUntilMillis() {
        return inactiveUntilMillis;
    }

    public void setInactiveUntilMillis(long inactiveUntilMillis) {
        this.inactiveUntilMillis = Math.max(0L, inactiveUntilMillis);
        markDirty();
    }

    public long growthBoostUntil() {
        return growthBoostUntil;
    }

    public void setGrowthBoostUntil(long growthBoostUntil) {
        this.growthBoostUntil = Math.max(0L, growthBoostUntil);
        markDirty();
    }

    public String state() {
        return state == null ? "FOLLOW" : state;
    }

    public void setState(String state) {
        this.state = state;
        markDirty();
    }

    public long xp() {
        return xp;
    }

    public void setXp(long xp) {
        this.xp = Math.max(0L, xp);
        markDirty();
    }

    public Map<String, Integer> progress() {
        return progress;
    }

    public boolean autoLootEnabled() {
        return progress.getOrDefault("autoloot", 1) != 0;
    }

    public void setAutoLootEnabled(boolean enabled) {
        progress.put("autoloot", enabled ? 1 : 0);
    }

    public boolean defenseEnabled() {
        return progress.getOrDefault("defense", 1) != 0;
    }

    public void setDefenseEnabled(boolean enabled) {
        progress.put("defense", enabled ? 1 : 0);
    }

    public boolean passiveEffectEnabled(String effectKey) {
        if (effectKey == null || effectKey.isBlank()) {
            return true;
        }
        return progress.getOrDefault("buff_" + effectKey.toLowerCase(java.util.Locale.ROOT), 1) != 0;
    }

    public boolean togglePassiveEffect(String effectKey) {
        String key = "buff_" + effectKey.toLowerCase(java.util.Locale.ROOT);
        boolean enabled = progress.getOrDefault(key, 1) == 0;
        progress.put(key, enabled ? 1 : 0);
        return enabled;
    }

    public void setPassiveEffectEnabled(String effectKey, boolean enabled) {
        if (effectKey == null || effectKey.isBlank()) {
            return;
        }
        progress.put("buff_" + effectKey.toLowerCase(java.util.Locale.ROOT), enabled ? 1 : 0);
    }

    public void recoverAfterRest(double minimumHealth, double minimumSatiety) {
        if (health() < minimumHealth) {
            setHealth(minimumHealth);
        }
        if (satiety() < minimumSatiety) {
            setSatiety(minimumSatiety);
        }
    }

    public int followPosition() {
        return Math.floorMod(progress.getOrDefault("follow_position", 0), FOLLOW_POSITION_COUNT);
    }

    public int cycleFollowPosition() {
        int next = Math.floorMod(followPosition() + 1, FOLLOW_POSITION_COUNT);
        progress.put("follow_position", next);
        return next;
    }

    public int previousFollowPosition() {
        int next = Math.floorMod(followPosition() - 1, FOLLOW_POSITION_COUNT);
        progress.put("follow_position", next);
        return next;
    }

    public void setFollowPosition(int position) {
        progress.put("follow_position", Math.floorMod(position, FOLLOW_POSITION_COUNT));
    }

    public int followDistanceBand() {
        return Math.max(FOLLOW_DISTANCE_MIN, Math.min(FOLLOW_DISTANCE_MAX, progress.getOrDefault("follow_distance", 3)));
    }

    public int increaseFollowDistance() {
        int next = Math.min(FOLLOW_DISTANCE_MAX, followDistanceBand() + 1);
        progress.put("follow_distance", next);
        return next;
    }

    public int decreaseFollowDistance() {
        int next = Math.max(FOLLOW_DISTANCE_MIN, followDistanceBand() - 1);
        progress.put("follow_distance", next);
        return next;
    }

    public String followPositionTitle() {
        return switch (followPosition()) {
            case 0 -> "впереди";
            case 1 -> "впереди справа";
            case 2 -> "справа";
            case 3 -> "сзади справа";
            case 4 -> "сзади";
            case 5 -> "сзади слева";
            case 6 -> "слева";
            case 7 -> "впереди слева";
            default -> "впереди";
        };
    }

    public String followDistanceTitle() {
        return switch (followDistanceBand()) {
            case 2 -> "2";
            case 5 -> "5";
            case 6 -> "6";
            case 4 -> "4";
            default -> "3";
        };
    }

    public void copyProgressionFrom(OwnedPetData other) {
        if (this == other) {
            return;
        }
        if (other.ownerId() != null) {
            setOwnerId(other.ownerId());
        }
        setPetName(other.petName());
        setRarity(other.rarity());
        setLevel(other.level());
        setSubLevel(other.subLevel());
        setEvolutionStage(other.evolutionStage());
        setBond(other.bond());
        setMaxHealth(other.maxHealth());
        setHealth(other.health());
        setSatiety(other.satiety());
        setDurability(other.durability());
        setInactiveUntilMillis(other.inactiveUntilMillis());
        setGrowthBoostUntil(other.growthBoostUntil());
        setXp(other.xp());
        progress.clear();
        progress.putAll(other.progress());
        markDirty();
    }

    void attachDirtyMarker(Runnable dirtyMarker) {
        this.dirtyMarker = dirtyMarker == null ? NO_OP : dirtyMarker;
        progress.attachDirtyMarker(this::markDirty);
    }

    private void markDirty() {
        dirtyMarker.run();
    }

    private static final class TrackedProgressMap extends HashMap<String, Integer> {
        private transient Runnable dirtyMarker = NO_OP;

        void attachDirtyMarker(Runnable dirtyMarker) {
            this.dirtyMarker = dirtyMarker == null ? NO_OP : dirtyMarker;
        }

        @Override
        public Integer put(String key, Integer value) {
            Integer previous = super.put(key, value);
            dirtyMarker.run();
            return previous;
        }

        @Override
        public void putAll(Map<? extends String, ? extends Integer> map) {
            if (map.isEmpty()) {
                return;
            }
            super.putAll(map);
            dirtyMarker.run();
        }

        @Override
        public Integer remove(Object key) {
            Integer removed = super.remove(key);
            if (removed != null) {
                dirtyMarker.run();
            }
            return removed;
        }

        @Override
        public void clear() {
            if (isEmpty()) {
                return;
            }
            super.clear();
            dirtyMarker.run();
        }
    }
}
