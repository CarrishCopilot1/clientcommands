package net.earthcomputer.enchantpredict.task;

import java.util.Set;

/**
 * Cooperative for-loop that can pause between iterations to allow the game to tick.
 * Direct port of the original {@code clientcommands} task framework.
 */
public abstract class LongTask {

    boolean isInitialized = false;
    private boolean delayScheduled;
    private boolean broken = false;

    public abstract void initialize();
    public abstract boolean condition();
    public abstract void increment();
    public abstract void body();

    public final void _break() { broken = true; }
    public final boolean isCompleted() { return broken || !condition(); }
    public void onCompleted() {}

    public final void scheduleDelay() { delayScheduled = true; }
    public final void unscheduleDelay() { delayScheduled = false; }
    public final boolean isDelayScheduled() { return delayScheduled; }

    public boolean stopOnLevelUnload(boolean isDisconnect) { return true; }

    public Set<Object> getMutexKeys() { return Set.of(); }

    public final boolean conflictsWith(LongTask other) {
        return getMutexKeys().stream().anyMatch(other.getMutexKeys()::contains);
    }
}
