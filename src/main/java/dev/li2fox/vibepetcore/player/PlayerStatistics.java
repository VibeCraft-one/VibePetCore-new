package dev.li2fox.vibepetcore.player;

public final class PlayerStatistics {
    private static final Runnable NO_OP = () -> {};

    private long questsCompleted;
    private long kills;
    private long activityTicks;
    private long blocksBroken;
    private long boxesOpened;
    private transient Runnable dirtyMarker = NO_OP;

    public long questsCompleted() {
        return questsCompleted;
    }

    public void addQuestCompleted() {
        questsCompleted++;
        markDirty();
    }

    public long kills() {
        return kills;
    }

    public void addKill() {
        kills++;
        markDirty();
    }

    public long activityTicks() {
        return activityTicks;
    }

    public void addActivityTicks(long ticks) {
        long normalized = Math.max(0L, ticks);
        activityTicks += normalized;
        if (normalized > 0L) {
            markDirty();
        }
    }

    public long blocksBroken() {
        return blocksBroken;
    }

    public void addBlockBroken() {
        blocksBroken++;
        markDirty();
    }

    public long boxesOpened() {
        return boxesOpened;
    }

    public void addBoxOpened() {
        boxesOpened++;
        markDirty();
    }

    void attachDirtyMarker(Runnable dirtyMarker) {
        this.dirtyMarker = dirtyMarker == null ? NO_OP : dirtyMarker;
    }

    private void markDirty() {
        dirtyMarker.run();
    }
}
