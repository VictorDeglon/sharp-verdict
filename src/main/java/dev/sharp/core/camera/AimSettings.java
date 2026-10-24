package dev.sharp.core.camera;

/**
 * Immutable camera tracking parameters. All angles are in degrees, times in seconds.
 *
 * @param sensitivity         responsiveness multiplier, 1 is the baseline, 2 closes the gap twice as fast
 * @param smoothing           0 is the snappiest allowed response, 1 the slowest and gentlest
 * @param maxSpeed            hard cap on rotation speed in degrees per second
 * @param maxAcceleration     hard cap on how fast the rotation speed may change, degrees per second squared
 * @param maxStepPerFrame     hard cap on the rotation applied in a single frame
 * @param deadzone            remaining error below which the camera stops moving, avoids jitter
 * @param rampSeconds         time over which the assist fades in after it starts, avoids a first-frame jerk
 * @param pitchMin            lowest pitch the assist may aim at (negative looks up)
 * @param pitchMax            highest pitch the assist may aim at (positive looks down)
 * @param yieldToManualInput  whether the assist backs off while the player moves the mouse themselves
 * @param yieldRate           player rotation speed (degrees per second) at which the assist has fully backed off
 */
public record AimSettings(
        double sensitivity,
        double smoothing,
        double maxSpeed,
        double maxAcceleration,
        double maxStepPerFrame,
        double deadzone,
        double rampSeconds,
        double pitchMin,
        double pitchMax,
        boolean yieldToManualInput,
        double yieldRate) {

    public static AimSettings defaults() {
        return new AimSettings(2.0, 0.2, 540.0, 6000.0, 45.0, 0.1, 0.08, -85.0, 85.0, true, 120.0);
    }
}
