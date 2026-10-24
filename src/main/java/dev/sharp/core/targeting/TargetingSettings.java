package dev.sharp.core.targeting;

/**
 * Immutable targeting limits and weights.
 *
 * @param range               maximum distance in blocks from the eye to the target's bounding box
 * @param fovDegrees          full cone angle around the crosshair, so 60 means 30 degrees to each side;
 *                            360 or more disables the angle limit
 * @param aimHeight           where on the box the target is aimed at when the feet are not preferred: 0 feet, 0.5 centre, 1 top.
 *                            Also the point targets are ranked by. Kept low so a target whose box shrinks (elytra, swimming,
 *                            crawling) still has the point inside it
 * @param distanceWeight      degrees of "angular penalty" added per block of distance. Small values keep
 *                            the crosshair the main criterion and use distance only to break near-ties
 * @param retainMarginPercent how much wider the range and angle limits are while keeping an existing lock.
 *                            0 means strictly the configured limits, a few percent stops flicker right on
 *                            the edge
 * @param requireLineOfSight  whether a target behind blocks is ineligible
 * @param feetFirst           aim at the feet when they can be seen, and fall back to {@code aimHeight} and higher
 * @param shieldRear          aim at the back of a target that holds a shield up
 * @param avoidCover          aim at a part of the target that another hitbox does not cover
 */
public record TargetingSettings(
        double range,
        double fovDegrees,
        double aimHeight,
        double distanceWeight,
        double retainMarginPercent,
        boolean requireLineOfSight,
        boolean feetFirst,
        boolean shieldRear,
        boolean avoidCover) {

    public TargetingSettings(
            double range, double fovDegrees, double aimHeight, double distanceWeight, double retainMarginPercent,
            boolean requireLineOfSight) {
        this(range, fovDegrees, aimHeight, distanceWeight, retainMarginPercent, requireLineOfSight, true, true, true);
    }

    public static TargetingSettings defaults() {
        return new TargetingSettings(64.0, 140.0, 0.3, 0.05, 0.0, true, true, true, true);
    }

    public double halfFovDegrees() {
        return fovDegrees * 0.5;
    }

    public boolean fovLimited() {
        return fovDegrees < 360.0;
    }
}
