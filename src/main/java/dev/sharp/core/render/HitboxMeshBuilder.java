package dev.sharp.core.render;

import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link HitboxScene} into the flat list of solid boxes the renderer draws: for the main target a faint fill, the 12-edge
 * outline and the aim marker, and for each other entity a plain thin outline. Everything the look depends on (colours, widths,
 * distance scaling) is decided here and in {@link HitboxStylePolicy}, so the in-game renderer only has to loop over the result.
 */
public final class HitboxMeshBuilder {
    /** Half length of each bar of the aim marker, in blocks. */
    static final double MARKER_ARM = 0.12;

    private HitboxMeshBuilder() {
    }

    public static List<ColoredBox> build(HitboxScene scene, HitboxSettings settings, V3 camera) {
        List<ColoredBox> out = new ArrayList<>();
        if (!settings.enabled()) {
            return out;
        }

        // Others first, so the main target is drawn last and reads on top where outlines overlap.
        if (settings.showOthers()) {
            int shown = 0;
            for (HitboxScene.Entry other : scene.others()) {
                if (shown++ >= settings.maxOthers()) {
                    break;
                }
                HitboxStyle style = HitboxStylePolicy.other(settings, other.attackable());
                double thickness = HitboxGeometry.edgeThickness(other.box().distanceTo(camera), style.lineWidthPixels());
                addAll(out, HitboxGeometry.wireframe(other.box(), thickness), style.outlineColor());
            }
        }

        HitboxScene.Entry main = scene.main();
        if (main != null) {
            HitboxStyle style = HitboxStylePolicy.main(settings, main.attackable(), scene.tracking(), scene.exposure());
            double thickness = HitboxGeometry.edgeThickness(main.box().distanceTo(camera), style.lineWidthPixels());
            if (style.fill() && Argb.alpha(style.fillColor()) > 0) {
                out.add(new ColoredBox(main.box(), style.fillColor()));
            }
            addAll(out, HitboxGeometry.wireframe(main.box(), thickness), style.outlineColor());
            if (style.aimMarker() && scene.aimPoint() != null) {
                addAll(out, HitboxGeometry.crossMarker(scene.aimPoint(), MARKER_ARM, thickness * 1.2), style.markerColor());
            }
        }
        return out;
    }

    private static void addAll(List<ColoredBox> out, List<Box> boxes, int argb) {
        for (Box b : boxes) {
            out.add(new ColoredBox(b, argb));
        }
    }
}
