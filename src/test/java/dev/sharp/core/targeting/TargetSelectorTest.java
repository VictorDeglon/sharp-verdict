package dev.sharp.core.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import java.util.List;
import org.junit.jupiter.api.Test;

class TargetSelectorTest {
    private static final int NONE = Selection.NO_TARGET;

    private final TargetSelector selector = new TargetSelector();

    /** Viewer at the origin looking along +Z, eye height 1.62. */
    private final EyeState eye = new EyeState(new V3(0, 1.62, 0), 0, 0);

    private TargetingSettings settings(double range, double fov) {
        return new TargetingSettings(range, fov, 0.5, 0.05, 0.0, false);
    }

    /** A player-sized box standing at (x, y, z). */
    private static TargetCandidate at(int id, double x, double y, double z) {
        return new TargetCandidate(id, "t" + id, new Box(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3));
    }

    // ---- crosshair beats distance ----------------------------------------------------------------

    @Test
    void prefersCandidateNearestTheCrosshairOverNearestByDistance() {
        TargetCandidate aligned = at(1, 0, 0.7, 14);
        TargetCandidate nearButOffAxis = at(2, 3, 0.7, 6);
        Selection s = selector.select(eye, List.of(nearButOffAxis, aligned), NONE, settings(20, 360), LineOfSight.ALWAYS);

        assertEquals(1, s.targetId(), "the entity the crosshair is on should win even if another is closer");
        assertEquals(Selection.Kind.ACQUIRED, s.kind());
        assertEquals(2, s.eligible());
    }

    @Test
    void closerEntityBehindThePlayerNeverWinsOverOneInFront() {
        TargetCandidate behind = at(1, 0, 0.7, -2);
        TargetCandidate front = at(2, 0, 0.7, 15);
        Selection s = selector.select(eye, List.of(behind, front), NONE, settings(20, 360), LineOfSight.ALWAYS);
        assertEquals(2, s.targetId());
    }

    @Test
    void distanceOnlyBreaksNearTies() {
        // Both exactly on the crosshair line: the nearer one wins through the distance penalty.
        TargetCandidate far = at(1, 0, 0.7, 12);
        TargetCandidate near = at(2, 0, 0.7, 5);
        Selection s = selector.select(eye, List.of(far, near), NONE, settings(20, 360), LineOfSight.ALWAYS);
        assertEquals(2, s.targetId());
    }

    @Test
    void identicalScoresResolveToTheLowerIdSoSelectionIsDeterministic() {
        TargetingSettings noDistance = new TargetingSettings(20, 360, 0.5, 0.0, 0.0, false);
        TargetCandidate a = at(7, 0, 0.7, 6);
        TargetCandidate b = at(3, 0, 0.7, 12);
        Selection s1 = selector.select(eye, List.of(a, b), NONE, noDistance, LineOfSight.ALWAYS);
        Selection s2 = selector.select(eye, List.of(b, a), NONE, noDistance, LineOfSight.ALWAYS);
        assertEquals(3, s1.targetId());
        assertEquals(3, s2.targetId());
    }

    // ---- range and field of view -----------------------------------------------------------------

    @Test
    void rangeLimitIsMeasuredToTheBoxSurface() {
        // Box front face is at z = 15.7, so it is inside 16 even though its centre is 16.0 away.
        TargetCandidate edge = at(1, 0, 0.7, 16.0);
        assertEquals(1, selector.select(eye, List.of(edge), NONE, settings(16, 360), LineOfSight.ALWAYS).targetId());

        TargetCandidate outside = at(2, 0, 0.7, 17.0);
        assertEquals(NONE, selector.select(eye, List.of(outside), NONE, settings(16, 360), LineOfSight.ALWAYS).targetId());
    }

    @Test
    void fieldOfViewIsAFullConeAroundTheCrosshair() {
        // 60 degree cone: 30 degrees to each side. Aim point at z=10, x offset chosen for the angle.
        double inside = Math.tan(Math.toRadians(25)) * 10;
        double outside = Math.tan(Math.toRadians(35)) * 10;
        TargetCandidate in = at(1, inside, 0.7, 10);
        TargetCandidate out = at(2, outside, 0.7, 10);

        assertEquals(1, selector.select(eye, List.of(in, out), NONE, settings(30, 60), LineOfSight.ALWAYS).targetId());
        Selection onlyOut = selector.select(eye, List.of(out), NONE, settings(30, 60), LineOfSight.ALWAYS);
        assertEquals(NONE, onlyOut.targetId());
        assertEquals(0, onlyOut.eligible());
    }

    @Test
    void fieldOfView360DisablesTheAngleLimit() {
        TargetCandidate behind = at(1, 0, 0.7, -8);
        assertEquals(1, selector.select(eye, List.of(behind), NONE, settings(20, 360), LineOfSight.ALWAYS).targetId());
    }

    @Test
    void reportsAngularErrorAndDistanceOfTheChoice() {
        TargetCandidate dead = at(1, 0, 0.72, 10); // aim point y = 0.72 + 0.9 = 1.62 = eye height
        Selection s = selector.select(eye, List.of(dead), NONE, settings(20, 60), LineOfSight.ALWAYS);
        assertEquals(0.0, s.angularErrorDegrees(), 1.0e-6);
        assertEquals(9.7, s.distance(), 1.0e-9);
        assertEquals(new V3(0, 1.62, 10), s.aimPoint());
    }

    // ---- line of sight ---------------------------------------------------------------------------

    @Test
    void skipsTargetsWithoutLineOfSightAndTakesTheNextBest() {
        TargetingSettings needLos = new TargetingSettings(20, 360, 0.5, 0.05, 0.0, true);
        TargetCandidate hidden = at(1, 0, 0.7, 8);
        TargetCandidate visible = at(2, 2, 0.7, 8);
        LineOfSight blockCentreLine = (from, to) -> Math.abs(to.x()) > 1.0;
        Selection s = selector.select(eye, List.of(hidden, visible), NONE, needLos, blockCentreLine);
        assertEquals(2, s.targetId());
    }

    @Test
    void acceptsTargetWhenOnlyTheNearestCornerIsVisible() {
        TargetingSettings needLos = new TargetingSettings(20, 360, 0.5, 0.05, 0.0, true);
        TargetCandidate t = at(1, 0, 0.7, 8);
        V3 aim = t.box().pointAtHeight(0.5);
        LineOfSight onlyCorner = (from, to) -> !to.equals(aim);
        assertEquals(1, selector.select(eye, List.of(t), NONE, needLos, onlyCorner).targetId());
    }

    @Test
    void lineOfSightIsNotQueriedWhenNotRequired() {
        TargetCandidate t = at(1, 0, 0.7, 8);
        LineOfSight forbidden = (from, to) -> {
            throw new AssertionError("raycast must not run when line of sight is not required");
        };
        assertEquals(1, selector.select(eye, List.of(t), NONE, settings(20, 360), forbidden).targetId());
    }

    // ---- stability -------------------------------------------------------------------------------

    @Test
    void keepsTheCurrentTargetEvenWhenARivalIsBetterPlaced() {
        TargetCandidate held = at(1, 4, 0.7, 10);
        TargetCandidate rival = at(2, 0, 0.7, 10);
        Selection s = selector.select(eye, List.of(held, rival), 1, settings(20, 360), LineOfSight.ALWAYS);
        assertEquals(1, s.targetId());
        assertEquals(Selection.Kind.KEPT, s.kind());
    }

    @Test
    void keepsTheTargetAcrossManyFramesWhileOthersWiggle() {
        TargetingSettings st = settings(20, 90);
        int current = NONE;
        TargetCandidate a = at(1, 3, 0.7, 10);
        for (int frame = 0; frame < 200; frame++) {
            double wiggle = Math.sin(frame * 0.3) * 6;
            TargetCandidate b = at(2, wiggle, 0.7, 9);
            Selection s = selector.select(eye, List.of(a, b), current, st, LineOfSight.ALWAYS);
            if (current == NONE) {
                current = s.targetId();
            }
            assertEquals(current, s.targetId(), "target changed at frame " + frame);
        }
    }

    // ---- switching -------------------------------------------------------------------------------

    @Test
    void switchesWhenTheCurrentTargetDisappears() {
        TargetCandidate other = at(2, 0, 0.7, 10);
        Selection s = selector.select(eye, List.of(other), 1, settings(20, 360), LineOfSight.ALWAYS);
        assertEquals(2, s.targetId());
        assertEquals(Selection.Kind.SWITCHED, s.kind());
    }

    @Test
    void reportsLostWhenTheCurrentTargetDisappearsAndNothingIsLeft() {
        Selection s = selector.select(eye, List.of(), 1, settings(20, 360), LineOfSight.ALWAYS);
        assertFalse(s.hasTarget());
        assertEquals(Selection.Kind.LOST, s.kind());
        assertNull(s.aimPoint());
    }

    @Test
    void switchesWhenTheCurrentTargetLeavesRange() {
        TargetCandidate gone = at(1, 0, 0.7, 25);
        TargetCandidate near = at(2, 1, 0.7, 12);
        Selection s = selector.select(eye, List.of(gone, near), 1, settings(16, 360), LineOfSight.ALWAYS);
        assertEquals(2, s.targetId());
        assertEquals(Selection.Kind.SWITCHED, s.kind());
    }

    @Test
    void switchesWhenTheCurrentTargetLeavesTheFieldOfView() {
        double wide = Math.tan(Math.toRadians(50)) * 10;
        TargetCandidate leftView = at(1, wide, 0.7, 10);
        TargetCandidate centred = at(2, 0, 0.7, 12);
        Selection s = selector.select(eye, List.of(leftView, centred), 1, settings(30, 60), LineOfSight.ALWAYS);
        assertEquals(2, s.targetId());
        assertEquals(Selection.Kind.SWITCHED, s.kind());
    }

    @Test
    void switchesWhenTheCurrentTargetLosesLineOfSight() {
        TargetingSettings needLos = new TargetingSettings(20, 360, 0.5, 0.05, 0.0, true);
        TargetCandidate hidden = at(1, 0, 0.7, 8);
        TargetCandidate clear = at(2, 3, 0.7, 8);
        LineOfSight los = (from, to) -> Math.abs(to.x()) > 1.0;
        Selection s = selector.select(eye, List.of(hidden, clear), 1, needLos, los);
        assertEquals(2, s.targetId());
        assertEquals(Selection.Kind.SWITCHED, s.kind());
    }

    @Test
    void doesNotReofferATargetThatWasJustJudgedInvalid() {
        // Target 1 is inside limits but not visible; it must not be "switched to" itself.
        TargetingSettings needLos = new TargetingSettings(20, 360, 0.5, 0.05, 0.0, true);
        TargetCandidate hidden = at(1, 0, 0.7, 8);
        Selection s = selector.select(eye, List.of(hidden), 1, needLos, (from, to) -> false);
        assertEquals(Selection.Kind.LOST, s.kind());
        assertEquals(NONE, s.targetId());
    }

    // ---- retain margin ---------------------------------------------------------------------------

    @Test
    void retainMarginKeepsALockJustOutsideTheLimitsButNeverAcquiresThere() {
        TargetingSettings strict = new TargetingSettings(16, 60, 0.5, 0.05, 0.0, false);
        TargetingSettings margin = new TargetingSettings(16, 60, 0.5, 0.05, 10.0, false);
        // About 32 degrees off axis: just outside the 30 degree half angle.
        double x = Math.tan(Math.toRadians(32)) * 10;
        TargetCandidate edge = at(1, x, 0.7, 10);

        assertEquals(NONE, selector.select(eye, List.of(edge), NONE, margin, LineOfSight.ALWAYS).targetId(),
                "acquisition always uses the strict limits");
        assertEquals(1, selector.select(eye, List.of(edge), 1, margin, LineOfSight.ALWAYS).targetId(),
                "an existing lock survives inside the margin");
        assertEquals(NONE, selector.select(eye, List.of(edge), 1, strict, LineOfSight.ALWAYS).targetId(),
                "without margin the same lock is dropped");
    }

    // ---- robustness ------------------------------------------------------------------------------

    @Test
    void ignoresCandidatesWithNonFiniteBoxes() {
        TargetCandidate broken = new TargetCandidate(1, "nan", new Box(Double.NaN, 0, 0, 1, 1, 1));
        TargetCandidate fine = at(2, 0, 0.7, 10);
        Selection s = selector.select(eye, List.of(broken, fine), NONE, settings(20, 360), LineOfSight.ALWAYS);
        assertEquals(2, s.targetId());
    }

    @Test
    void emptyCandidateListGivesNone() {
        Selection s = selector.select(eye, List.of(), NONE, settings(20, 60), LineOfSight.ALWAYS);
        assertEquals(Selection.Kind.NONE, s.kind());
        assertEquals(0, s.seen());
    }

    @Test
    void eligibleIdsAreOrderedBestFirstAndCountsAreReported() {
        TargetCandidate c1 = at(1, 5, 0.7, 10);
        TargetCandidate c2 = at(2, 0, 0.7, 10);
        TargetCandidate c3 = at(3, 2, 0.7, 10);
        TargetCandidate far = at(4, 0, 0.7, 90);
        Selection s = selector.select(eye, List.of(c1, c2, c3, far), NONE, settings(20, 360), LineOfSight.ALWAYS);
        assertEquals(List.of(2, 3, 1), s.eligibleIds());
        assertEquals(4, s.seen());
        assertEquals(3, s.eligible());
        assertNotNull(s.aimPoint());
        assertTrue(s.angularErrorDegrees() < 1.0);
    }

    @Test
    void anEntityWhoseBoxContainsTheEyeIsInsideTheConeWhereverYouLook() {
        // Standing inside it: its aim point is right below the eye, 90 degrees from a level look, far outside a 60 degree cone.
        TargetCandidate around = new TargetCandidate(1, "t1", new Box(-0.5, 0, -0.5, 0.5, 2.0, 0.5));
        Selection sel = selector.select(eye, List.of(around), NONE, settings(40, 60), LineOfSight.ALWAYS);
        assertEquals(1, sel.targetId());
        assertEquals(0.0, sel.angularErrorDegrees(), 1.0e-9);
        assertEquals(0.0, sel.distance(), 1.0e-9);

        EyeState lookingAway = new EyeState(new V3(0, 1.62, 0), 180, 40);
        assertEquals(1, selector.select(lookingAway, List.of(around), NONE, settings(40, 20), LineOfSight.ALWAYS).targetId());
    }

    @Test
    void aBoxJustBesideTheEyeIsNotInsideAndStillObeysTheCone() {
        TargetCandidate beside = new TargetCandidate(1, "t1", new Box(0.6, 0, -0.5, 1.6, 2.0, 0.5));
        assertEquals(NONE, selector.select(
                new EyeState(new V3(0, 1.62, 0), 180, 0), List.of(beside), NONE, settings(40, 60), LineOfSight.ALWAYS).targetId());
    }
}
