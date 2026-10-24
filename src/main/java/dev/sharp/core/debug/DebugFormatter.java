package dev.sharp.core.debug;

import dev.sharp.core.targeting.Selection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Turns a {@link DebugSnapshot} into the lines of the on-screen panel. */
public final class DebugFormatter {
    private DebugFormatter() {
    }

    public static List<String> lines(DebugSnapshot d) {
        List<String> out = new ArrayList<>(8);
        out.add("SHARP: Verdict debug");

        out.add(String.format(Locale.ROOT, "state: %s%s  key: %s  armed: %s",
                d.active() ? "ACTIVE" : "off",
                d.active() ? String.format(Locale.ROOT, " (%.1fs)", d.activeSeconds()) : "",
                d.keyDown() ? "held" : "up",
                d.armed() ? "yes" : "no"));

        out.add("last off: " + d.lastOffReason() + "  gate: " + d.gate());

        Selection s = d.selection();
        if (!d.active()) {
            out.add("target: -");
        } else if (s.hasTarget()) {
            out.add(String.format(Locale.ROOT, "target: #%d \"%s\"  %s  dist %.1f  err %.2f deg%s",
                    s.targetId(), d.targetLabel(), s.kind(), s.distance(), d.angularError(), d.inReach() ? "  IN REACH" : ""));
        } else {
            out.add("target: none (" + s.kind() + ")");
        }
        if (d.active() && s.hasTarget()) {
            out.add(String.format(Locale.ROOT, "aim: %s  exposure %s%s  others %d (%d in reach)",
                    d.zone().name().toLowerCase(Locale.ROOT), d.exposure(), d.shieldBlocksYou() ? " (shield blocks you)" : "",
                    d.othersInRange(), d.othersInReach()));
        }
        out.add(String.format(Locale.ROOT, "seen %d  eligible %d", s.seen(), s.eligible()));

        if (!d.trackingEnabled()) {
            out.add("camera: tracking off");
        } else if (d.aim() == null) {
            out.add("camera: tracking on, idle");
        } else {
            out.add(String.format(Locale.ROOT,
                    "camera: err yaw %.2f pitch %.2f  step %.3f / %.3f",
                    d.aim().yawError(), d.aim().pitchError(), d.aim().deltaYaw(), d.aim().deltaPitch()));
            out.add(String.format(Locale.ROOT,
                    "        speed %.1f / %.1f deg/s  yield %.0f%%  ramp %.0f%%",
                    d.yawRate(), d.pitchRate(), d.aim().strength() * 100.0, d.aim().ramp() * 100.0));
        }
        return out;
    }
}
