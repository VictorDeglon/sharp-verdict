package dev.sharp.client;

import dev.sharp.config.SharpConfig;
import dev.sharp.controller.SharpController;
import dev.sharp.controller.SharpController.FrameInput;
import dev.sharp.controller.SharpController.FrameResult;
import dev.sharp.controller.SharpController.WorldView;
import dev.sharp.core.activation.ActivationTracker;
import dev.sharp.core.debug.DebugSnapshot;
import dev.sharp.core.hud.CueBuilder;
import dev.sharp.core.hud.Cues;
import dev.sharp.core.render.HitboxScene;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/**
 * Feeds the game's state into the {@link SharpController} and passes its decisions on: the camera step to
 * {@link CameraController}, the scene to {@link HitboxRenderer}, the snapshot to {@link DebugOverlay}.
 *
 * <p>Two hooks keep the feature honest. {@link #onFrame} runs once per rendered frame and does the real
 * work. {@link #onTick} runs once per tick even where no world is rendered and only checks whether the
 * feature must switch off. Connection and world events call hard resets on top.
 *
 * <p>A bug here must never take the game down. Any unexpected exception switches the feature off and
 * logs it once; it stays off until the next world join.
 */
final class SharpRuntime {
    /** A scene older than this is not drawn, so a stalled frame loop can never leave an outline behind. */
    private static final long SCENE_MAX_AGE_NANOS = 250_000_000L;

    private record Sample(ActivationTracker.Input input, String gateText) {
    }

    private final ConfigManager configs;
    private final InputHandler input;
    private final CameraController camera;
    private final Logger logger;
    private final SharpController controller;

    private FrameResult latest;
    private long latestNanos;
    private boolean faulted;

    // The first warning shown during the current key press.
    private boolean wasDown;
    private String lastHint = "";

    SharpRuntime(ConfigManager configs, InputHandler input, CameraController camera, Logger logger) {
        this.configs = configs;
        this.input = input;
        this.camera = camera;
        this.logger = logger;
        this.controller = new SharpController(configs::get, message -> logger.info("[SHARP] {}", message));
    }

    // ---- hooks -------------------------------------------------------------------------------------

    void onFrame(LevelExtractionContext context) {
        if (faulted) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            double partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(false);
            Sample sample = sample(mc);
            long now = System.nanoTime();

            FrameResult result = controller.frame(
                    new FrameInput(sample.input(), now, sample.gateText(), () -> worldView(mc, partialTick)));
            latest = result;
            latestNanos = now;

            // Selection and hitbox visualization can run on a multiplayer client using the existing
            // practice-marker filter. Automated camera movement remains limited to a local practice world.
            if (GameAccess.practiceGateClosedReason(mc) == null) {
                camera.apply(mc, result.aim());
            }
            hints(sample, cues());
        } catch (RuntimeException e) {
            fault(e);
        }
    }

    void onTick(Minecraft mc) {
        if (faulted) {
            return;
        }
        try {
            handleToggles(mc);
            boolean wasActive = controller.isActive();
            controller.poll(sample(mc).input(), System.nanoTime());
            if (wasActive && !controller.isActive()) {
                // Switched off between two frames: drop the last scene right away instead of waiting
                // for the next frame to replace it.
                latest = null;
            }
        } catch (RuntimeException e) {
            fault(e);
        }
    }

    void onJoin() {
        faulted = false;
        configs.reload();
        resetAll(ActivationTracker.Reason.FORCED);
    }

    void onDisconnect() {
        resetAll(ActivationTracker.Reason.DISCONNECTED);
    }

    void onLevelChange() {
        resetAll(ActivationTracker.Reason.WORLD_CHANGED);
    }

    // ---- outputs for the renderer and overlay ------------------------------------------------------

    /** What to draw this frame. Empty when the feature is off or the data is stale. */
    HitboxScene scene() {
        FrameResult r = latest;
        if (r == null || System.nanoTime() - latestNanos > SCENE_MAX_AGE_NANOS) {
            return HitboxScene.EMPTY;
        }
        return r.scene();
    }

    /** The last warning the player was shown while the key was held, for the game test. Empty when the key was not held or all was well. */
    String lastHint() {
        return lastHint;
    }

    /** The coloured text to draw above the hotbar right now. Empty when the key is not held or the data is stale. */
    Cues cues() {
        FrameResult r = latest;
        if (r == null || System.nanoTime() - latestNanos > SCENE_MAX_AGE_NANOS) {
            return Cues.NONE;
        }
        SharpConfig c = configs.get();
        return CueBuilder.build(r.debug(), c.hitbox(), c.practiceMarker, c.range, c.cueStyle(), c.statusFullSeconds);
    }

    /**
     * Holding the key must never fail silently: the text above the hotbar says why nothing happens, for as long as the key is held.
     * This only remembers the first such message of each press, so the game test can read it.
     */
    private void hints(Sample sample, Cues cues) {
        if (!sample.input().keyDown()) {
            wasDown = false;
            lastHint = "";
            return;
        }
        if (!wasDown) {
            wasDown = true;
            lastHint = "";
        }
        if (lastHint.isEmpty() && cues.warning()) {
            lastHint = cues.warningText();
        }
    }

    /** True when something here threw and the feature switched itself off for the session. */
    boolean isFaulted() {
        return faulted;
    }

    /** Latest debug data, or null before the first frame. */
    DebugSnapshot debugSnapshot() {
        FrameResult r = latest;
        return r == null ? null : r.debug();
    }

    // ---- internals ---------------------------------------------------------------------------------

    private void resetAll(ActivationTracker.Reason reason) {
        controller.reset(reason);
        latest = null;
    }

    private void fault(RuntimeException e) {
        faulted = true;
        logger.error("SHARP hit an unexpected error and has switched itself off until the next world join.", e);
        try {
            resetAll(ActivationTracker.Reason.FORCED);
        } catch (RuntimeException ignored) {
            // Already failing; nothing more to do than make sure we stop.
        }
    }

    private Sample sample(Minecraft mc) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        boolean connected = player != null && level != null && mc.getConnection() != null;
        SharpConfig config = configs.get();

        String closedReason;
        if (!connected) {
            closedReason = "not connected";
        } else if (!config.enabled) {
            closedReason = "disabled in config";
        } else {
            // Multiplayer supports selection/visualization of explicitly marked practice entities;
            // the camera itself has a separate single-player-only gate in onFrame().
            closedReason = null;
        }
        boolean gateOpen = closedReason == null;

        ActivationTracker.Input activation = new ActivationTracker.Input(
                input.holdDown(),
                GameAccess.windowActive(mc),
                mc.gui.screen() != null,
                connected && GameAccess.playerAlive(player),
                connected,
                gateOpen,
                level,
                player);
        String gateText = !gateOpen ? "CLOSED: " + closedReason
                : GameAccess.practiceGateClosedReason(mc) == null
                    ? "OPEN (single-player practice)"
                    : "OPEN (multiplayer practice selection; camera tracking disabled)";
        return new Sample(activation, gateText);
    }

    private WorldView worldView(Minecraft mc, double partialTick) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return null;
        }
        return new WorldView(
                level.getGameTime(),
                partialTick,
                GameAccess.eyePosition(player),
                player.getYRot(),
                player.getXRot(),
                GameAccess.candidates(level, player, configs.get()),
                GameAccess.lineOfSight(level, player),
                GameAccess.blockers(level, player, configs.get()),
                GameAccess.attackRange(player));
    }

    private void handleToggles(Minecraft mc) {
        // Always drain the queued presses, even if we ignore them, so they cannot pile up.
        boolean toggleTracking = input.consumeTrackingToggle();
        boolean toggleDebug = input.consumeDebugToggle();
        boolean cycleText = input.consumeTextCycle();
        if (mc.player == null) {
            return;
        }
        SharpConfig config = configs.get();
        if (toggleTracking) {
            config.cameraTrackingEnabled = !config.cameraTrackingEnabled;
            configs.save();
            announce(mc, "SHARP camera tracking: " + (config.cameraTrackingEnabled ? "ON" : "OFF"));
        }
        if (cycleText) {
            config.statusStyle = config.cueStyle().next().id();
            configs.save();
            announce(mc, "SHARP text: " + config.statusStyle);
        }
        if (toggleDebug) {
            config.debugOverlay = !config.debugOverlay;
            configs.save();
            announce(mc, "SHARP debug panel: " + (config.debugOverlay ? "ON" : "OFF"));
        }
    }

    private static void announce(Minecraft mc, String text) {
        mc.gui.hud.setOverlayMessage(Component.literal(text), false); // VERIFIED (NetworkingPlayPacketClientTest)
    }
}
