package dev.sharp.core.activation;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Decides when the hold-to-lock feature is active.
 *
 * <p>The rules, in plain terms:
 *
 * <ul>
 *   <li>It is active only while the key is physically held <em>and</em> nothing blocks it. Blockers are:
 *       no connection, a different world or player than at activation, a dead player, the feature
 *       being switched off, the window not having focus and a screen being open.
 *   <li>The moment any of those stops being true it deactivates, and {@link Listener#onDeactivated} fires
 *       exactly once with the reason.
 *   <li>Activation needs a <em>fresh press</em> while nothing is blocking ("armed"). The tracker only arms
 *       itself after it has seen the key up, and any block disarms it. So holding the key through a
 *       respawn, an alt-tab or a world change never silently re-activates it. The key has to be
 *       released and pressed again.
 * </ul>
 *
 * <p>Call {@link #update} every frame and every tick with fresh input. It does no timing and holds no
 * Minecraft types, so it is fully unit testable.
 */
public final class ActivationTracker {

    /** Why the feature is off. {@link #NONE} while active or before the first activation. */
    public enum Reason {
        NONE,
        KEY_RELEASED,
        FOCUS_LOST,
        SCREEN_OPENED,
        PLAYER_DIED,
        PLAYER_REPLACED,
        WORLD_CHANGED,
        DISCONNECTED,
        GATE_CLOSED,
        FORCED
    }

    /**
     * One snapshot of the world, taken by the caller each update.
     *
     * @param keyDown      whether the activation key is physically held right now
     * @param windowFocused whether the game window has input focus
     * @param screenOpen   whether any screen (inventory, pause, chat, ...) is open
     * @param playerAlive  whether the local player exists and is alive
     * @param connected    whether there is a player and a world at all
     * @param gateOpen     whether the feature is connected and enabled in the config
     * @param worldToken   identity of the current world; any change means a world transition
     * @param playerToken  identity of the current player entity; any change means a respawn or reconnect
     */
    public record Input(
            boolean keyDown,
            boolean windowFocused,
            boolean screenOpen,
            boolean playerAlive,
            boolean connected,
            boolean gateOpen,
            Object worldToken,
            Object playerToken) {

        /** An input where everything is fine except what the caller overrides afterwards. */
        public static Input idle(Object worldToken, Object playerToken) {
            return new Input(false, true, false, true, true, true, worldToken, playerToken);
        }
    }

    public interface Listener {
        void onActivated();

        void onDeactivated(Reason reason);
    }

    private final Listener listener;

    private boolean active;
    private boolean armed;
    private Reason lastReason = Reason.NONE;
    private Object lastWorld;
    private Object lastPlayer;
    private boolean sawConnected;

    public ActivationTracker(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.armed = false;
    }

    public static ActivationTracker withCallbacks(Runnable onActivated, Consumer<Reason> onDeactivated) {
        return new ActivationTracker(new Listener() {
            @Override
            public void onActivated() {
                onActivated.run();
            }

            @Override
            public void onDeactivated(Reason reason) {
                onDeactivated.accept(reason);
            }
        });
    }

    public boolean isActive() {
        return active;
    }

    /** True when the next press will activate. Exposed for the debug overlay. */
    public boolean isArmed() {
        return armed;
    }

    public Reason lastDeactivationReason() {
        return lastReason;
    }

    /** Feeds a fresh snapshot. Returns whether the feature is active afterwards. */
    public boolean update(Input in) {
        Reason block = blockerFor(in);

        // A world, player or connection change always disarms: the key has to be seen up again first.
        boolean transition = trackIdentity(in);
        if (!in.keyDown()) {
            armed = true;
        } else if (block != null || transition) {
            armed = false;
        }

        if (active) {
            if (block != null) {
                deactivate(block);
            } else if (!in.keyDown()) {
                deactivate(Reason.KEY_RELEASED);
            }
        } else if (in.keyDown() && armed && block == null) {
            active = true;
            armed = false;
            lastReason = Reason.NONE;
            listener.onActivated();
        }
        return active;
    }

    /**
     * Deactivates immediately from outside the polling loop, for example from a disconnect or world
     * change event. Disarms, so the key must be released before it can activate again.
     */
    public void forceDeactivate(Reason reason) {
        armed = false;
        if (active) {
            deactivate(reason == null ? Reason.FORCED : reason);
        } else if (reason != null) {
            lastReason = reason;
        }
    }

    private void deactivate(Reason reason) {
        active = false;
        lastReason = reason;
        if (reason != Reason.KEY_RELEASED) {
            armed = false;
        }
        listener.onDeactivated(reason);
    }

    /** Highest-priority blocker, or null when nothing blocks. */
    private Reason blockerFor(Input in) {
        if (!in.connected()) {
            return Reason.DISCONNECTED;
        }
        if (sawConnected && lastWorld != null && in.worldToken() != lastWorld) {
            return Reason.WORLD_CHANGED;
        }
        if (sawConnected && lastPlayer != null && in.playerToken() != lastPlayer) {
            return Reason.PLAYER_REPLACED;
        }
        if (!in.playerAlive()) {
            return Reason.PLAYER_DIED;
        }
        if (!in.gateOpen()) {
            return Reason.GATE_CLOSED;
        }
        if (!in.windowFocused()) {
            return Reason.FOCUS_LOST;
        }
        if (in.screenOpen()) {
            return Reason.SCREEN_OPENED;
        }
        return null;
    }

    /**
     * Tracks which world and player we saw last. Returns true when the identity changed or the
     * connection just (re)appeared.
     */
    private boolean trackIdentity(Input in) {
        if (!in.connected()) {
            boolean wasConnected = sawConnected;
            sawConnected = false;
            lastWorld = null;
            lastPlayer = null;
            return wasConnected;
        }
        boolean changed = !sawConnected || in.worldToken() != lastWorld || in.playerToken() != lastPlayer;
        sawConnected = true;
        lastWorld = in.worldToken();
        lastPlayer = in.playerToken();
        return changed;
    }
}
