package dev.sharp.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class CueStyleTest {
    @Test
    void parsesTheFourNamesInAnyCaseAndWithSpaces() {
        assertEquals(CueStyle.AUTO, CueStyle.parse("auto"));
        assertEquals(CueStyle.FULL, CueStyle.parse("FULL"));
        assertEquals(CueStyle.MINIMAL, CueStyle.parse("  Minimal "));
        assertEquals(CueStyle.OFF, CueStyle.parse("off"));
    }

    @Test
    void anythingElseIsAutoSoATypoNeverSilencesOrShoutsTheText() {
        for (String bad : new String[] {null, "", "  ", "quiet", "false", "0", "of f"}) {
            assertEquals(CueStyle.AUTO, CueStyle.parse(bad), "for: " + bad);
        }
    }

    @Test
    void theIdRoundTrips() {
        for (CueStyle style : CueStyle.values()) {
            assertEquals(style, CueStyle.parse(style.id()));
            assertEquals(style.name().toLowerCase(Locale.ROOT), style.id());
        }
    }

    @Test
    void theCycleKeyGoesAutoMinimalOffAndBackWithoutGettingStuck() {
        assertEquals(CueStyle.MINIMAL, CueStyle.AUTO.next());
        assertEquals(CueStyle.OFF, CueStyle.MINIMAL.next());
        assertEquals(CueStyle.AUTO, CueStyle.OFF.next());
        assertEquals(CueStyle.MINIMAL, CueStyle.FULL.next(), "full is a config choice, the key leaves it");
    }
}
