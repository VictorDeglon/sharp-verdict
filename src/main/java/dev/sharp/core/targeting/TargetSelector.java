package dev.sharp.core.targeting;

import dev.sharp.core.math.AimMath;
import dev.sharp.core.math.V3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks a target. Stateless: the caller passes in the id it currently holds.
 *
 * <p>Rules:
 *
 * <ol>
 *   <li>A candidate is eligible when its box is within {@code range} of the eye and the angle between
 *       the crosshair and its aim point is within half the configured field of view.
 *   <li>Among eligible candidates the best one is the one closest to the <em>crosshair</em>. Distance
 *       is only a small penalty ({@code distanceWeight}), so a nearer entity behind the player's
 *       shoulder never beats a farther one the player is looking at.
 *   <li>If a target is already held, it is kept while it is still present, inside the limits (widened by
 *       the retain margin) and, when required, visible. Only when that stops being true does the
 *       selector pick anything else. A better-placed rival never steals the lock.
 * </ol>
 */
public final class TargetSelector {

    private record Scored(TargetCandidate candidate, V3 aimPoint, double distance, double angularError, double score) {
        boolean within(TargetingSettings s, double scale) {
            if (distance > s.range() * scale) {
                return false;
            }
            return !s.fovLimited() || angularError <= s.halfFovDegrees() * scale;
        }
    }

    private static final Comparator<Scored> BEST_FIRST = Comparator
            .comparingDouble(Scored::score)
            .thenComparingInt(sc -> sc.candidate().id());

    public Selection select(
            EyeState eye,
            List<TargetCandidate> candidates,
            int currentId,
            TargetingSettings settings,
            LineOfSight lineOfSight) {

        double keepScale = 1.0 + Math.max(0.0, Math.min(100.0, settings.retainMarginPercent())) / 100.0;
        boolean hadTarget = currentId != Selection.NO_TARGET;

        Scored current = null;
        List<Scored> inLimits = new ArrayList<>();
        for (TargetCandidate c : candidates) {
            if (!c.box().isFinite()) {
                continue;
            }
            Scored sc = score(c, eye, settings);
            if (c.id() == currentId) {
                current = sc;
            }
            if (sc.within(settings, 1.0)) {
                inLimits.add(sc);
            }
        }
        inLimits.sort(BEST_FIRST);
        List<Integer> eligibleIds = new ArrayList<>(inLimits.size());
        for (Scored sc : inLimits) {
            eligibleIds.add(sc.candidate().id());
        }
        List<Integer> ids = List.copyOf(eligibleIds);
        int seen = candidates.size();

        // 1. Keep the current target for as long as it is valid and inside the limits.
        if (current != null && current.within(settings, keepScale) && visible(current, eye, settings, lineOfSight)) {
            return chosen(current, Selection.Kind.KEPT, seen, ids);
        }

        // 2. Otherwise take the best eligible candidate that can be seen. The old target was just judged
        //    invalid, so it is not offered again.
        for (Scored sc : inLimits) {
            if (hadTarget && sc.candidate().id() == currentId) {
                continue;
            }
            if (visible(sc, eye, settings, lineOfSight)) {
                return chosen(sc, hadTarget ? Selection.Kind.SWITCHED : Selection.Kind.ACQUIRED, seen, ids);
            }
        }

        return Selection.none(hadTarget ? Selection.Kind.LOST : Selection.Kind.NONE, seen, ids.size(), ids);
    }

    private static Selection chosen(Scored sc, Selection.Kind kind, int seen, List<Integer> ids) {
        return new Selection(
                sc.candidate().id(), kind, sc.aimPoint(), sc.angularError(), sc.distance(), seen, ids.size(), ids);
    }

    private static Scored score(TargetCandidate c, EyeState eye, TargetingSettings s) {
        V3 aim = c.box().pointAtHeight(s.aimHeight());
        double distance = c.box().distanceTo(eye.eye());
        // A ray that starts inside a box hits it at distance 0, whichever way it points, so standing inside counts as dead centre.
        double error = c.box().contains(eye.eye())
                ? 0.0
                : AimMath.angularErrorDegrees(eye.eye(), eye.yawDegrees(), eye.pitchDegrees(), aim);
        return new Scored(c, aim, distance, error, error + s.distanceWeight() * distance);
    }

    private static boolean visible(Scored sc, EyeState eye, TargetingSettings s, LineOfSight los) {
        if (!s.requireLineOfSight()) {
            return true;
        }
        // The centre may be hidden behind an edge while the nearest corner is plainly visible.
        return los.visible(eye.eye(), sc.aimPoint())
                || los.visible(eye.eye(), sc.candidate().box().closestPoint(eye.eye()));
    }
}
