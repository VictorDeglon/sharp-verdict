package dev.sharp.core.hud;

import java.util.Locale;

/** How loud the status text is. The colours of the outlines are unaffected by any of these. */
public enum CueStyle {
    /** The full text for the first few seconds of each hold, then one dim line. The default. */
    AUTO,
    /** The full text for as long as the key is held. */
    FULL,
    /** One dim line, always. */
    MINIMAL,
    /** No text at all. */
    OFF;

    /** The name used in the config file and the on-screen message. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Reads a config value. Anything unknown is {@link #AUTO}, so a typo never turns the text off or makes it louder. */
    public static CueStyle parse(String text) {
        if (text != null) {
            String t = text.trim().toLowerCase(Locale.ROOT);
            for (CueStyle style : values()) {
                if (style.id().equals(t)) {
                    return style;
                }
            }
        }
        return AUTO;
    }

    /** What the cycle key switches to: auto, then minimal, then off, then back to auto. "Full" is only reachable from the config. */
    public CueStyle next() {
        return switch (this) {
            case AUTO, FULL -> MINIMAL;
            case MINIMAL -> OFF;
            case OFF -> AUTO;
        };
    }
}
