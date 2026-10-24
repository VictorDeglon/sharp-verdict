package dev.sharp.core.targeting;

import dev.sharp.core.math.Box;

/**
 * One entity the selector may pick.
 *
 * @param id           stable identifier of the entity for as long as it exists (the entity id)
 * @param label        human readable name, only used for debug output
 * @param box          current bounding box in world coordinates
 * @param facingYaw    which way its head points, in degrees (same convention as {@code AimMath}); only matters with a shield up
 * @param shieldUp     whether it is holding a shield up right now
 * @param tier         what kind of entity it is, which decides how strongly it is preferred
 */
public record TargetCandidate(int id, String label, Box box, double facingYaw, boolean shieldUp, TargetTier tier) {
    public TargetCandidate(int id, String label, Box box, double facingYaw, boolean shieldUp) {
        this(id, label, box, facingYaw, shieldUp, TargetTier.OTHER);
    }

    public TargetCandidate(int id, String label, Box box) {
        this(id, label, box, 0.0, false);
    }

    public TargetCandidate withBox(Box newBox) {
        return new TargetCandidate(id, label, newBox, facingYaw, shieldUp, tier);
    }
}
