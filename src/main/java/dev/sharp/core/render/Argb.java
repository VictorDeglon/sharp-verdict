package dev.sharp.core.render;

import java.util.Locale;

/** Small helpers for packed 0xAARRGGBB colours. */
public final class Argb {
    private Argb() {
    }

    public static int pack(int a, int r, int g, int b) {
        return (clamp8(a) << 24) | (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    /** Returns the colour with its alpha multiplied by {@code factor} (clamped to the 0..255 range). */
    public static int scaleAlpha(int argb, double factor) {
        int a = (int) Math.round(alpha(argb) * Math.max(0.0, factor));
        return (clamp8(a) << 24) | (argb & 0x00FFFFFF);
    }

    /** Returns the colour's RGB with the given alpha in 0..1. */
    public static int withAlpha(int argb, double alpha01) {
        int a = (int) Math.round(Math.max(0.0, Math.min(1.0, alpha01)) * 255.0);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Parses {@code #RRGGBB}, {@code #AARRGGBB} and the same without the leading {@code #} or with
     * {@code 0x}. Six digits get full opacity. Returns {@code fallback} for anything else.
     */
    public static int parse(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("#")) {
            t = t.substring(1);
        } else if (t.startsWith("0x")) {
            t = t.substring(2);
        }
        if (t.length() != 6 && t.length() != 8) {
            return fallback;
        }
        for (int i = 0; i < t.length(); i++) {
            if (Character.digit(t.charAt(i), 16) < 0) {
                return fallback;
            }
        }
        long value = Long.parseLong(t, 16);
        return t.length() == 6 ? (int) (0xFF000000L | value) : (int) value;
    }

    /** {@code #RRGGBB} when fully opaque, otherwise {@code #AARRGGBB}. */
    public static String format(int argb) {
        return alpha(argb) == 0xFF
                ? String.format("#%06X", argb & 0x00FFFFFF)
                : String.format("#%08X", argb);
    }

    private static int clamp8(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
