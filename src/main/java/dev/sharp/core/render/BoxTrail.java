package dev.sharp.core.render;

import dev.sharp.core.math.Box;

/**
 * Smooths a box that is only known once per game tick. Remembers the last two tick values and blends
 * between them with the partial tick, which is what the game does for rendering entities. Used for the
 * outline and the aim point, so both move smoothly at any frame rate.
 */
public final class BoxTrail {
    private Box previous;
    private Box current;
    private long lastTick = Long.MIN_VALUE;

    /**
     * Records the box seen at {@code tick}. Calling again within the same tick just refreshes the
     * latest value. If the trail was not fed for more than one tick it restarts instead of sliding
     * across the gap.
     */
    public void observe(long tick, Box box) {
        if (current == null || tick - lastTick > 1 || tick < lastTick) {
            previous = box;
            current = box;
        } else if (tick == lastTick) {
            current = box;
        } else {
            previous = current;
            current = box;
        }
        lastTick = tick;
    }

    /** The box blended by {@code partialTick} (0 = previous tick, 1 = latest tick), or null if empty. */
    public Box at(double partialTick) {
        if (current == null) {
            return null;
        }
        double t = Math.max(0.0, Math.min(1.0, partialTick));
        return previous.lerp(current, t);
    }

    public boolean isEmpty() {
        return current == null;
    }

    public void reset() {
        previous = null;
        current = null;
        lastTick = Long.MIN_VALUE;
    }
}
