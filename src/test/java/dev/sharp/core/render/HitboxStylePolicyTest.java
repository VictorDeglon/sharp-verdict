package dev.sharp.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.targeting.Exposure;
import org.junit.jupiter.api.Test;

class HitboxStylePolicyTest {
    private final HitboxSettings s = HitboxSettings.defaults();

    @Test
    void theMainTargetIsBlueTheOthersYellowAndWhatTheWeaponReachesRed() {
        assertEquals(s.mainColor(), HitboxStylePolicy.main(s, false, false, Exposure.OPEN).outlineColor());
        assertEquals(s.rangeColor(), HitboxStylePolicy.other(s, false).outlineColor());
        assertEquals(s.attackColor(), HitboxStylePolicy.other(s, true).outlineColor());
        assertEquals(0xFF4D8DFF, s.mainColor());
        assertEquals(0xFFFFD93D, s.rangeColor());
        assertEquals(0xFFFF5B54, s.attackColor());
    }

    @Test
    void theMainOutlineStaysBlueInReachButFillAndCrossTurnRed() {
        HitboxStyle plain = HitboxStylePolicy.main(s, false, false, Exposure.OPEN);
        HitboxStyle reach = HitboxStylePolicy.main(s, true, false, Exposure.OPEN);
        assertEquals(plain.outlineColor(), reach.outlineColor(), "blue stays the identity of the main target");
        assertEquals(s.mainColor(), plain.markerColor());
        assertEquals(s.attackColor(), reach.markerColor());
        assertEquals(s.attackColor() & 0xFFFFFF, reach.fillColor() & 0xFFFFFF);
        assertEquals(s.mainColor() & 0xFFFFFF, plain.fillColor() & 0xFFFFFF);
        assertTrue(Argb.alpha(reach.fillColor()) > Argb.alpha(plain.fillColor()), "reach is a stronger fill");
    }

    @Test
    void trackingIsThickerAndFillsHarder() {
        HitboxStyle still = HitboxStylePolicy.main(s, false, false, Exposure.OPEN);
        HitboxStyle tracking = HitboxStylePolicy.main(s, false, true, Exposure.OPEN);
        assertTrue(tracking.lineWidthPixels() > still.lineWidthPixels());
        assertTrue(Argb.alpha(tracking.fillColor()) > Argb.alpha(still.fillColor()));
    }

    @Test
    void coveredAndInsideTurnTheWholeTargetPurpleEvenInReach() {
        for (Exposure e : new Exposure[] {Exposure.COVERED, Exposure.INSIDE}) {
            for (boolean reach : new boolean[] {false, true}) {
                HitboxStyle style = HitboxStylePolicy.main(s, reach, false, e);
                assertEquals(s.coverColor(), style.outlineColor(), e + " reach=" + reach);
                assertEquals(s.coverColor(), style.markerColor());
                assertEquals(s.coverColor() & 0xFFFFFF, style.fillColor() & 0xFFFFFF);
            }
        }
        assertNotEquals(s.coverColor(), s.mainColor());
    }

    @Test
    void aPartlyCoveredTargetStaysBlueWithAPurpleCrossAndAShieldedOneGetsACyanCross() {
        HitboxStyle partly = HitboxStylePolicy.main(s, false, false, Exposure.PARTLY_COVERED);
        assertEquals(s.mainColor(), partly.outlineColor());
        assertEquals(s.coverColor(), partly.markerColor());

        HitboxStyle shield = HitboxStylePolicy.main(s, true, false, Exposure.SHIELDED);
        assertEquals(s.mainColor(), shield.outlineColor());
        assertEquals(s.shieldColor(), shield.markerColor(), "the shield cue wins over the reach cue on the cross");
    }

    @Test
    void theFiveMeaningsUseFiveDifferentColours() {
        java.util.Set<Integer> colours = new java.util.HashSet<>(java.util.List.of(
                s.mainColor(), s.rangeColor(), s.attackColor(), s.coverColor(), s.shieldColor()));
        assertEquals(5, colours.size());
    }

    @Test
    void othersAreThinPlainOutlines() {
        HitboxStyle main = HitboxStylePolicy.main(s, false, false, Exposure.OPEN);
        HitboxStyle other = HitboxStylePolicy.other(s, false);
        assertTrue(other.lineWidthPixels() < main.lineWidthPixels());
        assertFalse(other.fill());
        assertFalse(other.aimMarker());
    }

    @Test
    void settingsToggleFillAndMarker() {
        HitboxSettings bare = new HitboxSettings(true, 48, false, 0.12, false, 2, false, 4,
                0xFF4D8DFF, 0xFFFFD93D, 0xFFFF5B54, 0xFFB36BFF, 0xFF4DE8E0);
        HitboxStyle off = HitboxStylePolicy.main(bare, false, true, Exposure.OPEN);
        assertFalse(off.fill());
        assertFalse(off.aimMarker());
        HitboxStyle on = HitboxStylePolicy.main(s, false, false, Exposure.OPEN);
        assertTrue(on.fill());
        assertTrue(on.aimMarker());
    }

    @Test
    void fillOpacityFollowsTheSetting() {
        HitboxSettings faint = new HitboxSettings(true, 48, true, 0.05, true, 2, false, 4,
                0xFF4D8DFF, 0xFFFFD93D, 0xFFFF5B54, 0xFFB36BFF, 0xFF4DE8E0);
        HitboxSettings strong = new HitboxSettings(true, 48, true, 0.40, true, 2, false, 4,
                0xFF4D8DFF, 0xFFFFD93D, 0xFFFF5B54, 0xFFB36BFF, 0xFF4DE8E0);
        assertTrue(Argb.alpha(HitboxStylePolicy.main(strong, false, false, Exposure.OPEN).fillColor())
                > Argb.alpha(HitboxStylePolicy.main(faint, false, false, Exposure.OPEN).fillColor()));
    }
}
