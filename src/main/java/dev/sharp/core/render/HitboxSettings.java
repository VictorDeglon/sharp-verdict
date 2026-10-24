package dev.sharp.core.render;

/**
 * Immutable hitbox visualisation settings.
 *
 * @param enabled         master switch for drawing
 * @param renderDistance  boxes farther than this from the camera are not drawn
 * @param fill            whether a faint translucent fill is drawn inside the main target's outline
 * @param fillAlpha       opacity of that fill, 0..1
 * @param aimMarker       whether a small cross marks the exact aim point
 * @param lineWidthPixels outline width in approximate screen pixels
 * @param showOthers      whether the other targets in range are outlined too
 * @param maxOthers       upper bound on how many of those are drawn
 * @param mainColor       outline of the locked target, ARGB (blue)
 * @param rangeColor      outline of another entity in range, ARGB (yellow)
 * @param attackColor     outline of an entity the held weapon can reach, ARGB (red)
 * @param coverColor      the main target when another hitbox covers it or the player is inside it, ARGB (purple)
 * @param shieldColor     the aim cross while the target holds a shield up, ARGB (cyan)
 */
public record HitboxSettings(
        boolean enabled,
        double renderDistance,
        boolean fill,
        double fillAlpha,
        boolean aimMarker,
        double lineWidthPixels,
        boolean showOthers,
        int maxOthers,
        int mainColor,
        int rangeColor,
        int attackColor,
        int coverColor,
        int shieldColor) {

    public static HitboxSettings defaults() {
        return new HitboxSettings(true, 96.0, true, 0.12, true, 2.0, true, 16, 0xFF4D8DFF, 0xFFFFD93D, 0xFFFF5B54,
                0xFFB36BFF, 0xFF4DE8E0);
    }
}
