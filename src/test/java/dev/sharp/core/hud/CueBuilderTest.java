package dev.sharp.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.activation.ActivationTracker.Reason;
import dev.sharp.core.debug.DebugSnapshot;
import dev.sharp.core.hud.Cues.Line;
import dev.sharp.core.hud.Cues.Segment;
import dev.sharp.core.math.V3;
import dev.sharp.core.render.HitboxSettings;
import dev.sharp.core.targeting.AimPointPicker.Zone;
import dev.sharp.core.targeting.Exposure;
import dev.sharp.core.targeting.Selection;
import java.util.List;
import org.junit.jupiter.api.Test;

class CueBuilderTest {
    private final HitboxSettings colors = HitboxSettings.defaults();

    private static Selection locked() {
        return new Selection(7, Selection.Kind.KEPT, new V3(0, 0, 0), 1.0, 12.34, 3, 3, List.of(7));
    }

    private static DebugSnapshot snap(boolean keyDown, boolean active, String gate, Selection sel, boolean inReach, Exposure exposure,
            boolean shieldBlocksYou, boolean tracking, int others, int othersInReach) {
        return snapAt(1.0, keyDown, active, gate, sel, inReach, exposure, shieldBlocksYou, tracking, others, othersInReach);
    }

    private static DebugSnapshot snapAt(double seconds, boolean keyDown, boolean active, String gate, Selection sel, boolean inReach,
            Exposure exposure, boolean shieldBlocksYou, boolean tracking, int others, int othersInReach) {
        return new DebugSnapshot(active, keyDown, true, Reason.NONE, 1.0, gate, sel, sel.hasTarget() ? "Sharp Dummy" : "", tracking,
                null, 1.0, 0, 0, inReach, exposure, Zone.FEET, shieldBlocksYou, others, othersInReach, keyDown ? seconds : 0.0);
    }

    private Cues cues(DebugSnapshot d) {
        return CueBuilder.build(d, colors, "sharp", 64, CueStyle.AUTO, 3.0);
    }

    private Cues cues(DebugSnapshot d, CueStyle style) {
        return CueBuilder.build(d, colors, "sharp", 64, style, 3.0);
    }

    private static String text(Cues c) {
        StringBuilder sb = new StringBuilder();
        for (Line l : c.lines()) {
            sb.append(l.plain()).append('\n');
        }
        return sb.toString();
    }

    private static int colourOf(Line line, String containing) {
        for (Segment s : line.segments()) {
            if (s.text().contains(containing)) {
                return s.argb();
            }
        }
        throw new AssertionError("no segment with '" + containing + "' in " + line.plain());
    }

    @Test
    void nothingIsShownWhileTheKeyIsUp() {
        assertTrue(cues(snap(false, false, "OPEN", Selection.empty(), false, Exposure.OPEN, false, false, 0, 0)).isEmpty());
        assertTrue(cues(snap(false, true, "OPEN", locked(), false, Exposure.OPEN, false, false, 0, 0)).isEmpty());
    }

    @Test
    void aClosedGateSaysWhyInsteadOfFailingSilently() {
        Cues c = cues(snap(true, false, "CLOSED: not a single-player world", Selection.empty(), false, Exposure.OPEN, false, false, 0, 0));
        assertTrue(c.warning());
        assertEquals("SHARP OFF", c.headline());
        assertEquals("not a single-player world", c.lines().get(1).plain());
    }

    @Test
    void anOpenGateWithTheKeyHeldButNotActiveShowsNothing() {
        assertTrue(cues(snap(true, false, "OPEN (single-player)", Selection.empty(), false, Exposure.OPEN, false, false, 0, 0)).isEmpty());
    }

    @Test
    void noTargetTellsThePlayerWhatTheModNeeds() {
        Cues c = cues(snap(true, true, "OPEN", Selection.empty(), false, Exposure.OPEN, false, false, 0, 0));
        assertTrue(c.warning());
        assertEquals("NO TARGET", c.headline());
        assertTrue(c.lines().get(1).plain().contains("named \"sharp...\""), c.lines().get(1).plain());
        assertTrue(c.lines().get(1).plain().contains("64 m"), c.lines().get(1).plain());
    }

    @Test
    void noTargetDoesNotMentionANameWhenNoMarkerIsRequired() {
        Cues c = CueBuilder.build(snap(true, true, "OPEN", Selection.empty(), false, Exposure.OPEN, false, false, 0, 0),
                colors, null, 64, CueStyle.AUTO, 3.0);
        assertEquals("NO TARGET", c.headline());
        assertEquals("needs a target within 64 m, in front of you", c.lines().get(1).plain());
    }

    @Test
    void aPlainLockIsBlueWithNameAndDistance() {
        Cues c = cues(snap(true, true, "OPEN", locked(), false, Exposure.OPEN, false, false, 0, 0));
        assertFalse(c.warning());
        assertEquals("LOCKED  Sharp Dummy  12.3 m", c.headline());
        assertEquals(colors.mainColor(), colourOf(c.lines().get(0), "LOCKED"));
    }

    @Test
    void inReachIsRed() {
        Cues c = cues(snap(true, true, "OPEN", locked(), true, Exposure.OPEN, false, false, 0, 0));
        assertTrue(c.headline().startsWith("IN REACH  Sharp Dummy"));
        assertEquals(colors.attackColor(), colourOf(c.lines().get(0), "IN REACH"));
        assertFalse(c.lines().get(1).plain().contains("in reach  |"), "the headline already says it: " + c.lines().get(1).plain());
    }

    @Test
    void inReachIsRepeatedInTheDetailRowWhenAnotherStateTakesTheHeadline() {
        Cues c = cues(snap(true, true, "OPEN", locked(), true, Exposure.SHIELDED, true, false, 0, 0));
        assertEquals(colors.attackColor(), colourOf(c.lines().get(1), "in reach"));
    }

    @Test
    void outOfReachIsSaidInTheDetailRowToo() {
        Cues c = cues(snap(true, true, "OPEN", locked(), false, Exposure.OPEN, false, false, 0, 0));
        assertTrue(c.lines().get(1).plain().contains("out of reach"), c.lines().get(1).plain());
    }

    @Test
    void coveredAndInsideArePurple() {
        for (Exposure e : new Exposure[] {Exposure.COVERED, Exposure.INSIDE}) {
            Cues c = cues(snap(true, true, "OPEN", locked(), true, e, false, false, 0, 0));
            assertEquals(colors.coverColor(), c.lines().get(0).segments().get(0).argb(), e.name());
        }
        assertEquals("COVERED  Sharp Dummy  12.3 m", cues(snap(true, true, "OPEN", locked(), false, Exposure.COVERED, false, false, 0, 0)).headline());
        assertEquals("INSIDE  Sharp Dummy  12.3 m", cues(snap(true, true, "OPEN", locked(), false, Exposure.INSIDE, false, false, 0, 0)).headline());
    }

    @Test
    void aPartlyCoveredTargetStaysLockedAndExplainsTheAim() {
        Cues c = cues(snap(true, true, "OPEN", locked(), false, Exposure.PARTLY_COVERED, false, false, 0, 0));
        assertTrue(c.headline().startsWith("LOCKED"));
        assertTrue(c.lines().get(1).plain().contains("aiming at the part that sticks out"));
    }

    @Test
    void aShieldSaysWhetherItBlocksYou() {
        Cues blocks = cues(snap(true, true, "OPEN", locked(), false, Exposure.SHIELDED, true, false, 0, 0));
        assertTrue(blocks.headline().startsWith("SHIELD UP"));
        assertEquals(colors.shieldColor(), blocks.lines().get(0).segments().get(0).argb());
        assertTrue(blocks.lines().get(1).plain().contains("shield blocks you, get behind it"));

        Cues behind = cues(snap(true, true, "OPEN", locked(), false, Exposure.SHIELDED, false, false, 0, 0));
        assertTrue(behind.lines().get(1).plain().contains("shield up, you are behind it"));
    }

    @Test
    void theDetailRowShowsWhereItAimsTheCameraStateAndTheOthers() {
        Cues c = cues(snap(true, true, "OPEN", locked(), false, Exposure.OPEN, false, true, 3, 0));
        String detail = c.lines().get(1).plain();
        assertEquals("out of reach  |  camera on  |  +3 in range  |  aim feet", detail);
        assertEquals(colors.rangeColor(), colourOf(c.lines().get(1), "+3 in range"));
        assertEquals(colors.mainColor() == 0 ? 0 : 0xFF7CE38B, colourOf(c.lines().get(1), "camera on"));

        Cues reachable = cues(snap(true, true, "OPEN", locked(), false, Exposure.OPEN, false, true, 3, 1));
        assertTrue(reachable.lines().get(1).plain().contains("+3 in range (1 in reach)"), reachable.lines().get(1).plain());
        assertEquals(colors.rangeColor(), colourOf(reachable.lines().get(1), "+3 in range (1 in reach)"));

        Cues off = cues(snap(true, true, "OPEN", locked(), false, Exposure.OPEN, false, false, 0, 0));
        assertTrue(off.lines().get(1).plain().contains("camera off"));
        assertFalse(off.lines().get(1).plain().contains("in range"), "no others, nothing to say");
    }

    @Test
    void theDetailRowIsCappedAndTheLeastImportantItemsDropFirst() {
        Cues c = cues(snap(true, true, "OPEN", locked(), false, Exposure.COVERED, false, true, 5, 2));
        String detail = c.lines().get(1).plain();
        assertTrue(detail.length() <= CueBuilder.DETAIL_MAX_CHARS, detail.length() + ": " + detail);
        assertTrue(detail.startsWith("another hitbox is in the way"), "the reason always stays: " + detail);
        assertTrue(detail.contains("out of reach"), detail);
        assertFalse(detail.contains("aim feet"), "the aim zone is the first to go: " + detail);
    }

    @Test
    void theColourKeyShowsAtTheStartOnlyAndAlwaysWithNoTargetInFullStyle() {
        assertEquals(3, cues(snapAt(0.5, true, true, "OPEN", locked(), false, Exposure.OPEN, false, false, 0, 0), CueStyle.FULL).lines().size());
        assertEquals(2, cues(snapAt(3.5, true, true, "OPEN", locked(), false, Exposure.OPEN, false, false, 0, 0), CueStyle.FULL).lines().size());
        assertEquals(3, cues(snapAt(30.0, true, true, "OPEN", Selection.empty(), false, Exposure.OPEN, false, false, 0, 0), CueStyle.FULL).lines().size());
    }

    @Test
    void theLegendUsesTheHitboxColoursSoTextAndOutlinesAgree() {
        Cues c = cues(snap(true, true, "OPEN", locked(), false, Exposure.OPEN, false, false, 0, 0));
        Line legend = c.lines().get(c.lines().size() - 1);
        assertEquals(colors.mainColor(), colourOf(legend, "TARGET"));
        assertEquals(colors.rangeColor(), colourOf(legend, "IN RANGE"));
        assertEquals(colors.attackColor(), colourOf(legend, "IN REACH"));
        assertEquals(colors.coverColor(), colourOf(legend, "COVERED"));
    }

    @Test
    void customColoursFlowThrough() {
        HitboxSettings custom = new HitboxSettings(true, 96, true, 0.12, true, 2, true, 16,
                0xFF010203, 0xFF040506, 0xFF070809, 0xFF0A0B0C, 0xFF0D0E0F);
        Cues c = CueBuilder.build(snap(true, true, "OPEN", locked(), false, Exposure.OPEN, false, false, 0, 0), custom, "sharp", 64, CueStyle.AUTO, 3.0);
        assertEquals(0xFF010203, colourOf(c.lines().get(0), "LOCKED"));
    }

    @Test
    void noSegmentIsEverEmptyOrTransparent() {
        for (Exposure e : Exposure.values()) {
            for (boolean reach : new boolean[] {false, true}) {
                Cues c = cues(snap(true, true, "OPEN", locked(), reach, e, true, true, 2, 1));
                for (Line l : c.lines()) {
                    for (Segment s : l.segments()) {
                        assertFalse(s.text().isEmpty(), text(c));
                        assertEquals(0xFF, s.argb() >>> 24, "opaque: " + s);
                    }
                }
            }
        }
    }

    @Test
    void usesADotAsDecimalSeparatorWhateverTheDefaultLocale() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertTrue(cues(snap(true, true, "OPEN", locked(), false, Exposure.OPEN, false, false, 0, 0)).headline().contains("12.3 m"));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    // ---- styles -------------------------------------------------------------------------------------

    private DebugSnapshot lockedFor(double seconds) {
        return snapAt(seconds, true, true, "OPEN", locked(), false, Exposure.OPEN, false, false, 2, 0);
    }

    @Test
    void autoIsFullAtFirstThenCollapsesToOneQuietLine() {
        Cues early = cues(lockedFor(1.0), CueStyle.AUTO);
        assertFalse(early.minimal());
        assertEquals(3, early.lines().size());

        Cues later = cues(lockedFor(3.5), CueStyle.AUTO);
        assertTrue(later.minimal());
        assertEquals(1, later.lines().size());
        assertEquals("LOCKED  12.3 m", later.headline());
        assertEquals(colors.mainColor(), later.lines().get(0).segments().get(0).argb(), "the colour still carries the meaning");
    }

    @Test
    void autoUsesTheConfiguredFullTime() {
        assertFalse(CueBuilder.build(lockedFor(5.0), colors, "sharp", 64, CueStyle.AUTO, 10.0).minimal());
        assertTrue(CueBuilder.build(lockedFor(0.5), colors, "sharp", 64, CueStyle.AUTO, 0.0).minimal(), "0 means quiet from the start");
    }

    @Test
    void fullStaysFullButStillDropsTheColourKeyAfterAFewSeconds() {
        Cues late = cues(lockedFor(60.0), CueStyle.FULL);
        assertFalse(late.minimal());
        assertEquals(2, late.lines().size());
        assertTrue(late.headline().contains("sharp") || late.headline().contains("Sharp"));
    }

    @Test
    void minimalIsAlwaysOneLineEvenAtTheStart() {
        Cues c = cues(lockedFor(0.1), CueStyle.MINIMAL);
        assertTrue(c.minimal());
        assertEquals(1, c.lines().size());
    }

    @Test
    void offSaysNothingInAnyState() {
        assertTrue(cues(lockedFor(0.1), CueStyle.OFF).isEmpty());
        assertTrue(cues(snap(true, true, "OPEN", Selection.empty(), false, Exposure.OPEN, false, false, 0, 0), CueStyle.OFF).isEmpty());
        assertTrue(cues(snap(true, false, "CLOSED: not a single-player world", Selection.empty(), false, Exposure.OPEN, false, false, 0, 0),
                CueStyle.OFF).isEmpty());
    }

    @Test
    void theQuietWarningsAreOneWordAndStillCountAsWarnings() {
        Cues none = cues(snapAt(10.0, true, true, "OPEN", Selection.empty(), false, Exposure.OPEN, false, false, 0, 0));
        assertTrue(none.minimal() && none.warning());
        assertEquals("NO TARGET", none.headline());
        assertEquals(1, none.lines().size());

        Cues closed = cues(snapAt(10.0, true, false, "CLOSED: world is open to LAN", Selection.empty(), false, Exposure.OPEN, false, false, 0, 0));
        assertTrue(closed.minimal() && closed.warning());
        assertEquals("SHARP OFF", closed.headline());
        assertEquals("SHARP OFF", closed.warningText());
    }

    @Test
    void theQuietLineStillNamesTheStateInItsColour() {
        assertEquals(colors.attackColor(), cues(snapAt(9, true, true, "OPEN", locked(), true, Exposure.OPEN, false, false, 0, 0)).lines().get(0).segments().get(0).argb());
        assertEquals(colors.coverColor(), cues(snapAt(9, true, true, "OPEN", locked(), false, Exposure.COVERED, false, false, 0, 0)).lines().get(0).segments().get(0).argb());
        assertEquals(colors.shieldColor(), cues(snapAt(9, true, true, "OPEN", locked(), false, Exposure.SHIELDED, true, false, 0, 0)).lines().get(0).segments().get(0).argb());
        assertEquals("SHIELD UP  12.3 m", cues(snapAt(9, true, true, "OPEN", locked(), false, Exposure.SHIELDED, true, false, 0, 0)).headline());
    }

    @Test
    void nothingIsShownWhileTheKeyIsUpInAnyStyle() {
        for (CueStyle style : CueStyle.values()) {
            assertTrue(cues(snapAt(9, false, true, "OPEN", locked(), false, Exposure.OPEN, false, false, 0, 0), style).isEmpty(), style.name());
        }
    }
}
