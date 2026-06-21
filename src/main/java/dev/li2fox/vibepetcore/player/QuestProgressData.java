package dev.li2fox.vibepetcore.player;

import java.util.UUID;

public final class QuestProgressData {
    private static final Runnable NO_OP = () -> {};

    private boolean accepted;
    private boolean completed;
    private int progress;
    private long acceptedAtMillis;
    private long completedAtMillis;
    private UUID boundPetId;
    private transient Runnable dirtyMarker = NO_OP;

    public boolean accepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
        if (accepted && acceptedAtMillis <= 0L) {
            acceptedAtMillis = System.currentTimeMillis();
        }
        markDirty();
    }

    public boolean completed() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
        if (completed) {
            completedAtMillis = System.currentTimeMillis();
        }
        markDirty();
    }

    public int progress() {
        return progress;
    }

    public void setProgress(int progress, int max) {
        this.progress = Math.min(Math.max(0, max), Math.max(0, progress));
        markDirty();
    }

    public void addProgress(int amount, int max) {
        int normalized = Math.max(0, amount);
        progress = Math.min(max, progress + normalized);
        if (normalized > 0) {
            markDirty();
        }
    }

    public long acceptedAtMillis() {
        return acceptedAtMillis;
    }

    public long completedAtMillis() {
        return completedAtMillis;
    }

    public UUID boundPetId() {
        return boundPetId;
    }

    public void setBoundPetId(UUID boundPetId) {
        this.boundPetId = boundPetId;
        markDirty();
    }

    public void resetForRepeat() {
        accepted = false;
        completed = false;
        progress = 0;
        acceptedAtMillis = 0L;
        completedAtMillis = 0L;
        boundPetId = null;
        markDirty();
    }

    public void cancel() {
        accepted = false;
        progress = 0;
        acceptedAtMillis = 0L;
        boundPetId = null;
        markDirty();
    }

    void attachDirtyMarker(Runnable dirtyMarker) {
        this.dirtyMarker = dirtyMarker == null ? NO_OP : dirtyMarker;
    }

    private void markDirty() {
        dirtyMarker.run();
    }
}
