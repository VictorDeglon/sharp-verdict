package dev.sharp.core.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import java.util.List;
import org.junit.jupiter.api.Test;

class TargetLockTest {
    private final EyeState eye = new EyeState(new V3(0, 1.62, 0), 0, 0);
    private final TargetingSettings settings = new TargetingSettings(16, 360, 0.5, 0.05, 0.0, false);

    private static TargetCandidate at(int id, double x, double z) {
        return new TargetCandidate(id, "t" + id, new Box(x - 0.3, 0.7, z - 0.3, x + 0.3, 2.5, z + 0.3));
    }

    @Test
    void acquiresThenKeepsAndTimestampsOnlyOnChange() {
        TargetLock lock = new TargetLock();
        List<TargetCandidate> both = List.of(at(1, 0, 10), at(2, 5, 10));

        Selection first = lock.update(eye, both, settings, LineOfSight.ALWAYS, 1_000L);
        assertEquals(Selection.Kind.ACQUIRED, first.kind());
        assertEquals(1, lock.currentId());
        assertEquals(1_000L, lock.lockedAtNanos());

        Selection second = lock.update(eye, both, settings, LineOfSight.ALWAYS, 9_000L);
        assertEquals(Selection.Kind.KEPT, second.kind());
        assertEquals(1_000L, lock.lockedAtNanos(), "a kept lock keeps its original timestamp");
    }

    @Test
    void neverMovesToAnotherEntityWhenTheTargetIsGone() {
        TargetLock lock = new TargetLock();
        lock.update(eye, List.of(at(1, 0, 10), at(2, 5, 10)), settings, LineOfSight.ALWAYS, 1L);

        Selection lost = lock.update(eye, List.of(at(2, 5, 10)), settings, LineOfSight.ALWAYS, 2L);
        assertEquals(Selection.Kind.LOST, lost.kind());
        assertFalse(lock.hasTarget());
        assertTrue(lock.isCommitted(), "the hold is still committed to the first entity");

        Selection after = lock.update(eye, List.of(at(2, 5, 10)), settings, LineOfSight.ALWAYS, 3L);
        assertEquals(Selection.Kind.NONE, after.kind(), "LOST is reported once, then there is simply no target");
        assertEquals(Selection.NO_TARGET, after.targetId());
        assertEquals(1, after.eligible(), "the other entity is eligible, it is just not taken");
    }

    @Test
    void picksTheSameEntityUpAgainWhenItBecomesValid() {
        TargetLock lock = new TargetLock();
        lock.update(eye, List.of(at(1, 0, 10)), settings, LineOfSight.ALWAYS, 1_000L);

        lock.update(eye, List.of(at(2, 5, 10)), settings, LineOfSight.ALWAYS, 2_000L);
        assertFalse(lock.hasTarget());

        Selection back = lock.update(eye, List.of(at(2, 5, 10), at(1, 0, 11)), settings, LineOfSight.ALWAYS, 3_000L);
        assertEquals(1, back.targetId());
        assertEquals(1, lock.currentId());
        assertTrue(lock.hasTarget());
    }

    @Test
    void aRivalNeverTakesOverWhileTheTargetIsOnlyHidden() {
        TargetLock lock = new TargetLock();
        lock.update(eye, List.of(at(1, 0, 10), at(2, 5, 10)), new TargetingSettings(16, 360, 0.5, 0.05, 0.0, true), LineOfSight.ALWAYS, 1L);
        assertEquals(1, lock.currentId());

        TargetingSettings needLos = new TargetingSettings(16, 360, 0.5, 0.05, 0.0, true);
        LineOfSight onlyTheRivalIsSeen = (from, to) -> to.x() > 2.0;
        Selection hidden = lock.update(eye, List.of(at(1, 0, 10), at(2, 5, 10)), needLos, onlyTheRivalIsSeen, 2L);
        assertFalse(hidden.hasTarget());

        Selection visible = lock.update(eye, List.of(at(1, 0, 10), at(2, 5, 10)), needLos, LineOfSight.ALWAYS, 3L);
        assertEquals(1, visible.targetId());
    }

    @Test
    void aNewEntityIsOnlyTakenAfterTheLockIsCleared() {
        TargetLock lock = new TargetLock();
        lock.update(eye, List.of(at(1, 0, 10)), settings, LineOfSight.ALWAYS, 1L);
        lock.update(eye, List.of(), settings, LineOfSight.ALWAYS, 2L);

        Selection ignored = lock.update(eye, List.of(at(3, 0, 10)), settings, LineOfSight.ALWAYS, 3L);
        assertFalse(ignored.hasTarget());

        lock.clear();
        Selection again = lock.update(eye, List.of(at(3, 0, 10)), settings, LineOfSight.ALWAYS, 4L);
        assertEquals(Selection.Kind.ACQUIRED, again.kind());
        assertEquals(3, lock.currentId());
    }

    @Test
    void aHoldThatHasTakenNothingYetCanStillTakeItsFirstTarget() {
        TargetLock lock = new TargetLock();
        Selection none = lock.update(eye, List.of(), settings, LineOfSight.ALWAYS, 1L);
        assertEquals(Selection.Kind.NONE, none.kind());
        assertFalse(lock.isCommitted());

        Selection first = lock.update(eye, List.of(at(4, 0, 10)), settings, LineOfSight.ALWAYS, 2L);
        assertEquals(Selection.Kind.ACQUIRED, first.kind());
        assertEquals(4, lock.currentId());
    }

    @Test
    void clearForgetsEverything() {
        TargetLock lock = new TargetLock();
        lock.update(eye, List.of(at(1, 0, 10), at(2, 5, 10)), settings, LineOfSight.ALWAYS, 1L);
        lock.update(eye, List.of(at(2, 5, 10)), settings, LineOfSight.ALWAYS, 2L);
        assertTrue(lock.isCommitted());

        lock.clear();

        assertFalse(lock.hasTarget());
        assertFalse(lock.isCommitted());
        assertEquals(Selection.NO_TARGET, lock.currentId());
        assertEquals(0L, lock.lockedAtNanos());
        assertEquals(Selection.Kind.NONE, lock.last().kind());

        // After a clear the old id has no pull: the best candidate is simply acquired.
        Selection s = lock.update(eye, List.of(at(1, 0, 10), at(2, 5, 10)), settings, LineOfSight.ALWAYS, 3L);
        assertEquals(Selection.Kind.ACQUIRED, s.kind());
        assertEquals(1, s.targetId());
    }
}
