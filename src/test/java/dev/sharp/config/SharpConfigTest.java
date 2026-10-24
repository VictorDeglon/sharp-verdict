package dev.sharp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.camera.AimSettings;
import dev.sharp.core.render.HitboxSettings;
import dev.sharp.core.targeting.TargetingSettings;
import org.junit.jupiter.api.Test;

class SharpConfigTest {

    @Test
    void defaultsAreAlreadyValid() {
        String before = ConfigStore.toJson(new SharpConfig());
        String after = ConfigStore.toJson(new SharpConfig().sanitize());
        assertEquals(before, after, "sanitize() must not change the defaults");
    }

    @Test
    void sanitizeIsIdempotent() {
        SharpConfig c = wild();
        c.sanitize();
        String once = ConfigStore.toJson(c);
        c.sanitize();
        assertEquals(once, ConfigStore.toJson(c));
    }

    @Test
    void defaultsMatchTheCoreDefaults() {
        SharpConfig c = new SharpConfig();
        assertEquals(TargetingSettings.defaults(), c.targeting());
        assertEquals(AimSettings.defaults(), c.aim());
        assertEquals(HitboxSettings.defaults(), c.hitbox());
    }

    @Test
    void numbersAreClampedIntoTheirRanges() {
        SharpConfig c = wild().sanitize();
        assertEquals(128.0, c.range);
        assertEquals(1.0, c.fovDegrees);
        assertEquals(1.0, c.aimHeight);
        assertEquals(0.0, c.distanceWeight);
        assertEquals(50.0, c.retainMarginPercent);
        assertEquals(5.0, c.cameraSensitivity);
        assertEquals(0.0, c.cameraSmoothing);
        assertEquals(10.0, c.maxRotationSpeed);
        assertEquals(20000.0, c.maxRotationAcceleration);
        assertEquals(0.1, c.maxStepPerFrame);
        assertEquals(5.0, c.deadzoneDegrees);
        assertEquals(3.0, c.rampSeconds);
        assertEquals(2000.0, c.yieldRate);
        assertEquals(256.0, c.hitboxRenderDistance);
        assertEquals(1.0, c.hitboxFillAlpha);
        assertEquals(0.5, c.hitboxLineWidth);
        assertEquals(32, c.hitboxMaxOthers);
    }

    @Test
    void nonFiniteNumbersFallBackToDefaults() {
        SharpConfig c = new SharpConfig();
        c.range = Double.NaN;
        c.fovDegrees = Double.POSITIVE_INFINITY;
        c.cameraSmoothing = Double.NEGATIVE_INFINITY;
        c.sanitize();
        assertEquals(64.0, c.range);
        assertEquals(140.0, c.fovDegrees);
        assertEquals(0.2, c.cameraSmoothing);
    }

    @Test
    void pitchRangeMustBeUsable() {
        SharpConfig c = new SharpConfig();
        c.pitchMin = 40;
        c.pitchMax = -40;
        c.sanitize();
        assertEquals(-85.0, c.pitchMin);
        assertEquals(85.0, c.pitchMax);

        SharpConfig narrow = new SharpConfig();
        narrow.pitchMin = -60;
        narrow.pitchMax = 30;
        narrow.sanitize();
        assertEquals(-60.0, narrow.pitchMin);
        assertEquals(30.0, narrow.pitchMax);
    }

    @Test
    void markerIsTrimmedNeverEmptyAndBounded() {
        SharpConfig c = new SharpConfig();
        c.practiceMarker = "  Dummy  ";
        assertEquals("Dummy", c.sanitize().practiceMarker);
        c.practiceMarker = "   ";
        assertEquals(SharpConfig.DEFAULT_MARKER, c.sanitize().practiceMarker);
        c.practiceMarker = null;
        assertEquals(SharpConfig.DEFAULT_MARKER, c.sanitize().practiceMarker);
        c.practiceMarker = "x".repeat(100);
        assertEquals(32, c.sanitize().practiceMarker.length());
    }

    @Test
    void colourTextIsNormalisedOrReplaced() {
        SharpConfig c = new SharpConfig();
        c.hitboxMainColor = "ff0000";
        c.hitboxRangeColor = "not a colour";
        c.hitboxAttackColor = null;
        c.sanitize();
        assertEquals("#FF0000", c.hitboxMainColor);
        assertEquals("#FFD93D", c.hitboxRangeColor);
        assertEquals("#FF5B54", c.hitboxAttackColor);
        assertEquals(0xFFFF0000, c.hitbox().mainColor());
        assertEquals(0xFFFFD93D, c.hitbox().rangeColor());
    }

    @Test
    void snapshotsReflectCurrentValues() {
        SharpConfig c = new SharpConfig();
        c.range = 30;
        c.cameraSensitivity = 2.5;
        c.hitboxFill = false;
        assertEquals(30.0, c.targeting().range());
        assertEquals(2.5, c.aim().sensitivity());
        assertTrue(!c.hitbox().fill());
    }

    private static SharpConfig wild() {
        SharpConfig c = new SharpConfig();
        c.range = 1_000;
        c.fovDegrees = -5;
        c.aimHeight = 7;
        c.distanceWeight = -1;
        c.retainMarginPercent = 900;
        c.cameraSensitivity = 99;
        c.cameraSmoothing = -3;
        c.maxRotationSpeed = 0;
        c.maxRotationAcceleration = 1e9;
        c.maxStepPerFrame = -4;
        c.deadzoneDegrees = 50;
        c.rampSeconds = 99;
        c.yieldRate = 1e6;
        c.hitboxRenderDistance = 1e6;
        c.hitboxFillAlpha = 4;
        c.hitboxLineWidth = 0;
        c.hitboxMaxOthers = 500;
        return c;
    }

    @Test
    void theDefaultsAreWideAndAimLow() {
        SharpConfig c = new SharpConfig();
        assertTrue(c.range >= 64.0, "range in blocks: " + c.range);
        assertTrue(c.fovDegrees >= 140.0, "cone in degrees: " + c.fovDegrees);
        assertTrue(c.aimHeight <= 0.35, "the fallback aim point is low: " + c.aimHeight);
        assertTrue(c.aimFeetFirst);
        assertTrue(c.aimShieldRear);
        assertTrue(c.aimAvoidCover);
    }

    @Test
    void theStatusTextIsAutoByDefaultAndTheStyleIsSanitised() {
        SharpConfig c = new SharpConfig();
        assertEquals("auto", c.statusStyle);
        assertEquals(3.0, c.statusFullSeconds);
        assertEquals(dev.sharp.core.hud.CueStyle.AUTO, c.cueStyle());

        c.statusStyle = " Minimal ";
        assertEquals("minimal", c.sanitize().statusStyle);
        c.statusStyle = "nonsense";
        assertEquals("auto", c.sanitize().statusStyle);
        c.statusStyle = null;
        assertEquals("auto", c.sanitize().statusStyle);
        c.statusStyle = "OFF";
        assertEquals(dev.sharp.core.hud.CueStyle.OFF, c.sanitize().cueStyle());
    }

    @Test
    void theFullTextTimeIsClamped() {
        SharpConfig c = new SharpConfig();
        c.statusFullSeconds = -4;
        assertEquals(0.0, c.sanitize().statusFullSeconds);
        c.statusFullSeconds = 1e9;
        assertEquals(30.0, c.sanitize().statusFullSeconds);
        c.statusFullSeconds = Double.NaN;
        assertEquals(3.0, c.sanitize().statusFullSeconds);
    }

    @Test
    void theAimSwitchesReachTheCore() {
        SharpConfig c = new SharpConfig();
        c.aimFeetFirst = false;
        c.aimShieldRear = false;
        c.aimAvoidCover = false;
        TargetingSettings t = c.targeting();
        assertTrue(!t.feetFirst() && !t.shieldRear() && !t.avoidCover());
    }

    @Test
    void migratingKeepsOnlyTheSwitches() {
        SharpConfig old = new SharpConfig();
        old.practiceMarker = "bot";
        old.enabled = false;
        old.cameraTrackingEnabled = true;
        old.debugLogging = true;
        old.range = 5;
        old.hitboxMainColor = "#000000";
        SharpConfig fresh = SharpConfig.migratedFrom(old);
        assertEquals("bot", fresh.practiceMarker);
        assertTrue(!fresh.enabled && fresh.cameraTrackingEnabled && fresh.debugLogging);
        assertEquals(64.0, fresh.range);
        assertEquals("#4D8DFF", fresh.hitboxMainColor);
    }
}
