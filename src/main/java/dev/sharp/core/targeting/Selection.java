package dev.sharp.core.targeting;

import dev.sharp.core.math.V3;
import java.util.List;

/**
 * Result of one selection pass.
 *
 * @param targetId            selected entity id, or {@link #NO_TARGET}
 * @param kind                what happened compared with the previous pass
 * @param aimPoint            the point on the target that is aimed at, or null without a target
 * @param angularErrorDegrees angle between the crosshair and the aim point, 0 without a target
 * @param distance            distance from the eye to the target's box, 0 without a target
 * @param seen                number of candidates that were offered
 * @param eligible            number of candidates inside range and angle limits
 * @param eligibleIds         ids of those candidates, best first
 */
public record Selection(
        int targetId,
        Kind kind,
        V3 aimPoint,
        double angularErrorDegrees,
        double distance,
        int seen,
        int eligible,
        List<Integer> eligibleIds) {

    public static final int NO_TARGET = -1;

    public enum Kind {
        /** No target now and none before. */
        NONE,
        /** A target was picked and there was none before. */
        ACQUIRED,
        /** The previous target is still valid and in limits, so it was kept. */
        KEPT,
        /** The previous target became invalid or left the limits and another one replaced it. */
        SWITCHED,
        /** The previous target became invalid or left the limits and nothing replaced it. */
        LOST
    }

    public boolean hasTarget() {
        return targetId != NO_TARGET;
    }

    public static Selection none(Kind kind, int seen, int eligible, List<Integer> eligibleIds) {
        return new Selection(NO_TARGET, kind, null, 0.0, 0.0, seen, eligible, eligibleIds);
    }

    public static Selection empty() {
        return none(Kind.NONE, 0, 0, List.of());
    }
}
