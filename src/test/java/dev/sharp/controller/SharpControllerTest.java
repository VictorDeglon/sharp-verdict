package dev.sharp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.config.SharpConfig;
import dev.sharp.controller.SharpController.FrameInput;
import dev.sharp.controller.SharpController.FrameResult;
import dev.sharp.controller.SharpController.WorldView;
import dev.sharp.core.activation.ActivationTracker;
import dev.sharp.core.activation.ActivationTracker.Reason;
import dev.sharp.core.math.AimMath;
import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import dev.sharp.core.render.HitboxScene;
import dev.sharp.core.targeting.AimPointPicker;
import dev.sharp.core.targeting.Exposure;
import dev.sharp.core.targeting.Selection;
import dev.sharp.core.targeting.TargetCandidate;
import dev.sharp.core.targeting.TargetTier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scenario tests for the whole feature: a fake world is stepped frame by frame, the "camera" really
 * turns by whatever the controller asks for, and the tests check what happens on key release, target
 * loss and world transitions.
 */
class SharpControllerTest {
    private static final double FRAME = 1.0 / 60;

    private SharpConfig config;
    private final List<String> log = new ArrayList<>();
    private SharpController controller;
    private Scenario w;

    @BeforeEach
    void setUp() {
        config = new SharpConfig();
        config.requireLineOfSight = false;
        config.fovDegrees = 360;
        config.range = 40;
        controller = new SharpController(() -> config, log::add);
        w = new Scenario();
    }

    /** A mutable fake world plus the camera the controller is allowed to turn. */
    private final class Scenario {
        Object world = new Object();
        Object player = new Object();
        boolean key;
        boolean focus = true;
        boolean screen;
        boolean alive = true;
        boolean connected = true;
        boolean gate = true;
        long tick = 100;
        double partial = 0.0;
        V3 eye = new V3(0, 1.62, 0);
        double yaw = 0;
        double pitch = 0;
        long now = 5_000_000_000L;
        final List<TargetCandidate> candidates = new ArrayList<>();
        boolean worldRequested;
        Predicate<Box> reach = box -> false;
        /** Entities that are not practice entities but can still get in the way. */
        final List<TargetCandidate> extraBlockers = new ArrayList<>();

        FrameResult frame() {
            return frame(FRAME, true);
        }

        FrameResult frame(double dt, boolean applyCamera) {
            now += (long) (dt * 1.0e9);
            ActivationTracker.Input input = new ActivationTracker.Input(
                    key, focus, screen, alive && connected, connected, gate,
                    connected ? world : null, connected ? player : null);
            FrameInput in = new FrameInput(input, now, "OPEN", () -> {
                worldRequested = true;
                return new WorldView(tick, partial, eye, yaw, pitch, List.copyOf(candidates), (a, b) -> true, blockers(), reach);
            });
            FrameResult r = controller.frame(in);
            if (applyCamera && r.aim() != null) {
                yaw += r.aim().deltaYaw();
                pitch += r.aim().deltaPitch();
            }
            return r;
        }

        List<TargetCandidate> blockers() {
            List<TargetCandidate> all = new ArrayList<>(candidates);
            all.addAll(extraBlockers);
            return List.copyOf(all);
        }

        FrameResult frames(int n) {
            FrameResult last = null;
            for (int i = 0; i < n; i++) {
                last = frame();
            }
            return last;
        }

        /** Releases then presses the key, as a player would. */
        FrameResult pressFresh() {
            key = false;
            frame();
            key = true;
            return frame();
        }
    }

    /** The slow, long-fade tuning the first release shipped, which shows the fade-in mechanism clearly. */
    private void gentleTuning() {
        config.cameraSensitivity = 1.0;
        config.cameraSmoothing = 0.5;
        config.maxRotationSpeed = 240.0;
        config.maxRotationAcceleration = 1800.0;
        config.maxStepPerFrame = 20.0;
        config.rampSeconds = 0.25;
    }

    private static TargetCandidate tier(TargetCandidate c, TargetTier tier) {
        return new TargetCandidate(c.id(), c.label(), c.box(), c.facingYaw(), c.shieldUp(), tier);
    }

    private static TargetCandidate dummy(int id, double x, double z) {
        return new TargetCandidate(id, "sharp " + id, new Box(x - 0.3, 0.7, z - 0.3, x + 0.3, 2.5, z + 0.3));
    }

    /** A dummy placed {@code distance} blocks away at the given yaw, at eye height. */
    private static TargetCandidate dummyAtYaw(int id, double yawDegrees, double distance) {
        V3 dir = AimMath.lookVector(yawDegrees, 0);
        return dummy(id, dir.x() * distance, dir.z() * distance);
    }

    // ---- activation basics ----------------------------------------------------------------------

    @Test
    void doesNotTouchTheWorldWhileIdle() {
        w.candidates.add(dummy(1, 0, 10));
        for (int i = 0; i < 30; i++) {
            FrameResult r = w.frame();
            assertFalse(r.active());
            assertEquals(HitboxScene.EMPTY, r.scene());
            assertNull(r.aim());
        }
        assertFalse(w.worldRequested, "the entity scan must not run while the key is up");
    }

    @Test
    void pressingTheKeyAcquiresAndOutlinesTheTarget() {
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        FrameResult r = w.frame();

        assertTrue(r.active());
        assertEquals(Selection.Kind.ACQUIRED, r.selection().kind());
        assertEquals(1, r.selection().targetId());
        assertNotNull(r.scene().main());
        assertFalse(r.scene().tracking(), "the camera is not following, so the outline is the plain one");
        assertNull(r.aim(), "camera tracking is off by default");
        assertTrue(controller.hasTarget());
    }

    @Test
    void theTargetStaysLockedWhileHeld() {
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        w.frame();
        // A better placed rival appears and moves around; the lock must not move.
        for (int i = 0; i < 120; i++) {
            w.candidates.removeIf(c -> c.id() == 2);
            w.candidates.add(dummy(2, Math.sin(i * 0.2) * 3, 8));
            FrameResult r = w.frame();
            assertEquals(1, r.selection().targetId(), "frame " + i);
        }
    }

    // ---- camera tracking --------------------------------------------------------------------------

    @Test
    void trackingTurnsTheCameraTowardsTheTargetAndShowsTheTrackingState() {
        config.cameraTrackingEnabled = true;
        w.candidates.add(dummyAtYaw(1, 40, 10));
        w.frame();
        w.key = true;

        FrameResult r = null;
        for (int i = 0; i < 60 * 6; i++) {
            r = w.frame();
        }
        assertTrue(r.scene().tracking());
        assertEquals(40.0, w.yaw, 0.5);
        assertTrue(r.debug().angularError() < 1.0, "crosshair should be on the target: " + r.debug().angularError());
    }

    @Test
    void cameraDoesNotMoveWhenTrackingIsOffEvenWithATarget() {
        config.cameraTrackingEnabled = false;
        w.candidates.add(dummyAtYaw(1, 40, 10));
        w.frame();
        w.key = true;
        w.frames(120);
        assertEquals(0.0, w.yaw, 0.0);
        assertEquals(0.0, w.pitch, 0.0);
    }

    @Test
    void firstTrackedFramesAreGentle() {
        gentleTuning();
        config.cameraTrackingEnabled = true;
        w.candidates.add(dummyAtYaw(1, 90, 10));
        w.frame();
        w.key = true;
        FrameResult first = w.frame();
        assertNotNull(first.aim());
        assertEquals(0.0, first.aim().deltaYaw(), 0.0, "no time has passed on the very first frame");
        double total = 0;
        for (int i = 0; i < 6; i++) {
            total += Math.abs(w.frame().aim().deltaYaw());
        }
        assertTrue(total < 6.0, "moved " + total + " degrees in the first 100ms");
    }

    // ---- key release ----------------------------------------------------------------------------

    @Test
    void releasingTheKeyStopsEverythingOnTheSameFrame() {
        config.cameraTrackingEnabled = true;
        w.candidates.add(dummyAtYaw(1, 40, 10));
        w.frame();
        w.key = true;
        w.frames(40);
        assertTrue(controller.hasTarget());
        double yawAtRelease = w.yaw;

        w.key = false;
        FrameResult r = w.frame();
        assertFalse(r.active());
        assertNull(r.aim(), "no camera motion on the frame the key goes up");
        assertEquals(HitboxScene.EMPTY, r.scene());
        assertEquals(Selection.Kind.NONE, r.selection().kind());
        assertFalse(controller.hasTarget());
        assertEquals(Reason.KEY_RELEASED, controller.lastDeactivationReason());

        w.frames(60);
        assertEquals(yawAtRelease, w.yaw, 0.0, "the camera must stay put after release");
    }

    @Test
    void pressingAgainStartsFromScratch() {
        config.cameraTrackingEnabled = true;
        w.candidates.add(dummyAtYaw(1, 40, 10));
        w.frame();
        w.key = true;
        w.frames(30);
        FrameResult again = w.pressFresh();
        assertEquals(Selection.Kind.ACQUIRED, again.selection().kind());
        assertEquals(0.0, again.aim().deltaYaw(), 0.0, "camera fade-in restarts");
    }

    // ---- target loss ----------------------------------------------------------------------------

    @Test
    void targetLossClearsTheLockAndStopsTheCamera() {
        config.cameraTrackingEnabled = true;
        w.candidates.add(dummyAtYaw(1, 40, 10));
        w.frame();
        w.key = true;
        w.frames(40);
        assertTrue(Math.abs(w.yaw) > 1.0);

        w.candidates.clear();
        FrameResult lost = w.frame();
        assertTrue(lost.active(), "the key is still held");
        assertEquals(Selection.Kind.LOST, lost.selection().kind());
        assertNull(lost.aim());
        assertEquals(HitboxScene.EMPTY, lost.scene());
        assertFalse(controller.hasTarget());

        double yaw = w.yaw;
        w.frames(30);
        assertEquals(yaw, w.yaw, 0.0, "no motion without a target");
    }

    @Test
    void aTargetThatComesBackIsPickedUpAgainAndFadesIn() {
        gentleTuning();
        config.cameraTrackingEnabled = true;
        w.candidates.add(dummyAtYaw(1, 40, 10));
        w.frame();
        w.key = true;
        w.frames(60);
        w.candidates.clear();
        w.frames(5);

        w.candidates.add(dummyAtYaw(1, w.yaw + 60, 10));
        FrameResult back = w.frame();
        assertEquals(1, back.selection().targetId());
        assertTrue(back.aim() != null, "the camera follows it again");
        // Time keeps running while there is no target, so the first step is tiny rather than exactly zero.
        assertTrue(back.aim().ramp() < 0.05, "fade-in did not restart: ramp=" + back.aim().ramp());
        assertTrue(Math.abs(back.aim().deltaYaw()) < 0.1, "first step after a loss: " + back.aim().deltaYaw());
    }

    @Test
    void anotherEntityIsNeverTakenWhileTheKeyStaysDownAndIsTakenOnTheNextPress() {
        gentleTuning();
        config.cameraTrackingEnabled = true;
        w.candidates.add(dummyAtYaw(1, 40, 10));
        w.frame();
        w.key = true;
        w.frames(60);
        assertEquals(1, controller.targetId());

        w.candidates.clear();
        w.candidates.add(dummyAtYaw(2, w.yaw + 10, 6)); // close and nearly dead ahead: the best target by any measure
        FrameResult r = w.frame();
        assertFalse(r.selection().hasTarget());
        assertNull(r.aim());
        double yaw = w.yaw;
        w.frames(120);
        assertEquals(yaw, w.yaw, 0.0, "the camera does not turn to the other entity");
        assertFalse(controller.hasTarget());
        assertTrue(controller.isActive(), "still held, still on, just with nothing to follow");

        FrameResult again = w.pressFresh();
        assertEquals(Selection.Kind.ACQUIRED, again.selection().kind());
        assertEquals(2, again.selection().targetId());
        assertEquals(0.0, again.aim().deltaYaw(), 0.0, "camera fade-in restarts");
    }

    @Test
    void aBetterPlacedPlayerNeverStealsTheLockFromTheFirstEntity() {
        w.candidates.add(tier(dummyAtYaw(1, 20, 10), TargetTier.OTHER));
        w.frame();
        w.key = true;
        w.frames(5);
        assertEquals(1, controller.targetId());

        w.candidates.add(tier(dummyAtYaw(2, 0, 3), TargetTier.PLAYER));
        w.frames(60);
        assertEquals(1, controller.targetId(), "even a player right in front of you waits for the next press");
    }

    @Test
    void whenTheTargetIsGoneTheCameraStopsAndTheNextPressGlidesToTheOtherOne() {
        config.cameraTrackingEnabled = true;
        w.candidates.add(dummyAtYaw(1, 30, 10));
        w.candidates.add(dummyAtYaw(2, -100, 12));
        w.frame();
        w.key = true;
        w.frames(120); // settled on target 1 (it is the nearer one)
        assertEquals(1, controller.targetId());

        w.candidates.removeIf(c -> c.id() == 1);
        double stopped = w.yaw;
        w.frames(60);
        assertEquals(stopped, w.yaw, 0.0, "no motion toward target 2 while the key is still held");

        w.pressFresh();
        double dt = FRAME;
        double previousRate = 0;
        double maxRateJump = 0;
        FrameResult r = null;
        for (int i = 0; i < 180; i++) {
            r = w.frame();
            double rate = r.aim().deltaYaw() / dt;
            maxRateJump = Math.max(maxRateJump, Math.abs(rate - previousRate));
            previousRate = rate;
        }
        assertEquals(2, r.selection().targetId());
        assertTrue(maxRateJump <= config.maxRotationAcceleration * dt + 1.0e-6,
                "camera speed jumped by " + maxRateJump + " deg/s in one frame");
        assertEquals(-100.0, w.yaw, 1.0);
    }

    @Test
    void aPlayerIsLockedBeforeASheepThatIsAlmostAsNear() {
        w.candidates.add(tier(dummyAtYaw(1, 0, 2.0), TargetTier.OTHER));
        w.candidates.add(tier(dummyAtYaw(2, 15, 2.5), TargetTier.PLAYER));
        w.frame();
        w.key = true;
        assertEquals(2, w.frame().selection().targetId());
    }

    // ---- world transitions ----------------------------------------------------------------------

    @Test
    void changingWorldClearsTheTargetAndStaysOffWhileTheKeyIsStillHeld() {
        config.cameraTrackingEnabled = true;
        w.candidates.add(dummyAtYaw(1, 40, 10));
        w.frame();
        w.key = true;
        w.frames(30);
        assertTrue(controller.hasTarget());

        w.world = new Object(); // e.g. walked through a portal
        FrameResult r = w.frame();
        assertFalse(r.active());
        assertFalse(controller.hasTarget());
        assertNull(r.aim());
        assertEquals(HitboxScene.EMPTY, r.scene());
        assertEquals(Reason.WORLD_CHANGED, controller.lastDeactivationReason());

        double yaw = w.yaw;
        for (int i = 0; i < 60; i++) {
            assertFalse(w.frame().active(), "must not re-activate while the key stays down");
        }
        assertEquals(yaw, w.yaw, 0.0);

        // The new world has an entity with the SAME id; it must be a fresh acquisition, not a stale lock.
        FrameResult fresh = w.pressFresh();
        assertTrue(fresh.active());
        assertEquals(Selection.Kind.ACQUIRED, fresh.selection().kind());
    }

    @Test
    void disconnectingClearsTheTarget() {
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        w.frames(10);
        assertTrue(controller.hasTarget());

        w.connected = false;
        FrameResult r = w.frame();
        assertFalse(r.active());
        assertFalse(controller.hasTarget());
        assertEquals(Reason.DISCONNECTED, controller.lastDeactivationReason());

        w.connected = true;
        w.world = new Object();
        w.player = new Object();
        assertFalse(w.frame().active(), "rejoining with the key held stays off");
        assertFalse(w.worldRequested && controller.hasTarget());
    }

    @Test
    void dyingClearsTheTargetAndRespawningWithTheKeyHeldStaysOff() {
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        w.frames(10);

        w.alive = false;
        assertFalse(w.frame().active());
        assertFalse(controller.hasTarget());
        assertEquals(Reason.PLAYER_DIED, controller.lastDeactivationReason());

        w.alive = true;
        w.player = new Object();
        assertFalse(w.frames(20).active());
        assertTrue(w.pressFresh().active());
    }

    @Test
    void losingFocusOrOpeningAScreenClearsTheTarget() {
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        w.frames(10);
        w.focus = false;
        assertFalse(w.frame().active());
        assertFalse(controller.hasTarget());
        assertEquals(Reason.FOCUS_LOST, controller.lastDeactivationReason());

        w.focus = true;
        w.pressFresh();
        assertTrue(controller.hasTarget());
        w.screen = true;
        assertFalse(w.frame().active());
        assertFalse(controller.hasTarget());
        assertEquals(Reason.SCREEN_OPENED, controller.lastDeactivationReason());
    }

    @Test
    void closingThePracticeGateShutsTheFeatureDown() {
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        w.frames(10);
        w.gate = false; // e.g. the world was opened to LAN
        assertFalse(w.frame().active());
        assertEquals(Reason.GATE_CLOSED, controller.lastDeactivationReason());
        assertFalse(controller.hasTarget());
    }

    @Test
    void anEventDrivenResetClearsEverythingEvenBetweenFrames() {
        config.cameraTrackingEnabled = true;
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        w.frames(30);
        assertTrue(controller.isActive());

        controller.reset(Reason.DISCONNECTED);

        assertFalse(controller.isActive());
        assertFalse(controller.hasTarget());
        assertEquals(Reason.DISCONNECTED, controller.lastDeactivationReason());
        assertFalse(w.frame().active(), "still disarmed until the key is released");
        assertTrue(w.pressFresh().active());
    }

    @Test
    void theTickPollAloneSwitchesTheFeatureOffWhenFocusIsLost() {
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        w.frames(10);
        assertTrue(controller.hasTarget());

        ActivationTracker.Input unfocused = new ActivationTracker.Input(
                true, false, false, true, true, true, w.world, w.player);
        assertFalse(controller.poll(unfocused, w.now + 1_000_000L));
        assertFalse(controller.isActive());
        assertFalse(controller.hasTarget());
    }

    // ---- selection and camera are independent ---------------------------------------------------

    @Test
    void selectionDoesNotDependOnWhetherTheCameraTracks() {
        List<Integer> withCamera = selectionTrace(true);
        List<Integer> withoutCamera = selectionTrace(false);
        assertEquals(withoutCamera, withCamera);
        assertTrue(withCamera.stream().anyMatch(id -> id != Selection.NO_TARGET));
    }

    /** Camera is not applied here, so only the configuration differs between the two runs. */
    private List<Integer> selectionTrace(boolean tracking) {
        SharpConfig c = new SharpConfig();
        c.requireLineOfSight = false;
        c.fovDegrees = 360;
        c.range = 40;
        c.cameraTrackingEnabled = tracking;
        SharpController local = new SharpController(() -> c, s -> { });
        Scenario s = new Scenario();
        s.candidates.add(dummyAtYaw(1, 20, 10));
        s.candidates.add(dummyAtYaw(2, -35, 8));
        List<Integer> trace = new ArrayList<>();
        s.key = false;
        for (int i = 0; i < 90; i++) {
            s.key = i > 5;
            if (i == 50) {
                s.candidates.removeIf(x -> x.id() == 1);
            }
            s.now += (long) (FRAME * 1e9);
            ActivationTracker.Input in = new ActivationTracker.Input(
                    s.key, true, false, true, true, true, s.world, s.player);
            FrameResult r = local.frame(new FrameInput(in, s.now, "OPEN", () ->
                    new WorldView(s.tick, 0, s.eye, s.yaw, s.pitch, List.copyOf(s.candidates), (a, b) -> true, s.blockers(), box -> false)));
            trace.add(r.selection().targetId());
        }
        return trace;
    }

    // ---- hitbox scene ---------------------------------------------------------------------------

    @Test
    void hitboxCanBeSwitchedOff() {
        config.hitboxEnabled = false;
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        FrameResult r = w.frame();
        assertTrue(controller.hasTarget());
        assertEquals(HitboxScene.EMPTY, r.scene());
    }

    @Test
    void targetsBeyondTheRenderDistanceAreSelectedButNotDrawn() {
        config.hitboxRenderDistance = 5;
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        FrameResult r = w.frame();
        assertTrue(controller.hasTarget());
        assertEquals(HitboxScene.EMPTY, r.scene());

        w.candidates.clear();
        w.candidates.add(dummy(2, 0, 4));
        w.pressFresh();
        assertNotNull(w.frame().scene().main());
    }

    @Test
    void theOtherEntitiesInRangeAreOutlinedNearestFirstCappedAndNeverIncludeTheTarget() {
        for (int i = 1; i <= 8; i++) {
            w.candidates.add(dummyAtYaw(i, i * 8, 10 + i));
        }
        w.frame();
        w.key = true;
        config.hitboxMaxOthers = 3;
        FrameResult r = w.frame();

        assertEquals(1, r.selection().targetId(), "closest to the crosshair");
        List<HitboxScene.Entry> others = r.scene().others();
        assertEquals(3, others.size());
        Box targetBox = r.scene().main().box();
        assertTrue(others.stream().noneMatch(e -> e.box().equals(targetBox)));
        V3 eye = w.eye;
        for (int i = 1; i < others.size(); i++) {
            assertTrue(others.get(i - 1).box().distanceTo(eye) <= others.get(i).box().distanceTo(eye), "nearest first");
        }
        assertEquals(7, r.debug().othersInRange(), "the count is not capped by what is drawn");
    }

    @Test
    void theOthersAreSortedByDistanceWhateverOrderTheGameListsThem() {
        w.candidates.add(dummyAtYaw(1, 0, 10));
        // Listed far to near, so only real sorting puts the near one first.
        w.candidates.add(dummyAtYaw(2, 30, 30));
        w.candidates.add(dummyAtYaw(3, -30, 20));
        w.candidates.add(dummyAtYaw(4, 50, 12));
        w.frame();
        w.key = true;
        List<HitboxScene.Entry> others = w.frame().scene().others();
        assertEquals(3, others.size());
        double[] distances = others.stream().mapToDouble(e -> e.box().distanceTo(w.eye)).toArray();
        assertTrue(distances[0] < distances[1] && distances[1] < distances[2], java.util.Arrays.toString(distances));
    }

    @Test
    void othersBeyondTheRenderDistanceAreCountedButNotDrawn() {
        config.hitboxRenderDistance = 15;
        w.candidates.add(dummyAtYaw(1, 0, 10));
        w.candidates.add(dummyAtYaw(2, 30, 12));
        w.candidates.add(dummyAtYaw(3, -30, 30));
        w.frame();
        w.key = true;
        FrameResult r = w.frame();
        assertEquals(1, r.scene().others().size(), "only the one inside 15 blocks is drawn");
        assertEquals(2, r.debug().othersInRange(), "but the text still counts both");
    }

    @Test
    void othersBeyondTheLockRangeAreNotOutlinedOrCounted() {
        config.range = 20;
        w.candidates.add(dummyAtYaw(1, 0, 10));
        w.candidates.add(dummyAtYaw(2, 30, 15));
        w.candidates.add(dummyAtYaw(3, -30, 21.5));
        w.frame();
        w.key = true;
        FrameResult r = w.frame();
        assertEquals(1, r.scene().others().size());
        assertEquals(1, r.debug().othersInRange());
    }

    @Test
    void showOthersOffKeepsTheCountButDrawsOnlyTheTarget() {
        config.hitboxShowOthers = false;
        w.candidates.add(dummyAtYaw(1, 0, 10));
        w.candidates.add(dummyAtYaw(2, 30, 12));
        w.frame();
        w.key = true;
        FrameResult r = w.frame();
        assertTrue(r.scene().others().isEmpty());
        assertEquals(1, r.debug().othersInRange(), "the on-screen text still says how many are around");
    }

    @Test
    void whatTheHeldWeaponReachesIsMarkedOnTheTargetAndTheOthers() {
        w.candidates.add(dummyAtYaw(1, 0, 3));
        w.candidates.add(dummyAtYaw(2, 30, 3.5));
        w.candidates.add(dummyAtYaw(3, -30, 12));
        w.reach = box -> box.distanceTo(new V3(0, 1.62, 0)) <= 4.0;
        w.frame();
        w.key = true;
        FrameResult r = w.frame();

        assertTrue(r.scene().main().attackable());
        assertTrue(r.debug().inReach());
        assertEquals(2, r.debug().othersInRange());
        assertEquals(1, r.debug().othersInReach());
        assertEquals(List.of(true, false), r.scene().others().stream().map(HitboxScene.Entry::attackable).toList());
    }

    @Test
    void aTargetOutOfReachIsNotMarkedAttackable() {
        w.candidates.add(dummyAtYaw(1, 0, 10));
        w.frame();
        w.key = true;
        FrameResult r = w.frame();
        assertFalse(r.scene().main().attackable());
        assertFalse(r.debug().inReach());
    }

    @Test
    void reachIsJudgedOnTheBoxThatIsDrawnNotOnAStaleOne() {
        w.candidates.add(dummy(1, 0, 10));
        w.reach = box -> box.center().z() < 6.0;
        w.frame();
        w.key = true;
        w.tick = 100;
        assertFalse(w.frame().scene().main().attackable());

        w.tick = 101;
        w.candidates.clear();
        w.candidates.add(dummy(1, 0, 4));
        w.partial = 1.0;
        assertTrue(w.frame().scene().main().attackable());
    }

    @Test
    void theSnapshotKnowsHowLongTheKeyHasBeenDown() {
        w.candidates.add(dummy(1, 0, 10));
        assertEquals(0.0, w.frame().debug().keyHeldSeconds(), 0.0, "key up");
        w.key = true;
        FrameResult first = w.frame();
        assertEquals(0.0, first.debug().keyHeldSeconds(), 1.0e-9, "the frame it went down");
        FrameResult later = w.frames(120);
        assertEquals(2.0, later.debug().keyHeldSeconds(), 0.05);
        w.key = false;
        assertEquals(0.0, w.frame().debug().keyHeldSeconds(), 0.0, "released");
        w.key = true;
        assertEquals(0.0, w.frame().debug().keyHeldSeconds(), 1.0e-9, "a new press starts the clock again");
    }

    @Test
    void theClockAlsoRunsWhenTheFeatureCannotStart() {
        w.gate = false;
        w.frame();
        w.key = true;
        w.frames(90);
        assertEquals(1.5, w.frame().debug().keyHeldSeconds(), 0.05, "so the closed-gate message can quieten down too");
    }

    // ---- where it aims ----------------------------------------------------------------------------

    @Test
    void itAimsAtTheFeetByDefaultWhichIsBelowTheMiddle() {
        w.reach = box -> true; // in reach, where the normal spot is used
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        FrameResult r = w.frame();
        Box box = r.scene().main().box();
        assertTrue(r.scene().aimPoint().y() < box.center().y() - 0.4, "aim y " + r.scene().aimPoint().y());
        assertTrue(r.scene().aimPoint().y() > box.minY(), "but not on the very edge");
        assertEquals(Exposure.OPEN, r.scene().exposure());
        assertEquals(AimPointPicker.Zone.FEET, r.debug().zone());
    }

    @Test
    void withFeetFirstOffItAimsAtTheLoweredCentre() {
        config.aimFeetFirst = false;
        w.reach = box -> true;
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        FrameResult r = w.frame();
        Box box = r.scene().main().box();
        assertEquals(box.minY() + box.height() * 0.3, r.scene().aimPoint().y(), 1.0e-9);
        assertEquals(AimPointPicker.Zone.LOW, r.debug().zone());
    }

    @Test
    void aTargetWhoseBoxShrinksStaysLockedAndAimedInsideTheNewBox() {
        // Gliding with an elytra: the same feet, a box a third as tall.
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        w.frame();
        w.tick = 101;
        w.candidates.clear();
        w.candidates.add(new TargetCandidate(1, "sharp 1", new Box(-0.3, 0.7, 9.7, 0.3, 1.3, 10.3)));
        w.partial = 1.0;
        FrameResult r = w.frame();

        assertEquals(1, r.selection().targetId());
        assertEquals(Selection.Kind.KEPT, r.selection().kind(), "no switch, no loss");
        assertTrue(r.scene().main().box().contains(r.scene().aimPoint()), "aim " + r.scene().aimPoint());
    }

    @Test
    void aShieldUpMovesTheAimToTheBackAndSaysWhetherItBlocksYou() {
        // The target at z = 10 faces the player (yaw 0 looks toward +z, so it faces away; yaw 180 looks toward -z, at the player).
        w.candidates.add(new TargetCandidate(1, "sharp 1", new Box(-0.3, 0.7, 9.7, 0.3, 2.5, 10.3), 180.0, true));
        w.frame();
        w.key = true;
        FrameResult r = w.frame();

        assertEquals(Exposure.SHIELDED, r.scene().exposure());
        assertTrue(r.debug().shieldBlocksYou(), "the player stands in front of it");
        assertTrue(r.scene().aimPoint().z() > 10.0, "the back is the far side from the player: " + r.scene().aimPoint());

        w.candidates.clear();
        w.candidates.add(new TargetCandidate(1, "sharp 1", new Box(-0.3, 0.7, 9.7, 0.3, 2.5, 10.3), 0.0, true));
        r = w.frame();
        assertEquals(Exposure.SHIELDED, r.scene().exposure());
        assertFalse(r.debug().shieldBlocksYou(), "it faces away, so the player is already behind it");
        assertTrue(r.scene().aimPoint().z() < 10.0, "the back is now the near side: " + r.scene().aimPoint());
    }

    @Test
    void withTheShieldOptionOffItIgnoresTheShield() {
        config.aimShieldRear = false;
        w.candidates.add(new TargetCandidate(1, "sharp 1", new Box(-0.3, 0.7, 9.7, 0.3, 2.5, 10.3), 180.0, true));
        w.frame();
        w.key = true;
        FrameResult r = w.frame();
        assertEquals(Exposure.OPEN, r.scene().exposure());
        assertEquals(10.0, r.scene().aimPoint().z(), 1.0e-9);
    }

    @Test
    void standingInsideTheTargetsBoxShowsItAndHoldsTheCamera() {
        config.cameraTrackingEnabled = true;
        config.fovDegrees = 140; // the default: the point below the eye is far outside it, but standing inside still locks
        w.candidates.add(new TargetCandidate(1, "sharp 1", new Box(-0.5, 0.0, -0.5, 0.5, 2.0, 0.5)));
        w.frame();
        w.key = true;
        FrameResult r = w.frames(10);

        assertEquals(1, r.selection().targetId());
        assertEquals(Exposure.INSIDE, r.scene().exposure());
        assertNull(r.aim(), "from inside there is no sensible point to turn toward");
        assertEquals(0.0, w.yaw, 0.0);
    }

    /** The player at the origin looks level; the real target is straight ahead, a wide plank hangs in front of it, off to one side of the crosshair. */
    private void plankInFrontOfTheTarget(double plankMinX, double plankMaxX) {
        config.priorityTiers = false; // these tests are about cover: the target is the one on the crosshair, not the nearer plank
        w.candidates.add(new TargetCandidate(1, "sharp 1", new Box(-0.3, 0.0, 11.7, 0.3, 1.8, 12.3)));
        w.candidates.add(new TargetCandidate(2, "sharp 2", new Box(plankMinX, 0.0, 5.0, plankMaxX, 3.0, 6.0)));
        w.pitch = 5.0;
    }

    @Test
    void aTargetWhollyBehindAnotherHitboxIsShownCoveredAndStillFollowed() {
        config.cameraTrackingEnabled = true;
        plankInFrontOfTheTarget(-0.3, 4.3);
        w.frame();
        w.key = true;
        FrameResult r = w.frame();

        assertEquals(1, r.selection().targetId(), "the plank is far from the crosshair, the target is on it");
        assertEquals(Exposure.COVERED, r.scene().exposure());
        assertEquals(Exposure.COVERED, r.debug().exposure());
        assertNotNull(r.aim(), "a covered target is still followed, so the aim is ready the moment the cover moves");
    }

    @Test
    void aTargetThatSticksOutIsAimedAtWhereItSticksOut() {
        plankInFrontOfTheTarget(-0.3, 0.06);
        w.frame();
        w.key = true;
        FrameResult r = w.frame();

        assertEquals(1, r.selection().targetId());
        assertEquals(Exposure.PARTLY_COVERED, r.scene().exposure());
        assertTrue(r.scene().aimPoint().x() > 0.1, "moved to the exposed side: " + r.scene().aimPoint());
        assertTrue(r.scene().main().box().contains(r.scene().aimPoint()));
    }

    @Test
    void anEntityThatIsNotAPracticeEntityStillCoversTheTarget() {
        // A player or a plain mob standing in front: not lockable, but the hit would land on it.
        w.candidates.add(new TargetCandidate(1, "sharp 1", new Box(-0.3, 0.0, 11.7, 0.3, 1.8, 12.3)));
        w.extraBlockers.add(new TargetCandidate(99, "", new Box(-1.0, 0.0, 5.0, 1.0, 3.0, 6.0)));
        w.pitch = 5.0;
        w.frame();
        w.key = true;
        FrameResult r = w.frame();
        assertEquals(1, r.selection().targetId());
        assertEquals(Exposure.COVERED, r.scene().exposure());
        assertEquals(0, r.debug().othersInRange(), "but it is not counted as a practice entity in range");
    }

    @Test
    void withAvoidCoverOffTheCoverIsIgnored() {
        config.aimAvoidCover = false;
        plankInFrontOfTheTarget(-0.3, 4.3);
        w.frame();
        w.key = true;
        assertEquals(Exposure.OPEN, w.frame().scene().exposure());
    }

    @Test
    void sceneBoxIsInterpolatedBetweenTicksForSmoothMotion() {
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        w.tick = 100;
        w.frame();

        // The target moves 2 blocks per tick. Frames inside tick 101 must glide, not jump.
        w.tick = 101;
        w.candidates.clear();
        w.candidates.add(dummy(1, 2, 10));
        double previousX = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 4; i++) {
            w.partial = i / 4.0;
            double x = w.frame().scene().main().box().center().x();
            assertEquals(2.0 * w.partial, x, 1.0e-9);
            assertTrue(x >= previousX);
            previousX = x;
        }
    }

    // ---- debug and logging ----------------------------------------------------------------------

    @Test
    void debugSnapshotDescribesTheCurrentState() {
        config.cameraTrackingEnabled = true;
        w.candidates.add(dummyAtYaw(1, 25, 9));
        FrameResult off = w.frame();
        assertFalse(off.debug().active());
        assertEquals("OPEN", off.debug().gate());

        w.key = true;
        w.frames(5);
        FrameResult on = w.frame();
        assertTrue(on.debug().active());
        assertTrue(on.debug().keyDown());
        assertEquals("sharp 1", on.debug().targetLabel());
        assertTrue(on.debug().angularError() > 1.0);
        assertTrue(on.debug().activeSeconds() > 0.05);
        assertNotNull(on.debug().aim());
        assertEquals(1, on.debug().selection().targetId());
    }

    @Test
    void eventsAreLoggedOnlyWhenDebugLoggingIsOn() {
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        w.frames(3);
        w.key = false;
        w.frame();
        assertTrue(log.isEmpty(), "silent by default: " + log);

        config.debugLogging = true;
        w.pressFresh();
        w.candidates.clear();
        w.frame();
        w.key = false;
        w.frame();
        String all = String.join("\n", log);
        assertTrue(all.contains("activated"), all);
        assertTrue(all.contains("target acquired #1"), all);
        assertTrue(all.contains("target lost"), all);
        assertTrue(all.contains("deactivated (KEY_RELEASED)"), all);
    }

    @Test
    void worksWithANegativeNanoTimeOrigin() {
        // System.nanoTime() has an arbitrary origin and may be negative.
        config.cameraTrackingEnabled = true;
        w.now = Long.MIN_VALUE / 2;
        w.candidates.add(dummyAtYaw(1, 30, 10));
        w.frame();
        w.key = true;
        for (int i = 0; i < 60 * 5; i++) {
            w.frame();
        }
        assertEquals(30.0, w.yaw, 0.5);
    }

    // ---- aim height: out of reach, in reach, falling ----------------------------------------------

    private static double fractionOf(FrameResult r) {
        Box box = r.scene().main().box();
        return (r.scene().aimPoint().y() - box.minY()) / box.height();
    }

    @Test
    void outOfReachItAimsAtTheMiddleAndDropsToTheFeetTheMomentItIsInReach() {
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        FrameResult far = w.frames(20);
        assertEquals(AimPointPicker.Zone.MIDDLE, far.debug().zone());
        assertEquals(0.5, fractionOf(far), 1.0e-9);

        w.reach = box -> true;
        double previous = fractionOf(far);
        int frames = 0;
        FrameResult r = far;
        while (frames < 30 && fractionOf(r) > 0.13) {
            r = w.frame();
            double f = fractionOf(r);
            assertTrue(f <= previous + 1.0e-9, "only ever moves down: " + previous + " -> " + f);
            assertTrue(previous - f <= 2.5 / 60 + 1.0e-9, "no jump: " + previous + " -> " + f);
            previous = f;
            frames++;
        }
        assertEquals(AimPointPicker.Zone.FEET, r.debug().zone());
        assertTrue(frames <= 12, "feet within about a tenth of a second: " + frames + " frames");
    }

    @Test
    void withAdaptiveAimOffItAlwaysAimsAtTheNormalSpot() {
        config.aimAdaptive = false;
        w.candidates.add(dummy(1, 0, 10));
        w.frame();
        w.key = true;
        assertEquals(AimPointPicker.Zone.FEET, w.frames(20).debug().zone(), "out of reach, but the usual feet-first order");
    }

    /** One 60 fps frame of a target falling 0.4 blocks per 20 Hz tick, from {@code startY}; {@code i} is the frame number. */
    private FrameResult fallingFrame(double startY, int i) {
        w.tick = 100 + i / 3;
        w.partial = (i % 3) / 3.0;
        double y = startY - 0.4 * (i / 3); // the box only moves on a tick, as in the game
        w.candidates.clear();
        w.candidates.add(new TargetCandidate(1, "sharp 1", new Box(-0.3, y, 9.7, 0.3, y + 1.8, 10.3)));
        return w.frame();
    }

    @Test
    void aFallingTargetIsAimedAtInTheMiddleThenTheSpotSlidesDownAsItComesLevelWithThePlayer() {
        w.reach = box -> true;
        w.candidates.add(new TargetCandidate(1, "sharp 1", new Box(-0.3, 12, 9.7, 0.3, 13.8, 10.3)));
        w.frame();
        w.key = true;

        double previous = Double.NaN;
        double peak = 0;
        boolean peaked = false;
        FrameResult r = null;
        for (int i = 0; i < 100; i++) { // 12 blocks at 0.4 per tick is 90 frames; the last ten are below the player's feet
            r = fallingFrame(12, i);
            double f = fractionOf(r);
            if (!Double.isNaN(previous)) {
                assertTrue(Math.abs(f - previous) <= 2.5 / 60 + 1.0e-9, "no jump at frame " + i + ": " + previous + " -> " + f);
            }
            peak = Math.max(peak, f);
            if (peaked) {
                assertTrue(f <= previous + 1.0e-9, "once in the middle it only slides down, frame " + i + ": " + previous + " -> " + f);
            }
            if (f >= 0.5 - 1.0e-9) {
                peaked = true;
            }
            previous = f;
        }
        assertEquals(0.5, peak, 0.01, "while it was high above, the middle");
        assertEquals(AimPointPicker.Zone.FEET, r.debug().zone(), "level with the player it is the normal spot again");
    }

    @Test
    void aFallingTargetThatIsOutOfReachKeepsTheMiddleAndOnceInReachGoesToTheNormalSpot() {
        w.candidates.add(new TargetCandidate(1, "sharp 1", new Box(-0.3, 12, 9.7, 0.3, 13.8, 10.3)));
        w.frame();
        w.key = true;
        FrameResult r = null;
        for (int i = 0; i < 120; i++) {
            r = fallingFrame(12, i);
        }
        assertEquals(0.5, fractionOf(r), 1.0e-9, "still out of reach, so still the middle");

        w.reach = box -> true;
        for (int i = 120; i < 150; i++) {
            r = fallingFrame(12, i);
        }
        assertTrue(fractionOf(r) < 0.2, "in reach and level with the player: " + fractionOf(r));
    }
}
