package dev.sharp.core.targeting;

import java.util.List;

/**
 * Holds the current lock between frames. All state is dropped by {@link #clear()}, which the controller
 * calls whenever the feature switches off, the player dies, the world changes or the connection ends.
 */
public final class TargetLock {
    private final TargetSelector selector = new TargetSelector();

    private int currentId = Selection.NO_TARGET;
    private long lockedAtNanos;
    private int switches;
    private Selection last = Selection.empty();

    public Selection update(
            EyeState eye,
            List<TargetCandidate> candidates,
            TargetingSettings settings,
            LineOfSight lineOfSight,
            long nowNanos) {

        Selection s = selector.select(eye, candidates, currentId, settings, lineOfSight);
        switch (s.kind()) {
            case ACQUIRED -> lockedAtNanos = nowNanos;
            case SWITCHED -> {
                lockedAtNanos = nowNanos;
                switches++;
            }
            default -> {
                // KEPT keeps the original timestamp, NONE and LOST have nothing to timestamp.
            }
        }
        currentId = s.hasTarget() ? s.targetId() : Selection.NO_TARGET;
        last = s;
        return s;
    }

    /** Forgets the target and all counters. */
    public void clear() {
        currentId = Selection.NO_TARGET;
        lockedAtNanos = 0L;
        switches = 0;
        last = Selection.empty();
    }

    public boolean hasTarget() {
        return currentId != Selection.NO_TARGET;
    }

    public int currentId() {
        return currentId;
    }

    public long lockedAtNanos() {
        return lockedAtNanos;
    }

    /** Number of times the lock moved from one entity to another since the last {@link #clear()}. */
    public int switchCount() {
        return switches;
    }

    public Selection last() {
        return last;
    }
}
