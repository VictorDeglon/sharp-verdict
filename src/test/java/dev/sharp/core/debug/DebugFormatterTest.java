package dev.sharp.core.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sharp.core.activation.ActivationTracker.Reason;
import dev.sharp.core.camera.AimStep;
import dev.sharp.core.math.V3;
import dev.sharp.core.targeting.AimPointPicker.Zone;
import dev.sharp.core.targeting.Exposure;
import dev.sharp.core.targeting.Selection;
import java.util.List;
import org.junit.jupiter.api.Test;

class DebugFormatterTest {
    private static String joined(DebugSnapshot d) {
        return String.join("\n", DebugFormatter.lines(d));
    }

    private static DebugSnapshot off() {
        return new DebugSnapshot(false, false, true, Reason.KEY_RELEASED, 0, "OPEN",
                Selection.empty(), "", false, null, 0, 0, 0,
                false, Exposure.OPEN, Zone.MIDDLE, false, 0, 0, 0);
    }

    @Test
    void showsTheActivationStateAndLastReasonWhenOff() {
        String text = joined(off());
        assertTrue(text.contains("state: off"), text);
        assertTrue(text.contains("KEY_RELEASED"), text);
        assertTrue(text.contains("gate: OPEN"), text);
        assertTrue(text.contains("armed: yes"), text);
        assertTrue(text.contains("target: -"), text);
        assertTrue(text.contains("tracking off"), text);
    }

    @Test
    void showsTargetDistanceAndAngularErrorWhenLocked() {
        Selection s = new Selection(42, Selection.Kind.KEPT, new V3(1, 2, 3), 3.456, 7.89, 5, 2, List.of(42, 7));
        DebugSnapshot d = new DebugSnapshot(true, true, false, Reason.NONE, 2.34, "OPEN",
                s, "sharp dummy", false, null, 3.456, 0, 0,
                false, Exposure.OPEN, Zone.MIDDLE, false, 0, 0, 0);
        String text = joined(d);
        assertTrue(text.contains("state: ACTIVE (2.3s)"), text);
        assertTrue(text.contains("key: held"), text);
        assertTrue(text.contains("#42 \"sharp dummy\""), text);
        assertTrue(text.contains("KEPT"), text);
        assertTrue(text.contains("dist 7.9"), text);
        assertTrue(text.contains("err 3.46 deg"), text);
        assertTrue(text.contains("seen 5  eligible 2"), text);
    }

    @Test
    void showsCameraErrorsStepAndFactorsWhenTracking() {
        Selection s = new Selection(1, Selection.Kind.KEPT, new V3(0, 0, 0), 10, 5, 1, 1, List.of(1));
        AimStep aim = new AimStep(0.1234, -0.0567, 12.5, -3.25, 12.9, 0.5, 0.75);
        DebugSnapshot d = new DebugSnapshot(true, true, false, Reason.NONE, 1, "OPEN",
                s, "t", true, aim, 12.9, 45.5, -12.25,
                false, Exposure.OPEN, Zone.MIDDLE, false, 0, 0, 0);
        String text = joined(d);
        assertTrue(text.contains("err yaw 12.50 pitch -3.25"), text);
        assertTrue(text.contains("step 0.123 / -0.057"), text);
        assertTrue(text.contains("yield 50%"), text);
        assertTrue(text.contains("ramp 75%"), text);
    }

    @Test
    void usesADotAsDecimalSeparatorWhateverTheDefaultLocale() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            Selection s = new Selection(1, Selection.Kind.KEPT, new V3(0, 0, 0), 1.5, 2.5, 1, 1, List.of(1));
            DebugSnapshot d = new DebugSnapshot(true, true, false, Reason.NONE, 1.5, "OPEN",
                    s, "t", false, null, 1.5, 0, 0,
                false, Exposure.OPEN, Zone.MIDDLE, false, 0, 0, 0);
            assertTrue(joined(d).contains("dist 2.5"), joined(d));
            assertEquals(-1, joined(d).indexOf("2,5"));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    void sayswhenThereIsNoTargetEvenThoughActive() {
        Selection none = Selection.none(Selection.Kind.LOST, 3, 0, List.of());
        DebugSnapshot d = new DebugSnapshot(true, true, false, Reason.NONE, 1, "OPEN",
                none, "", true, null, 0, 0, 0,
                false, Exposure.OPEN, Zone.MIDDLE, false, 0, 0, 0);
        String text = joined(d);
        assertTrue(text.contains("target: none (LOST)"), text);
        assertTrue(text.contains("tracking on, idle"), text);
    }

    @Test
    void showsReachWhereItAimsAndWhatIsInTheWay() {
        Selection s = new Selection(1, Selection.Kind.KEPT, new V3(0, 0, 0), 2, 5, 3, 3, List.of(1));
        DebugSnapshot d = new DebugSnapshot(true, true, false, Reason.NONE, 1, "OPEN",
                s, "t", false, null, 2, 0, 0,
                true, Exposure.SHIELDED, Zone.FEET, true, 4, 2, 0);
        String text = joined(d);
        assertTrue(text.contains("IN REACH"), text);
        assertTrue(text.contains("aim: feet  exposure SHIELDED (shield blocks you)  others 4 (2 in reach)"), text);
    }
}
