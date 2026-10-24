package dev.sharp.config;

import dev.sharp.core.camera.AimSettings;
import dev.sharp.core.hud.CueStyle;
import dev.sharp.core.render.Argb;
import dev.sharp.core.render.HitboxSettings;
import dev.sharp.core.targeting.TargetingSettings;

/**
 * All user settings, stored as {@code config/sharp.json}.
 *
 * <p>The fields are plain public values so they map directly to JSON keys. Anything read from disk goes
 * through {@link #sanitize()}, which clamps every number to a safe range and replaces unusable values
 * with defaults, so a hand-edited file can never put the mod into a broken state. The getters below
 * hand the core immutable snapshots.
 */
public final class SharpConfig {
    public static final String DEFAULT_MARKER = "sharp";

    /**
     * Bumped when the meaning or the default of a setting changes enough that an old file would keep the mod behaving the old way.
     * A file written by an older version is backed up and its tuning replaced with the new defaults, see {@link #migratedFrom}.
     */
    public static final int CURRENT_VERSION = 2;

    /** Version of the file this was read from. Always written as {@link #CURRENT_VERSION}. */
    public int configVersion = CURRENT_VERSION;

    // ---- general -----------------------------------------------------------------------------------

    /** Master switch. When false the hold key does nothing. */
    public boolean enabled = true;

    /**
     * When true, only entities carrying the marker can be targeted, and everything else is ignored. When false (the default) every
     * living entity in range is a candidate, and {@link #practiceMarker} is not consulted.
     */
    public boolean requireMarker = false;

    /**
     * Which entities carry the marker, when {@link #requireMarker} is on. An entity does when its custom name starts with this
     * text (case-insensitive) or it is in a scoreboard team with exactly this name.
     */
    public String practiceMarker = DEFAULT_MARKER;

    // ---- targeting ---------------------------------------------------------------------------------

    /** Maximum distance in blocks, 1..128. */
    public double range = 64.0;

    /** Full cone angle around the crosshair in degrees, 1..360 (360 = no angle limit). */
    public double fovDegrees = 140.0;

    /**
     * Where on the box to aim when the feet are not wanted or not visible: 0 feet, 0.5 centre, 1 top. A fraction of the box's
     * current height, so it stays inside the box when the box shrinks (elytra, swimming, crawling).
     */
    public double aimHeight = 0.3;

    /** Aim at the feet when they can be seen, and fall back to {@link #aimHeight}. */
    public boolean aimFeetFirst = true;

    /** Aim at the back of a target that holds a shield up. */
    public boolean aimShieldRear = true;

    /** Aim at a part of the target that another hitbox does not cover. */
    public boolean aimAvoidCover = true;

    /**
     * Rank candidates by distance with a handicap per kind of entity (players, then armor stands, then everything else) instead of by
     * the crosshair. When false, the crosshair decides and {@link #armorStandPenalty} and {@link #otherPenalty} are unused.
     */
    public boolean priorityTiers = true;

    /** Blocks added to an armor stand's distance when ranking, 0..64. A player is chosen over an armor stand unless it is this much farther. */
    public double armorStandPenalty = 3.0;

    /** The same for every other entity (animals), 0..64. Never below {@link #armorStandPenalty}. */
    public double otherPenalty = 6.0;

    /** Degrees of angular penalty per block of distance, 0..5. Keeps the crosshair the main criterion when {@link #priorityTiers} is off. */
    public double distanceWeight = 0.05;

    /** Extra slack in percent for keeping an existing lock at the range/angle edge, 0..50. */
    public double retainMarginPercent = 0.0;

    /** Ignore targets that are behind blocks. */
    public boolean requireLineOfSight = true;

    // ---- practice camera ---------------------------------------------------------------------------

    /** Whether the camera follows the locked target while the key is held. Off by default. */
    public boolean cameraTrackingEnabled = false;

    /** Responsiveness multiplier, 0.1..5. */
    public double cameraSensitivity = 2.0;

    /** 0 = snappiest, 1 = gentlest. */
    public double cameraSmoothing = 0.2;

    /** Hard cap on camera speed in degrees per second, 10..1440. */
    public double maxRotationSpeed = 540.0;

    /** Hard cap on camera acceleration in degrees per second squared, 50..20000. */
    public double maxRotationAcceleration = 6000.0;

    /** Hard cap on the rotation applied in one frame, 0.1..90 degrees. */
    public double maxStepPerFrame = 45.0;

    /** Remaining error under which the camera stays still, 0..5 degrees. */
    public double deadzoneDegrees = 0.1;

    /** Fade-in time after the assist starts, 0..3 seconds. */
    public double rampSeconds = 0.08;

    /** Lowest pitch the assist may aim at, -90..90 (negative looks up). */
    public double pitchMin = -85.0;

    /** Highest pitch the assist may aim at, -90..90 (positive looks down). */
    public double pitchMax = 85.0;

    /** Back off while the player moves the mouse themselves. */
    public boolean yieldToManualInput = true;

    /** Player turn speed in degrees per second at which the assist has fully backed off, 10..2000. */
    public double yieldRate = 120.0;

    // ---- hitbox visualisation ----------------------------------------------------------------------

    public boolean hitboxEnabled = true;

    /** Boxes farther than this from the camera are not drawn, 1..256 blocks. */
    public double hitboxRenderDistance = 96.0;

    public boolean hitboxFill = true;

    /** Opacity of the fill, 0..1. */
    public double hitboxFillAlpha = 0.12;

    public boolean hitboxAimMarker = true;

    /** Outline width in approximate pixels, 0.5..8. */
    public double hitboxLineWidth = 2.0;

    /** Also outline the other practice entities in range: yellow, or red when the held weapon reaches them. */
    public boolean hitboxShowOthers = true;

    /** At most this many extra outlines, 0..32. */
    public int hitboxMaxOthers = 16;

    /** Colours as {@code #RRGGBB} or {@code #AARRGGBB}. */
    public String hitboxMainColor = "#4D8DFF";
    public String hitboxRangeColor = "#FFD93D";
    public String hitboxAttackColor = "#FF5B54";
    public String hitboxCoverColor = "#B36BFF";
    public String hitboxShieldColor = "#4DE8E0";

    // ---- on-screen text ----------------------------------------------------------------------------

    /**
     * How loud the status text above the hotbar is: {@code "auto"} (the full text for the first few seconds of each hold, then one dim
     * line), {@code "full"} (always the full text), {@code "minimal"} (one dim line) or {@code "off"} (nothing). A key in game cycles
     * auto, minimal and off.
     */
    public String statusStyle = "auto";

    /** How long {@code "auto"} keeps the full text after the key goes down, 0..30 seconds. */
    public double statusFullSeconds = 3.0;

    // ---- debug -------------------------------------------------------------------------------------

    /** Show the on-screen debug panel. Can also be toggled with a key. */
    public boolean debugOverlay = false;

    /** Log activation, target and camera events to the game log. */
    public boolean debugLogging = false;

    // ---- validation --------------------------------------------------------------------------------

    /** Clamps every value into its documented range and repairs unusable ones. Idempotent. */
    public SharpConfig sanitize() {
        practiceMarker = cleanMarker(practiceMarker);

        configVersion = CURRENT_VERSION;
        statusStyle = CueStyle.parse(statusStyle).id();
        statusFullSeconds = clamp(statusFullSeconds, 0.0, 30.0, 3.0);
        range = clamp(range, 1.0, 128.0, 64.0);
        fovDegrees = clamp(fovDegrees, 1.0, 360.0, 140.0);
        aimHeight = clamp(aimHeight, 0.0, 1.0, 0.3);
        distanceWeight = clamp(distanceWeight, 0.0, 5.0, 0.05);
        armorStandPenalty = clamp(armorStandPenalty, 0.0, 64.0, 3.0);
        otherPenalty = Math.max(armorStandPenalty, clamp(otherPenalty, 0.0, 64.0, 6.0));
        retainMarginPercent = clamp(retainMarginPercent, 0.0, 50.0, 0.0);

        cameraSensitivity = clamp(cameraSensitivity, 0.1, 5.0, 2.0);
        cameraSmoothing = clamp(cameraSmoothing, 0.0, 1.0, 0.2);
        maxRotationSpeed = clamp(maxRotationSpeed, 10.0, 1440.0, 540.0);
        maxRotationAcceleration = clamp(maxRotationAcceleration, 50.0, 20000.0, 6000.0);
        maxStepPerFrame = clamp(maxStepPerFrame, 0.1, 90.0, 45.0);
        deadzoneDegrees = clamp(deadzoneDegrees, 0.0, 5.0, 0.1);
        rampSeconds = clamp(rampSeconds, 0.0, 3.0, 0.08);
        pitchMin = clamp(pitchMin, -90.0, 90.0, -85.0);
        pitchMax = clamp(pitchMax, -90.0, 90.0, 85.0);
        if (pitchMax - pitchMin < 10.0) {
            pitchMin = -85.0;
            pitchMax = 85.0;
        }
        yieldRate = clamp(yieldRate, 10.0, 2000.0, 120.0);

        hitboxRenderDistance = clamp(hitboxRenderDistance, 1.0, 256.0, 96.0);
        hitboxFillAlpha = clamp(hitboxFillAlpha, 0.0, 1.0, 0.12);
        hitboxLineWidth = clamp(hitboxLineWidth, 0.5, 8.0, 2.0);
        hitboxMaxOthers = Math.max(0, Math.min(32, hitboxMaxOthers));
        hitboxMainColor = cleanColor(hitboxMainColor, "#4D8DFF");
        hitboxRangeColor = cleanColor(hitboxRangeColor, "#FFD93D");
        hitboxAttackColor = cleanColor(hitboxAttackColor, "#FF5B54");
        hitboxCoverColor = cleanColor(hitboxCoverColor, "#B36BFF");
        hitboxShieldColor = cleanColor(hitboxShieldColor, "#4DE8E0");
        return this;
    }

    /**
     * What to keep from a config written by an older version: the switches the player is likely to have flipped on purpose (enabled,
     * marker, camera tracking, debug), and nothing else. Tuning and colours go back to the current defaults, because an old file's
     * values are only the old defaults written down, and keeping them would hide every improvement.
     */
    public static SharpConfig migratedFrom(SharpConfig old) {
        SharpConfig fresh = new SharpConfig();
        fresh.enabled = old.enabled;
        fresh.requireMarker = true; // the first releases only ever targeted marked entities, so an upgrade keeps that
        fresh.practiceMarker = old.practiceMarker;
        fresh.cameraTrackingEnabled = old.cameraTrackingEnabled;
        fresh.debugOverlay = old.debugOverlay;
        fresh.debugLogging = old.debugLogging;
        return fresh.sanitize();
    }

    // ---- snapshots for the core --------------------------------------------------------------------

    public TargetingSettings targeting() {
        return new TargetingSettings(
                range, fovDegrees, aimHeight, distanceWeight, retainMarginPercent, requireLineOfSight,
                aimFeetFirst, aimShieldRear, aimAvoidCover, priorityTiers, armorStandPenalty, otherPenalty);
    }

    public AimSettings aim() {
        return new AimSettings(
                cameraSensitivity,
                cameraSmoothing,
                maxRotationSpeed,
                maxRotationAcceleration,
                maxStepPerFrame,
                deadzoneDegrees,
                rampSeconds,
                pitchMin,
                pitchMax,
                yieldToManualInput,
                yieldRate);
    }

    public CueStyle cueStyle() {
        return CueStyle.parse(statusStyle);
    }

    public HitboxSettings hitbox() {
        return new HitboxSettings(
                hitboxEnabled,
                hitboxRenderDistance,
                hitboxFill,
                hitboxFillAlpha,
                hitboxAimMarker,
                hitboxLineWidth,
                hitboxShowOthers,
                hitboxMaxOthers,
                Argb.parse(hitboxMainColor, 0xFF4D8DFF),
                Argb.parse(hitboxRangeColor, 0xFFFFD93D),
                Argb.parse(hitboxAttackColor, 0xFFFF5B54),
                Argb.parse(hitboxCoverColor, 0xFFB36BFF),
                Argb.parse(hitboxShieldColor, 0xFF4DE8E0));
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private static double clamp(double v, double lo, double hi, double fallback) {
        if (!Double.isFinite(v)) {
            return fallback;
        }
        return Math.max(lo, Math.min(hi, v));
    }

    private static String cleanMarker(String marker) {
        if (marker == null) {
            return DEFAULT_MARKER;
        }
        String m = marker.trim();
        if (m.isEmpty()) {
            return DEFAULT_MARKER;
        }
        return m.length() > 32 ? m.substring(0, 32) : m;
    }

    /** Normalises a valid colour to canonical upper-case form, and replaces an invalid one with the fallback. */
    private static String cleanColor(String text, String fallback) {
        int fallbackArgb = Argb.parse(fallback, 0xFFFFFFFF);
        return Argb.format(Argb.parse(text, fallbackArgb));
    }
}
