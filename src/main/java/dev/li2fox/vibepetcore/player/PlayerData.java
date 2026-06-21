package dev.li2fox.vibepetcore.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerData {
    private static final Runnable NO_OP = () -> {};

    private @Nullable UUID playerId;
    private final TrackedPetList pets = new TrackedPetList();
    private @Nullable UUID activePetId;
    private long points;
    private long freeBoxNextAtMillis;
    private int extraBoxAttempts;
    private final PlayerStatistics statistics = new PlayerStatistics();
    private final TrackedQuestMap quests = new TrackedQuestMap();
    private final TrackedIntMap boxPity = new TrackedIntMap();
    private transient Runnable dirtyMarker = NO_OP;

    public PlayerData() {
    }

    public PlayerData(@NotNull UUID playerId) {
        this.playerId = playerId;
    }

    public @Nullable UUID playerId() {
        return playerId;
    }

    public void ensurePlayerId(@NotNull UUID playerId) {
        if (this.playerId == null) {
            this.playerId = playerId;
            markDirty();
        }
    }

    public List<OwnedPetData> pets() {
        return pets;
    }

    public @NotNull Optional<UUID> activePetId() {
        return Optional.ofNullable(activePetId);
    }

    public void setActivePetId(@Nullable UUID activePetId) {
        if (!Objects.equals(this.activePetId, activePetId)) {
            this.activePetId = activePetId;
            markDirty();
        }
    }

    public long points() {
        return points;
    }

    public void addPoints(long amount) {
        points = Math.max(0L, points + amount);
        if (amount != 0L) {
            markDirty();
        }
    }

    public boolean takePoints(long amount) {
        if (amount < 0L || points < amount) {
            return false;
        }
        points -= amount;
        markDirty();
        return true;
    }

    public PlayerStatistics statistics() {
        return statistics;
    }

    public Map<String, QuestProgressData> quests() {
        return quests;
    }

    public Map<String, Integer> boxPity() {
        return boxPity;
    }

    public long freeBoxNextAtMillis() {
        return freeBoxNextAtMillis;
    }

    public void setFreeBoxNextAtMillis(long freeBoxNextAtMillis) {
        this.freeBoxNextAtMillis = Math.max(0L, freeBoxNextAtMillis);
        markDirty();
    }

    public int extraBoxAttempts() {
        return extraBoxAttempts;
    }

    public void addExtraBoxAttempt() {
        extraBoxAttempts++;
        markDirty();
    }

    public boolean takeExtraBoxAttempt() {
        if (extraBoxAttempts <= 0) {
            return false;
        }
        extraBoxAttempts--;
        markDirty();
        return true;
    }

    public void attachDirtyMarker(Runnable dirtyMarker) {
        this.dirtyMarker = dirtyMarker == null ? NO_OP : dirtyMarker;
        statistics.attachDirtyMarker(this::markDirty);
        pets.attachDirtyMarker(this::markDirty);
        quests.attachDirtyMarker(this::markDirty);
        boxPity.attachDirtyMarker(this::markDirty);
    }

    private void markDirty() {
        dirtyMarker.run();
    }

    private static final class TrackedPetList extends ArrayList<OwnedPetData> {
        private transient Runnable dirtyMarker = NO_OP;

        void attachDirtyMarker(Runnable dirtyMarker) {
            this.dirtyMarker = dirtyMarker == null ? NO_OP : dirtyMarker;
            forEach(this::attach);
        }

        @Override
        public boolean add(OwnedPetData pet) {
            boolean added = super.add(attach(pet));
            if (added) {
                dirtyMarker.run();
            }
            return added;
        }

        @Override
        public void add(int index, OwnedPetData element) {
            super.add(index, attach(element));
            dirtyMarker.run();
        }

        @Override
        public boolean addAll(Collection<? extends OwnedPetData> collection) {
            if (collection.isEmpty()) {
                return false;
            }
            List<OwnedPetData> attached = collection.stream().map(this::attach).toList();
            boolean added = super.addAll(attached);
            if (added) {
                dirtyMarker.run();
            }
            return added;
        }

        @Override
        public boolean addAll(int index, Collection<? extends OwnedPetData> collection) {
            if (collection.isEmpty()) {
                return false;
            }
            List<OwnedPetData> attached = collection.stream().map(this::attach).toList();
            boolean added = super.addAll(index, attached);
            if (added) {
                dirtyMarker.run();
            }
            return added;
        }

        @Override
        public OwnedPetData set(int index, OwnedPetData element) {
            OwnedPetData previous = super.set(index, attach(element));
            dirtyMarker.run();
            return previous;
        }

        @Override
        public OwnedPetData remove(int index) {
            OwnedPetData removed = super.remove(index);
            dirtyMarker.run();
            return removed;
        }

        @Override
        public boolean remove(Object element) {
            boolean removed = super.remove(element);
            if (removed) {
                dirtyMarker.run();
            }
            return removed;
        }

        @Override
        public boolean removeIf(Predicate<? super OwnedPetData> filter) {
            boolean removed = super.removeIf(filter);
            if (removed) {
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

        private OwnedPetData attach(OwnedPetData pet) {
            if (pet != null) {
                pet.attachDirtyMarker(dirtyMarker);
            }
            return pet;
        }
    }

    private static final class TrackedQuestMap extends HashMap<String, QuestProgressData> {
        private transient Runnable dirtyMarker = NO_OP;

        void attachDirtyMarker(Runnable dirtyMarker) {
            this.dirtyMarker = dirtyMarker == null ? NO_OP : dirtyMarker;
            values().forEach(this::attach);
        }

        @Override
        public QuestProgressData put(String key, QuestProgressData value) {
            QuestProgressData previous = super.put(key, attach(value));
            dirtyMarker.run();
            return previous;
        }

        @Override
        public void putAll(Map<? extends String, ? extends QuestProgressData> map) {
            if (map.isEmpty()) {
                return;
            }
            map.forEach((key, value) -> super.put(key, attach(value)));
            dirtyMarker.run();
        }

        @Override
        public QuestProgressData remove(Object key) {
            QuestProgressData removed = super.remove(key);
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

        @Override
        public @Nullable QuestProgressData computeIfAbsent(
            @NotNull String key,
            @NotNull Function<? super String, ? extends QuestProgressData> mappingFunction
        ) {
            QuestProgressData existing = get(key);
            if (existing != null) {
                return existing;
            }
            QuestProgressData created = attach(mappingFunction.apply(key));
            if (created == null) {
                return null;
            }
            super.put(key, created);
            dirtyMarker.run();
            return created;
        }

        private @Nullable QuestProgressData attach(@Nullable QuestProgressData progress) {
            if (progress != null) {
                progress.attachDirtyMarker(dirtyMarker);
            }
            return progress;
        }
    }

    private static final class TrackedIntMap extends HashMap<String, Integer> {
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
