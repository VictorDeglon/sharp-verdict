package dev.sharp.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HitboxGeometryTest {
    private static final Box PLAYER = new Box(10, 64, -5, 10.6, 65.8, -4.4);

    @Test
    void wireframeHasTwelveDistinctEdges() {
        List<Box> edges = HitboxGeometry.wireframe(PLAYER, 0.02);
        assertEquals(12, edges.size());
        assertEquals(12, new HashSet<>(edges).size());
    }

    @Test
    void everyEdgeIsThinInTwoAxesAndFullLengthInOne() {
        double t = 0.02;
        int alongX = 0;
        int alongY = 0;
        int alongZ = 0;
        for (Box e : HitboxGeometry.wireframe(PLAYER, t)) {
            boolean x = Math.abs(e.width() - (PLAYER.width() + t)) < 1e-9;
            boolean y = Math.abs(e.height() - (PLAYER.height() + t)) < 1e-9;
            boolean z = Math.abs(e.depth() - (PLAYER.depth() + t)) < 1e-9;
            int longAxes = (x ? 1 : 0) + (y ? 1 : 0) + (z ? 1 : 0);
            assertEquals(1, longAxes, "edge must be long in exactly one axis: " + e);
            if (x) {
                alongX++;
            }
            if (y) {
                alongY++;
            }
            if (z) {
                alongZ++;
            }
            if (!x) {
                assertEquals(t, e.width(), 1e-9);
            }
            if (!y) {
                assertEquals(t, e.height(), 1e-9);
            }
            if (!z) {
                assertEquals(t, e.depth(), 1e-9);
            }
        }
        assertEquals(4, alongX);
        assertEquals(4, alongY);
        assertEquals(4, alongZ);
    }

    @Test
    void edgesSitOnTheRealBoxAndNeverLeaveItsInflatedBounds() {
        double t = 0.04;
        Box outer = PLAYER.inflate(t / 2);
        Set<Integer> cornersCovered = new HashSet<>();
        for (Box e : HitboxGeometry.wireframe(PLAYER, t)) {
            assertTrue(e.minX() >= outer.minX() - 1e-9 && e.maxX() <= outer.maxX() + 1e-9);
            assertTrue(e.minY() >= outer.minY() - 1e-9 && e.maxY() <= outer.maxY() + 1e-9);
            assertTrue(e.minZ() >= outer.minZ() - 1e-9 && e.maxZ() <= outer.maxZ() + 1e-9);
        }
        for (int i = 0; i < 8; i++) {
            V3 corner = new V3(
                    (i & 1) == 0 ? PLAYER.minX() : PLAYER.maxX(),
                    (i & 2) == 0 ? PLAYER.minY() : PLAYER.maxY(),
                    (i & 4) == 0 ? PLAYER.minZ() : PLAYER.maxZ());
            int touching = 0;
            for (Box e : HitboxGeometry.wireframe(PLAYER, t)) {
                if (e.contains(corner)) {
                    touching++;
                }
            }
            assertEquals(3, touching, "three edges must meet at each corner");
            cornersCovered.add(i);
        }
        assertEquals(8, cornersCovered.size());
    }

    @Test
    void edgeMidpointsAreCovered() {
        List<Box> edges = HitboxGeometry.wireframe(PLAYER, 0.02);
        V3 midTopFront = new V3(10.3, 65.8, -5);
        V3 midVerticalCorner = new V3(10.6, 64.9, -4.4);
        assertTrue(edges.stream().anyMatch(e -> e.contains(midTopFront)));
        assertTrue(edges.stream().anyMatch(e -> e.contains(midVerticalCorner)));
        // The middle of a face is not part of the outline.
        V3 faceCentre = new V3(10.3, 64.9, -5);
        assertTrue(edges.stream().noneMatch(e -> e.contains(faceCentre)));
    }

    @Test
    void thicknessGrowsWithDistanceWithinBounds() {
        double near = HitboxGeometry.edgeThickness(2, 2);
        double mid = HitboxGeometry.edgeThickness(20, 2);
        double far = HitboxGeometry.edgeThickness(120, 2);
        assertTrue(near < mid && mid < far, near + " " + mid + " " + far);
        assertEquals(HitboxGeometry.MIN_THICKNESS, HitboxGeometry.edgeThickness(0, 0.1), 1e-12);
        assertEquals(HitboxGeometry.MAX_THICKNESS, HitboxGeometry.edgeThickness(10_000, 8), 1e-12);
        assertEquals(HitboxGeometry.edgeThickness(1, 2), HitboxGeometry.edgeThickness(Double.NaN, 2), 1e-12);
    }

    @Test
    void thickerLinesRequestedInPixelsGiveThickerGeometry() {
        assertTrue(HitboxGeometry.edgeThickness(30, 4) > HitboxGeometry.edgeThickness(30, 2));
    }

    @Test
    void crossMarkerHasThreeBarsCrossingAtThePoint() {
        V3 p = new V3(3, 4, 5);
        List<Box> bars = HitboxGeometry.crossMarker(p, 0.2, 0.02);
        assertEquals(3, bars.size());
        for (Box b : bars) {
            assertTrue(b.contains(p));
        }
        assertEquals(0.4, bars.get(0).width(), 1e-9);
        assertEquals(0.4, bars.get(1).height(), 1e-9);
        assertEquals(0.4, bars.get(2).depth(), 1e-9);
    }
}
