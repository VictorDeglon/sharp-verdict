package dev.sharp.core.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AimMathTest {
    private static final double EPS = 1.0e-9;
    private static final V3 ORIGIN = V3.ZERO;

    @Test
    void yawZeroLooksSouthPlusZ() {
        V3 look = AimMath.lookVector(0, 0);
        assertEquals(0.0, look.x(), EPS);
        assertEquals(0.0, look.y(), EPS);
        assertEquals(1.0, look.z(), EPS);
    }

    @Test
    void yaw90LooksWestMinusX() {
        V3 look = AimMath.lookVector(90, 0);
        assertEquals(-1.0, look.x(), EPS);
        assertEquals(0.0, look.z(), 1.0e-9);
    }

    @Test
    void positivePitchLooksDown() {
        V3 look = AimMath.lookVector(0, 90);
        assertEquals(-1.0, look.y(), EPS);
    }

    @Test
    void yawToMatchesLookVectorConvention() {
        assertEquals(0.0, AimMath.yawTo(ORIGIN, new V3(0, 0, 5)), EPS);
        assertEquals(90.0, AimMath.yawTo(ORIGIN, new V3(-5, 0, 0)), EPS);
        assertEquals(-90.0, AimMath.yawTo(ORIGIN, new V3(5, 0, 0)), EPS);
        assertEquals(180.0, Math.abs(AimMath.yawTo(ORIGIN, new V3(0, 0, -5))), EPS);
    }

    @Test
    void pitchToIsPositiveWhenTargetIsBelow() {
        assertEquals(45.0, AimMath.pitchTo(ORIGIN, new V3(0, -5, 5)), EPS);
        assertEquals(-45.0, AimMath.pitchTo(ORIGIN, new V3(0, 5, 5)), EPS);
        assertEquals(0.0, AimMath.pitchTo(ORIGIN, new V3(3, 0, 4)), EPS);
    }

    @Test
    void yawPitchRoundTripsThroughLookVector() {
        V3 eye = new V3(10, 64, -3);
        V3 target = new V3(14.5, 62.25, 9.75);
        double yaw = AimMath.yawTo(eye, target);
        double pitch = AimMath.pitchTo(eye, target);
        V3 look = AimMath.lookVector(yaw, pitch);
        V3 expected = target.sub(eye).normalized();
        assertEquals(expected.x(), look.x(), 1.0e-9);
        assertEquals(expected.y(), look.y(), 1.0e-9);
        assertEquals(expected.z(), look.z(), 1.0e-9);
        assertEquals(0.0, AimMath.angularErrorDegrees(eye, yaw, pitch, target), 1.0e-6);
    }

    @Test
    void coincidentPointsDoNotProduceNaN() {
        assertEquals(0.0, AimMath.yawTo(ORIGIN, ORIGIN), EPS);
        assertEquals(0.0, AimMath.pitchTo(ORIGIN, ORIGIN), EPS);
        assertEquals(0.0, AimMath.angularErrorDegrees(ORIGIN, 33, 12, ORIGIN), EPS);
    }

    @Test
    void wrapDegreesPicksShortestRepresentation() {
        assertEquals(0.0, AimMath.wrapDegrees(0), EPS);
        assertEquals(-170.0, AimMath.wrapDegrees(190), EPS);
        assertEquals(170.0, AimMath.wrapDegrees(-190), EPS);
        assertEquals(10.0, AimMath.wrapDegrees(370), EPS);
        assertEquals(-180.0, AimMath.wrapDegrees(180), EPS);
        for (double d = -1000; d <= 1000; d += 7.3) {
            double w = AimMath.wrapDegrees(d);
            assertTrue(w >= -180.0 && w < 180.0, "wrapped value out of range for " + d + ": " + w);
        }
    }

    @Test
    void angleBetweenIsClampedAndSymmetric() {
        V3 a = new V3(1, 0, 0);
        assertEquals(90.0, AimMath.angleBetweenDegrees(a, new V3(0, 1, 0)), EPS);
        assertEquals(180.0, AimMath.angleBetweenDegrees(a, new V3(-3, 0, 0)), EPS);
        assertEquals(0.0, AimMath.angleBetweenDegrees(a, new V3(9, 0, 0)), EPS);
        // Slightly above 1 after rounding must not turn into NaN.
        V3 b = new V3(0.1, 0.2, 0.3).normalized();
        assertEquals(0.0, AimMath.angleBetweenDegrees(b, b), 1.0e-6);
    }
}
