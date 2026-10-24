package dev.sharp.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArgbTest {
    private static final int FALLBACK = 0xFF123456;

    @Test
    void parsesSixDigitColoursAsOpaque() {
        assertEquals(0xFFFFD23F, Argb.parse("#FFD23F", FALLBACK));
        assertEquals(0xFFFFD23F, Argb.parse("ffd23f", FALLBACK));
        assertEquals(0xFFFFD23F, Argb.parse("0xFFD23F", FALLBACK));
        assertEquals(0xFF000000, Argb.parse("  #000000  ", FALLBACK));
    }

    @Test
    void parsesEightDigitColoursWithTheirAlpha() {
        assertEquals(0x80FF0000, Argb.parse("#80FF0000", FALLBACK));
        assertEquals(0x00FFFFFF, Argb.parse("#00FFFFFF", FALLBACK));
    }

    @Test
    void fallsBackOnGarbage() {
        assertEquals(FALLBACK, Argb.parse(null, FALLBACK));
        assertEquals(FALLBACK, Argb.parse("", FALLBACK));
        assertEquals(FALLBACK, Argb.parse("#12345", FALLBACK));
        assertEquals(FALLBACK, Argb.parse("#GGGGGG", FALLBACK));
        assertEquals(FALLBACK, Argb.parse("red", FALLBACK));
        assertEquals(FALLBACK, Argb.parse("#1234567890", FALLBACK));
    }

    @Test
    void formatAndParseRoundTrip() {
        for (int c : new int[] {0xFFFFD23F, 0xFF000000, 0x80FF0000, 0x00ABCDEF}) {
            assertEquals(c, Argb.parse(Argb.format(c), 0));
        }
        assertEquals("#FFD23F", Argb.format(0xFFFFD23F));
        assertEquals("#80FF0000", Argb.format(0x80FF0000));
    }

    @Test
    void alphaHelpers() {
        assertEquals(0x80FFD23F, Argb.withAlpha(0xFFFFD23F, 0.5019607843));
        assertEquals(0xFFFFD23F, Argb.withAlpha(0x11FFD23F, 7.0));
        assertEquals(0x00FFD23F, Argb.withAlpha(0xFFFFD23F, -1.0));
        assertEquals(0x80FFD23F, Argb.scaleAlpha(0xFFFFD23F, 0.5019607843));
        assertEquals(0xFFFFD23F, Argb.scaleAlpha(0xFFFFD23F, 5.0));
        assertEquals(0x00FFD23F, Argb.scaleAlpha(0xFFFFD23F, -2.0));
        assertEquals(0x40102030, Argb.pack(0x40, 0x10, 0x20, 0x30));
        assertEquals(0xFF00FF00, Argb.pack(999, -5, 255, 0));
    }
}
