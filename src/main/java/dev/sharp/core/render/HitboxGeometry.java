package dev.sharp.core.render;

import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a bounding box into the little boxes that make up its wireframe.
 *
 * <p>Drawing real GL lines needs a render pipeline that differs between game versions. Thin solid
 * prisms only need "draw a filled box", which is the one primitive every version has, and they
 * give full control over thickness and colour. The thickness grows with distance so the outline keeps
 * roughly the same width on screen.
 */
public final class HitboxGeometry {
    /** World units per screen pixel per block of distance for a typical 70 degree field of view. */
    static final double WORLD_PER_PIXEL_PER_BLOCK = 0.0013;
    static final double MIN_THICKNESS = 0.006;
    static final double MAX_THICKNESS = 0.25;

    private HitboxGeometry() {
    }

    /** Edge thickness in blocks that looks about {@code widthPixels} wide at {@code distance} blocks. */
    public static double edgeThickness(double distance, double widthPixels) {
        double d = Math.max(1.0, Double.isFinite(distance) ? distance : 1.0);
        double t = widthPixels * WORLD_PER_PIXEL_PER_BLOCK * d;
        return Math.max(MIN_THICKNESS, Math.min(MAX_THICKNESS, t));
    }

    /**
     * The 12 edges of {@code box}, each as a thin box of the given thickness centred on the edge. Each
     * edge is extended by half the thickness at both ends so the corners join without notches.
     */
    public static List<Box> wireframe(Box box, double thickness) {
        double h = thickness * 0.5;
        List<Box> edges = new ArrayList<>(12);
        double[] xs = {box.minX(), box.maxX()};
        double[] ys = {box.minY(), box.maxY()};
        double[] zs = {box.minZ(), box.maxZ()};
        for (double y : ys) {
            for (double z : zs) {
                edges.add(new Box(box.minX() - h, y - h, z - h, box.maxX() + h, y + h, z + h));
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                edges.add(new Box(x - h, box.minY() - h, z - h, x + h, box.maxY() + h, z + h));
            }
        }
        for (double x : xs) {
            for (double y : ys) {
                edges.add(new Box(x - h, y - h, box.minZ() - h, x + h, y + h, box.maxZ() + h));
            }
        }
        return edges;
    }

    /** Three crossing bars marking a point; {@code arm} is the half length of each bar. */
    public static List<Box> crossMarker(V3 p, double arm, double thickness) {
        double h = thickness * 0.5;
        List<Box> bars = new ArrayList<>(3);
        bars.add(new Box(p.x() - arm, p.y() - h, p.z() - h, p.x() + arm, p.y() + h, p.z() + h));
        bars.add(new Box(p.x() - h, p.y() - arm, p.z() - h, p.x() + h, p.y() + arm, p.z() + h));
        bars.add(new Box(p.x() - h, p.y() - h, p.z() - arm, p.x() + h, p.y() + h, p.z() + arm));
        return bars;
    }
}
