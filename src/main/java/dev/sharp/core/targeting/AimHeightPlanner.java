package dev.sharp.core.targeting;

import dev.sharp.core.math.Box;

/**
 * Decides how high on the target to prefer aiming, frame by frame, as a fraction of its height (0 feet, 1 top). The result is a
 * preference only: {@link AimPointPicker} still moves off it when a block or another hitbox is in the way.
 *
 * <p>Three situations, in the order they matter:
 *
 * <ul>
 *   <li><b>Out of reach.</b> The middle of the body. It is the biggest and most forgiving spot, and the one that moves least when the
 *       target turns or crouches, so it is the easiest to stay on while closing in. The moment the held weapon can reach the target
 *       the preference becomes the normal one (feet first), a little over a tenth of a second later because the change is slewed. The
 *       way back is slower: the target must stay out of reach for a quarter of a second, so standing on the edge of reach does not make
 *       the aim point flicker.
 *   <li><b>Falling.</b> The middle, and while it comes down the point slides toward the normal spot in step with how far it still is
 *       above the player's feet: a target 5 blocks or more above is aimed at in the middle, and one level with the player's feet is
 *       aimed at as usual. It will land where the player is, so the point is already there when it does.
 *   <li><b>Otherwise.</b> The normal preference: the feet zone with {@code feetFirst}, else {@code aimHeight}.
 * </ul>
 *
 * <p>Alongside the height it keeps a second number, {@link #nearestWeight()}, that follows the same three situations. The controller uses it
 * to lean the aim point from the middle of the body toward the point of the hitbox nearest the eye, see
 * {@link AimPointPicker#preferredPoint}.
 *
 * <p>Everything is smoothed: the value never changes faster than {@link #MAX_SLEW} per second, so no switch above can make the camera
 * lurch. There is no randomness anywhere in here; the same inputs always give the same output.
 */
public final class AimHeightPlanner {
    public static final double CENTRE = AimPointPicker.MIDDLE_FRACTION;
    /** The most the preference may change per second, as a fraction of the box height. */
    public static final double MAX_SLEW = 2.5;
    /** A target counts as falling once it drops faster than this, in blocks per second. */
    static final double FALL_ENTER = -2.5;
    /** and stops counting once it drops slower than this. */
    static final double FALL_EXIT = -1.0;
    /** Height above the player's feet at which a falling target is aimed at in the middle. */
    static final double BLEND_HEIGHT = 5.0;
    /** How long a target must stay out of reach before the preference moves back to the middle. */
    static final double REACH_HOLD_SECONDS = 0.25;
    /** The most the nearest-point weight may change per second. Faster than the height, so the better spot is taken quickly in reach. */
    public static final double MAX_WEIGHT_SLEW = 6.0;
    /** Time constant for smoothing the measured vertical speed. */
    private static final double SPEED_SMOOTHING_SECONDS = 0.06;
    private static final double PLAYER_EYE_HEIGHT = 1.62;

    private boolean started;
    private double fraction;
    private boolean hasLastY;
    private double lastMinY;
    private double verticalSpeed;
    private boolean falling;
    private boolean reachState;
    private double outOfReachSeconds;
    private boolean weightStarted;
    private double nearestWeight;

    /**
     * @param box     the target's box for this frame
     * @param eyeY    the player's eye height in the world
     * @param inReach whether the held weapon can hit the target from where the player stands
     * @param dt      seconds since the previous call
     */
    public double plan(Box box, double eyeY, boolean inReach, double dt, TargetingSettings settings) {
        double normal = settings.feetFirst() ? AimPointPicker.FEET_FRACTION : settings.aimHeight();
        double step = Math.max(0.0, Math.min(0.25, dt));

        if (inReach) {
            reachState = true;
            outOfReachSeconds = 0.0;
        } else {
            outOfReachSeconds += step;
            if (!started || outOfReachSeconds >= REACH_HOLD_SECONDS) {
                reachState = false;
            }
        }

        if (hasLastY && step > 1.0e-4) {
            double raw = (box.minY() - lastMinY) / step;
            double blend = 1.0 - Math.exp(-step / SPEED_SMOOTHING_SECONDS);
            verticalSpeed += (raw - verticalSpeed) * blend;
        }
        lastMinY = box.minY();
        hasLastY = true;
        if (falling) {
            falling = verticalSpeed < FALL_EXIT;
        } else {
            falling = verticalSpeed < FALL_ENTER;
        }

        double base = reachState ? normal : CENTRE;
        double desired = base;
        double fallBlend = 0.0;
        if (falling) {
            double above = box.minY() - (eyeY - PLAYER_EYE_HEIGHT);
            fallBlend = smoothstep(clamp01(above / BLEND_HEIGHT));
            desired = base + (CENTRE - base) * fallBlend;
        }

        // The same three situations, as how far to lean toward the point nearest the eye: all the way in reach, not at all out of reach,
        // and while falling in step with how far above the player it still is.
        double desiredWeight = reachState ? 1.0 - fallBlend : 0.0;
        if (!weightStarted) {
            weightStarted = true;
            nearestWeight = desiredWeight;
        } else {
            double maxWeightMove = MAX_WEIGHT_SLEW * step;
            nearestWeight += Math.max(-maxWeightMove, Math.min(maxWeightMove, desiredWeight - nearestWeight));
        }

        if (!started) {
            started = true;
            fraction = desired;
        } else {
            double maxMove = MAX_SLEW * step;
            fraction += Math.max(-maxMove, Math.min(maxMove, desired - fraction));
        }
        return fraction;
    }

    /**
     * How far to lean toward the point of the box nearest the eye, after the latest {@link #plan}: 0 is the middle of the body, 1 is the
     * nearest point. Moves at most {@link #MAX_WEIGHT_SLEW} per second.
     */
    public double nearestWeight() {
        return nearestWeight;
    }

    /** True while the target is dropping fast enough to count as falling. */
    public boolean isFalling() {
        return falling;
    }

    /** Forgets the target. Call when the lock moves to nothing or to another entity. */
    public void reset() {
        started = false;
        fraction = 0.0;
        hasLastY = false;
        lastMinY = 0.0;
        verticalSpeed = 0.0;
        falling = false;
        reachState = false;
        outOfReachSeconds = 0.0;
        weightStarted = false;
        nearestWeight = 0.0;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }
}
