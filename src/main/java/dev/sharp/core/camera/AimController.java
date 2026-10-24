package dev.sharp.core.camera;

import dev.sharp.core.math.AimMath;
import dev.sharp.core.math.V3;

/**
 * Turns "the camera points here, the target is there" into a small, smooth rotation for this frame.
 *
 * <p>It knows nothing about entities or selection. Give it an eye position, the current yaw and pitch
 * and a point to aim at, and it returns how many degrees to turn. That keeps camera control testable on
 * its own and independent of how targets are chosen.
 *
 * <p>Per axis, the motion is built so that it cannot snap or ring:
 *
 * <ol>
 *   <li><b>First-order response.</b> The wanted rotation speed is proportional to the remaining error
 *       ({@code error / tau}). A first-order system approaches its goal and never overshoots it.
 *   <li><b>Speed cap</b> ({@code maxSpeed}) and a <b>braking cap</b> of {@code sqrt(2 * a * error)}, the
 *       fastest speed from which the camera can still stop inside the remaining error at the allowed
 *       acceleration.
 *   <li><b>Acceleration limit.</b> The actual rotation speed moves toward the wanted one by at most
 *       {@code maxAcceleration * dt} per frame, so a sudden new target produces a smooth ramp instead of
 *       a jerk.
 *   <li><b>No crossing.</b> A frame never rotates farther than the error left, and never farther than
 *       {@code maxStepPerFrame}.
 *   <li><b>Dead zone</b> (soft, measured from the edge, so there is no jump at its border) and a
 *       <b>fade-in</b> after the assist starts.
 *   <li><b>Yielding.</b> If the camera moved by something other than this controller since the last
 *       frame (the player's mouse), the assist backs off immediately and returns gradually once the
 *       player stops.
 * </ol>
 *
 * <p>Not thread safe. Call {@link #reset()} when the assist stops so the next start fades in again.
 */
public final class AimController {
    /** Time constant of the response at smoothing 0 (fastest allowed) and smoothing 1 (gentlest). */
    static final double TAU_FASTEST = 0.05;
    static final double TAU_GENTLEST = 0.60;
    /** Frames longer than this (a hitch) are treated as this long so one stall cannot cause a jump. */
    static final double MAX_FRAME_SECONDS = 0.1;
    /** How quickly the assist returns after the player stops moving the mouse. */
    static final double STRENGTH_RECOVERY_SECONDS = 0.25;

    private double engagedSeconds;
    private double yawRate;
    private double pitchRate;
    private double strength = 1.0;
    private boolean hasPrevious;
    private double expectedYaw;
    private double expectedPitch;

    /**
     * Computes this frame's rotation.
     *
     * @param yaw      current camera yaw in degrees
     * @param pitch    current camera pitch in degrees
     * @param eye      camera position
     * @param aimPoint point to aim at
     * @param dtSeconds time since the previous frame; non-positive values yield no movement
     */
    public AimStep step(double yaw, double pitch, V3 eye, V3 aimPoint, double dtSeconds, AimSettings s) {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || !Double.isFinite(dtSeconds)
                || !eye.isFinite() || !aimPoint.isFinite()) {
            reset();
            return AimStep.ZERO;
        }

        double pitchLo = Math.max(-90.0, Math.min(s.pitchMin(), s.pitchMax()));
        double pitchHi = Math.min(90.0, Math.max(s.pitchMin(), s.pitchMax()));
        double desiredYaw = AimMath.yawTo(eye, aimPoint);
        double desiredPitch = AimMath.clamp(AimMath.pitchTo(eye, aimPoint), pitchLo, pitchHi);
        double yawError = AimMath.wrapDegrees(desiredYaw - yaw);
        double pitchError = desiredPitch - pitch;
        double angularError = AimMath.angularErrorDegrees(eye, yaw, pitch, aimPoint);

        if (dtSeconds <= 0.0) {
            // No time has passed, nothing to do. Do not touch the state either.
            return new AimStep(0, 0, yawError, pitchError, angularError, strength, ramp(s));
        }
        double dt = Math.min(dtSeconds, MAX_FRAME_SECONDS);

        updateStrength(yaw, pitch, dt, s);

        engagedSeconds += dt;
        double ramp = ramp(s);

        double tau = lerp(TAU_FASTEST, TAU_GENTLEST, AimMath.clamp(s.smoothing(), 0.0, 1.0))
                / Math.max(s.sensitivity(), 1.0e-3);
        double accel = Math.max(1.0, s.maxAcceleration());
        double maxSpeed = Math.max(0.0, s.maxSpeed());
        double maxStep = Math.max(0.0, s.maxStepPerFrame());
        double gain = ramp * strength;

        Axis yawAxis = axis(yawError, yawRate, dt, tau, gain, accel, maxSpeed, maxStep, s.deadzone());
        Axis pitchAxis = axis(pitchError, pitchRate, dt, tau, gain, accel, maxSpeed, maxStep, s.deadzone());
        yawRate = yawAxis.rate();
        pitchRate = pitchAxis.rate();

        double newPitch = AimMath.clamp(pitch + pitchAxis.step(), -90.0, 90.0);
        double dPitch = newPitch - pitch;

        expectedYaw = yaw + yawAxis.step();
        expectedPitch = pitch + dPitch;
        hasPrevious = true;

        return new AimStep(yawAxis.step(), dPitch, yawError, pitchError, angularError, strength, ramp);
    }

    /** Forgets all motion state. The next {@link #step} fades in from rest. */
    public void reset() {
        engagedSeconds = 0.0;
        yawRate = 0.0;
        pitchRate = 0.0;
        strength = 1.0;
        hasPrevious = false;
        expectedYaw = 0.0;
        expectedPitch = 0.0;
    }

    /** Current yaw rotation speed in degrees per second, for debug output. */
    public double yawRate() {
        return yawRate;
    }

    /** Current pitch rotation speed in degrees per second, for debug output. */
    public double pitchRate() {
        return pitchRate;
    }

    public double strength() {
        return strength;
    }

    private record Axis(double step, double rate) {
    }

    private static Axis axis(
            double error,
            double previousRate,
            double dt,
            double tau,
            double gain,
            double accel,
            double maxSpeed,
            double maxStep,
            double deadzone) {

        // Soft dead zone: only the part of the error beyond the zone counts, so there is no jump at its edge.
        double effective = Math.copySign(Math.max(0.0, Math.abs(error) - Math.max(0.0, deadzone)), error);
        if (effective == 0.0) {
            // Inside the dead zone the camera is simply still. Dropping the leftover speed here is
            // invisible (it is already tiny by the braking cap) and rules out coasting past the target.
            return new Axis(0.0, 0.0);
        }

        double wanted = effective / tau * gain;
        wanted = AimMath.clamp(wanted, -maxSpeed, maxSpeed);
        // Fastest speed from which the camera can still stop inside the remaining error, allowing for the
        // frame it takes to start braking: v*dt + v^2/(2a) = error. This is always <= error/dt, so a
        // stationary target is never crossed.
        double accelStep = accel * dt;
        double brake = -accelStep + Math.sqrt(accelStep * accelStep + 2.0 * accel * Math.abs(effective));
        wanted = AimMath.clamp(wanted, -brake, brake);

        double maxDelta = accel * dt;
        double rate = previousRate + AimMath.clamp(wanted - previousRate, -maxDelta, maxDelta);
        double step = rate * dt;

        // Never rotate past the target within one frame.
        if (Math.abs(step) > Math.abs(effective) && Math.signum(step) == Math.signum(effective)) {
            step = effective;
            rate = step / dt;
        }
        // Hard per-frame cap.
        if (Math.abs(step) > maxStep) {
            step = Math.copySign(maxStep, step);
            rate = step / dt;
        }
        return new Axis(step, rate);
    }

    private void updateStrength(double yaw, double pitch, double dt, AimSettings s) {
        if (!s.yieldToManualInput()) {
            strength = 1.0;
            return;
        }
        double manualRate = 0.0;
        if (hasPrevious) {
            double moved = Math.hypot(AimMath.wrapDegrees(yaw - expectedYaw), pitch - expectedPitch);
            manualRate = moved / dt;
        }
        double wanted = 1.0 - AimMath.clamp(manualRate / Math.max(s.yieldRate(), 1.0e-6), 0.0, 1.0);
        if (wanted < strength) {
            strength = wanted;
        } else {
            strength += (wanted - strength) * (1.0 - Math.exp(-dt / STRENGTH_RECOVERY_SECONDS));
        }
    }

    private double ramp(AimSettings s) {
        if (s.rampSeconds() <= 1.0e-6) {
            return 1.0;
        }
        double t = AimMath.clamp(engagedSeconds / s.rampSeconds(), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
