package dev.sharp.core.hud;

import dev.sharp.core.debug.DebugSnapshot;
import dev.sharp.core.hud.Cues.Line;
import dev.sharp.core.hud.Cues.Segment;
import dev.sharp.core.render.HitboxSettings;
import dev.sharp.core.targeting.Exposure;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides what the player is told while the key is held, in as few words as will do. The colours are the hitbox colours, so the text
 * and the outlines agree: blue is the target, yellow is in range, red is in reach, purple is covered.
 *
 * <p>Rows, top to bottom: the headline (state, target, distance), one detail row (why, reach, camera, the others, where it aims), and
 * for the first few seconds a colour key. In the quiet styles it is a single line: the state word and the distance.
 */
public final class CueBuilder {
    static final int WHITE = 0xFFFFFFFF;
    static final int GREY = 0xFFBDBDBD;
    static final int DIM = 0xFF8C8C8C;
    static final int WARN = 0xFFFF9F43;
    static final int GOOD = 0xFF7CE38B;

    /** The colour key shows for this long after the key goes down, long enough to read once and then out of the way. */
    static final double LEGEND_SECONDS = 3.0;
    /** The detail row never grows past this many characters, so it fits a small window. */
    static final int DETAIL_MAX_CHARS = 64;
    private static final String SEPARATOR = "  |  ";

    private CueBuilder() {
    }

    /**
     * @param marker      the name prefix that makes an entity a practice entity, to tell the player what to name a mob
     * @param range       the lock range in blocks
     * @param style       how loud to be; {@link CueStyle#OFF} says nothing at all
     * @param fullSeconds how long {@link CueStyle#AUTO} keeps the full text after the key goes down
     */
    public static Cues build(DebugSnapshot d, HitboxSettings colors, String marker, double range, CueStyle style, double fullSeconds) {
        if (style == CueStyle.OFF || !d.keyDown()) {
            return Cues.NONE;
        }
        boolean full = style == CueStyle.FULL || (style == CueStyle.AUTO && d.keyHeldSeconds() < fullSeconds);
        if (d.gate().startsWith("CLOSED: ")) {
            if (!full) {
                return new Cues(List.of(line(seg("SHARP OFF", WARN))), true, true);
            }
            return new Cues(List.of(
                    line(seg("SHARP OFF", WARN)),
                    line(seg(d.gate().substring("CLOSED: ".length()), GREY))), true);
        }
        if (!d.active()) {
            return Cues.NONE;
        }
        if (!d.selection().hasTarget()) {
            if (!full) {
                return new Cues(List.of(line(seg("NO TARGET", WARN))), true, true);
            }
            return new Cues(List.of(
                    line(seg("NO TARGET", WARN)),
                    line(seg(String.format(Locale.ROOT, "needs a mob named \"%s...\" within %d m, in front of you",
                            marker, Math.round(range)), GREY)),
                    legend(colors)), true);
        }

        Exposure exposure = d.exposure();
        int badgeColor;
        String badge;
        if (exposure == Exposure.INSIDE) {
            badge = "INSIDE";
            badgeColor = colors.coverColor();
        } else if (exposure == Exposure.COVERED) {
            badge = "COVERED";
            badgeColor = colors.coverColor();
        } else if (exposure == Exposure.SHIELDED) {
            badge = "SHIELD UP";
            badgeColor = colors.shieldColor();
        } else if (d.inReach()) {
            badge = "IN REACH";
            badgeColor = colors.attackColor();
        } else {
            badge = "LOCKED";
            badgeColor = colors.mainColor();
        }
        if (!full) {
            return new Cues(List.of(line(
                    seg(badge, badgeColor),
                    seg(String.format(Locale.ROOT, "  %.1f m", d.selection().distance()), DIM))), false, true);
        }
        Line headline = line(
                seg(badge, badgeColor),
                seg("  " + d.targetLabel(), WHITE),
                seg(String.format(Locale.ROOT, "  %.1f m", d.selection().distance()), GREY));

        // Most important first. The row is capped in width, so when a lot is going on the least important items drop off the end.
        List<Segment> items = new ArrayList<>();
        switch (exposure) {
            case INSIDE -> items.add(seg("you are inside its hitbox, camera holds", colors.coverColor()));
            case COVERED -> items.add(seg("another hitbox is in the way", colors.coverColor()));
            case PARTLY_COVERED -> items.add(seg("aiming at the part that sticks out", colors.coverColor()));
            case SHIELDED -> items.add(d.shieldBlocksYou()
                    ? seg("shield blocks you, get behind it", colors.shieldColor())
                    : seg("shield up, you are behind it", colors.shieldColor()));
            default -> {
                // Nothing special to say.
            }
        }
        if (!badge.equals("IN REACH")) {
            items.add(d.inReach() ? seg("in reach", colors.attackColor()) : seg("out of reach", DIM));
        }
        items.add(d.trackingEnabled() ? seg("camera on", GOOD) : seg("camera off", DIM));
        if (d.othersInRange() > 0) {
            String text = "+" + d.othersInRange() + " in range" + (d.othersInReach() > 0 ? " (" + d.othersInReach() + " in reach)" : "");
            items.add(seg(text, colors.rangeColor()));
        }
        items.add(seg("aim " + d.zone().name().toLowerCase(Locale.ROOT), GREY));

        List<Segment> detail = new ArrayList<>();
        int width = 0;
        for (Segment item : items) {
            int add = item.text().length() + (detail.isEmpty() ? 0 : SEPARATOR.length());
            if (!detail.isEmpty() && width + add > DETAIL_MAX_CHARS) {
                continue;
            }
            if (!detail.isEmpty()) {
                detail.add(seg(SEPARATOR, DIM));
            }
            detail.add(item);
            width += add;
        }
        List<Line> lines = new ArrayList<>(List.of(headline, new Line(List.copyOf(detail))));
        if (d.keyHeldSeconds() < LEGEND_SECONDS) {
            lines.add(legend(colors));
        }
        return new Cues(List.copyOf(lines), false);
    }

    /** The colour key, as the words themselves in their colours. */
    private static Line legend(HitboxSettings c) {
        return line(
                seg("TARGET", c.mainColor()), seg("   ", GREY),
                seg("IN RANGE", c.rangeColor()), seg("   ", GREY),
                seg("IN REACH", c.attackColor()), seg("   ", GREY),
                seg("COVERED", c.coverColor()));
    }

    private static Segment seg(String text, int argb) {
        return new Segment(text, argb);
    }

    private static Line line(Segment... segments) {
        return new Line(List.of(segments));
    }
}
