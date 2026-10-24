package dev.sharp.core.targeting;

import dev.sharp.core.math.V3;

/** Where the viewer's eyes are and which way they point. */
public record EyeState(V3 eye, double yawDegrees, double pitchDegrees) {
}
