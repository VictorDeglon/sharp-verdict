package dev.sharp.core.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.activation.ActivationTracker.Input;
import dev.sharp.core.activation.ActivationTracker.Reason;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActivationTrackerTest {
    private final Object world = new Object();
    private final Object player = new Object();

    private int activations;
    private final List<Reason> deactivations = new ArrayList<>();
    private ActivationTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = ActivationTracker.withCallbacks(() -> activations++, deactivations::add);
    }

    private Input held() {
        return new Input(true, true, false, true, true, true, world, player);
    }

    private Input up() {
        return Input.idle(world, player);
    }

    private Input with(Input in, boolean key, boolean focus, boolean screen, boolean alive, boolean connected,
            boolean gate, Object w, Object p) {
        return new Input(key, focus, screen, alive, connected, gate, w, p);
    }

    // ---- key press and release ------------------------------------------------------------------

    @Test
    void activatesOnPressAndOnlyWhileHeld() {
        assertFalse(tracker.update(up()));
        assertTrue(tracker.update(held()));
        assertTrue(tracker.update(held()));
        assertEquals(1, activations);
    }

    @Test
    void releaseDeactivatesInTheSameUpdate() {
        tracker.update(up());
        tracker.update(held());
        assertTrue(tracker.isActive());

        assertFalse(tracker.update(up()), "must be off on the very update that sees the key up");
        assertEquals(List.of(Reason.KEY_RELEASED), deactivations);
        assertEquals(Reason.KEY_RELEASED, tracker.lastDeactivationReason());
    }

    @Test
    void releaseAndPressAgainActivatesAgain() {
        tracker.update(up());
        tracker.update(held());
        tracker.update(up());
        assertTrue(tracker.update(held()));
        assertEquals(2, activations);
    }

    @Test
    void repeatedIdenticalUpdatesAreIdempotent() {
        tracker.update(up());
        for (int i = 0; i < 5; i++) {
            tracker.update(held());
        }
        for (int i = 0; i < 5; i++) {
            tracker.update(up());
        }
        assertEquals(1, activations);
        assertEquals(1, deactivations.size());
    }

    // ---- focus and screens ----------------------------------------------------------------------

    @Test
    void losingWindowFocusDeactivatesAndDoesNotComeBackByItself() {
        tracker.update(up());
        tracker.update(held());

        Input unfocused = with(held(), true, false, false, true, true, true, world, player);
        assertFalse(tracker.update(unfocused));
        assertEquals(List.of(Reason.FOCUS_LOST), deactivations);

        // Focus returns while the key is still reported as held: must stay off.
        assertFalse(tracker.update(held()));
        assertFalse(tracker.update(held()));
        assertEquals(1, activations);

        // A fresh press works again.
        tracker.update(up());
        assertTrue(tracker.update(held()));
    }

    @Test
    void openingAScreenDeactivates() {
        tracker.update(up());
        tracker.update(held());
        Input screen = with(held(), true, true, true, true, true, true, world, player);
        assertFalse(tracker.update(screen));
        assertEquals(List.of(Reason.SCREEN_OPENED), deactivations);
    }

    @Test
    void pressingWhileBlockedDoesNotActivateWhenTheBlockClears() {
        tracker.update(up());
        Input blocked = with(held(), true, true, true, true, true, true, world, player);
        assertFalse(tracker.update(blocked));
        // Screen closes, key is still down: not a fresh press, so still off.
        assertFalse(tracker.update(held()));
        assertEquals(0, activations);
        tracker.update(up());
        assertTrue(tracker.update(held()));
    }

    // ---- death ----------------------------------------------------------------------------------

    @Test
    void deathDeactivatesAndRespawnWithKeyHeldStaysOff() {
        tracker.update(up());
        tracker.update(held());

        Input dead = with(held(), true, true, false, false, true, true, world, player);
        assertFalse(tracker.update(dead));
        assertEquals(List.of(Reason.PLAYER_DIED), deactivations);

        Object respawned = new Object();
        Input afterRespawn = with(held(), true, true, false, true, true, true, world, respawned);
        assertFalse(tracker.update(afterRespawn));
        assertFalse(tracker.update(afterRespawn));
        assertEquals(1, activations);

        Input released = with(up(), false, true, false, true, true, true, world, respawned);
        tracker.update(released);
        assertTrue(tracker.update(afterRespawn));
    }

    @Test
    void replacingThePlayerEntityDeactivates() {
        tracker.update(up());
        tracker.update(held());
        Input replaced = with(held(), true, true, false, true, true, true, world, new Object());
        assertFalse(tracker.update(replaced));
        assertEquals(List.of(Reason.PLAYER_REPLACED), deactivations);
    }

    // ---- worlds and connection ------------------------------------------------------------------

    @Test
    void worldChangeDeactivatesAndStaysOffWhileKeyIsHeld() {
        tracker.update(up());
        tracker.update(held());

        Object nether = new Object();
        Input inNether = with(held(), true, true, false, true, true, true, nether, player);
        assertFalse(tracker.update(inNether));
        assertEquals(List.of(Reason.WORLD_CHANGED), deactivations);
        assertFalse(tracker.update(inNether));
        assertFalse(tracker.update(inNether));
        assertEquals(1, activations);

        tracker.update(with(up(), false, true, false, true, true, true, nether, player));
        assertTrue(tracker.update(inNether));
    }

    @Test
    void disconnectDeactivatesAndReconnectWithKeyHeldStaysOff() {
        tracker.update(up());
        tracker.update(held());

        Input gone = new Input(true, true, false, false, false, true, null, null);
        assertFalse(tracker.update(gone));
        assertEquals(List.of(Reason.DISCONNECTED), deactivations);

        Object newWorld = new Object();
        Object newPlayer = new Object();
        Input rejoined = with(held(), true, true, false, true, true, true, newWorld, newPlayer);
        assertFalse(tracker.update(rejoined));
        assertFalse(tracker.update(rejoined));
        assertEquals(1, activations);
    }

    @Test
    void keyAlreadyHeldWhenFirstJoiningDoesNotActivate() {
        assertFalse(tracker.update(held()));
        assertFalse(tracker.update(held()));
        assertEquals(0, activations);
        tracker.update(up());
        assertTrue(tracker.update(held()));
    }

    // ---- practice gate --------------------------------------------------------------------------

    @Test
    void closedGateNeverActivates() {
        Input closedUp = with(up(), false, true, false, true, true, false, world, player);
        Input closedHeld = with(held(), true, true, false, true, true, false, world, player);
        tracker.update(closedUp);
        assertFalse(tracker.update(closedHeld));
        assertEquals(0, activations);
    }

    @Test
    void gateClosingWhileActiveDeactivates() {
        tracker.update(up());
        tracker.update(held());
        Input closed = with(held(), true, true, false, true, true, false, world, player);
        assertFalse(tracker.update(closed));
        assertEquals(List.of(Reason.GATE_CLOSED), deactivations);
    }

    // ---- forced deactivation from events ---------------------------------------------------------

    @Test
    void forceDeactivateFiresOnceAndRequiresARelease() {
        tracker.update(up());
        tracker.update(held());

        tracker.forceDeactivate(Reason.DISCONNECTED);
        assertFalse(tracker.isActive());
        tracker.forceDeactivate(Reason.DISCONNECTED);
        assertEquals(List.of(Reason.DISCONNECTED), deactivations);

        assertFalse(tracker.update(held()), "key still held, must not re-activate");
        tracker.update(up());
        assertTrue(tracker.update(held()));
    }

    @Test
    void forceDeactivateWithNullReasonIsReportedAsForced() {
        tracker.update(up());
        tracker.update(held());
        tracker.forceDeactivate(null);
        assertEquals(List.of(Reason.FORCED), deactivations);
    }

    // ---- invariants -----------------------------------------------------------------------------

    /**
     * Randomised property: whatever sequence of inputs arrives, the tracker is never active while the key
     * is up or a blocker is present, and every activation is matched by at most one deactivation.
     */
    @Test
    void neverActiveWhenKeyIsUpOrAnythingBlocks() {
        Random rnd = new Random(0xC0FFEE);
        Object[] worlds = {new Object(), new Object(), new Object()};
        Object[] players = {new Object(), new Object()};
        int liveActivations = 0;

        for (int step = 0; step < 20_000; step++) {
            boolean connected = rnd.nextInt(12) != 0;
            Input in = new Input(
                    rnd.nextBoolean(),
                    rnd.nextInt(8) != 0,
                    rnd.nextInt(6) == 0,
                    rnd.nextInt(15) != 0,
                    connected,
                    rnd.nextInt(20) != 0,
                    connected ? worlds[rnd.nextInt(rnd.nextInt(10) == 0 ? worlds.length : 1)] : null,
                    connected ? players[rnd.nextInt(rnd.nextInt(10) == 0 ? players.length : 1)] : null);

            boolean wasActive = tracker.isActive();
            boolean active = tracker.update(in);

            if (active) {
                assertTrue(in.keyDown(), "active with key up at step " + step);
                assertTrue(in.windowFocused(), "active without focus at step " + step);
                assertFalse(in.screenOpen(), "active with screen open at step " + step);
                assertTrue(in.playerAlive(), "active while dead at step " + step);
                assertTrue(in.connected(), "active while disconnected at step " + step);
                assertTrue(in.gateOpen(), "active while gate closed at step " + step);
            }
            if (!wasActive && active) {
                liveActivations++;
            }
            if (wasActive && !active) {
                liveActivations--;
            }
            assertTrue(liveActivations == 0 || liveActivations == 1, "unbalanced transitions at step " + step);
            assertEquals(active, liveActivations == 1);
        }
        assertEquals(activations - deactivations.size(), tracker.isActive() ? 1 : 0);

        // Guard against a vacuous pass: the random walk must have really switched on and off many
        // times and hit every kind of blocker.
        assertTrue(activations > 500, "fuzzer barely activated: " + activations);
        for (Reason r : new Reason[] {Reason.KEY_RELEASED, Reason.FOCUS_LOST, Reason.SCREEN_OPENED,
                Reason.PLAYER_DIED, Reason.PLAYER_REPLACED, Reason.WORLD_CHANGED, Reason.DISCONNECTED,
                Reason.GATE_CLOSED}) {
            assertTrue(deactivations.contains(r), "fuzzer never produced " + r);
        }
    }
}
