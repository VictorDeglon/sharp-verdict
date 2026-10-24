package dev.sharp.client;

import dev.sharp.core.debug.DebugFormatter;
import dev.sharp.core.debug.DebugSnapshot;
import dev.sharp.core.hud.Cues;
import dev.sharp.core.math.V3;
import dev.sharp.core.render.HitboxMeshBuilder;
import dev.sharp.core.render.HitboxScene;
import dev.sharp.core.render.HitboxSettings;
import dev.sharp.core.render.HitboxStylePolicy;

/**
 * What the client game test (src/gametest) may read and set. Reads only, plus the one switch for camera tracking; nothing here
 * is used by the mod itself.
 */
public final class SharpTestHooks {
    private SharpTestHooks() {
    }

    private static DebugSnapshot snapshot() {
        SharpRuntime r = SharpClient.runtime();
        return r == null ? null : r.debugSnapshot();
    }

    public static boolean started() {
        return SharpClient.runtime() != null;
    }

    public static String lastHint() {
        SharpRuntime r = SharpClient.runtime();
        return r == null ? "" : r.lastHint();
    }

    public static boolean faulted() {
        SharpRuntime r = SharpClient.runtime();
        return r != null && r.isFaulted();
    }

    public static boolean active() {
        DebugSnapshot s = snapshot();
        return s != null && s.active();
    }

    public static String gate() {
        DebugSnapshot s = snapshot();
        return s == null ? "no snapshot" : s.gate();
    }

    public static String targetLabel() {
        DebugSnapshot s = snapshot();
        return s == null ? "" : s.targetLabel();
    }

    public static double angularError() {
        DebugSnapshot s = snapshot();
        return s == null ? Double.NaN : s.angularError();
    }

    public static String lastOffReason() {
        DebugSnapshot s = snapshot();
        return s == null ? "none" : s.lastOffReason().name();
    }

    public static boolean sceneHasTarget() {
        SharpRuntime r = SharpClient.runtime();
        return r != null && r.scene().main() != null;
    }

    /** Outline colour of the locked target as ARGB, or 0 when nothing is locked. */
    public static int mainOutlineColor() {
        SharpRuntime r = SharpClient.runtime();
        HitboxScene scene = r == null ? HitboxScene.EMPTY : r.scene();
        if (scene.main() == null) return 0;
        HitboxSettings hs = SharpClient.configs().get().hitbox();
        return HitboxStylePolicy.main(hs, scene.main().attackable(), scene.tracking(), scene.exposure()).outlineColor();
    }

    public static boolean mainAttackable() {
        SharpRuntime r = SharpClient.runtime();
        return r != null && r.scene().main() != null && r.scene().main().attackable();
    }

    public static String exposure() {
        SharpRuntime r = SharpClient.runtime();
        return r == null || r.scene().main() == null ? "none" : r.scene().exposure().name();
    }

    /** Colours of the other outlined entities, nearest first, as ARGB. */
    public static int[] otherOutlineColors() {
        SharpRuntime r = SharpClient.runtime();
        if (r == null) return new int[0];
        HitboxSettings hs = SharpClient.configs().get().hitbox();
        return r.scene().others().stream().mapToInt(e -> HitboxStylePolicy.other(hs, e.attackable()).outlineColor()).toArray();
    }

    /** The box drawn for the locked target as {minX, minY, minZ, maxX, maxY, maxZ}, or null. */
    public static double[] mainBox() {
        SharpRuntime r = SharpClient.runtime();
        return r == null || r.scene().main() == null ? null : toArray(r.scene().main().box());
    }

    /** The boxes drawn for the other entities, nearest first. */
    public static java.util.List<double[]> otherBoxes() {
        SharpRuntime r = SharpClient.runtime();
        if (r == null) return java.util.List.of();
        return r.scene().others().stream().map(e -> toArray(e.box())).toList();
    }

    private static double[] toArray(dev.sharp.core.math.Box b) {
        return new double[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
    }

    /** Where the camera is aimed on the locked target: {x, y, z}, or null. */
    public static double[] aimPoint() {
        SharpRuntime r = SharpClient.runtime();
        V3 p = r == null ? null : r.scene().aimPoint();
        return p == null ? null : new double[] {p.x(), p.y(), p.z()};
    }

    /** The coloured text rows above the hotbar as plain text, one string per row. */
    public static java.util.List<String> cueRows() {
        SharpRuntime r = SharpClient.runtime();
        if (r == null) return java.util.List.of();
        return r.cues().lines().stream().map(Cues.Line::plain).toList();
    }

    /** How many solid boxes the renderer is asked to draw this frame. */
    public static int sceneParts() {
        SharpRuntime r = SharpClient.runtime();
        if (r == null) return 0;
        return HitboxMeshBuilder.build(r.scene(), SharpClient.configs().get().hitbox(), new V3(0, 0, 0)).size();
    }

    public static void setStatusStyle(String style) {
        SharpClient.configs().get().statusStyle = style;
    }

    public static boolean cuesMinimal() {
        SharpRuntime r = SharpClient.runtime();
        return r != null && r.cues().minimal();
    }

    public static void setRequireMarker(boolean on) {
        SharpClient.configs().get().requireMarker = on;
    }

    public static void setCameraTracking(boolean on) {
        SharpClient.configs().get().cameraTrackingEnabled = on;
    }

    public static String panelText() {
        DebugSnapshot s = snapshot();
        return s == null ? "" : String.join("\n", DebugFormatter.lines(s));
    }
}
