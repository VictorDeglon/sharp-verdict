package dev.sharp.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import dev.sharp.core.targeting.Exposure;
import java.util.List;
import org.junit.jupiter.api.Test;

class HitboxMeshBuilderTest {
    private static final Box TARGET = new Box(-0.3, 0, 9.7, 0.3, 1.8, 10.3);
    private static final V3 CAMERA = new V3(0, 1.62, 0);
    private static final V3 AIM = new V3(0, 0.9, 10);
    private final HitboxSettings s = HitboxSettings.defaults();

    private static long count(List<ColoredBox> boxes, int argb) {
        return boxes.stream().filter(b -> b.argb() == argb).count();
    }

    private static HitboxScene scene(HitboxScene.Entry main, boolean tracking, V3 aim, Exposure exposure, HitboxScene.Entry... others) {
        return new HitboxScene(main, tracking, aim, exposure, List.of(others));
    }

    @Test
    void targetGetsFillTwelveEdgesAndAMarker() {
        HitboxScene scene = scene(new HitboxScene.Entry(TARGET, false), true, AIM, Exposure.OPEN);
        List<ColoredBox> parts = HitboxMeshBuilder.build(scene, s, CAMERA);
        assertEquals(1 + 12 + 3, parts.size());

        HitboxStyle style = HitboxStylePolicy.main(s, false, true, Exposure.OPEN);
        assertEquals(15, count(parts, style.outlineColor()), "12 edges in blue plus the blue cross");
        assertEquals(1, count(parts, style.fillColor()));
        assertEquals(TARGET, parts.get(0).box(), "the fill covers exactly the real hitbox");
    }

    @Test
    void fillAndMarkerFollowTheSettings() {
        HitboxSettings plain = new HitboxSettings(true, 48, false, 0.12, false, 2, false, 4,
                0xFF4D8DFF, 0xFFFFD93D, 0xFFFF5B54, 0xFFB36BFF, 0xFF4DE8E0);
        HitboxScene scene = scene(new HitboxScene.Entry(TARGET, false), false, AIM, Exposure.OPEN);
        assertEquals(12, HitboxMeshBuilder.build(scene, plain, CAMERA).size());

        HitboxSettings noFillAlpha = new HitboxSettings(true, 48, true, 0.0, false, 2, false, 4,
                0xFF4D8DFF, 0xFFFFD93D, 0xFFFF5B54, 0xFFB36BFF, 0xFF4DE8E0);
        assertEquals(12, HitboxMeshBuilder.build(scene, noFillAlpha, CAMERA).size(), "fully transparent fill is skipped");
    }

    @Test
    void disabledOrEmptyScenesProduceNothing() {
        HitboxSettings off = new HitboxSettings(false, 48, true, 0.12, true, 2, true, 4,
                0xFF4D8DFF, 0xFFFFD93D, 0xFFFF5B54, 0xFFB36BFF, 0xFF4DE8E0);
        HitboxScene scene = scene(new HitboxScene.Entry(TARGET, false), false, AIM, Exposure.OPEN, new HitboxScene.Entry(TARGET, false));
        assertTrue(HitboxMeshBuilder.build(scene, off, CAMERA).isEmpty());
        assertTrue(HitboxMeshBuilder.build(HitboxScene.EMPTY, s, CAMERA).isEmpty());
    }

    @Test
    void othersAreThinPlainOutlinesYellowOrRed() {
        Box near = new Box(4, 0, 9, 4.6, 1.8, 9.6);
        Box reachable = new Box(-4.6, 0, 9, -4, 1.8, 9.6);
        HitboxScene scene = scene(new HitboxScene.Entry(TARGET, false), false, AIM, Exposure.OPEN,
                new HitboxScene.Entry(near, false), new HitboxScene.Entry(reachable, true));
        List<ColoredBox> parts = HitboxMeshBuilder.build(scene, s, CAMERA);

        assertEquals(12, count(parts, s.rangeColor()), "yellow edges of the one in range");
        assertEquals(12, count(parts, s.attackColor()), "red edges of the one in reach");
        assertEquals(12 + 12 + 1 + 12 + 3, parts.size());
    }

    @Test
    void othersAreDrawnBeforeTheMainTargetSoItReadsOnTop() {
        HitboxScene scene = scene(new HitboxScene.Entry(TARGET, false), false, AIM, Exposure.OPEN,
                new HitboxScene.Entry(new Box(4, 0, 9, 4.6, 1.8, 9.6), false));
        List<ColoredBox> parts = HitboxMeshBuilder.build(scene, s, CAMERA);
        assertEquals(s.rangeColor(), parts.get(0).argb());
        assertEquals(s.mainColor() & 0xFFFFFF, parts.get(12).argb() & 0xFFFFFF, "main fill follows the others");
    }

    @Test
    void maxOthersCapsTheDrawnOutlines() {
        HitboxSettings two = new HitboxSettings(true, 48, true, 0.12, true, 2, true, 2,
                0xFF4D8DFF, 0xFFFFD93D, 0xFFFF5B54, 0xFFB36BFF, 0xFF4DE8E0);
        HitboxScene.Entry[] many = new HitboxScene.Entry[5];
        for (int i = 0; i < many.length; i++) {
            many[i] = new HitboxScene.Entry(new Box(4 + i, 0, 9, 4.6 + i, 1.8, 9.6), false);
        }
        HitboxScene scene = scene(null, false, null, Exposure.OPEN, many);
        assertEquals(24, HitboxMeshBuilder.build(scene, two, CAMERA).size());
    }

    @Test
    void showOthersOffDrawsOnlyTheMainTarget() {
        HitboxSettings noOthers = new HitboxSettings(true, 48, true, 0.12, true, 2, false, 16,
                0xFF4D8DFF, 0xFFFFD93D, 0xFFFF5B54, 0xFFB36BFF, 0xFF4DE8E0);
        HitboxScene scene = scene(new HitboxScene.Entry(TARGET, false), false, AIM, Exposure.OPEN,
                new HitboxScene.Entry(new Box(4, 0, 9, 4.6, 1.8, 9.6), false));
        assertEquals(1 + 12 + 3, HitboxMeshBuilder.build(scene, noOthers, CAMERA).size());
    }

    @Test
    void aCoveredTargetIsPurpleOutlineAndCross() {
        HitboxScene scene = scene(new HitboxScene.Entry(TARGET, true), false, AIM, Exposure.COVERED);
        List<ColoredBox> parts = HitboxMeshBuilder.build(scene, s, CAMERA);
        assertEquals(15, count(parts, s.coverColor()));
        assertEquals(0, count(parts, s.attackColor()));
    }

    @Test
    void aShieldedTargetGetsACyanCross() {
        HitboxScene scene = scene(new HitboxScene.Entry(TARGET, false), false, AIM, Exposure.SHIELDED);
        List<ColoredBox> parts = HitboxMeshBuilder.build(scene, s, CAMERA);
        assertEquals(3, count(parts, s.shieldColor()), "three bars make the cross");
        assertEquals(12, count(parts, s.mainColor()));
    }

    @Test
    void lineThicknessScalesWithCameraDistance() {
        HitboxScene scene = scene(new HitboxScene.Entry(TARGET, false), true, null, Exposure.OPEN);
        double near = thinnestEdge(HitboxMeshBuilder.build(scene, s, new V3(0, 1, 6)));
        double far = thinnestEdge(HitboxMeshBuilder.build(scene, s, new V3(0, 1, -40)));
        assertTrue(far > near, near + " vs " + far);
    }

    private static double thinnestEdge(List<ColoredBox> parts) {
        // Skip the fill (index 0): edges are the thin boxes.
        return parts.stream().skip(1)
                .mapToDouble(b -> Math.min(b.box().width(), Math.min(b.box().height(), b.box().depth())))
                .min().orElseThrow();
    }
}
