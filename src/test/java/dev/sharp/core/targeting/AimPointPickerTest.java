package dev.sharp.core.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import dev.sharp.core.targeting.AimPointPicker.Choice;
import dev.sharp.core.targeting.AimPointPicker.Zone;
import java.util.List;
import org.junit.jupiter.api.Test;

class AimPointPickerTest {
    private static final V3 EYE = new V3(0, 1.62, 0);
    private final TargetingSettings defaults = TargetingSettings.defaults();

    private static TargetCandidate standing(int id, double x, double z) {
        return new TargetCandidate(id, "t" + id, new Box(x - 0.3, 0, z - 0.3, x + 0.3, 1.8, z + 0.3));
    }

    private static Choice pick(TargetCandidate t, List<TargetCandidate> all, TargetingSettings s, LineOfSight los) {
        return AimPointPicker.pick(EYE, t, all, s, los);
    }

    private static Choice pick(TargetCandidate t, TargetingSettings s) {
        return pick(t, List.of(t), s, LineOfSight.ALWAYS);
    }

    // ---- height -----------------------------------------------------------------------------------

    @Test
    void aimsAtTheFeetFirstButNotOnTheVeryEdge() {
        TargetCandidate t = standing(1, 0, 10);
        Choice c = pick(t, defaults);
        assertEquals(Exposure.OPEN, c.exposure());
        assertEquals(Zone.FEET, c.zone());
        assertEquals(0.12 * 1.8, c.point().y(), 1.0e-9);
        assertTrue(c.point().y() > 0.1, "tracking error must still land inside the box: " + c.point().y());
        assertEquals(0.0, c.point().x(), 1.0e-9);
        assertEquals(10.0, c.point().z(), 1.0e-9);
    }

    @Test
    void withoutFeetFirstItUsesTheConfiguredHeight() {
        TargetingSettings s = new TargetingSettings(64, 140, 0.3, 0.05, 0, true, false, true, true);
        Choice c = pick(standing(1, 0, 10), s);
        assertEquals(0.3 * 1.8, c.point().y(), 1.0e-9);
        assertEquals(Zone.LOW, c.zone());

        TargetingSettings centre = new TargetingSettings(64, 140, 0.5, 0.05, 0, true, false, true, true);
        assertEquals(Zone.MIDDLE, pick(standing(1, 0, 10), centre).zone());
    }

    @Test
    void theHeightIsAFractionOfTheCurrentBoxSoAShrunkBoxStillContainsThePoint() {
        // Elytra, swimming, crawling: the vanilla box drops to about a third of its height, feet unchanged.
        Box gliding = new Box(-0.3, 0, 9.7, 0.3, 0.6, 10.3);
        for (boolean feet : new boolean[] {true, false}) {
            TargetingSettings s = new TargetingSettings(64, 140, 0.3, 0.05, 0, true, feet, true, true);
            Choice c = pick(new TargetCandidate(1, "t", gliding), s);
            assertTrue(gliding.contains(c.point()), "feetFirst=" + feet + " point " + c.point());
        }
    }

    @Test
    void anAbsoluteMidHeightWouldLeaveAShrunkBoxButTheLoweredDefaultNeverDoes() {
        Box gliding = new Box(-0.3, 0, 9.7, 0.3, 0.6, 10.3);
        double oldCentreOfStandingBox = 0.9;
        assertFalse(gliding.contains(new V3(0, oldCentreOfStandingBox, 10)), "the point a standing centre aim would keep");
        Choice c = pick(new TargetCandidate(1, "t", gliding), defaults);
        assertTrue(c.point().y() <= 0.6 * 0.5, "default aim is in the lower half: " + c.point().y());
    }

    @Test
    void whenTheFeetAreHiddenByABlockItFallsBackUpTheBody() {
        TargetCandidate t = standing(1, 0, 10);
        double lowestVisible = 0.7; // a low wall hides everything below this height: the feet and the lowered centre
        LineOfSight lowWall = (from, to) -> to.y() >= lowestVisible;
        Choice c = pick(t, List.of(t), defaults, lowWall);
        assertTrue(c.point().y() >= lowestVisible, "point " + c.point());
        assertEquals(Exposure.OPEN, c.exposure(), "a block is not another hitbox, nothing to report");
        assertEquals(0.5 * 1.8, c.point().y(), 1.0e-9, "the next rung is the middle once the lowered centre is hidden too");
    }

    @Test
    void ifNothingIsVisibleItStillAimsAtThePreferredSpot() {
        TargetCandidate t = standing(1, 0, 10);
        Choice c = pick(t, List.of(t), defaults, (a, b) -> false);
        assertEquals(0.12 * 1.8, c.point().y(), 1.0e-9);
        assertEquals(Exposure.OPEN, c.exposure());
    }

    @Test
    void lineOfSightIsNotAskedWhenItIsNotRequired() {
        TargetingSettings s = new TargetingSettings(64, 140, 0.3, 0.05, 0, false, true, true, true);
        TargetCandidate t = standing(1, 0, 10);
        Choice c = pick(t, List.of(t), s, (a, b) -> {
            throw new AssertionError("must not be called");
        });
        assertEquals(Zone.FEET, c.zone());
    }

    // ---- shield -----------------------------------------------------------------------------------

    @Test
    void aShieldUpMovesTheAimToTheBackOfTheBox() {
        // yaw 180 looks toward -z: the target at z=10 faces the player, so its back is the far side (larger z).
        TargetCandidate facingYou = new TargetCandidate(1, "t", new Box(-0.3, 0, 9.7, 0.3, 1.8, 10.3), 180.0, true);
        Choice c = pick(facingYou, defaults);
        assertEquals(Exposure.SHIELDED, c.exposure());
        assertTrue(c.shieldBlocksYou());
        assertTrue(c.point().z() > 10.1 && c.point().z() < 10.3, "inside the box, on the far side: " + c.point());
        assertEquals(0.0, c.point().x(), 1.0e-9);
    }

    @Test
    void facingAwayTheShieldDoesNotBlockYouAndTheBackIsTheNearSide() {
        TargetCandidate facingAway = new TargetCandidate(1, "t", new Box(-0.3, 0, 9.7, 0.3, 1.8, 10.3), 0.0, true);
        Choice c = pick(facingAway, defaults);
        assertEquals(Exposure.SHIELDED, c.exposure());
        assertFalse(c.shieldBlocksYou());
        assertTrue(c.point().z() < 9.9 && c.point().z() > 9.7, "near side: " + c.point());
    }

    @Test
    void sidewaysFacingBlocksOnlyWhenYouAreOnTheFacingSide() {
        // Facing +x (yaw -90): a viewer at x < 0 is behind it, one at x > 0 is in front.
        Box box = new Box(4.7, 0, -0.3, 5.3, 1.8, 0.3);
        TargetCandidate faceEast = new TargetCandidate(1, "t", box, -90.0, true);
        assertFalse(pick(faceEast, defaults).shieldBlocksYou(), "the viewer at the origin is west of it, behind");
        TargetCandidate faceWest = new TargetCandidate(1, "t", box, 90.0, true);
        assertTrue(pick(faceWest, defaults).shieldBlocksYou());
        assertTrue(pick(faceWest, defaults).point().x() > 5.0, "back of a west-facing target is east");
    }

    @Test
    void noShieldMeansNoRearAim() {
        TargetCandidate t = new TargetCandidate(1, "t", new Box(-0.3, 0, 9.7, 0.3, 1.8, 10.3), 180.0, false);
        Choice c = pick(t, defaults);
        assertEquals(Exposure.OPEN, c.exposure());
        assertFalse(c.shieldBlocksYou());
        assertEquals(10.0, c.point().z(), 1.0e-9);
    }

    @Test
    void theShieldOptionCanBeSwitchedOff() {
        TargetingSettings s = new TargetingSettings(64, 140, 0.3, 0.05, 0, true, true, false, true);
        TargetCandidate t = new TargetCandidate(1, "t", new Box(-0.3, 0, 9.7, 0.3, 1.8, 10.3), 180.0, true);
        Choice c = pick(t, s);
        assertEquals(Exposure.OPEN, c.exposure());
        assertEquals(10.0, c.point().z(), 1.0e-9);
    }

    // ---- other hitboxes ---------------------------------------------------------------------------

    @Test
    void anotherEntityRightInFrontCoversTheTarget() {
        TargetCandidate t = standing(1, 0, 10);
        TargetCandidate blocker = new TargetCandidate(2, "b", new Box(-1.5, 0, 4, 1.5, 3, 5));
        Choice c = pick(t, List.of(t, blocker), defaults, LineOfSight.ALWAYS);
        assertEquals(Exposure.COVERED, c.exposure());
        assertTrue(c.exposure().blocksAim());
        assertEquals(0.12 * 1.8, c.point().y(), 1.0e-9, "it still has a best-guess point to follow");
    }

    @Test
    void anEntityBehindTheTargetDoesNotCoverIt() {
        TargetCandidate t = standing(1, 0, 10);
        // Tall and deep enough that the same line, continued past the target, runs straight through it: only "nearer" tells them apart.
        TargetCandidate behind = new TargetCandidate(2, "b", new Box(-1.5, -3, 14, 1.5, 3, 15));
        assertEquals(Exposure.OPEN, pick(t, List.of(t, behind), defaults, LineOfSight.ALWAYS).exposure());
    }

    @Test
    void anEntityOffToTheSideDoesNotCoverIt() {
        TargetCandidate t = standing(1, 0, 10);
        TargetCandidate beside = new TargetCandidate(2, "b", new Box(3, 0, 4, 5, 3, 5));
        assertEquals(Exposure.OPEN, pick(t, List.of(t, beside), defaults, LineOfSight.ALWAYS).exposure());
    }

    @Test
    void aTargetStickingOutIsAimedAtWhereItSticksOut() {
        TargetCandidate t = standing(1, 0, 10);
        // Covers the middle line but not the right-hand side of the target.
        TargetCandidate partial = new TargetCandidate(2, "b", new Box(-1.0, 0, 5, 0.04, 3, 6));
        Choice c = pick(t, List.of(t, partial), defaults, LineOfSight.ALWAYS);
        assertEquals(Exposure.PARTLY_COVERED, c.exposure());
        assertTrue(c.point().x() > 0.1, "moved sideways onto the exposed part: " + c.point());
        assertTrue(t.box().contains(c.point()));
        assertFalse(AimPointPicker.coveredByOther(EYE, c.point(), t, List.of(t, partial)));
    }

    @Test
    void aTargetHiddenOnlyAtTheFeetIsAimedAtHigherUp() {
        TargetCandidate t = standing(1, 0, 10);
        // A low box (a sleeping dog, a minecart) right in front of the legs hides them but not the chest.
        TargetCandidate low = new TargetCandidate(2, "b", new Box(-2, 0, 8.5, 2, 0.6, 9.5));
        Choice c = pick(t, List.of(t, low), defaults, LineOfSight.ALWAYS);
        assertEquals(Exposure.PARTLY_COVERED, c.exposure());
        assertEquals(0.5 * 1.8, c.point().y(), 1.0e-9, "feet and the lowered centre are hidden, the middle is the first clear rung");
    }

    @Test
    void theTargetsOwnStaleBoxInTheListIsNeverCover() {
        // The list holds the box from the last tick, the target carries the one blended for this frame, and the old one is nearer.
        TargetCandidate t = standing(1, 0, 10);
        TargetCandidate staleSelf = new TargetCandidate(1, "t1", new Box(-0.3, 0, 9.4, 0.3, 1.8, 10.0));
        assertEquals(Exposure.OPEN, pick(t, List.of(staleSelf), defaults, LineOfSight.ALWAYS).exposure());
    }

    @Test
    void coverCanBeSwitchedOff() {
        TargetingSettings s = new TargetingSettings(64, 140, 0.3, 0.05, 0, true, true, true, false);
        TargetCandidate t = standing(1, 0, 10);
        TargetCandidate blocker = new TargetCandidate(2, "b", new Box(-1.5, 0, 4, 1.5, 3, 5));
        assertEquals(Exposure.OPEN, pick(t, List.of(t, blocker), s, LineOfSight.ALWAYS).exposure());
    }

    @Test
    void aBoxTheEyeIsInsideOfNeverCoversAnything() {
        TargetCandidate t = standing(1, 0, 10);
        TargetCandidate aroundYou = new TargetCandidate(2, "b", new Box(-2, 0, -2, 2, 3, 2));
        assertEquals(Exposure.OPEN, pick(t, List.of(t, aroundYou), defaults, LineOfSight.ALWAYS).exposure());
    }

    @Test
    void aCoveredShieldedTargetStillReportsTheShield() {
        TargetCandidate t = new TargetCandidate(1, "t", new Box(-0.3, 0, 9.7, 0.3, 1.8, 10.3), 180.0, true);
        TargetCandidate blocker = new TargetCandidate(2, "b", new Box(-1.5, 0, 4, 1.5, 3, 5));
        Choice c = pick(t, List.of(t, blocker), defaults, LineOfSight.ALWAYS);
        assertEquals(Exposure.COVERED, c.exposure(), "no clean shot beats the shield note");
        assertTrue(c.shieldBlocksYou());
    }

    // ---- inside -----------------------------------------------------------------------------------

    @Test
    void theEyeInsideTheBoxIsReportedAndGivesASafePoint() {
        TargetCandidate t = new TargetCandidate(1, "t", new Box(-0.5, 0, -0.5, 0.5, 2.0, 0.5));
        Choice c = pick(t, defaults);
        assertEquals(Exposure.INSIDE, c.exposure());
        assertTrue(c.point().isFinite());
        assertTrue(t.box().contains(c.point()));
    }

    // ---- robustness -------------------------------------------------------------------------------

    @Test
    void aZeroHeightBoxDoesNotBreakIt() {
        Box flat = new Box(-0.3, 1, 9.7, 0.3, 1, 10.3);
        Choice c = pick(new TargetCandidate(1, "t", flat), defaults);
        assertTrue(c.point().isFinite());
        assertEquals(1.0, c.point().y(), 1.0e-9);
    }

    @Test
    void aTargetStraightAboveOrBelowStillGetsAFinitePoint() {
        TargetCandidate above = new TargetCandidate(1, "t", new Box(-0.3, 6, -0.3, 0.3, 7.8, 0.3));
        Choice c = pick(above, List.of(above, standing(2, 0, 10)), defaults, LineOfSight.ALWAYS);
        assertTrue(c.point().isFinite());
        assertEquals(Exposure.OPEN, c.exposure());
    }

    // ---- a preferred height from the planner ------------------------------------------------------

    @Test
    void aPreferredHeightIsTriedBeforeTheUsualOrder() {
        TargetCandidate t = standing(1, 0, 10);
        Choice c = AimPointPicker.pick(EYE, t, List.of(t), defaults, LineOfSight.ALWAYS, 0.5);
        assertEquals(Zone.MIDDLE, c.zone());
        assertEquals(0.5 * 1.8, c.point().y(), 1.0e-9);
        assertEquals(Exposure.OPEN, c.exposure());
    }

    @Test
    void aPreferredHeightThatIsHiddenFallsBackToTheUsualSpots() {
        TargetCandidate t = standing(1, 0, 10);
        // Only the lower part can be seen, as when a wall edge hides the top.
        LineOfSight lowOnly = (from, to) -> to.y() < 0.5;
        Choice c = AimPointPicker.pick(EYE, t, List.of(t), defaults, lowOnly, 0.5);
        assertEquals(Zone.FEET, c.zone(), "the middle is hidden, so the feet zone is taken");
    }

    @Test
    void aPreferredHeightAtTheFeetIsNotTriedTwice() {
        TargetCandidate t = standing(1, 0, 10);
        Choice c = AimPointPicker.pick(EYE, t, List.of(t), defaults, LineOfSight.ALWAYS, 0.12);
        assertEquals(0.12 * 1.8, c.point().y(), 1.0e-9);
    }

    @Test
    void aNonFinitePreferenceMeansTheUsualOrder() {
        TargetCandidate t = standing(1, 0, 10);
        assertEquals(pick(t, defaults), AimPointPicker.pick(EYE, t, List.of(t), defaults, LineOfSight.ALWAYS, Double.NaN));
    }

    @Test
    void aPreferredHeightOutsideTheBoxIsClamped() {
        TargetCandidate t = standing(1, 0, 10);
        Choice c = AimPointPicker.pick(EYE, t, List.of(t), defaults, LineOfSight.ALWAYS, 7.0);
        assertTrue(t.box().contains(c.point()), "still inside the box: " + c.point());
    }

    // ---- the point nearest the eye ----------------------------------------------------------------

    private static final Box STANDING = new Box(-0.3, 0, 9.7, 0.3, 1.8, 10.3);

    @Test
    void theNearestPointIsOnTheNearFaceAtEyeHeightWhenTheEyeIsLevelWithTheBox() {
        V3 p = AimPointPicker.nearestPoint(STANDING, EYE);
        assertEquals(0.0, p.x(), 1.0e-9);
        assertEquals(1.62, p.y(), 1.0e-9, "the eye is inside the box's height range, so the point is at the eye's height");
        assertEquals(9.7 + AimPointPicker.NEAREST_INSET, p.z(), 1.0e-9, "on the face nearest the eye, pulled a little inside");
    }

    @Test
    void theNearestPointIsOnTheTopWhenTheEyeIsAboveTheBoxAndOnTheBottomWhenBelow() {
        V3 above = AimPointPicker.nearestPoint(STANDING, new V3(0, 6.0, 0));
        assertEquals(1.8 - AimPointPicker.NEAREST_INSET, above.y(), 1.0e-9);
        V3 below = AimPointPicker.nearestPoint(STANDING, new V3(0, -4.0, 0));
        assertEquals(AimPointPicker.NEAREST_INSET, below.y(), 1.0e-9);
    }

    @Test
    void theNearestPointIsOnTheNearSideWhenTheEyeIsToTheSide() {
        V3 east = AimPointPicker.nearestPoint(STANDING, new V3(8.0, 1.62, 10.0));
        assertEquals(0.3 - AimPointPicker.NEAREST_INSET, east.x(), 1.0e-9);
        assertEquals(10.0, east.z(), 1.0e-9, "level with the box in depth, so the same depth as the eye");
        V3 west = AimPointPicker.nearestPoint(STANDING, new V3(-8.0, 1.62, 10.0));
        assertEquals(-0.3 + AimPointPicker.NEAREST_INSET, west.x(), 1.0e-9);
    }

    @Test
    void theNearestPointIsAlwaysInsideTheBoxAndAsNearAsTheInsetAllows() {
        for (double x = -6; x <= 6; x += 1.5) {
            for (double y = -3; y <= 6; y += 1.5) {
                for (double z = -4; z <= 16; z += 2.0) {
                    V3 eye = new V3(x, y, z);
                    if (STANDING.contains(eye)) continue;
                    V3 p = AimPointPicker.nearestPoint(STANDING, eye);
                    assertTrue(STANDING.contains(p), "inside the box for eye " + eye + ": " + p);
                    double gap = p.distanceTo(eye) - STANDING.distanceTo(eye);
                    assertTrue(gap >= -1.0e-9, "never nearer than the surface: " + gap);
                    assertTrue(gap <= AimPointPicker.NEAREST_INSET * Math.sqrt(3.0) + 1.0e-9, "within the inset of the surface: " + gap);
                }
            }
        }
    }

    @Test
    void theNearestPointMovesNoFasterThanTheEyeDoes() {
        // Continuity: a small step of the eye can only move the point by at most that step, so the camera never lurches.
        V3 a = new V3(-1.0, 3.0, 2.0);
        for (int i = 0; i < 400; i++) {
            V3 b = new V3(a.x() + 0.01, a.y() - 0.007, a.z() + 0.02);
            double eyeMove = a.distanceTo(b);
            double pointMove = AimPointPicker.nearestPoint(STANDING, a).distanceTo(AimPointPicker.nearestPoint(STANDING, b));
            assertTrue(pointMove <= eyeMove + 1.0e-9, "step " + i + ": point moved " + pointMove + " for an eye move of " + eyeMove);
            a = b;
        }
    }

    @Test
    void aThinBoxIsStillAimedAtOnItsSurface() {
        Box plate = new Box(0, 0, 5, 0, 2, 5);
        V3 p = AimPointPicker.nearestPoint(plate, new V3(0, 1, 0));
        assertTrue(plate.contains(p));
    }

    @Test
    void thePreferredPointBlendsFromTheMiddleToTheNearestPoint() {
        V3 middle = STANDING.pointAtHeight(0.5);
        V3 nearest = AimPointPicker.nearestPoint(STANDING, EYE);
        assertEquals(middle, AimPointPicker.preferredPoint(STANDING, EYE, 0.0));
        assertEquals(nearest, AimPointPicker.preferredPoint(STANDING, EYE, 1.0));
        V3 half = AimPointPicker.preferredPoint(STANDING, EYE, 0.5);
        assertEquals((middle.y() + nearest.y()) / 2, half.y(), 1.0e-9);
        assertEquals((middle.z() + nearest.z()) / 2, half.z(), 1.0e-9);
        assertTrue(STANDING.contains(half));
        assertEquals(middle, AimPointPicker.preferredPoint(STANDING, EYE, -3.0), "clamped below");
        assertEquals(nearest, AimPointPicker.preferredPoint(STANDING, EYE, 9.0), "clamped above");
    }

    @Test
    void aPreferredPointIsTakenWhenItIsOpen() {
        TargetCandidate t = standing(1, 0, 10);
        V3 want = AimPointPicker.nearestPoint(t.box(), EYE);
        Choice c = AimPointPicker.pick(EYE, t, List.of(t), defaults, LineOfSight.ALWAYS, Double.NaN, want);
        assertEquals(want, c.point());
        assertEquals(Exposure.OPEN, c.exposure());
    }

    @Test
    void aHiddenPreferredPointFallsBackToTheUsualSpots() {
        TargetCandidate t = standing(1, 0, 10);
        V3 want = AimPointPicker.nearestPoint(t.box(), EYE);
        LineOfSight lowOnly = (from, to) -> to.y() < 0.5;
        Choice c = AimPointPicker.pick(EYE, t, List.of(t), defaults, lowOnly, Double.NaN, want);
        assertEquals(Zone.FEET, c.zone());
        assertTrue(c.point().y() < 0.5);
    }

    @Test
    void aCoveredPreferredPointStillShowsAsCoveredWhenEverySpotIsBehindSomethingElse() {
        TargetCandidate t = standing(1, 0, 10);
        TargetCandidate plank = new TargetCandidate(2, "plank", new Box(-3, 0, 5, 3, 3, 6));
        V3 want = AimPointPicker.nearestPoint(t.box(), EYE);
        Choice c = AimPointPicker.pick(EYE, t, List.of(t, plank), defaults, LineOfSight.ALWAYS, Double.NaN, want);
        assertEquals(Exposure.COVERED, c.exposure());
        assertEquals(want, c.point(), "the first spot that could be seen is reported");
    }

    @Test
    void aShieldedTargetIgnoresThePreferredPoint() {
        Box box = new Box(-0.3, 0, 9.7, 0.3, 1.8, 10.3);
        TargetCandidate t = new TargetCandidate(1, "t1", box, 0.0, true, TargetTier.PLAYER);
        V3 want = AimPointPicker.nearestPoint(box, EYE);
        Choice c = AimPointPicker.pick(EYE, t, List.of(t), defaults, LineOfSight.ALWAYS, Double.NaN, want);
        assertEquals(Exposure.SHIELDED, c.exposure());
        assertTrue(!c.point().equals(want), "the back of the target is aimed at, not the near face");
    }

    @Test
    void standingInsideTheBoxStillGivesInsideWhateverIsPreferred() {
        TargetCandidate around = new TargetCandidate(1, "t1", new Box(-0.5, 0, -0.5, 0.5, 2.0, 0.5));
        Choice c = AimPointPicker.pick(EYE, around, List.of(around), defaults, LineOfSight.ALWAYS, Double.NaN, new V3(0, 1, 0.3));
        assertEquals(Exposure.INSIDE, c.exposure());
    }
}
