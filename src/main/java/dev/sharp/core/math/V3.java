package dev.sharp.core.math;

/**
 * Immutable 3D vector.
 *
 * <p>The core deliberately has its own vector and box types instead of using Minecraft's, so every
 * rule in it can be unit tested without starting the game.
 */
public record V3(double x, double y, double z) {
    public static final V3 ZERO = new V3(0.0, 0.0, 0.0);

    public V3 add(V3 o) {
        return new V3(x + o.x, y + o.y, z + o.z);
    }

    public V3 sub(V3 o) {
        return new V3(x - o.x, y - o.y, z - o.z);
    }

    public V3 scale(double k) {
        return new V3(x * k, y * k, z * k);
    }

    public double dot(V3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public double lengthSq() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public double distanceTo(V3 o) {
        return sub(o).length();
    }

    /** Unit vector, or {@link #ZERO} when this vector is (almost) zero. */
    public V3 normalized() {
        double len = length();
        return len < 1.0e-12 ? ZERO : scale(1.0 / len);
    }

    public V3 lerp(V3 to, double t) {
        return new V3(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
