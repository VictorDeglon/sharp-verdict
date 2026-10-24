package dev.sharp.core.targeting;

import dev.sharp.core.math.Box;
import dev.sharp.core.math.V3;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the exact spot on the locked target to aim at.
 *
 * <p>The spots are tried in order of preference and the first one that is really hittable wins:
 *
 * <ol>
 *   <li>the feet zone, when {@code feetFirst} is on,
 *   <li>the configured {@code aimHeight} (low by default),
 *   <li>the middle, then the upper body.
 * </ol>
 *
 * <p>All heights are fractions of the box's current height, so the spot follows the box when it shrinks (elytra, swimming, crawling)
 * and is always inside it. A spot counts as hittable when no block is between the eye and it and no <em>other</em> hitbox is entered
 * before the target's own. Spots to the left and right of the middle are tried too, so a target half hidden behind another one is
 * aimed at where it sticks out.
 *
 * <p>With a shield up the spot moves to the back of the box. Vanilla decides whether a shield blocks a hit from where the
 * <em>attacker stands</em>, not from where the crosshair is, so the result also says whether the shield is currently in the player's
 * way.
 */
public final class AimPointPicker {
    /** Height of the feet zone, as a fraction of the box. High enough above the bottom edge that tracking error still hits. */
    static final double FEET_FRACTION = 0.12;
    static final double MIDDLE_FRACTION = 0.5;
    static final double UPPER_FRACTION = 0.8;
    /** How far to the side the sideways spots sit, as a fraction of the half width. */
    static final double SIDE_FRACTION = 0.65;
    /** How far toward the back a shielded target is aimed at, as a fraction of its half depth along that direction. */
    static final double REAR_FRACTION = 0.7;

    /** Which part of the body the chosen spot is on, for the on-screen text. */
    public enum Zone {
        FEET, LOW, MIDDLE, UPPER;

        static Zone of(double fraction) {
            if (fraction <= 0.2) {
                return FEET;
            }
            if (fraction <= 0.4) {
                return LOW;
            }
            return fraction <= 0.65 ? MIDDLE : UPPER;
        }
    }

    /**
     * @param point           where to aim
     * @param exposure        what is in the way
     * @param zone            part of the body the point is on
     * @param shieldBlocksYou true when the target holds a shield up and the player stands in front of it, where the shield blocks
     */
    public record Choice(V3 point, Exposure exposure, Zone zone, boolean shieldBlocksYou) {
    }

    private AimPointPicker() {
    }

    /**
     * @param target the locked target, with its box already interpolated for this frame
     * @param all    every candidate, to find boxes that cover the target
     */
    public static Choice pick(V3 eye, TargetCandidate target, List<TargetCandidate> all, TargetingSettings s, LineOfSight los) {
        return pick(eye, target, all, s, los, Double.NaN);
    }

    /**
     * As above, but a finite {@code preferredFraction} (0 feet .. 1 top, from {@link AimHeightPlanner}) is tried before the usual order.
     * It is only a preference: cover and line of sight still move the choice to the next spot, exactly as for the usual ones.
     */
    public static Choice pick(
            V3 eye, TargetCandidate target, List<TargetCandidate> all, TargetingSettings s, LineOfSight los, double preferredFraction) {
        Box box = target.box();
        double fallbackFraction = Double.isFinite(preferredFraction) ? preferredFraction : s.feetFirst() ? FEET_FRACTION : s.aimHeight();
        if (box.contains(eye)) {
            return new Choice(box.pointAtHeight(s.aimHeight()), Exposure.INSIDE, Zone.of(fallbackFraction), false);
        }

        boolean shielded = s.shieldRear() && target.shieldUp();
        double[] forward = {-Math.sin(Math.toRadians(target.facingYaw())), Math.cos(Math.toRadians(target.facingYaw()))};
        boolean shieldBlocksYou = shielded
                && ((eye.x() - box.center().x()) * forward[0] + (eye.z() - box.center().z()) * forward[1]) > 0.0;

        List<Double> fractions = fractions(s, preferredFraction);
        List<double[]> sides = sideOffsets(eye, box, s.avoidCover());
        List<V3> bases = new ArrayList<>();
        for (double[] side : sides) {
            bases.add(new V3(box.center().x() + side[0], 0.0, box.center().z() + side[1]));
        }
        if (shielded) {
            double halfAlong = Math.abs(forward[0]) * box.width() * 0.5 + Math.abs(forward[1]) * box.depth() * 0.5;
            double back = REAR_FRACTION * halfAlong;
            bases.replaceAll(b -> new V3(b.x() - forward[0] * back, 0.0, b.z() - forward[1] * back));
        }

        V3 firstSeen = null;
        double firstSeenFraction = 0.0;
        boolean preferredCovered = false;
        int index = 0;
        for (double f : fractions) {
            for (V3 base : bases) {
                V3 point = new V3(base.x(), box.minY() + box.height() * f, base.z());
                int position = index++;
                if (s.requireLineOfSight() && !los.visible(eye, point)) {
                    continue;
                }
                if (firstSeen == null) {
                    firstSeen = point;
                    firstSeenFraction = f;
                }
                if (s.avoidCover() && coveredByOther(eye, point, target, all)) {
                    preferredCovered |= position == 0;
                    continue;
                }
                Exposure exposure = shielded ? Exposure.SHIELDED : preferredCovered ? Exposure.PARTLY_COVERED : Exposure.OPEN;
                return new Choice(point, exposure, Zone.of(f), shieldBlocksYou);
            }
        }

        if (firstSeen != null) {
            // Seen, but every spot is behind another hitbox.
            return new Choice(firstSeen, Exposure.COVERED, Zone.of(firstSeenFraction), shieldBlocksYou);
        }
        // Blocks hide every spot. The selector already decides whether such a target may be locked; just aim at the preferred spot.
        V3 preferred = new V3(bases.get(0).x(), box.minY() + box.height() * fractions.get(0), bases.get(0).z());
        return new Choice(preferred, shielded ? Exposure.SHIELDED : Exposure.OPEN, Zone.of(fractions.get(0)), shieldBlocksYou);
    }

    private static List<Double> fractions(TargetingSettings s, double preferredFraction) {
        List<Double> out = new ArrayList<>(5);
        if (Double.isFinite(preferredFraction)) {
            out.add(Math.max(0.0, Math.min(1.0, preferredFraction)));
        }
        if (s.feetFirst() && !(out.size() == 1 && Math.abs(out.get(0) - FEET_FRACTION) < 0.05)) {
            out.add(FEET_FRACTION);
        }
        for (double f : new double[] {s.aimHeight(), MIDDLE_FRACTION, UPPER_FRACTION}) {
            boolean duplicate = false;
            for (double have : out) {
                if (Math.abs(have - f) < 0.05) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                out.add(f);
            }
        }
        return out;
    }

    /** Offsets in the horizontal plane, perpendicular to the view direction: middle, then right, then left. */
    private static List<double[]> sideOffsets(V3 eye, Box box, boolean avoidCover) {
        List<double[]> out = new ArrayList<>(3);
        out.add(new double[] {0.0, 0.0});
        if (!avoidCover) {
            return out;
        }
        V3 c = box.center();
        double dx = c.x() - eye.x();
        double dz = c.z() - eye.z();
        double len = Math.hypot(dx, dz);
        if (len < 1.0e-6) {
            return out;
        }
        // Right-hand perpendicular of the view direction, scaled to the box half extent along it.
        double px = -dz / len;
        double pz = dx / len;
        double half = Math.abs(px) * box.width() * 0.5 + Math.abs(pz) * box.depth() * 0.5;
        double side = SIDE_FRACTION * half;
        out.add(new double[] {px * side, pz * side});
        out.add(new double[] {-px * side, -pz * side});
        return out;
    }

    /** True when a ray from the eye to the point enters another candidate's box before it enters the target's. */
    static boolean coveredByOther(V3 eye, V3 point, TargetCandidate target, List<TargetCandidate> all) {
        V3 delta = point.sub(eye);
        double length = delta.length();
        if (length < 1.0e-9) {
            return false;
        }
        V3 dir = new V3(delta.x() / length, delta.y() / length, delta.z() / length);
        double own = target.box().rayEntry(eye, dir);
        if (Double.isNaN(own)) {
            return false;
        }
        for (TargetCandidate other : all) {
            if (other.id() == target.id() || !other.box().isFinite() || other.box().contains(eye)) {
                continue;
            }
            double t = other.box().rayEntry(eye, dir);
            if (!Double.isNaN(t) && t < own - 1.0e-6) {
                return true;
            }
        }
        return false;
    }
}
