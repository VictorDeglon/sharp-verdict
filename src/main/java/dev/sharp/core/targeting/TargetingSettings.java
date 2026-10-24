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
 *                            the crosshair the main criterion and use distance only to break near-ties. Only used when
 *                            {@code tiered} is off
 * @param retainMarginPercent how much wider the range and angle limits are while keeping an existing lock.
 *                            0 means strictly the configured limits, a few percent stops flicker right on
 *                            the edge
 * @param requireLineOfSight  whether a target behind blocks is ineligible
 * @param feetFirst           aim at the feet when they can be seen, and fall back to {@code aimHeight} and higher
 * @param shieldRear          aim at the back of a target that holds a shield up
 * @param avoidCover          aim at a part of the target that another hitbox does not cover
 * @param tiered              rank by distance with a handicap per kind of entity (players, then armor stands, then the rest) instead
 *                            of by the crosshair
 * @param armorStandPenalty   blocks added to an armor stand's distance when ranking, so a player beats it unless it is this much nearer
 * @param otherPenalty        the same for every other entity (animals); never less than {@code armorStandPenalty}
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
        boolean avoidCover,
        boolean tiered,
        double armorStandPenalty,
        double otherPenalty) {

    /** In tiered ranking, how many blocks of distance one degree away from the crosshair costs. 30 degrees off counts as 3 blocks. */
    public static final double ANGLE_BLOCKS_PER_DEGREE = 0.1;

    /** Crosshair-first ranking, as the first releases did it. */
    public TargetingSettings(
            double range, double fovDegrees, double aimHeight, double distanceWeight, double retainMarginPercent,
            boolean requireLineOfSight, boolean feetFirst, boolean shieldRear, boolean avoidCover) {
        this(range, fovDegrees, aimHeight, distanceWeight, retainMarginPercent, requireLineOfSight,
                feetFirst, shieldRear, avoidCover, false, 0.0, 0.0);
    }

    public TargetingSettings(
            double range, double fovDegrees, double aimHeight, double distanceWeight, double retainMarginPercent,
            boolean requireLineOfSight) {
        this(range, fovDegrees, aimHeight, distanceWeight, retainMarginPercent, requireLineOfSight, true, true, true);
    }

    public static TargetingSettings defaults() {
        return new TargetingSettings(64.0, 140.0, 0.3, 0.05, 0.0, true, true, true, true, true, 3.0, 6.0);
    }

    /** Blocks added to a candidate's distance when ranking: 0 for a player, then the handicaps for the lower tiers. */
    public double penalty(TargetTier tier) {
        return switch (tier) {
            case PLAYER -> 0.0;
            case ARMOR_STAND -> armorStandPenalty;
            case OTHER -> otherPenalty;
        };
    }

    public double halfFovDegrees() {
        return fovDegrees * 0.5;
    }

    public boolean fovLimited() {
        return fovDegrees < 360.0;
    }
}
