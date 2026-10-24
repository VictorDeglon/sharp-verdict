package dev.sharp.core.render;

import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import dev.sharp.core.targeting.Exposure;
import java.util.List;

/**
 * Everything the renderer needs for one frame, already in world coordinates and already interpolated.
 * Null/empty means there is nothing to draw.
 *
 * @param main     the locked target, or null when nothing is locked
 * @param tracking whether the camera is actively following the main target
 * @param aimPoint exact point the assist aims at on the main target
 * @param exposure what is in the way of the main target (another hitbox, a shield, the player's own eye)
 * @param others   the other practice entities in range, nearest first
 */
public record HitboxScene(Entry main, boolean tracking, V3 aimPoint, Exposure exposure, List<Entry> others) {
    public static final HitboxScene EMPTY = new HitboxScene(null, false, null, Exposure.OPEN, List.of());

    /**
     * One outlined entity.
     *
     * @param box        its real bounding box
     * @param attackable whether the held weapon could hit it from where the player stands
     */
    public record Entry(Box box, boolean attackable) {
    }

    public boolean isEmpty() {
        return main == null && others.isEmpty();
    }
}
