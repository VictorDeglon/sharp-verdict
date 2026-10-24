package dev.sharp.core.math;

/**
 * Angle helpers using Minecraft's rotation conventions.
 *
 * <ul>
 *   <li>Yaw 0 looks toward +Z (south), yaw 90 toward -X (west). Yaw grows clockwise seen from above.
 *   <li>Pitch 0 is level, +90 looks straight down, -90 straight up.
 * </ul>
 *
 * These match {@code Entity#calculateViewVector}, so a yaw/pitch pair produced here points at the same
 * spot the game's own view vector would.
 */
public final class AimMath {
    private AimMath() {
    }

    /** Wraps an angle into [-180, 180). */
    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        }
        if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    /** Yaw (degrees) a viewer at {@code from} needs to face {@code to}. */
    public static double yawTo(V3 from, V3 to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        if (Math.abs(dx) < 1.0e-12 && Math.abs(dz) < 1.0e-12) {
            return 0.0;
        }
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Pitch (degrees) a viewer at {@code from} needs to face {@code to}. */
    public static double pitchTo(V3 from, V3 to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1.0e-12 && Math.abs(dy) < 1.0e-12) {
            return 0.0;
        }
        return -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    /** Unit view vector for the given rotation. */
    public static V3 lookVector(double yawDegrees, double pitchDegrees) {
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(-yawDegrees);
        double cosPitch = Math.cos(pitch);
        return new V3(Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    /** Angle in degrees between two directions, 0 when either is a zero vector. */
    public static double angleBetweenDegrees(V3 a, V3 b) {
        double la = a.length();
        double lb = b.length();
        if (la < 1.0e-12 || lb < 1.0e-12) {
            return 0.0;
        }
        double cos = a.dot(b) / (la * lb);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    /**
     * Angle between where the viewer is looking and the direction to {@code point}. This is the "angular
     * error" the crosshair would need to close to sit exactly on the point.
     */
    public static double angularErrorDegrees(V3 eye, double yawDegrees, double pitchDegrees, V3 point) {
        return angleBetweenDegrees(lookVector(yawDegrees, pitchDegrees), point.sub(eye));
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
