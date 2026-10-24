package dev.sharp.core.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.math.AimMath;
import dev.sharp.core.math.V3;
import org.junit.jupiter.api.Test;

/**
 * Closed-loop tests: a simulated camera is turned by the controller frame after frame, which is how the
 * game uses it. The properties checked here are the ones that matter for "no snapping, no oscillation".
 */
class AimControllerTest {
    private static final V3 EYE = V3.ZERO;

    /** Point at the given yaw/pitch, 10 blocks away from the eye. */
    private static V3 pointAt(double yaw, double pitch) {
        return EYE.add(AimMath.lookVector(yaw, pitch).scale(10.0));
    }

    private static AimSettings tuned(double sensitivity, double smoothing) {
        return new AimSettings(sensitivity, smoothing, 240, 1800, 20, 0.15, 0.25, -80, 80, true, 120);
    }

    /** Minimal simulated camera. */
    private static final class Sim {
        double yaw;
        double pitch;
        double time;
        final AimController controller = new AimController();

        Sim(double yaw, double pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        AimStep frame(V3 aim, double dt, AimSettings s) {
            AimStep step = controller.step(yaw, pitch, EYE, aim, dt, s);
            yaw += step.deltaYaw();
            pitch += step.deltaPitch();
            time += dt;
            return step;
        }
    }

    // ---- convergence and the absence of overshoot -----------------------------------------------

    @Test
    void convergesWithoutOvershootOnAStationaryTarget() {
        AimSettings s = tuned(1.0, 0.5);
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(40, 15);
        double prevYawErr = Math.abs(AimMath.wrapDegrees(40 - sim.yaw));
        double prevPitchErr = Math.abs(15 - sim.pitch);

        for (int i = 0; i < 60 * 6; i++) {
            sim.frame(aim, 1.0 / 60, s);
            double yawErr = AimMath.wrapDegrees(40 - sim.yaw);
            double pitchErr = 15 - sim.pitch;
            assertTrue(yawErr >= -1.0e-9, "yaw overshot the target at frame " + i + ": " + yawErr);
            assertTrue(pitchErr >= -1.0e-9, "pitch overshot the target at frame " + i + ": " + pitchErr);
            assertTrue(Math.abs(yawErr) <= prevYawErr + 1.0e-9, "yaw error grew at frame " + i);
            assertTrue(Math.abs(pitchErr) <= prevPitchErr + 1.0e-9, "pitch error grew at frame " + i);
            prevYawErr = Math.abs(yawErr);
            prevPitchErr = Math.abs(pitchErr);
        }
        assertEquals(40.0, sim.yaw, 0.3);
        assertEquals(15.0, sim.pitch, 0.3);
    }

    @Test
    void neverOvershootsEvenAtMaximumSensitivityAndLowestSmoothing() {
        // dt/tau is well above 1 here, which would ring in a naive proportional controller.
        AimSettings aggressive = new AimSettings(5.0, 0.0, 1440, 20000, 90, 0.0, 0.0, -80, 80, false, 120);
        Sim sim = new Sim(-30, 10);
        V3 aim = pointAt(25, -20);
        int signChanges = 0;
        double lastSign = 0;
        for (int i = 0; i < 200; i++) {
            sim.frame(aim, 1.0 / 60, aggressive);
            double err = AimMath.wrapDegrees(25 - sim.yaw);
            assertTrue(err >= -1.0e-9, "overshoot at frame " + i + ": " + err);
            // Once it has landed the error is floating point noise around zero, not a real sign.
            double sign = Math.abs(err) < 1.0e-6 ? 0 : Math.signum(err);
            if (sign != 0 && lastSign != 0 && sign != lastSign) {
                signChanges++;
            }
            if (sign != 0) {
                lastSign = sign;
            }
        }
        assertEquals(0, signChanges);
        assertEquals(25.0, sim.yaw, 1.0e-6);
        assertEquals(-20.0, sim.pitch, 1.0e-6);
    }

    @Test
    void reachesTheTargetInReasonableTimeAtDefaultTuning() {
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(60, 0);
        double reached = -1;
        for (int i = 0; i < 60 * 10; i++) {
            sim.frame(aim, 1.0 / 60, AimSettings.defaults());
            if (reached < 0 && Math.abs(60 - sim.yaw) < 1.0) {
                reached = sim.time;
            }
        }
        assertTrue(reached > 0.3, "default tuning is too abrupt: " + reached);
        assertTrue(reached < 3.0, "default tuning is too slow: " + reached);
    }

    @Test
    void higherSensitivityAndLowerSmoothingAreFaster() {
        double slow = timeToClose(0.5, 0.9);
        double normal = timeToClose(1.0, 0.5);
        double fast = timeToClose(3.0, 0.1);
        assertTrue(fast < normal && normal < slow, "fast=" + fast + " normal=" + normal + " slow=" + slow);
    }

    private double timeToClose(double sensitivity, double smoothing) {
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(30, 0);
        AimSettings s = tuned(sensitivity, smoothing);
        for (int i = 0; i < 60 * 30; i++) {
            sim.frame(aim, 1.0 / 60, s);
            if (Math.abs(30 - sim.yaw) < 1.0) {
                return sim.time;
            }
        }
        return Double.MAX_VALUE;
    }

    // ---- the default tuning -----------------------------------------------------------------------

    @Test
    void theDefaultsAreFastButStillNeverSnapOnTheFirstFrame() {
        AimSettings s = AimSettings.defaults();
        Sim sim = new Sim(0, 0);
        AimStep first = sim.frame(pointAt(90, 0), 1.0 / 60, s);
        assertTrue(Math.abs(first.deltaYaw()) < 3.0, "first frame moved " + first.deltaYaw());
        assertTrue(first.ramp() < 0.5, "still fading in: " + first.ramp());
    }

    @Test
    void theDefaultsCloseAThirtyDegreeGapQuicklyWithoutOvershoot() {
        AimSettings s = AimSettings.defaults();
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(30, 0);
        double reached = -1;
        double peak = 0;
        for (int i = 0; i < 60 * 3; i++) {
            sim.frame(aim, 1.0 / 60, s);
            peak = Math.max(peak, sim.yaw);
            if (reached < 0 && Math.abs(30 - sim.yaw) < 1.0) {
                reached = sim.time;
            }
        }
        assertTrue(reached > 0 && reached < 0.35, "took " + reached + " s to get within a degree");
        assertTrue(peak <= 30.0 + 0.2, "overshot to " + peak);
    }

    @Test
    void theDefaultsAreAtLeastTwiceAsFastAsTheOldGentleTuning() {
        AimSettings gentle = tuned(1.0, 0.5);
        AimSettings fast = AimSettings.defaults();
        double slow = timeToCloseWith(gentle);
        double quick = timeToCloseWith(fast);
        assertTrue(quick * 2.0 <= slow, "old " + slow + " s, new " + quick + " s");
    }

    private static double timeToCloseWith(AimSettings s) {
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(60, 0);
        for (int i = 0; i < 60 * 30; i++) {
            sim.frame(aim, 1.0 / 60, s);
            if (Math.abs(60 - sim.yaw) < 1.0) {
                return sim.time;
            }
        }
        return Double.MAX_VALUE;
    }

    // ---- no snapping ----------------------------------------------------------------------------

    @Test
    void firstFramesAreGentleBecauseOfTheFadeIn() {
        AimSettings s = tuned(1.0, 0.5); // a long 0.25 s fade-in, to see the mechanism clearly
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(90, 0);

        AimStep first = sim.frame(aim, 1.0 / 60, s);
        assertTrue(Math.abs(first.deltaYaw()) < 0.1, "first frame moved " + first.deltaYaw());
        assertEquals(0.0, first.deltaPitch(), 1.0e-12);

        // Over the first tenth of a second the assist is still fading in: a few degrees at most towards a
        // target 90 degrees away, and each frame only slightly bigger than the one before.
        double total = Math.abs(first.deltaYaw());
        double previous = Math.abs(first.deltaYaw());
        for (int i = 0; i < 5; i++) {
            double now = Math.abs(sim.frame(aim, 1.0 / 60, s).deltaYaw());
            assertTrue(now >= previous - 1.0e-9, "fade-in is not monotonic at frame " + (i + 2));
            assertTrue(now - previous <= 0.5 + 1.0e-9, "fade-in stepped up too sharply at frame " + (i + 2));
            total += now;
            previous = now;
        }
        assertTrue(total < 6.0, "first tenth of a second moved " + total + " degrees");
    }

    @Test
    void rotationSpeedAndAccelerationStayWithinTheConfiguredCaps() {
        AimSettings s = new AimSettings(3.0, 0.0, 200, 1000, 20, 0.0, 0.0, -80, 80, false, 120);
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(170, 0);
        double dt = 1.0 / 60;
        double prevRate = 0;
        double maxRate = 0;
        for (int i = 0; i < 400; i++) {
            AimStep step = sim.frame(aim, dt, s);
            double rate = step.deltaYaw() / dt;
            maxRate = Math.max(maxRate, Math.abs(rate));
            assertTrue(Math.abs(rate - prevRate) <= 1000 * dt + 1.0e-6, "acceleration cap broken at frame " + i);
            prevRate = rate;
        }
        assertTrue(maxRate <= 200 + 1.0e-6, "speed cap broken: " + maxRate);
        assertTrue(maxRate > 100, "never got anywhere near the cap, test is not exercising it: " + maxRate);
    }

    @Test
    void perFrameStepCapIsRespected() {
        AimSettings s = new AimSettings(5.0, 0.0, 1440, 20000, 0.5, 0.0, 0.0, -80, 80, false, 120);
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(120, 40);
        for (int i = 0; i < 200; i++) {
            AimStep step = sim.frame(aim, 1.0 / 60, s);
            assertTrue(Math.abs(step.deltaYaw()) <= 0.5 + 1.0e-12);
            assertTrue(Math.abs(step.deltaPitch()) <= 0.5 + 1.0e-12);
        }
    }

    @Test
    void aNewTargetPositionDoesNotProduceAJerk() {
        // Settle on one target, then jump the aim point by 120 degrees. Rotation speed must ramp, not leap.
        AimSettings s = tuned(2.0, 0.2);
        Sim sim = new Sim(0, 0);
        V3 first = pointAt(20, 0);
        for (int i = 0; i < 300; i++) {
            sim.frame(first, 1.0 / 60, s);
        }
        V3 second = pointAt(-100, 0);
        double dt = 1.0 / 60;
        double prevRate = 0;
        for (int i = 0; i < 120; i++) {
            AimStep step = sim.frame(second, dt, s);
            double rate = step.deltaYaw() / dt;
            assertTrue(Math.abs(rate - prevRate) <= 1800 * dt + 1.0e-6, "rate jumped at frame " + i);
            prevRate = rate;
        }
    }

    @Test
    void neverCrossesATargetThatJumpsCloserWhileTheCameraIsMovingFast() {
        // Braking is planned for the error the camera knew about. If the target suddenly appears much
        // closer than the braking distance, momentum alone would carry the camera past it. The crossing
        // guard must land it on the target instead.
        AimSettings s = tuned(3.0, 0.0);
        Sim sim = new Sim(0, 0);
        V3 far = pointAt(170, 0);
        for (int i = 0; i < 24; i++) {
            sim.frame(far, 1.0 / 60, s);
        }
        assertTrue(Math.abs(sim.controller.yawRate()) > 100,
                "setup: the camera should be moving fast, was " + sim.controller.yawRate());

        double goal = sim.yaw + 2.0;
        V3 near = pointAt(goal, 0);
        for (int i = 0; i < 60; i++) {
            sim.frame(near, 1.0 / 60, s);
            assertTrue(AimMath.wrapDegrees(goal - sim.yaw) >= -1.0e-9, "crossed the new target at frame " + i);
        }
        assertEquals(goal, sim.yaw, 0.2);
    }

    // ---- limits ---------------------------------------------------------------------------------

    @Test
    void yawTakesTheShortWayAroundTheCircle() {
        Sim sim = new Sim(170, 0);
        for (int i = 0; i < 200; i++) {
            sim.frame(pointAt(-170, 0), 1.0 / 60, AimSettings.defaults());
        }
        // Path from 170 to -170 (= 190) is 20 degrees forward, not 340 backwards.
        assertTrue(sim.yaw > 185 && sim.yaw < 195, "went the long way: " + sim.yaw);
        assertTrue(Math.abs(AimMath.wrapDegrees(sim.yaw - (-170))) < 0.5);
    }

    @Test
    void pitchLimitsClampTheAim() {
        AimSettings s = new AimSettings(2.0, 0.2, 240, 1800, 20, 0.0, 0.0, -60, 70, false, 120);
        Sim up = new Sim(0, 0);
        for (int i = 0; i < 600; i++) {
            up.frame(pointAt(0, -85), 1.0 / 60, s);
        }
        assertEquals(-60.0, up.pitch, 1.0e-6, "pitch must stop at the upward limit");

        Sim down = new Sim(0, 0);
        for (int i = 0; i < 600; i++) {
            down.frame(pointAt(0, 85), 1.0 / 60, s);
        }
        assertEquals(70.0, down.pitch, 1.0e-6, "pitch must stop at the downward limit");
    }

    @Test
    void neverPushesPitchBeyondTheGameLimits() {
        AimSettings s = new AimSettings(5.0, 0.0, 1440, 20000, 90, 0.0, 0.0, -200, 200, false, 120);
        Sim sim = new Sim(0, 89);
        for (int i = 0; i < 100; i++) {
            sim.frame(pointAt(0, 89.9), 1.0 / 60, s);
            assertTrue(sim.pitch <= 90.0 && sim.pitch >= -90.0);
        }
    }

    @Test
    void deadZoneStopsTheCameraAtItsEdge() {
        AimSettings s = new AimSettings(1.0, 0.5, 240, 1800, 20, 0.5, 0.0, -80, 80, false, 120);
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(10, 0);
        for (int i = 0; i < 60 * 10; i++) {
            sim.frame(aim, 1.0 / 60, s);
        }
        double remaining = 10 - sim.yaw;
        assertEquals(0.5, remaining, 0.02, "should rest at the dead zone edge");

        // Starting inside the dead zone: no movement at all.
        Sim still = new Sim(0, 0);
        for (int i = 0; i < 120; i++) {
            AimStep step = still.frame(pointAt(0.3, 0.2), 1.0 / 60, s);
            assertTrue(step.isZero());
        }
    }

    // ---- bad input ------------------------------------------------------------------------------

    @Test
    void nonPositiveFrameTimeProducesNoMovement() {
        AimController c = new AimController();
        AimStep zero = c.step(0, 0, EYE, pointAt(50, 0), 0.0, AimSettings.defaults());
        AimStep negative = c.step(0, 0, EYE, pointAt(50, 0), -0.5, AimSettings.defaults());
        assertTrue(zero.isZero());
        assertTrue(negative.isZero());
        assertEquals(50.0, zero.yawError(), 1.0e-9, "errors are still reported");
    }

    @Test
    void aHugeFrameTimeIsTreatedAsAShortOne() {
        AimSettings s = new AimSettings(5.0, 0.0, 1440, 20000, 90, 0.0, 0.0, -80, 80, false, 120);
        AimController c = new AimController();
        c.step(0, 0, EYE, pointAt(90, 0), 0.016, s); // advance the fade-in, otherwise the ramp hides the cap
        for (int i = 0; i < 20; i++) {
            c.step(0, 0, EYE, pointAt(90, 0), 0.016, s);
        }
        AimStep stall = c.step(0, 0, EYE, pointAt(90, 0), 5.0, s);
        assertTrue(Math.abs(stall.deltaYaw()) <= 1440 * AimController.MAX_FRAME_SECONDS + 1.0e-9,
                "a 5 second stall moved " + stall.deltaYaw());
    }

    @Test
    void nonFiniteInputIsIgnoredSafely() {
        AimController c = new AimController();
        assertTrue(c.step(Double.NaN, 0, EYE, pointAt(10, 0), 0.016, AimSettings.defaults()).isZero());
        assertTrue(c.step(0, 0, EYE, new V3(Double.NaN, 0, 1), 0.016, AimSettings.defaults()).isZero());
        assertTrue(c.step(0, 0, EYE, pointAt(10, 0), Double.NaN, AimSettings.defaults()).isZero());
        // Still usable afterwards.
        AimStep ok = null;
        for (int i = 0; i < 30; i++) {
            ok = c.step(0, 0, EYE, pointAt(10, 0), 0.016, AimSettings.defaults());
        }
        assertFalse(ok.isZero());
    }

    @Test
    void aimingAtTheEyeItselfDoesNotBlowUp() {
        Sim sim = new Sim(33, 12);
        for (int i = 0; i < 60; i++) {
            AimStep step = sim.frame(EYE, 1.0 / 60, AimSettings.defaults());
            assertTrue(Double.isFinite(step.deltaYaw()) && Double.isFinite(step.deltaPitch()));
        }
    }

    // ---- moving targets -------------------------------------------------------------------------

    @Test
    void followsAMovingTargetWithBoundedLagAndNoRinging() {
        AimSettings s = tuned(2.0, 0.3);
        Sim sim = new Sim(0, 0);
        double dt = 1.0 / 60;
        double targetRate = 40; // degrees per second sweep
        double targetYaw = 0;
        double worstLagLate = 0;
        int signFlips = 0;
        double lastSign = 0;
        for (int i = 0; i < 60 * 8; i++) {
            targetYaw += targetRate * dt;
            sim.frame(pointAt(targetYaw, 0), dt, s);
            if (i > 60 * 4) {
                double lag = AimMath.wrapDegrees(targetYaw - sim.yaw);
                worstLagLate = Math.max(worstLagLate, Math.abs(lag));
                double sign = Math.signum(lag);
                if (lastSign != 0 && sign != 0 && sign != lastSign) {
                    signFlips++;
                }
                if (sign != 0) {
                    lastSign = sign;
                }
            }
        }
        assertTrue(worstLagLate < 15, "steady-state lag too large: " + worstLagLate);
        assertEquals(0, signFlips, "tracking error oscillated around zero");
    }

    @Test
    void behaviourIsSimilarAcrossFrameRates() {
        double at30 = yawAfterOneSecond(30);
        double at60 = yawAfterOneSecond(60);
        double at144 = yawAfterOneSecond(144);
        assertEquals(at60, at30, 3.0);
        assertEquals(at60, at144, 3.0);
    }

    private double yawAfterOneSecond(int fps) {
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(50, 0);
        AimSettings s = AimSettings.defaults();
        for (int i = 0; i < fps; i++) {
            sim.frame(aim, 1.0 / fps, s);
        }
        return sim.yaw;
    }

    // ---- yielding to the player -----------------------------------------------------------------

    @Test
    void backsOffWhileThePlayerMovesTheMouseAndReturnsAfterwards() {
        AimSettings s = AimSettings.defaults();
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(80, 0);
        double dt = 1.0 / 60;

        for (int i = 0; i < 60; i++) {
            sim.frame(aim, dt, s);
        }
        assertEquals(1.0, sim.controller.strength(), 1.0e-9);

        // The player drags the camera away at 300 degrees per second, well above the 120 yield rate.
        for (int i = 0; i < 30; i++) {
            sim.yaw -= 300 * dt;
            sim.frame(aim, dt, s);
        }
        assertTrue(sim.controller.strength() < 0.05, "assist did not back off: " + sim.controller.strength());
        assertTrue(Math.abs(sim.controller.yawRate()) < 5, "assist kept pulling: " + sim.controller.yawRate());

        // The player lets go: the assist returns gradually.
        double justAfter = 0;
        for (int i = 0; i < 90; i++) {
            sim.frame(aim, dt, s);
            if (i == 5) {
                justAfter = sim.controller.strength();
            }
        }
        assertTrue(justAfter < 0.6, "assist came back instantly: " + justAfter);
        assertTrue(sim.controller.strength() > 0.9, "assist never came back: " + sim.controller.strength());
    }

    @Test
    void yieldingCanBeDisabled() {
        AimSettings s = new AimSettings(1.0, 0.5, 240, 1800, 20, 0.15, 0.0, -80, 80, false, 120);
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(80, 0);
        for (int i = 0; i < 30; i++) {
            sim.yaw -= 5;
            sim.frame(aim, 1.0 / 60, s);
        }
        assertEquals(1.0, sim.controller.strength(), 1.0e-12);
    }

    // ---- reset ----------------------------------------------------------------------------------

    @Test
    void resetRestartsTheFadeInAndClearsMotion() {
        AimSettings s = tuned(1.0, 0.5);
        Sim sim = new Sim(0, 0);
        V3 aim = pointAt(90, 0);
        for (int i = 0; i < 60; i++) {
            sim.frame(aim, 1.0 / 60, s);
        }
        assertTrue(Math.abs(sim.controller.yawRate()) > 1);

        sim.controller.reset();
        assertEquals(0.0, sim.controller.yawRate());
        assertEquals(0.0, sim.controller.pitchRate());

        AimStep first = sim.frame(aim, 1.0 / 60, s);
        assertTrue(Math.abs(first.deltaYaw()) < 0.1, "no fade-in after reset: " + first.deltaYaw());
        assertTrue(first.ramp() < 0.05, "ramp did not restart: " + first.ramp());
    }
}
