package dev.sharp.core.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.math.Box;
import org.junit.jupiter.api.Test;

class AimHeightPlannerTest {
    private static final double DT = 1.0 / 60;
    private static final double EYE_Y = 61.62; // the player stands at y = 60
    private static final double FEET = AimPointPicker.FEET_FRACTION;
    private static final double MID = AimHeightPlanner.CENTRE;
    private final TargetingSettings settings = TargetingSettings.defaults();
    private final AimHeightPlanner planner = new AimHeightPlanner();

    private static Box at(double minY) {
        return new Box(-0.3, minY, 9.7, 0.3, minY + 1.8, 10.3);
    }

    private double run(Box box, boolean inReach, int frames) {
        double last = Double.NaN;
        for (int i = 0; i < frames; i++) {
            last = planner.plan(box, EYE_Y, inReach, DT, settings);
        }
        return last;
    }

    @Test
    void aGroundedTargetInReachIsAimedAtTheNormalSpot() {
        assertEquals(FEET, run(at(60), true, 30), 1.0e-9);
        assertFalse(planner.isFalling());
    }

    @Test
    void aTargetOutOfReachIsAimedAtTheMiddle() {
        assertEquals(MID, run(at(60), false, 30), 1.0e-9);
    }

    @Test
    void whenTheTargetComesInReachTheSpotMovesDownAtOnceButSmoothly() {
        run(at(60), false, 30);
        double previous = MID;
        int frames = 0;
        double value = MID;
        while (value > FEET + 1.0e-9 && frames < 60) {
            value = planner.plan(at(60), EYE_Y, true, DT, settings);
            assertTrue(value <= previous + 1.0e-12, "never goes back up while closing in");
            assertTrue(previous - value <= AimHeightPlanner.MAX_SLEW * DT + 1.0e-9, "slew limit");
            previous = value;
            frames++;
        }
        assertEquals(FEET, value, 1.0e-9);
        assertTrue(frames <= 12, "about a tenth of a second at 60 fps: " + frames);
    }

    @Test
    void standingOnTheEdgeOfReachDoesNotMakeTheSpotFlicker() {
        run(at(60), true, 30);
        // Out of reach for a tenth of a second, in reach again: no movement at all.
        for (int i = 0; i < 6; i++) {
            assertEquals(FEET, planner.plan(at(60), EYE_Y, false, DT, settings), 1.0e-9);
        }
        assertEquals(FEET, planner.plan(at(60), EYE_Y, true, DT, settings), 1.0e-9);
    }

    @Test
    void staysOutOfReachLongEnoughAndTheSpotMovesBackToTheMiddle() {
        run(at(60), true, 30);
        double v = run(at(60), false, 60);
        assertEquals(MID, v, 1.0e-9);
    }

    @Test
    void aFallingTargetHighAboveIsAimedAtTheMiddleAndSlidesDownAsItComesLevel() {
        // Falls from 12 blocks above the player's feet at 8 blocks per second, in reach the whole time.
        double y = 72;
        double first = Double.NaN;
        double previous = Double.NaN;
        boolean sawFalling = false;
        double atFiveAbove = Double.NaN;
        double atLevel = Double.NaN;
        while (y > 59.5) {
            double v = planner.plan(at(y), EYE_Y, true, DT, settings);
            if (Double.isNaN(first)) first = v;
            if (planner.isFalling()) sawFalling = true;
            if (!Double.isNaN(previous)) {
                assertTrue(Math.abs(v - previous) <= AimHeightPlanner.MAX_SLEW * DT + 1.0e-9, "slew limit at y=" + y);
            }
            if (Math.abs(y - 65.0) < 0.07) atFiveAbove = v;
            if (Math.abs(y - 60.0) < 0.07) atLevel = v;
            previous = v;
            y -= 8.0 * DT;
        }
        assertEquals(FEET, first, 1.0e-9, "the first frame has no speed yet, so it starts on the normal spot");
        assertTrue(sawFalling);
        assertTrue(atFiveAbove > MID - 0.05, "5 blocks above the feet it is aimed at in the middle: " + atFiveAbove);
        assertEquals(FEET, atLevel, 0.03, "level with the feet it is the normal spot: " + atLevel);
    }

    @Test
    void theSlideDownIsMonotonicOnceTheMiddleIsReached() {
        // Starts on the normal spot, rises to the middle while the fall is recognised, then only ever comes down.
        double y = 72;
        double previous = Double.NaN;
        boolean inTheMiddle = false;
        while (y > 59.5) {
            double v = planner.plan(at(y), EYE_Y, true, DT, settings);
            if (inTheMiddle) {
                assertTrue(v <= previous + 1.0e-9, "does not climb again at y=" + y + ": " + previous + " -> " + v);
            }
            if (v > MID - 1.0e-6) {
                inTheMiddle = true;
            }
            previous = v;
            y -= 8.0 * DT;
        }
        assertTrue(inTheMiddle);
    }

    @Test
    void aTargetOutOfReachAndFallingStaysInTheMiddleUntilItIsInReach() {
        double y = 72;
        double v = Double.NaN;
        while (y > 61) {
            v = planner.plan(at(y), EYE_Y, false, DT, settings);
            y -= 8.0 * DT;
        }
        assertEquals(MID, v, 1.0e-9);
        assertTrue(planner.isFalling());
    }

    @Test
    void aTargetBelowThePlayerThatFallsAwayIsAimedAtNormally() {
        double y = 55;
        double v = Double.NaN;
        for (int i = 0; i < 40; i++) {
            v = planner.plan(at(y), EYE_Y, true, DT, settings);
            y -= 8.0 * DT;
        }
        assertEquals(FEET, v, 1.0e-9);
    }

    @Test
    void aTargetThatIsRisingOrStandingIsNotFalling() {
        double y = 60;
        for (int i = 0; i < 60; i++) {
            planner.plan(at(y), EYE_Y, true, DT, settings);
            y += 6.0 * DT;
        }
        assertFalse(planner.isFalling());
        for (int i = 0; i < 60; i++) {
            planner.plan(at(y), EYE_Y, true, DT, settings);
        }
        assertFalse(planner.isFalling());
    }

    @Test
    void fallingEndsOnceTheTargetLands() {
        double y = 70;
        while (y > 60) {
            planner.plan(at(y), EYE_Y, true, DT, settings);
            y -= 8.0 * DT;
        }
        assertTrue(planner.isFalling());
        for (int i = 0; i < 30; i++) {
            planner.plan(at(60), EYE_Y, true, DT, settings);
        }
        assertFalse(planner.isFalling());
    }

    @Test
    void theSameInputsAlwaysGiveTheSameOutput() {
        AimHeightPlanner a = new AimHeightPlanner();
        AimHeightPlanner b = new AimHeightPlanner();
        double y = 70;
        for (int i = 0; i < 120; i++) {
            boolean reach = i % 40 > 15;
            assertEquals(a.plan(at(y), EYE_Y, reach, DT, settings), b.plan(at(y), EYE_Y, reach, DT, settings), 0.0);
            y -= 5.0 * DT;
        }
    }

    @Test
    void resetForgetsTheTarget() {
        run(at(60), false, 30);
        planner.reset();
        assertEquals(FEET, planner.plan(at(60), EYE_Y, true, DT, settings), 1.0e-9, "starts straight on the right spot");
    }

    @Test
    void aZeroOrHugeFrameTimeCannotBreakIt() {
        double v = planner.plan(at(60), EYE_Y, true, 0.0, settings);
        assertEquals(FEET, v, 1.0e-9);
        v = planner.plan(at(60), EYE_Y, false, 5.0, settings);
        assertTrue(v >= FEET && v <= MID);
        assertTrue(Double.isFinite(planner.plan(at(1.0e9), EYE_Y, true, 1.0e-9, settings)));
    }

    @Test
    void withoutFeetFirstTheNormalSpotIsTheConfiguredHeight() {
        TargetingSettings s = new TargetingSettings(64, 140, 0.3, 0.05, 0.0, true, false, true, true, true, 3.0, 6.0);
        double v = 0;
        for (int i = 0; i < 30; i++) {
            v = planner.plan(at(60), EYE_Y, true, DT, s);
        }
        assertEquals(0.3, v, 1.0e-9);
    }
}
