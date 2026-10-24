package dev.sharp.core.hud;

import java.util.List;

/**
 * The few coloured lines of text shown above the hotbar while the key is held. Plain data: the client only has to draw it.
 *
 * @param lines   top to bottom; the first is the headline
 * @param warning true when the feature is not doing its job (it is off here, or there is nothing to lock), so the lines explain why
 * @param minimal true for the quiet single-line form, which is drawn dimmer and without a backing strip
 */
public record Cues(List<Line> lines, boolean warning, boolean minimal) {
    public static final Cues NONE = new Cues(List.of(), false, false);

    public Cues(List<Line> lines, boolean warning) {
        this(lines, warning, false);
    }

    /** One run of text in one colour. */
    public record Segment(String text, int argb) {
    }

    /** Segments drawn left to right on one row. */
    public record Line(List<Segment> segments) {
        public String plain() {
            StringBuilder sb = new StringBuilder();
            for (Segment s : segments) {
                sb.append(s.text());
            }
            return sb.toString();
        }
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** The headline and the reason under it on one line, for the log and the game test: "NO TARGET: needs a mob named ...". */
    public String warningText() {
        if (lines.isEmpty()) {
            return "";
        }
        return lines.size() > 1 ? headline() + ": " + lines.get(1).plain() : headline();
    }

    /** The headline as plain text, empty when there are no lines. */
    public String headline() {
        return lines.isEmpty() ? "" : lines.get(0).plain();
    }
}
