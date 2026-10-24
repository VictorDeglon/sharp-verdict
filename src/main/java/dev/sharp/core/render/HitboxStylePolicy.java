package dev.sharp.core.render;

import dev.sharp.core.targeting.Exposure;

/**
 * What each outlined entity looks like. The colours carry the meaning, so they are the first thing to read:
 *
 * <ul>
 *   <li><b>Blue</b>: the main target, the one the lock is on. Thicker while the camera is following it. When the held weapon
 *       can reach it, its fill and aim cross turn red, so one glance says "locked and hittable".
 *   <li><b>Yellow</b>: another target in range.
 *   <li><b>Red</b>: another target that the held weapon can reach from where the player stands.
 *   <li><b>Purple</b>: the main target when another hitbox covers every spot of it, or the player is inside it. The aim cross is
 *       purple too when it had to move to the part that sticks out.
 *   <li><b>Cyan</b>: the aim cross of a target that holds a shield up. It sits on the target's back.
 * </ul>
 */
public final class HitboxStylePolicy {
    static final double MAIN_WIDTH = 1.2;
    static final double TRACKING_WIDTH = 1.7;
    static final double OTHER_WIDTH = 0.8;

    private HitboxStylePolicy() {
    }

    public static HitboxStyle main(HitboxSettings s, boolean attackable, boolean tracking, Exposure exposure) {
        boolean blocked = exposure.blocksAim();
        int outline = blocked ? s.coverColor() : s.mainColor();
        int fillBase = blocked ? s.coverColor() : attackable ? s.attackColor() : s.mainColor();
        double fillAlpha = Math.min(1.0, s.fillAlpha() * (attackable || tracking || blocked ? 1.5 : 1.0));
        int marker;
        if (blocked || exposure == Exposure.PARTLY_COVERED) {
            marker = s.coverColor();
        } else if (exposure == Exposure.SHIELDED) {
            marker = s.shieldColor();
        } else {
            marker = attackable ? s.attackColor() : s.mainColor();
        }
        return new HitboxStyle(
                outline,
                Argb.withAlpha(fillBase, fillAlpha),
                marker,
                s.lineWidthPixels() * (tracking ? TRACKING_WIDTH : MAIN_WIDTH),
                s.fill(),
                s.aimMarker());
    }

    public static HitboxStyle other(HitboxSettings s, boolean attackable) {
        int color = attackable ? s.attackColor() : s.rangeColor();
        return new HitboxStyle(color, 0, color, s.lineWidthPixels() * OTHER_WIDTH, false, false);
    }
}
