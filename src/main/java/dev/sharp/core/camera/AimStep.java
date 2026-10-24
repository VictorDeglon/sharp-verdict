package dev.sharp.core.camera;

/**
 * Output of one {@link AimController} step.
 *
 * @param deltaYaw       rotation to apply to the camera yaw this frame, degrees
 * @param deltaPitch     rotation to apply to the camera pitch this frame, degrees (positive looks down)
 * @param yawError       yaw still to go before the step, wrapped to [-180, 180)
 * @param pitchError     pitch still to go before the step (after clamping the aim to the pitch limits)
 * @param angularError   angle between the crosshair and the aim point before the step
 * @param strength       0..1 factor from yielding to the player's own mouse movement
 * @param ramp           0..1 factor of the fade-in since the assist started
 */
public record AimStep(
        double deltaYaw,
        double deltaPitch,
        double yawError,
        double pitchError,
        double angularError,
        double strength,
        double ramp) {

    public static final AimStep ZERO = new AimStep(0, 0, 0, 0, 0, 1, 0);

    public boolean isZero() {
        return deltaYaw == 0.0 && deltaPitch == 0.0;
    }
}
