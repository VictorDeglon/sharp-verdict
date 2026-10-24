package dev.sharp.core.targeting;

import java.util.List;

/**
 * Holds the current lock between frames. All state is dropped by {@link #clear()}, which the controller
 * calls whenever the feature switches off, the player dies, the world changes or the connection ends.
 *
 * <p>The first target taken is the target for the rest of the hold. If it dies, leaves range or the cone, or is hidden, the lock
 * reports nothing and waits: it never moves to another entity, and picks the same one up again if it becomes valid. Another
 * entity can only be locked after the key is released and pressed again, which is what {@link #clear()} marks.
 */
public final class TargetLock {
    private final TargetSelector selector = new TargetSelector();

    /** The entity this hold is committed to, valid or not right now. */
    private int committedId = Selection.NO_TARGET;
    private int currentId = Selection.NO_TARGET;
    private long lockedAtNanos;
    private Selection last = Selection.empty();

    public Selection update(
            EyeState eye,
            List<TargetCandidate> candidates,
            TargetingSettings settings,
            LineOfSight lineOfSight,
            long nowNanos) {

        Selection s = selector.select(eye, candidates, committedId, settings, lineOfSight);
        if (committedId != Selection.NO_TARGET && s.targetId() != committedId) {
            // The committed entity is not usable this frame. The selector would offer a replacement; this lock refuses it.
            s = Selection.none(
                    currentId != Selection.NO_TARGET ? Selection.Kind.LOST : Selection.Kind.NONE,
                    s.seen(), s.eligible(), s.eligibleIds());
        }
        if (s.kind() == Selection.Kind.ACQUIRED) {
            lockedAtNanos = nowNanos;
            committedId = s.targetId();
        }
        currentId = s.hasTarget() ? s.targetId() : Selection.NO_TARGET;
        last = s;
        return s;
    }

    /** Forgets the target and all counters. */
    public void clear() {
        committedId = Selection.NO_TARGET;
        currentId = Selection.NO_TARGET;
        lockedAtNanos = 0L;
        last = Selection.empty();
    }

    /** True while a target is locked and usable this frame. */
    public boolean hasTarget() {
        return currentId != Selection.NO_TARGET;
    }

    public int currentId() {
        return currentId;
    }

    /** True once a target was taken during this hold, even if it is out of reach right now. */
    public boolean isCommitted() {
        return committedId != Selection.NO_TARGET;
    }

    public long lockedAtNanos() {
        return lockedAtNanos;
    }

    public Selection last() {
        return last;
    }
}
