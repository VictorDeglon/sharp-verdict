package dev.sharp.core.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoxTest {
    private static final double EPS = 1.0e-9;

    @Test
    void constructorNormalisesCorners() {
        Box b = new Box(2, 5, 3, -2, 1, -3);
        assertEquals(-2, b.minX());
        assertEquals(2, b.maxX());
        assertEquals(1, b.minY());
        assertEquals(5, b.maxY());
        assertEquals(-3, b.minZ());
        assertEquals(3, b.maxZ());
    }

    @Test
    void centerAndSizes() {
        Box b = new Box(0, 0, 0, 0.6, 1.8, 0.6);
        assertEquals(0.3, b.center().x(), EPS);
        assertEquals(0.9, b.center().y(), EPS);
        assertEquals(1.8, b.height(), EPS);
        assertEquals(0.6, b.width(), EPS);
        assertEquals(0.6, b.depth(), EPS);
    }

    @Test
    void pointAtHeightClampsFraction() {
        Box b = new Box(0, 10, 0, 1, 12, 1);
        assertEquals(10.0, b.pointAtHeight(-3).y(), EPS);
        assertEquals(11.0, b.pointAtHeight(0.5).y(), EPS);
        assertEquals(12.0, b.pointAtHeight(7).y(), EPS);
        assertEquals(0.5, b.pointAtHeight(0.5).x(), EPS);
    }

    @Test
    void distanceIsZeroInsideAndPositiveOutside() {
        Box b = new Box(0, 0, 0, 2, 2, 2);
        assertEquals(0.0, b.distanceTo(new V3(1, 1, 1)), EPS);
        assertEquals(3.0, b.distanceTo(new V3(5, 1, 1)), EPS);
        assertEquals(Math.sqrt(2.0), b.distanceTo(new V3(3, 3, 1)), EPS);
        assertTrue(b.contains(new V3(2, 2, 2)));
        assertFalse(b.contains(new V3(2.01, 1, 1)));
    }

    @Test
    void lerpOffsetAndInflate() {
        Box a = new Box(0, 0, 0, 1, 1, 1);
        Box b = new Box(10, 0, 0, 11, 1, 1);
        Box mid = a.lerp(b, 0.5);
        assertEquals(5.0, mid.minX(), EPS);
        assertEquals(6.0, mid.maxX(), EPS);
        assertEquals(a, a.lerp(b, 0.0));
        assertEquals(b, a.lerp(b, 1.0));
        assertEquals(new Box(1, 2, 3, 2, 3, 4), a.offset(new V3(1, 2, 3)));
        assertEquals(new Box(-0.5, -0.5, -0.5, 1.5, 1.5, 1.5), a.inflate(0.5));
    }

    @Test
    void finiteCheckCatchesNaN() {
        assertTrue(new Box(0, 0, 0, 1, 1, 1).isFinite());
        assertFalse(new Box(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1).isFinite());
        assertFalse(new V3(Double.NaN, 0, 0).isFinite());
    }

    @Test
    void rayEntryGivesTheDistanceToTheNearFace() {
        Box b = new Box(-1, -1, 4, 1, 1, 6);
        assertEquals(4.0, b.rayEntry(V3.ZERO, new V3(0, 0, 1)), 1.0e-9);
        // Slightly to the right: enters the near face a little farther along the ray than straight ahead.
        assertEquals(4.0 * Math.sqrt(1.04), b.rayEntry(V3.ZERO, new V3(0.2, 0, 1).normalized()), 1.0e-9);
    }

    @Test
    void rayEntryMissesWhenPointingAwayOrPastTheBox() {
        Box b = new Box(-1, -1, 4, 1, 1, 6);
        assertTrue(Double.isNaN(b.rayEntry(V3.ZERO, new V3(0, 0, -1))));
        assertTrue(Double.isNaN(b.rayEntry(V3.ZERO, new V3(1, 0, 0))));
        assertTrue(Double.isNaN(b.rayEntry(V3.ZERO, new V3(0.5, 0, 1).normalized())));
        assertTrue(Double.isNaN(b.rayEntry(new V3(3, 0, 0), new V3(0, 0, 1))), "parallel to a slab and outside it");
    }

    @Test
    void rayEntryIsZeroFromInside() {
        Box b = new Box(-1, -1, -1, 1, 1, 1);
        assertEquals(0.0, b.rayEntry(V3.ZERO, new V3(0, 1, 0)));
    }
}
