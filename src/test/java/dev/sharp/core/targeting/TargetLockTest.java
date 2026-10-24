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
        assertEquals(0, lock.switchCount());
    }

    @Test
    void countsSwitchesWhenTheTargetIsReplaced() {
        TargetLock lock = new TargetLock();
        lock.update(eye, List.of(at(1, 0, 10), at(2, 5, 10)), settings, LineOfSight.ALWAYS, 1L);

        Selection s = lock.update(eye, List.of(at(2, 5, 10)), settings, LineOfSight.ALWAYS, 2L);
        assertEquals(Selection.Kind.SWITCHED, s.kind());
        assertEquals(2, lock.currentId());
        assertEquals(1, lock.switchCount());
        assertEquals(2L, lock.lockedAtNanos());
    }

    @Test
    void targetLossClearsTheCurrentIdSoTheNextCandidateIsAnAcquisition() {
        TargetLock lock = new TargetLock();
        lock.update(eye, List.of(at(1, 0, 10)), settings, LineOfSight.ALWAYS, 1L);

        Selection lost = lock.update(eye, List.of(), settings, LineOfSight.ALWAYS, 2L);
        assertEquals(Selection.Kind.LOST, lost.kind());
        assertFalse(lock.hasTarget());

        Selection again = lock.update(eye, List.of(at(3, 0, 10)), settings, LineOfSight.ALWAYS, 3L);
        assertEquals(Selection.Kind.ACQUIRED, again.kind());
        assertEquals(3, lock.currentId());
        assertEquals(0, lock.switchCount(), "re-acquiring after a loss is not a switch");
    }

    @Test
    void clearForgetsEverything() {
        TargetLock lock = new TargetLock();
        lock.update(eye, List.of(at(1, 0, 10), at(2, 5, 10)), settings, LineOfSight.ALWAYS, 1L);
        lock.update(eye, List.of(at(2, 5, 10)), settings, LineOfSight.ALWAYS, 2L);
        assertTrue(lock.hasTarget());

        lock.clear();

        assertFalse(lock.hasTarget());
        assertEquals(Selection.NO_TARGET, lock.currentId());
        assertEquals(0, lock.switchCount());
        assertEquals(0L, lock.lockedAtNanos());
        assertEquals(Selection.Kind.NONE, lock.last().kind());

        // After a clear the old id has no pull: the best candidate is simply acquired.
        Selection s = lock.update(eye, List.of(at(1, 0, 10), at(2, 5, 10)), settings, LineOfSight.ALWAYS, 3L);
        assertEquals(Selection.Kind.ACQUIRED, s.kind());
        assertEquals(1, s.targetId());
    }
}
