package dev.sharp.controller;

import dev.sharp.config.SharpConfig;
import dev.sharp.core.activation.ActivationTracker;
import dev.sharp.core.camera.AimController;
import dev.sharp.core.camera.AimStep;
import dev.sharp.core.debug.DebugSnapshot;
import dev.sharp.core.math.AimMath;
import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import dev.sharp.core.render.BoxTrail;
import dev.sharp.core.render.HitboxScene;
import dev.sharp.core.render.HitboxSettings;
import dev.sharp.core.targeting.AimHeightPlanner;
import dev.sharp.core.targeting.AimPointPicker;
import dev.sharp.core.targeting.EyeState;
import dev.sharp.core.targeting.Exposure;
import dev.sharp.core.targeting.LineOfSight;
import dev.sharp.core.targeting.Selection;
import dev.sharp.core.targeting.TargetCandidate;
import dev.sharp.core.targeting.TargetLock;
import dev.sharp.core.targeting.TargetingSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Runs one frame of the feature: activation, target selection, the optional camera step, the scene for
 * the hitbox renderer and the debug snapshot. Everything Minecraft-specific is handed in through
 * {@link FrameInput}, and everything it decides is handed back through {@link FrameResult}, so the whole
 * behaviour, including key release, target loss and world transitions, is testable without the game.
 *
 * <p>The three features are separate objects with their own state: {@link ActivationTracker},
 * {@link TargetLock} and {@link AimController}. This class only sequences them. Camera tracking is
 * optional and can be switched off in the config while selection and drawing still run.
 *
 * <p>All target state is cleared the instant the feature deactivates, whatever the reason.
 */
public final class SharpController {

    /**
     * What the world looks like right now. Only built while the feature is active.
     *
     * @param blockers    every entity that could intercept an attack on the way to the target, practice entity or not, so a hit
     *                    that would land on somebody else is noticed. Includes the candidates; the target itself is skipped by id
     * @param attackRange whether the weapon the player holds could hit an entity with this box from where the player stands
     */
    public record WorldView(
            long gameTick,
            double partialTick,
            V3 eyeAtTick,
            double yawDegrees,
            double pitchDegrees,
            List<TargetCandidate> candidates,
            LineOfSight lineOfSight,
            List<TargetCandidate> blockers,
            Predicate<Box> attackRange) {
    }

    /**
     * @param activation activation inputs, read every frame
     * @param nowNanos   a monotonic clock reading
     * @param gate       short text describing the practice gate, for the debug panel
     * @param world      builds the {@link WorldView}; called only when the feature is active
     */
    public record FrameInput(
            ActivationTracker.Input activation,
            long nowNanos,
            String gate,
            Supplier<WorldView> world) {
    }

    /**
     * @param active    whether the feature is on
     * @param selection result of this frame's selection
     * @param scene     what to draw, {@link HitboxScene#EMPTY} when nothing
     * @param aim       the rotation to apply to the camera this frame, null when the camera should not move
     * @param debug     snapshot for the overlay
     */
    public record FrameResult(
            boolean active,
            Selection selection,
            HitboxScene scene,
            AimStep aim,
            DebugSnapshot debug) {
    }

    private final Supplier<SharpConfig> configSource;
    private final Consumer<String> log;
    private final ActivationTracker tracker;
    private final TargetLock lock = new TargetLock();
    private final AimController aimController = new AimController();
    private final AimHeightPlanner heightPlanner = new AimHeightPlanner();
    private final BoxTrail targetTrail = new BoxTrail();
    private final BoxTrail eyeTrail = new BoxTrail();

    private int trailTargetId = Selection.NO_TARGET;
    private boolean hasLastFrame;
    private long lastFrameNanos;
    private long activeSinceNanos;
    private long frameNanos;
    private boolean keyDown;
    private long keyDownSinceNanos;
    private String targetLabel = "";
    private double lastAngularError;
    private boolean mainInReach;
    private Exposure exposure = Exposure.OPEN;
    private AimPointPicker.Zone zone = AimPointPicker.Zone.MIDDLE;
    private boolean shieldBlocksYou;
    private int othersInRange;
    private int othersInReach;
    private AimStep lastAim;

    /**
     * @param configSource read afresh every frame, so settings changes and config reloads take effect
     *                     immediately without rebuilding the controller
     * @param log          receives event messages when {@code debugLogging} is on
     */
    public SharpController(Supplier<SharpConfig> configSource, Consumer<String> log) {
        this.configSource = configSource;
        this.log = log;
        this.tracker = ActivationTracker.withCallbacks(this::onActivated, this::onDeactivated);
    }

    // ---- inputs from outside the frame loop ---------------------------------------------------------

    /**
     * Activation check without selection or camera work, for the once-per-tick safety poll. It makes sure
     * the feature switches off promptly even on ticks or screens where no world frame is rendered.
     */
    public boolean poll(ActivationTracker.Input input, long nowNanos) {
        frameNanos = nowNanos;
        noteKey(input.keyDown(), nowNanos);
        return tracker.update(input);
    }

    /**
     * Immediate shutdown from an event such as a disconnect or world change. Safe to call at any time;
     * clears every piece of target state even when the feature was not active.
     */
    public void reset(ActivationTracker.Reason reason) {
        tracker.forceDeactivate(reason);
        clearTargetState();
    }

    // ---- the frame ----------------------------------------------------------------------------------

    public FrameResult frame(FrameInput in) {
        frameNanos = in.nowNanos();
        noteKey(in.activation().keyDown(), in.nowNanos());

        if (!tracker.update(in.activation())) {
            return inactive(in.gate());
        }
        WorldView view = in.world().get();
        if (view == null) {
            return new FrameResult(true, Selection.empty(), HitboxScene.EMPTY, null,
                    snapshot(configSource.get(), in.gate(), Selection.empty(), null));
        }

        double dt = hasLastFrame ? (in.nowNanos() - lastFrameNanos) / 1.0e9 : 0.0;
        hasLastFrame = true;
        lastFrameNanos = in.nowNanos();

        eyeTrail.observe(view.gameTick(), Box.point(view.eyeAtTick()));
        V3 eye = eyeTrail.at(view.partialTick()).center();
        double yaw = view.yawDegrees();
        double pitch = view.pitchDegrees();

        SharpConfig config = configSource.get();
        TargetingSettings targeting = config.targeting();
        Selection selection = lock.update(
                new EyeState(eye, yaw, pitch), view.candidates(), targeting, view.lineOfSight(), in.nowNanos());
        logSelectionChange(config, selection, view);

        Box targetBox = null;
        V3 aimPoint = null;
        AimStep aimStep = null;
        if (selection.hasTarget()) {
            TargetCandidate candidate = find(view.candidates(), selection.targetId());
            if (trailTargetId != selection.targetId()) {
                targetTrail.reset();
                heightPlanner.reset();
                trailTargetId = selection.targetId();
            }
            targetLabel = candidate.label();
            targetTrail.observe(view.gameTick(), candidate.box());
            targetBox = targetTrail.at(view.partialTick());
            mainInReach = view.attackRange().test(targetBox);
            double preferredHeight = config.aimAdaptive
                    ? heightPlanner.plan(targetBox, eye.y(), mainInReach, dt, targeting)
                    : Double.NaN;
            AimPointPicker.Choice choice = AimPointPicker.pick(
                    eye, candidate.withBox(targetBox), view.blockers(), targeting, view.lineOfSight(), preferredHeight);
            aimPoint = choice.point();
            exposure = choice.exposure();
            zone = choice.zone();
            shieldBlocksYou = choice.shieldBlocksYou();
            lastAngularError = AimMath.angularErrorDegrees(eye, yaw, pitch, aimPoint);

            // From inside the box the aim point is a hand's width away and every correction would be a violent swing.
            if (config.cameraTrackingEnabled && exposure != Exposure.INSIDE) {
                // The motion state survives a switch from one target to another, so the camera glides
                // over instead of stopping and restarting.
                aimStep = aimController.step(yaw, pitch, eye, aimPoint, dt, config.aim());
            } else {
                aimController.reset();
            }
        } else {
            targetTrail.reset();
            heightPlanner.reset();
            trailTargetId = Selection.NO_TARGET;
            targetLabel = "";
            lastAngularError = 0.0;
            clearAimInfo();
            // With nothing to follow the camera stops, and the next target fades in from rest.
            aimController.reset();
        }
        lastAim = aimStep;

        HitboxScene scene = buildScene(config.hitbox(), targeting.range(), eye, targetBox, aimPoint, aimStep != null, selection, view);
        return new FrameResult(true, selection, scene, aimStep, snapshot(config, in.gate(), selection, aimStep));
    }

    // ---- state --------------------------------------------------------------------------------------

    public boolean isActive() {
        return tracker.isActive();
    }

    public int switchCount() {
        return lock.switchCount();
    }

    public boolean hasTarget() {
        return lock.hasTarget();
    }

    public int targetId() {
        return lock.currentId();
    }

    public ActivationTracker.Reason lastDeactivationReason() {
        return tracker.lastDeactivationReason();
    }

    // ---- internals ----------------------------------------------------------------------------------

    private void onActivated() {
        clearTargetState();
        activeSinceNanos = frameNanos;
        logIfEnabled("activated");
    }

    private void onDeactivated(ActivationTracker.Reason reason) {
        clearTargetState();
        logIfEnabled("deactivated (" + reason + "), target state cleared");
    }

    private void clearTargetState() {
        lock.clear();
        aimController.reset();
        heightPlanner.reset();
        targetTrail.reset();
        eyeTrail.reset();
        trailTargetId = Selection.NO_TARGET;
        hasLastFrame = false;
        targetLabel = "";
        lastAngularError = 0.0;
        clearAimInfo();
        lastAim = null;
    }

    /** Remembers when the key went down, so the on-screen text can quieten down a few seconds into a hold. */
    private void noteKey(boolean down, long nowNanos) {
        if (down && !keyDown) {
            keyDownSinceNanos = nowNanos;
        }
        keyDown = down;
    }

    private void clearAimInfo() {
        mainInReach = false;
        exposure = Exposure.OPEN;
        zone = AimPointPicker.Zone.MIDDLE;
        shieldBlocksYou = false;
        othersInRange = 0;
        othersInReach = 0;
    }

    private FrameResult inactive(String gate) {
        return new FrameResult(false, Selection.empty(), HitboxScene.EMPTY, null,
                snapshot(configSource.get(), gate, Selection.empty(), null));
    }

    private DebugSnapshot snapshot(SharpConfig config, String gate, Selection selection, AimStep aim) {
        boolean active = tracker.isActive();
        return new DebugSnapshot(
                active,
                keyDown,
                tracker.isArmed(),
                tracker.lastDeactivationReason(),
                active ? (frameNanos - activeSinceNanos) / 1.0e9 : 0.0,
                gate,
                selection,
                targetLabel,
                lock.switchCount(),
                config.cameraTrackingEnabled,
                aim,
                lastAngularError,
                aimController.yawRate(),
                aimController.pitchRate(),
                mainInReach,
                exposure,
                zone,
                shieldBlocksYou,
                othersInRange,
                othersInReach,
                keyDown ? Math.max(0.0, (frameNanos - keyDownSinceNanos) / 1.0e9) : 0.0);
    }

    /**
     * The main target, and every other practice entity within the lock range (nearest first, as many as the settings allow), each
     * marked with whether the held weapon can reach it. Only what the player could already see is drawn: the outlines are depth
     * tested, so nothing shows through a block. The counts of the others feed the on-screen text and do not depend on what is drawn.
     */
    private HitboxScene buildScene(
            HitboxSettings hs, double range, V3 eye, Box targetBox, V3 aimPoint, boolean tracking, Selection sel, WorldView view) {
        List<TargetCandidate> near = new ArrayList<>();
        for (TargetCandidate c : view.candidates()) {
            if (c.id() != sel.targetId() && c.box().distanceTo(eye) <= range) {
                near.add(c);
            }
        }
        near.sort(Comparator.comparingDouble((TargetCandidate c) -> c.box().distanceTo(eye)).thenComparingInt(TargetCandidate::id));
        othersInRange = near.size();
        othersInReach = 0;
        List<Boolean> reach = new ArrayList<>(near.size());
        for (TargetCandidate c : near) {
            boolean r = view.attackRange().test(c.box());
            reach.add(r);
            if (r) {
                othersInReach++;
            }
        }

        if (!hs.enabled()) {
            return HitboxScene.EMPTY;
        }
        HitboxScene.Entry main = null;
        if (targetBox != null && targetBox.distanceTo(eye) <= hs.renderDistance()) {
            main = new HitboxScene.Entry(targetBox, mainInReach);
        }
        List<HitboxScene.Entry> others = new ArrayList<>();
        if (hs.showOthers()) {
            for (int i = 0; i < near.size() && others.size() < hs.maxOthers(); i++) {
                TargetCandidate c = near.get(i);
                if (c.box().distanceTo(eye) <= hs.renderDistance()) {
                    others.add(new HitboxScene.Entry(c.box(), reach.get(i)));
                }
            }
        }
        if (main == null && others.isEmpty()) {
            return HitboxScene.EMPTY;
        }
        return new HitboxScene(main, tracking, main == null ? null : aimPoint, exposure, List.copyOf(others));
    }

    private static TargetCandidate find(List<TargetCandidate> candidates, int id) {
        for (TargetCandidate c : candidates) {
            if (c.id() == id) {
                return c;
            }
        }
        throw new IllegalStateException("selected target " + id + " is not among the candidates");
    }

    private void logSelectionChange(SharpConfig config, Selection s, WorldView view) {
        if (!config.debugLogging) {
            return;
        }
        switch (s.kind()) {
            case ACQUIRED, SWITCHED -> log.accept(String.format(
                    Locale.ROOT,
                    "target %s #%d \"%s\" dist=%.1f err=%.2f",
                    s.kind().name().toLowerCase(Locale.ROOT),
                    s.targetId(),
                    find(view.candidates(), s.targetId()).label(),
                    s.distance(),
                    s.angularErrorDegrees()));
            case LOST -> log.accept("target lost");
            default -> {
                // KEPT and NONE are not events.
            }
        }
    }

    private void logIfEnabled(String message) {
        if (configSource.get().debugLogging) {
            log.accept(message);
        }
    }
}
