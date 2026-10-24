package dev.sharp.core.math;

/** Immutable axis-aligned box. Corners are normalised so that min is never greater than max. */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public Box {
        double loX = Math.min(minX, maxX);
        double hiX = Math.max(minX, maxX);
        double loY = Math.min(minY, maxY);
        double hiY = Math.max(minY, maxY);
        double loZ = Math.min(minZ, maxZ);
        double hiZ = Math.max(minZ, maxZ);
        minX = loX;
        maxX = hiX;
        minY = loY;
        maxY = hiY;
        minZ = loZ;
        maxZ = hiZ;
    }

    public static Box point(V3 p) {
        return new Box(p.x(), p.y(), p.z(), p.x(), p.y(), p.z());
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    public double depth() {
        return maxZ - minZ;
    }

    public V3 center() {
        return new V3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
    }

    public Box offset(V3 d) {
        return new Box(minX + d.x(), minY + d.y(), minZ + d.z(), maxX + d.x(), maxY + d.y(), maxZ + d.z());
    }

    /** Grows (or shrinks, when negative) the box by {@code amount} on every side. */
    public Box inflate(double amount) {
        return new Box(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
    }

    /** Component-wise interpolation from this box to {@code to}. */
    public Box lerp(Box to, double t) {
        return new Box(
                minX + (to.minX - minX) * t,
                minY + (to.minY - minY) * t,
                minZ + (to.minZ - minZ) * t,
                maxX + (to.maxX - maxX) * t,
                maxY + (to.maxY - maxY) * t,
                maxZ + (to.maxZ - maxZ) * t);
    }

    /** The point of the box closest to {@code p}. Equals {@code p} when {@code p} is inside. */
    public V3 closestPoint(V3 p) {
        return new V3(clamp(p.x(), minX, maxX), clamp(p.y(), minY, maxY), clamp(p.z(), minZ, maxZ));
    }

    /** Distance from {@code p} to the box surface, 0 when {@code p} is inside. */
    public double distanceTo(V3 p) {
        return closestPoint(p).distanceTo(p);
    }

    public boolean contains(V3 p) {
        return p.x() >= minX && p.x() <= maxX
                && p.y() >= minY && p.y() <= maxY
                && p.z() >= minZ && p.z() <= maxZ;
    }

    /**
     * Point on the vertical centre line of the box. {@code fraction} 0 is the bottom, 1 the top,
     * values outside that range are clamped.
     */
    public V3 pointAtHeight(double fraction) {
        double f = clamp(fraction, 0.0, 1.0);
        return new V3((minX + maxX) * 0.5, minY + (maxY - minY) * f, (minZ + maxZ) * 0.5);
    }

    /**
     * Distance along a ray at which it first enters this box, 0 when the origin is already inside, or {@code NaN} when the ray
     * misses. {@code dir} must be a unit vector.
     */
    public double rayEntry(V3 origin, V3 dir) {
        double tMin = 0.0;
        double tMax = Double.POSITIVE_INFINITY;
        double[] o = {origin.x(), origin.y(), origin.z()};
        double[] d = {dir.x(), dir.y(), dir.z()};
        double[] lo = {minX, minY, minZ};
        double[] hi = {maxX, maxY, maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0e-12) {
                if (o[i] < lo[i] || o[i] > hi[i]) {
                    return Double.NaN;
                }
                continue;
            }
            double t1 = (lo[i] - o[i]) / d[i];
            double t2 = (hi[i] - o[i]) / d[i];
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            if (tMin > tMax) {
                return Double.NaN;
            }
        }
        return tMin;
    }

    public boolean isFinite() {
        return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
                && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
